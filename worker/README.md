# SoundFlow 轉檔服務（N100）

把 App 送出的 YouTube 網址（`yt_job`）轉成 Opus 音檔，上傳私人 bucket `sf`，寫入 `sf_track`。

## 架構

```
App 分享 → yt_job（queued）
N100 soundflow-worker（每 15 秒看一次）
  → 搶工作（status=processing）→ yt-dlp 取資訊 → 下載最佳音軌 → ffmpeg 轉 Opus 單聲道
  → 上傳 sf/yt/<影片id>-<隨機碼>.opus → 新增 sf_track → yt_job（done）
每 5 分鐘寫 sf_worker_heartbeat；每天 04:30 更新 yt-dlp
```

- 位元率依長度：100 分鐘內 64 kbps、140 分鐘內 48 kbps、其餘 32 kbps；超過 3 小時拒絕（Supabase 免費方案單檔 50 MB）
- yt-dlp 2025.11 起解 YouTube 需要外部 JavaScript 執行環境；N100 已有 Node 22，用 `--js-runtimes node`，不另裝 Deno
- 暫時性錯誤（被當機器人、429、網路）自動重試一次；其他錯誤標成 failed，App 上可按「重試」
- 服務重啟時，超過 30 分鐘還在 processing 的工作改回排隊

## 安裝（在 N100）

需要：Python 3.10+、ffmpeg、Node（N100 都已經有）。

```bash
# 1. 把 worker/ 複製到 N100（在 Windows 執行）
scp -r D:/dev/APK-SoundFlow/worker n100:~/soundflow-worker-src

# 2. 在 N100 安裝程式、yt-dlp 與 systemd 單元（不會自動啟動）
ssh n100 'bash ~/soundflow-worker-src/deploy/install.sh'

# 3. 設定金鑰：要互動輸入，請自己 ssh 進去執行（金鑰不經過任何對話或 Git）
ssh -t n100 'bash ~/soundflow-worker-src/deploy/install.sh set-key'

# 4. 啟動服務與每日更新
ssh n100 'bash ~/soundflow-worker-src/deploy/install.sh start'
```

service role key 在 Supabase 後台：Project Settings → API Keys → `service_role`（或 Secret key）。

### 金鑰為什麼不用 dotenvx

N100 沒有安裝 dotenvx；若要在 N100 用 dotenvx 解密，就得把**所有專案共用的** `DOTENV_PRIVATE_KEY` 放到 N100 上，外洩影響範圍更大。所以這個服務只放它自己需要的一把金鑰：`~/.config/soundflow/worker.env`，權限 600、資料夾 700，不在任何 Git repo 裡，由 systemd 的 `EnvironmentFile` 讀取。（倬 2026-10-26 確認）

## 日常維運

```bash
journalctl --user -u soundflow-worker -f          # 即時紀錄
systemctl --user restart soundflow-worker         # 重啟
systemctl --user list-timers soundflow-ytdlp-update.timer
~/apps/soundflow-worker/.venv/bin/yt-dlp --version
```

更新程式：重複安裝步驟 1、2，再 `systemctl --user restart soundflow-worker`。

移除：`systemctl --user disable --now soundflow-worker soundflow-ytdlp-update.timer`，再把 `~/apps/soundflow-worker` 移到垃圾桶。

## 測試

```bash
python -m unittest test_sfw_logic -v      # 純邏輯（位元率、錯誤訊息、拒絕條件）
```

## 注意

- 下載 YouTube 音軌違反 YouTube 服務條款：僅供倬個人收聽；bucket `sf` 是私人的，只有白名單成員登入後能播放
- 如果 YouTube 開始要求驗證（錯誤「被當成機器人」一直出現），可能需要 cookies；使用帳號 cookies 有被封鎖的風險，要先跟倬確認
