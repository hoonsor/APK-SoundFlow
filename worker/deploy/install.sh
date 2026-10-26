#!/usr/bin/env bash
# SoundFlow 轉檔服務安裝（在 N100 上執行；可重複執行）
#   bash install.sh            安裝或更新程式、yt-dlp、systemd 單元（不會自動啟動）
#   bash install.sh set-key    輸入 Supabase service role key（不顯示、不進 Git）
#   bash install.sh start      啟用並啟動服務與每日更新
set -euo pipefail
APP="$HOME/apps/soundflow-worker"
CONF="$HOME/.config/soundflow"
UNITS="$HOME/.config/systemd/user"
SRC="$(cd "$(dirname "$0")/.." && pwd)"

case "${1:-install}" in
  install)
    mkdir -p "$APP" "$UNITS"
    cp "$SRC/soundflow_worker.py" "$SRC/sfw_logic.py" "$APP/"
    [ -d "$APP/.venv" ] || python3 -m venv "$APP/.venv"
    "$APP/.venv/bin/pip" install --quiet --upgrade pip "yt-dlp[default]"   # [default] 含 yt-dlp-ejs
    cp "$SRC"/deploy/soundflow-*.service "$SRC"/deploy/soundflow-*.timer "$UNITS/"
    systemctl --user daemon-reload
    command -v ffmpeg >/dev/null || echo "⚠ 找不到 ffmpeg：sudo apt install ffmpeg"
    command -v node >/dev/null || echo "⚠ 沒有 node：新版 yt-dlp 解 YouTube 需要 JavaScript 執行環境（Node 或 Deno），見 README"
    echo "安裝完成（yt-dlp $("$APP/.venv/bin/yt-dlp" --version)）。下一步：bash install.sh set-key"
    ;;
  set-key)
    mkdir -p "$CONF"; chmod 700 "$CONF"
    read -r -s -p "貼上 Supabase service role key（不會顯示）：" KEY; echo
    [ -n "$KEY" ] || { echo "沒有輸入，已取消"; exit 1; }
    umask 077
    printf 'SUPABASE_URL=%s\nSUPABASE_SERVICE_ROLE_KEY=%s\n' "https://yczvjdawynmcorvauyey.supabase.co" "$KEY" > "$CONF/worker.env"
    unset KEY
    echo "已寫入 $CONF/worker.env（權限 600）。下一步：bash install.sh start"
    ;;
  start)
    [ -f "$CONF/worker.env" ] || { echo "還沒設定金鑰：bash install.sh set-key"; exit 1; }
    systemctl --user enable --now soundflow-worker.service soundflow-ytdlp-update.timer
    sleep 3
    systemctl --user --no-pager status soundflow-worker.service | head -12
    echo "看紀錄：journalctl --user -u soundflow-worker -f"
    ;;
  *) echo "用法：bash install.sh [install|set-key|start]"; exit 1 ;;
esac
