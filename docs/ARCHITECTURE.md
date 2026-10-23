# 架構草案 — SoundFlow

> 狀態：規劃中（2026-10-23）。功能清單確認後才進入語音 API 規劃與版面設計，確認前不寫功能程式。

## 1. 一張圖

```
【Podcast 來源：既有，27 號新知雷達】
網站「送進 NotebookLM」→ nlm_job（queued）→ 電腦 A nlm runner → m4a 上傳 Supabase nlm bucket → nlm_job（done）
                                                                         │ 資料庫觸發器
                                                                         ▼
【YouTube 來源：新建】                                               sf_track（統一的音軌表）
手機 YouTube「分享 → SoundFlow」→ yt_job（queued）                      ▲
  → N100 轉檔服務（Realtime 監聽）→ yt-dlp＋ffmpeg（Opus 64k）→ 上傳 Supabase sf bucket ┘

【封存：新建，N100 每日】
sf_track 中超過 30 天或 Supabase 用量 > 800 MB 的音檔 → 上傳 Google Drive「SoundFlow」資料夾
  → 驗證大小 → 寫回 drive_file_id、tier = 'drive' → 刪 Supabase 的檔案

【播放：SoundFlow App】
tier = 'supabase' → 直接用公開／簽章網址串流
tier = 'drive'    → 向 Edge Function `drive-token` 取 1 小時權杖 → ExoPlayer 帶 Authorization 標頭串流 Drive
有本機快取        → 直接播本機檔（離線可用）
```

## 2. 關鍵決定與原因

| 決定 | 原因 | 放棄的方案＋原因 |
|---|---|---|
| Podcast 直接讀 27 號的 `nlm_job`，用觸發器同步進 `sf_track` | 資料已存在，不重複建；27 號 runner 不用改 | 改 27 號 runner 直接寫 sf_track：要動另一個專案，且兩邊耦合 |
| 統一的 `sf_track` 表（kind = podcast／youtube） | App 只讀一張表，分頁只是篩選；改名、刪除、進度都掛在同一處 | App 分別讀 nlm_job 與 yt_job：每個功能都要寫兩份 |
| 兩層儲存：Supabase 暫存＋Google Drive 封存（倬 2026-10-23 決定） | 新知網站播放器不用改；Supabase 1 GB 只需放最近的；Drive 容量大 | 只用 Supabase：約 100 集就滿；全部直接放 Drive：27 號網站播放器也要改 |
| Drive 串流用 Edge Function 發短效權杖，OAuth 權限只用 `drive.file` | 手機不存長期憑證；權杖只能存取本 App 上傳的檔案，外洩影響範圍最小 | 服務帳號：2025 年起服務帳號沒有儲存配額，無法擁有檔案；公開分享連結：大檔有病毒掃描攔截頁、任何人都能下載 |
| YouTube 轉檔在 N100（倬 2026-10-23 決定） | 24 小時開機，分享後幾分鐘就能聽 | 電腦 A：關機時排隊；GitHub Actions：YouTube 封鎖資料中心 IP，不可靠 |
| 轉成 Opus 64 kbps 單聲道 | 語音內容清楚，檔案約為原始 m4a 的 1/3，Android 原生支援 | 保留原始音軌：每小時 60～100 MB |
| 手機端 Media3（ExoPlayer＋MediaSessionService）＋Glance 小工具 | Android 官方播放架構，通知列、鎖屏、藍牙、Android Auto 都靠 MediaSession 免費得到 | 自刻 MediaPlayer：要自己處理音訊焦點與通知，容易出錯 |
| 使用同一個 Supabase 專案（AI_News） | 要讀 nlm_job，跨專案查詢做不到 | 新專案：要同步資料 |

## 3. 資料表草案

```sql
sf_track(
  id uuid pk, kind text check (kind in ('podcast','youtube')),
  nlm_item_id text null,            -- Podcast 對應 nlm_job.item_id
  title text, original_title text,  -- title 是顯示名稱（可改），original_title 不變
  channel text, source_url text, thumbnail_url text,
  duration_sec int, size_bytes bigint, mime text,
  tier text check (tier in ('supabase','drive')),
  storage_path text, drive_file_id text,
  hidden boolean default false, deleted_at timestamptz,   -- 垃圾桶
  created_at, updated_at
)
yt_job(id uuid pk, url text, status queued|processing|done|failed, error text, track_id uuid null, requested_at, updated_at)
sf_progress(track_id pk, position_ms int, played boolean, last_played_at)
sf_playlist(id, name, sort_order, created_at) / sf_playlist_item(playlist_id, track_id, sort_order)
sf_worker_heartbeat(worker text pk, seen_at, version, yt_dlp_version)
```

權限（建議，未確認）：App 用 Supabase Auth（hoonsor-kit 的登入模組）＋RLS 限本人；N100 用 service role key（dotenvx 加密）。

## 4. 已知風險與坑（實作前就要處理）

- **Google OAuth 同意畫面停在「測試」狀態時，refresh token 7 天就失效** → 要把 OAuth 用戶端發布為「正式」（只用 `drive.file` 這類非敏感權限，個人使用不需 Google 審查；實作時再確認）
- **yt-dlp 常因 YouTube 改版失效** → 每日自動更新；失敗率升高時通知
- **YouTube 可能要求登入或 PO token** → 家用 IP 通常沒事；若遇到再評估 cookies（帳號風險要先跟倬確認）
- **下載 YouTube 音軌違反 YouTube 服務條款**：僅限個人收聽、不公開分享，倬已知悉再實作
- **ColorOS 會殺背景播放與小工具更新** → 健康檢查頁引導關閉電池最佳化、開自啟動
- **Gradle 不支援中文路徑** → 程式碼放 `D:\dev\APK-SoundFlow`
