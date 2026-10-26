# AGENTS.md — SoundFlow

> 給接手的 AI 讀。先讀 `PROJECT_STATUS.md` 的「下一步」與最新一筆交接紀錄，不要叫倬重講背景。

## 專案目的

倬的手機音訊播放器（Android）。播放兩種來源：
1. 27 號「新知雷達」送進 NotebookLM 產生的 Podcast（已存在 Supabase 的 `nlm_job` 表與 `nlm` bucket）
2. 從手機 YouTube App 分享進來、由 N100 抽成音檔的 YouTube 影片

外觀與操作比照一般音樂播放器（參考 Spotify、YouTube Music），有桌面小工具，不用再開網頁或 YouTube。

## 文件地圖

| 檔案 | 內容 |
|---|---|
| `docs/FEATURES.md` | 帶編號功能清單（F01–F50）、優先、語音欄、倬確認日期、不做的項目 |
| `docs/ARCHITECTURE.md` | 架構圖、關鍵決定＋原因、資料表草案、已知風險 |
| `docs/DESIGN.md` | 設計規範：C 訊號格版面、3 種佈景主題色票（唯一來源）、元件規則；畫布網址 |
| `docs/AGENT_API.md` | 21 個語音動作的回傳與錯誤（規劃中） |
| `docs/agent-manifest.json` | 語音動作的機器可讀清單；`describe` 直接回傳它 |
| `PROJECT_STATUS.md` | 版本、任務勾選、交接紀錄、下一步 |
| `README.md` | 給倬的使用說明 |

## 技術棧

- Kotlin 2.1、Compose（BOM 2025.01）＋M3、DataStore；minSdk 30／targetSdk 35；套件名 `tw.hoonsor.soundflow`（LAB 版 `.lab`）
- 預定加入：Media3（ExoPlayer＋MediaSessionService）、Glance 小工具、Room（本機快取）、Supabase（沿用 27 號的 AI_News 專案）
- 版本集中在 `gradle/libs.versions.toml`；AGP 8.7.3、Gradle 8.11.1
- 轉檔與封存：N100 上的服務（yt-dlp、ffmpeg、Google Drive API），尚未建立

## 建置

- 本機：JDK 21（`C:\Users\hoonsor\.jdks\jbr-21.0.11`），**不要用 Android Studio 內附的 JBR 25**（Gradle 8.11 不支援）
  ```powershell
  $env:JAVA_HOME="C:\Users\hoonsor\.jdks\jbr-21.0.11"; .\gradlew.bat :app:testProdDebugUnitTest :app:assembleRelease
  ```
- 產出：`app/build/outputs/apk/{prod,lab}/release/`；本機沒有簽章 Secrets 時是 debug 簽章，**不能覆蓋安裝正式簽章版**
- CI（`.github/workflows/build.yml`）：Agent API 相容檢查 → 單元測試 → 正式版＋LAB 版；main 推送更新 `latest` 預先發行版；`vX.Y.Z` tag 建 Release（tag 必須等於 `app/build.gradle.kts` 的 `appVersion`）
- 發新版：改 `app/build.gradle.kts` 的 `appVersion`（versionCode 自動推導 1.2.3→10203），更新 `PROJECT_STATUS.md` 版本歷程，push tag

## 目錄與架構

```
app/src/main/java/tw/hoonsor/
├─ agent/HoonsorAgent.kt          共用 SDK（整份複製自 APK-VoiceHub，不要在這裡改）
└─ soundflow/
   ├─ App.kt                      Application＋Graph（手動依賴注入）
   ├─ agent/AgentProvider.kt      Agent API：describe 讀 assets 的 agent-manifest.json
   ├─ data/
   │  ├─ SettingsRepository.kt    設定的唯一寫入入口（DataStore，檔名 settings，有進系統備份）
   │  ├─ AuthRepository.kt        信箱驗證碼登入、權杖刷新、登出（DataStore 檔名 auth，不備份）
   │  ├─ TrackRepository.kt       音軌清單（讀 sf_track）＋本機 JSON 快取 tracks_<kind>.json
   │  ├─ ProgressRepository.kt    收聽進度（progress.json），播放服務與畫面共用
   │  ├─ JobRepository.kt         YouTube 轉檔工作：送出、列出、重試、取消、離線暫存 pending_shares.json
   │  ├─ YouTube.kt               純邏輯：YouTubeLink（分享文字→影片 id）、JobJson、WorkerStatus
   │  ├─ SecureBox.kt             Keystore AES-GCM，加密登入權杖
   │  ├─ Models.kt                Track、Session、SfJson（解析）、SfFormat（顯示格式）、SourceStyle（封面色）
   │  └─ supabase/SupabaseClient.kt  OkHttp 直接呼叫 GoTrue／PostgREST，錯誤轉中文（SupabaseErrors）
   ├─ share/ShareActivity.kt      接收「分享 → SoundFlow 轉成音檔」：沒有畫面，送出後立刻關閉，結果用 Toast
   ├─ player/
   │  ├─ PlaybackService.kt       Media3 MediaSessionService＋ExoPlayer；自訂指令：睡眠計時、跳過靜音（狀態放 session extras）
   │  ├─ PlayerConnection.kt      App 端唯一的播放控制入口（MediaController），輸出 PlayerUi
   │  └─ PlaybackLogic.kt         純邏輯：Speed、Repeat、ProgressRules、Queue、TimeText（PlaybackLogicTest）
   └─ ui/
      ├─ MainActivity.kt、SoundFlowApp.kt   底部四分頁外框（音檔／清單／待播／設定）、設定頁
      ├─ TracksScreen.kt、LoginCard.kt     音檔分頁（兩欄網格、下拉重新整理）、登入卡片
      ├─ PlayerViews.kt                   迷你播放列、全螢幕播放頁、倍速與睡眠計時面板
      ├─ JobsPanel.kt                     YouTube 分頁上方：N100 狀態、轉檔工作、貼上網址
worker/                          N100 轉檔服務（Python 標準庫＋yt-dlp＋ffmpeg），部署與金鑰見 worker/README.md
      └─ theme/  SfColors.kt（HSL 色票＋對比度計算）、SfTheme.kt（Compose 主題）、SfIcons.kt（手繪線條圖示）
```

之後新增的套件依 SOP：`backup/`、`health/`、`player/`（Media3）、`widget/`（Glance）、`data/`（Repository、Room、Supabase）。

## 後端（Supabase AI_News，與 27 號新知雷達共用）

- migration 放 `supabase/migrations/`，用 Supabase MCP 的 apply_migration 套用；**動 AI_News 前要先問倬**（是 27 號的正式資料庫）
- `sf_track`：App 只讀這張；Podcast 由 `nlm_job` 的 AFTER 觸發器（`soundflow_private.sync_from_nlm_job`）同步，使用者改過的 title 不會被覆蓋
- `sf_member`：白名單；RLS 開啟但不給任何 policy（用戶端讀不到），判斷用 `soundflow_private.is_member()`（security definer）
- 新增成員：`insert into public.sf_member (email) values ('<小寫信箱>')`，該信箱也必須已是 Auth 使用者（寄驗證碼用 create_user=false）
- 音檔網址：nlm bucket 公開 → `/storage/v1/object/public/...`；sf bucket（YouTube）**私人** → `/storage/v1/object/authenticated/...`，播放服務的 ResolvingDataSource 會自動加 `Authorization`（`SfUrls.PRIVATE_BUCKETS`）
- `yt_job`：App 只能新增 queued、改成 queued（重試）或 cancelled；processing／done／failed 只有 N100（service role）能寫
- `sf_worker_heartbeat`：N100 每 5 分鐘寫；App 顯示「N100 X 分鐘前在線」，超過 15 分鐘當離線
- N100 部署：systemd 使用者服務 `soundflow-worker`＋timer `soundflow-ytdlp-update`；**在 N100 裝服務、改 crontab 前要先問倬**

## 關鍵設計決策

- **describe 直接讀 `docs/agent-manifest.json`**（Gradle 任務 `copyAgentManifest` 複製進 assets）：manifest 是唯一來源，不可能與 describe 不一致；所以 JVM 測試改成檢查 manifest 符合協定（`AgentManifestTest`）
- **色票存成 HSL 三元組的純 Kotlin 類別**：可以在 JVM 測試裡算 WCAG 對比度（`ThemeTest`），三個主題都必須通過；Compose 端用 `Hsl.toColor()` 轉換
- **主題只換顏色**：版面、字體、間距三個主題都一樣（倬 2026-10-23 決定）
- **字型暫用系統字型**：DESIGN.md 指定 IBM Plex Sans／Mono，打包 ttf 需要下載字型檔，尚未做（列在下一步）

## 絕對不要重踩的坑

- Gradle 用 JDK 25 會失敗；中文路徑會讓 Gradle 出錯（程式碼必須在 `D:\dev\APK-SoundFlow`）
- `local.properties` 的 `sdk.dir` 用正斜線（`C:/Users/...`）；反斜線沒跳脫會出現「Invalid file path」
- ContentProvider 比 `Application.onCreate` 先建立：`AgentProvider.onCreate` 要自己呼叫 `Graph.init`
- `androidx.compose.foundation.layout.weight` 不能 import（是 internal）；`Modifier.weight` 在 Row／Column 的 scope 裡直接用
- 只複製 `tools/agent_compat_check.py`；語音總控的 `test_agent_compat_check.py` 讀的是它自己的 manifest，複製過來會失敗
- 下載 YouTube 音軌違反 YouTube 服務條款：僅限倬個人收聽、不公開分享
- **repo 是公開的**：信箱、權杖、service role key 都不能寫進任何檔案（含 migration）；publishable key 可以（本來就公開）
- PostgREST 的時間格式是 `+00:00` 偏移，要用 `OffsetDateTime.parse` 再轉 Instant（`SfJson.normalizeTimestamp`）
- **驗證碼長度與信件範本是 Supabase 專案層級設定**，AI_News 與 43 號記帳網站、27 號新知雷達共用：目前 8 碼、範本是記帳網站的文字。App 不可寫死長度（`SfJson.OTP_MAX`，接受 6～10 碼）；改範本會影響其他專案，要先問倬（v0.4.1 修正）
- GoTrue 的 refresh token 會輪替：每次刷新都要存新的；被拒絕（4xx）才登出，網路錯誤保留登入
- 白名單外的帳號登入後讀 sf_track 會拿到空陣列（不是錯誤），畫面要提示「確認信箱在白名單」
- 畫面不要直接碰 Media3：一律經過 `Graph.player`（PlayerConnection）；之後的小工具、語音動作也連同一個 MediaSession
- MediaItem 的 mediaId＝sf_track.id；分頁種類放在 metadata extras 的 `sf.kind`（服務靠它套用分頁倍速）
- Android 13+ 沒有通知權限時，背景播放照常，但通知列看不到控制；第一次播放時才請求權限
- yt-dlp 2025.11 起抓 YouTube 需要外部 JavaScript 執行環境＋yt-dlp-ejs：用 `pip install "yt-dlp[default]"`，N100 用既有 Node（`--js-runtimes node`）
- Supabase 免費方案單檔 50 MB：worker 依長度降位元率，超過 3 小時拒絕
- `.gitattributes` 讓 .sh／.service／.timer／.py 一律 LF；Windows CRLF 的 shell 腳本在 N100 會跑不起來
- ShareActivity 的送出放在 `Graph.appScope`（使用者提早關卡片也照樣送出）；結果用畫面底部卡片顯示，**不要用 Toast**：ColorOS 不顯示背景 App 的 Toast（v0.4.0 實測分享後完全沒反應）
- **能登入 ≠ 能用 SoundFlow**：Auth Users 是登入帳號（三個服務共用），`sf_member` 才是 SoundFlow 白名單。白名單外的帳號讀到空陣列、寫入回 403（42501）；App 登入後呼叫 `rpc/sf_whoami` 判斷，顯示「這個信箱還沒開通」（v0.4.2）

## 倬的偏好

- 繁體中文（台灣用語）；commit 用 Conventional Commits、繁體中文
- 深色、HSL、不用原色、按鈕有邊框或對比、手繪向量圖示、資訊密度高、長按多選
- 每個設定要有「測試」或即時預覽；刪除可復原

## 規則

- Always：改程式前先 commit；寫入一律走 Repository；文件只寫相對路徑；決定與放棄的方案都寫原因；色碼只能寫在 `SfColors.kt`
- Ask：建立 GitHub repo、動 27 號專案的程式或資料表、在 N100 上安裝服務、Google Cloud OAuth 設定、下載字型等外部檔案
- Never：提交任何 key／token／簽章檔；讀取或顯示 dotenvx 解密後的值；force push
