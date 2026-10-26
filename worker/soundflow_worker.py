"""SoundFlow YouTube 轉檔服務（N100，systemd 使用者服務）。

流程：每 15 秒看一次 yt_job 有沒有排隊的工作 → 搶到就把狀態改成 processing
→ yt-dlp 取資訊＋下載音軌 → ffmpeg 轉 Opus 單聲道 → 上傳私人 bucket sf → 寫入 sf_track → 工作改成 done。
每 5 分鐘寫一次心跳（sf_worker_heartbeat），App 的健康檢查與 YouTube 分頁會顯示。

環境變數（由 systemd 的 EnvironmentFile 提供，值不寫進任何 repo）：
  SUPABASE_URL、SUPABASE_SERVICE_ROLE_KEY
選填：SFW_YTDLP（yt-dlp 執行檔路徑）、SFW_JS_RUNTIME（預設 node）、SFW_POLL（秒，預設 15）、SFW_NAME（預設 n100）
只用 Python 標準庫，另外需要 yt-dlp 與 ffmpeg。
"""
from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

import sfw_logic as L

VERSION = "0.4.0"
log = logging.getLogger("soundflow-worker")


class Supabase:
    def __init__(self, url: str, key: str):
        self.url = url.rstrip("/")
        self.key = key

    def _req(self, method: str, path: str, body: bytes | None = None, headers: dict | None = None, timeout: int = 30):
        h = {"apikey": self.key, "Authorization": f"Bearer {self.key}"}
        h.update(headers or {})
        req = urllib.request.Request(self.url + path, data=body, method=method, headers=h)
        with urllib.request.urlopen(req, timeout=timeout) as res:
            raw = res.read()
            return json.loads(raw) if raw and res.headers.get_content_type() == "application/json" else None

    def rest(self, method: str, path: str, payload=None, prefer: str = "return=representation"):
        body = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
        return self._req(method, "/rest/v1/" + path, body, {"Content-Type": "application/json", "Prefer": prefer})

    def upload(self, bucket: str, name: str, file: Path, mime: str):
        quoted = urllib.parse.quote(name)
        with file.open("rb") as f:
            self._req("POST", f"/storage/v1/object/{bucket}/{quoted}", f.read(),
                      {"Content-Type": mime, "x-upsert": "true", "cache-control": "31536000"}, timeout=300)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def ytdlp_bin() -> str:
    return os.environ.get("SFW_YTDLP") or shutil.which("yt-dlp") or str(Path(sys.executable).with_name("yt-dlp"))


def ytdlp_base() -> list[str]:
    """yt-dlp 2025.11 起解 YouTube 需要外部 JavaScript 執行環境；N100 已有 Node 22，不必另裝 Deno"""
    return [ytdlp_bin(), "--js-runtimes", os.environ.get("SFW_JS_RUNTIME", "node"), "--no-playlist", "--no-warnings"]


def ytdlp_version() -> str:
    try:
        return subprocess.run([ytdlp_bin(), "--version"], capture_output=True, text=True, timeout=30).stdout.strip()
    except Exception:  # noqa: BLE001 — 版本只是顯示用
        return "unknown"


class JobError(Exception):
    def __init__(self, message: str, transient: bool = False):
        super().__init__(message)
        self.transient = transient


def run(cmd: list[str], timeout: int) -> subprocess.CompletedProcess:
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    if p.returncode != 0:
        msg, transient = L.explain_error(p.stderr)
        raise JobError(msg, transient)
    return p


def fetch_metadata(url: str) -> dict:
    p = run([*ytdlp_base(), "-J", url], timeout=120)
    return json.loads(p.stdout)


def download_and_encode(url: str, workdir: Path, kbps: int) -> Path:
    raw = workdir / "source"
    run([*ytdlp_base(), "-f", "bestaudio/best", "--no-part", "-o", str(raw) + ".%(ext)s", url], timeout=3600)
    source = next(workdir.glob("source.*"))
    out = workdir / "audio.opus"
    p = subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(source), "-vn", "-ac", "1",
         "-c:a", "libopus", "-b:a", f"{kbps}k", "-application", "audio", str(out)],
        capture_output=True, text=True, timeout=3600,
    )
    if p.returncode != 0:
        raise JobError(f"ffmpeg 轉檔失敗：{p.stderr.strip()[-160:]}")
    return out


class Worker:
    def __init__(self, db: Supabase, name: str):
        self.db = db
        self.name = name
        self.last_beat = 0.0

    def heartbeat(self, force: bool = False):
        if not force and time.time() - self.last_beat < 300:
            return
        try:
            self.db.rest("POST", "sf_worker_heartbeat?on_conflict=worker", {
                "worker": self.name, "seen_at": now_iso(), "version": VERSION, "yt_dlp_version": ytdlp_version(),
                "info": {"python": sys.version.split()[0]},
            }, prefer="resolution=merge-duplicates,return=minimal")
            self.last_beat = time.time()
        except Exception as e:  # noqa: BLE001
            log.warning("心跳失敗：%s", e)

    def requeue_stale(self):
        """N100 重開機或服務當掉時，轉到一半的工作改回排隊"""
        cutoff = (datetime.now(timezone.utc) - timedelta(minutes=30)).isoformat()
        self.db.rest("PATCH", f"yt_job?status=eq.processing&started_at=lt.{urllib.parse.quote(cutoff)}",
                     {"status": "queued"}, prefer="return=minimal")

    def claim(self) -> dict | None:
        rows = self.db.rest("GET", "yt_job?select=id,url,video_id,attempts&status=eq.queued&order=requested_at.asc&limit=1")
        if not rows:
            return None
        job = rows[0]
        # 只有狀態還是 queued 才搶得到；被取消或別台搶走時回傳空陣列
        got = self.db.rest("PATCH", f"yt_job?id=eq.{job['id']}&status=eq.queued",
                           {"status": "processing", "started_at": now_iso(), "attempts": job["attempts"] + 1, "error": None})
        return got[0] if got else None

    def process(self, job: dict):
        vid, url = job["video_id"], job["url"]
        log.info("開始 %s %s", job["id"], url)
        existing = self.db.rest("GET", f"sf_track?select=id&video_id=eq.{vid}&limit=1")
        if existing:
            self.finish(job, existing[0]["id"])
            return
        meta = fetch_metadata(url)
        self.db.rest("PATCH", f"yt_job?id=eq.{job['id']}", {"title": meta.get("title")}, prefer="return=minimal")
        reason = L.check_metadata(meta)
        if reason:
            raise JobError(reason)
        duration = int(meta.get("duration") or 0)
        kbps = L.choose_bitrate(duration)
        with tempfile.TemporaryDirectory(prefix="sfw-") as tmp:
            out = download_and_encode(url, Path(tmp), kbps)
            size = out.stat().st_size
            if size > L.MAX_BYTES:
                raise JobError(f"轉完的檔案 {size // 1048576} MB，超過 50 MB 上限")
            name = L.object_name(vid)
            self.db.upload("sf", name, out, "audio/ogg")
        row = L.track_row(meta, vid, name, size)
        created = self.db.rest("POST", "sf_track", row)
        self.finish(job, created[0]["id"])
        log.info("完成 %s「%s」%d KB", vid, row["title"], size // 1024)

    def finish(self, job: dict, track_id: str):
        self.db.rest("PATCH", f"yt_job?id=eq.{job['id']}", {"status": "done", "track_id": track_id, "error": None}, prefer="return=minimal")

    def fail(self, job: dict, e: JobError):
        # 暫時性錯誤自動重試一次（attempts 由 claim 累加）
        retry = e.transient and job.get("attempts", 1) < 2
        self.db.rest("PATCH", f"yt_job?id=eq.{job['id']}",
                     {"status": "queued" if retry else "failed", "error": str(e)}, prefer="return=minimal")
        log.warning("%s %s：%s", "稍後重試" if retry else "失敗", job["id"], e)
        if retry:
            time.sleep(60)

    def loop(self, poll: int):
        log.info("SoundFlow worker %s 啟動（%s，yt-dlp %s）", VERSION, self.name, ytdlp_version())
        self.heartbeat(force=True)
        self.requeue_stale()
        while True:
            self.heartbeat()
            job = None
            try:
                job = self.claim()
                if job:
                    self.process(job)
                    continue
            except JobError as e:
                if job:
                    self.fail(job, e)
                continue
            except urllib.error.URLError as e:
                log.warning("連不上 Supabase：%s", e)
                if job:
                    self.fail(job, JobError("N100 連不上 Supabase，稍後會自動重試", transient=True))
            except Exception as e:  # noqa: BLE001 — 單一工作出錯不能讓整個服務掛掉
                log.exception("未預期的錯誤")
                if job:
                    self.fail(job, JobError(f"N100 發生錯誤：{type(e).__name__}"))
            time.sleep(poll)


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not key:
        sys.exit("缺少 SUPABASE_URL 或 SUPABASE_SERVICE_ROLE_KEY（見 worker/README.md）")
    if key.startswith("encrypted:"):
        sys.exit("SUPABASE_SERVICE_ROLE_KEY 還是 dotenvx 密文，請照 worker/README.md 設定")
    if not shutil.which("ffmpeg"):
        sys.exit("找不到 ffmpeg")
    Worker(Supabase(url, key), os.environ.get("SFW_NAME", "n100")).loop(int(os.environ.get("SFW_POLL", "15")))


if __name__ == "__main__":
    main()
