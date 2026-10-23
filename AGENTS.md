# AGENTS.md — SoundFlow

> 給接手的 AI 讀。目前在**規劃階段**（功能清單待倬確認），尚無程式碼。先讀 `PROJECT_STATUS.md` 的「下一步」。

## 專案目的

倬的手機音訊播放器（Android）。播放兩種來源：
1. 27 號「新知雷達」送進 NotebookLM 產生的 Podcast（已存在 Supabase）
2. 從手機 YouTube App 分享進來、由 N100 抽成音檔的 YouTube 影片

外觀與操作比照一般音樂播放器（參考 Spotify、YouTube Music），有桌面小工具，不用再開網頁或 YouTube。

## 文件地圖

| 檔案 | 內容 |
|---|---|
| `docs/FEATURES.md` | 帶編號功能清單、優先、語音欄、倬確認日期、不做的項目 |
| `docs/ARCHITECTURE.md` | 架構圖、關鍵決定＋原因、資料表草案、已知風險 |
| `PROJECT_STATUS.md` | 版本、任務勾選、交接紀錄、下一步 |

## 技術棧（預定，依倬的 Android SOP）

- Android：Kotlin、Compose＋M3、Media3（ExoPlayer＋MediaSessionService）、Glance 小工具、minSdk 30／targetSdk 35、套件名 `tw.hoonsor.soundflow`
- 後端：Supabase（沿用 27 號的 AI_News 專案）、Edge Function `drive-token`
- 轉檔與封存：N100 拉斐爾主機上的服務（yt-dlp、ffmpeg、Google Drive API）
- 編譯：GitHub Actions（repo `hoonsor/APK-SoundFlow`，尚未建立）

## 規則

- Always：改程式前先 commit；動作一律走 Repository；文件只寫相對路徑；決定與放棄的方案都寫原因
- Ask：建立 GitHub repo、動 27 號專案的程式或資料表、在 N100 上安裝服務、Google Cloud OAuth 設定
- Never：提交任何 key／token／簽章檔；讀取或顯示 dotenvx 解密後的值；force push
