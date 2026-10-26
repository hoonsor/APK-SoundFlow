"""SoundFlow 轉檔服務的純邏輯（沒有網路、沒有 subprocess），test_sfw_logic.py 涵蓋。"""
from __future__ import annotations

import re
import secrets

MAX_SECONDS = 3 * 3600          # 超過 3 小時不收（Supabase 免費方案單檔 50 MB）
MAX_BYTES = 50 * 1024 * 1024
_ID = re.compile(r"^[A-Za-z0-9_-]{11}$")


def choose_bitrate(duration_sec: int | None) -> int:
    """Opus 單聲道位元率（kbps）：語音 32～64k 都清楚；長片降位元率才放得進 50 MB。"""
    d = duration_sec or 0
    if d <= 100 * 60:
        return 64
    if d <= 140 * 60:
        return 48
    return 32


def estimate_bytes(duration_sec: int, kbps: int) -> int:
    return int(duration_sec * kbps * 1000 / 8 * 1.02)  # 1.02：Ogg 封裝


def object_name(video_id: str) -> str:
    """Storage 路徑：yt/<影片 id>-<隨機碼>.opus（bucket 是私人的，隨機碼只是避免覆蓋）"""
    if not _ID.match(video_id):
        raise ValueError(f"不合法的影片 id：{video_id}")
    return f"yt/{video_id}-{secrets.token_hex(4)}.opus"


# yt-dlp 的錯誤 → App 上顯示的中文；第二個值：是不是暫時性錯誤（可以自動重試一次）
_ERRORS: list[tuple[str, str, bool]] = [
    (r"confirm you.?re not a bot|sign in to confirm", "YouTube 要求驗證（被當成機器人），稍後會自動重試", True),
    (r"age.?restrict|confirm your age|inappropriate for some users", "影片有年齡限制，需要登入才能看，無法轉檔", False),
    (r"private video", "這是私人影片，無法轉檔", False),
    (r"members.?only|join this channel", "會員限定影片，無法轉檔", False),
    (r"premieres? in|this live event will begin|is_upcoming", "影片還沒開始播出，等首播後再分享", False),
    (r"video unavailable|has been removed|does not exist|no longer available", "影片不存在或已被移除", False),
    (r"copyright", "影片因版權被下架", False),
    (r"http error 429|too many requests", "YouTube 暫時限制下載次數，稍後會自動重試", True),
    (r"timed out|connection (reset|refused)|temporary failure in name resolution|network is unreachable", "N100 網路不穩，稍後會自動重試", True),
]


def explain_error(stderr: str) -> tuple[str, bool]:
    low = (stderr or "").lower()
    for pattern, message, transient in _ERRORS:
        if re.search(pattern, low):
            return message, transient
    last = next((ln.strip() for ln in reversed((stderr or "").splitlines()) if ln.strip()), "")
    return (f"轉檔失敗：{last[:160]}" if last else "轉檔失敗（原因不明）"), False


def check_metadata(meta: dict) -> str | None:
    """下載前就能判斷的拒絕原因；可以轉就回 None。"""
    if meta.get("is_live") or meta.get("live_status") == "is_live":
        return "直播還沒結束，結束後再分享"
    if meta.get("live_status") == "is_upcoming":
        return "影片還沒開始播出，等首播後再分享"
    duration = meta.get("duration") or 0
    if duration > MAX_SECONDS:
        return f"影片太長（{duration // 3600} 小時 {duration % 3600 // 60} 分），超過 3 小時的不轉檔"
    return None


def track_row(meta: dict, video_id: str, path: str, size: int) -> dict:
    title = (meta.get("title") or video_id).strip()
    return {
        "kind": "youtube",
        "title": title,
        "original_title": title,
        "channel": meta.get("channel") or meta.get("uploader"),
        "source_url": f"https://www.youtube.com/watch?v={video_id}",
        "thumbnail_url": meta.get("thumbnail"),
        "duration_sec": int(meta["duration"]) if meta.get("duration") else None,
        "size_bytes": size,
        "mime": "audio/ogg",
        "tier": "supabase",
        "storage_bucket": "sf",
        "storage_path": path,
        "video_id": video_id,
    }


# ── 垃圾桶永久刪除（v0.5.0）────────────────────────────────────────
TRASH_DAYS = 7


def purge_due(row: dict, now_iso_text: str) -> bool:
    """要不要永久刪除：使用者按了「立即永久刪除」，或進垃圾桶超過 7 天"""
    from datetime import datetime, timedelta
    if row.get("purged_at"):
        return False
    if row.get("purge_requested_at"):
        return True
    deleted = row.get("deleted_at")
    if not deleted:
        return False
    now = datetime.fromisoformat(now_iso_text)
    return datetime.fromisoformat(deleted) <= now - timedelta(days=TRASH_DAYS)


def should_delete_file(row: dict) -> bool:
    """YouTube 一律刪實體檔；Podcast 只有倬選了「連新知雷達的音檔一起刪」才刪"""
    if row.get("tier") != "supabase" or not row.get("storage_bucket") or not row.get("storage_path"):
        return False
    return row.get("kind") == "youtube" or bool(row.get("delete_source"))
