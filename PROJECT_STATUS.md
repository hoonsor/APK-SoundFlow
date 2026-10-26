# PROJECT_STATUS — SoundFlow

| 項目 | 內容 |
|---|---|
| 目前版本 | v0.4.0（YouTube 分享＋N100 轉檔；App 已推上，N100 服務待倬設定金鑰後啟動）；v0.3.0 播放功能一起待實機確認；上一個正式版 v0.2.0 |
| 階段 | SOP 第 5 步分版實作中（倬 2026-10-26 要求 YouTube 分享提前到 v0.4.0，先測它再測播放與小工具） |
| GitHub | https://github.com/hoonsor/APK-SoundFlow（public） |
| 最新測試版 | https://github.com/hoonsor/APK-SoundFlow/releases/tag/latest |
| 最後更新 | 2026-10-26 |

## 任務

- [x] 意圖訪談：儲存空間、暫存區、轉檔主機、參考 App
- [x] 倬確認 `docs/FEATURES.md`（2026-10-23 整份定案）
- [x] 第 2 步：語音 API 規劃（21 個動作；`docs/AGENT_API.md`、`docs/agent-manifest.json`、語音總控說明書已新增一章）
- [x] 倬確認語音 API（2026-10-23 定案）
- [x] 第 3 步之一：3 個方向（A 夜航、B 琥珀電台、C 訊號格）× 主畫面／全螢幕播放／桌面小工具，設計畫布 https://claude.ai/artifact/UTp8JU3oHebkETK5NfyYUi
- [x] 第 3 步之二：倬選 C（2026-10-23）＋設定可切 3 種配色（F50）；補設定、單集動作／改名、清單、空狀態、健康檢查、配色對照；`docs/DESIGN.md` 草案
- [x] 倬確認其餘畫面與 DESIGN.md（2026-10-23 定案）
- [x] 第 4 步之一：本機骨架 v0.1.0（Gradle、正式版＋LAB 版、版本號推導、簽章讀環境變數、3 種主題可切換、底部四分頁、Agent SDK＋Provider、CI 設定、7 個 JVM 測試通過）
- [x] 第 4 步之二：建立 public repo `hoonsor/APK-SoundFlow`＋push＋CI 通過（2026-10-23，run 39977475986，3 分 24 秒；latest 預先發行版有 SoundFlow／SoundFlow-LAB 兩個 APK）
- [x] 第 4 步之三：共用簽章（2026-10-25）：`setup-hoonsor-signing.ps1` 清單補上 APK-SoundFlow，倬在 A 電腦執行；CI run 40198619549 用共用簽章編譯，prod／lab APK 憑證 SHA-256 `51:4E:C0:…:54:88:25` 與 `~/hoonsor-signing/fingerprint.txt` 一致
- [x] v0.2.0 後端：AI_News 建 `sf_member`、`sf_track`、`soundflow_private`（is_member、觸發器），nlm_job 觸發器同步 Podcast，補入既有 5 集；migration 在 `supabase/migrations/20261025_soundflow_init.sql`
- [x] v0.3.0：Media3 PlaybackService（通知列、鎖定畫面、藍牙、音訊焦點、拔耳機暫停）、迷你播放列、全螢幕播放（進度拖曳、±15／30 秒、上下首、倍速 0.5–3×、循環、隨機、睡眠計時含淡出、跳過靜音、接下來 3 集）、依分頁記住倍速、本機收聽進度與自動標記已聽完
- [x] v0.2.0 App：信箱驗證碼登入（權杖 Keystore AES-GCM 加密）、Podcast 兩欄網格、下拉重新整理、離線快取、錯誤橫幅與重試、設定頁帳號（測試連線、登出）
- [ ] 打包 IBM Plex Sans／Mono 字型（要下載 ttf，先問倬）
- [ ] 更新 Claude Project 的 `APP-REGISTRY.md` 一列（本機沒有這份檔案，要在 claude.ai 的 Project 更新）
- [x] v0.4.0 後端：`yt_job`（成員只能新增排隊、重試、取消）、`sf_worker_heartbeat`、`sf_track.video_id`、私人 bucket `sf`（成員登入後可讀）；migration `supabase/migrations/20261026_youtube_jobs.sql`
- [x] v0.4.0 App：分享接收（ShareActivity，送出後立刻關閉）、YouTube 分頁的轉檔面板（N100 狀態、排隊／轉檔中／失敗＋重試／取消、貼上網址）、沒網路先存手機自動補送、私人音檔播放自動帶登入權杖
- [x] v0.4.0 N100 服務程式：`worker/`（soundflow_worker.py、systemd 單元、每日更新 yt-dlp、install.sh）
- [x] v0.4.0 N100 安裝（2026-10-26 12:01 上線）：倬同意金鑰放 `~/.config/soundflow/worker.env`（600，資料夾 700）並自己執行 set-key；服務 `soundflow-worker` 啟用、timer `soundflow-ytdlp-update` 啟用；yt-dlp 2026.10.21 用 Node 讀 YouTube 資訊實測成功；心跳已寫入 sf_worker_heartbeat
- [ ] 後端：sf_progress、sf_playlist 資料表（用到的版本再建）、Edge Function drive-token、Google Cloud OAuth
- [ ] N100 封存工作（Supabase → Google Drive）

## 版本歷程

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.4.1 | 2026/10/26 | （本次） | 驗證碼接受 6～10 位數（AI_News 專案設定為 8 碼）；LAB 版在分享選單顯示「SoundFlow LAB 轉成音檔」 | 倬回報收到 8 碼驗證碼但 App 只能輸入 6 碼；正式版與 LAB 版並存時分享選單分不出來 |
| v0.4.0 | 2026/10/26 | 09ed7ab | YouTube 分享進 SoundFlow、N100 轉檔服務（yt-dlp＋ffmpeg 轉 Opus）、YouTube 分頁轉檔進度與重試、私人音檔空間 | 倬要求提前：先能把 YouTube 影片變成音檔，再測播放與小工具 |
| v0.3.0 | 2026/10/25 | 663edbe | Media3 背景播放（通知列、鎖定畫面、藍牙）、迷你播放列、全螢幕播放、倍速依分頁記住、循環／隨機、睡眠計時（淡出）、跳過靜音、本機收聽進度與已聽完標記 | 讓 App 真的能聽；背景播放是桌面小工具（v0.4.0）與語音動作的基礎 |
| v0.2.0 | 2026/10/25 | 404d067 | 信箱驗證碼登入、Podcast 列表（兩欄網格、下拉重新整理、離線快取）、設定頁帳號與測試連線；Supabase 新增 sf_track 與 nlm_job 同步觸發器 | 先讓新知雷達產生的 Podcast 出現在 App 裡，播放（v0.3.0）才有東西可播 |
| v0.1.0 | 2026/10/23 | 7cdaf63 | 骨架：正式版＋LAB 版、3 種佈景主題可切換、底部四分頁、Agent API describe、CI | SOP 第 4 步；先把編譯、簽章、發布管線跑通再做功能 |

## 待實機驗證

- [x] 正式版與 LAB 版可同時安裝，名稱分別為 SoundFlow／SoundFlow LAB
- [ ] 設定 → 佈景主題三選一，點了立即換色，重開 App 後仍記得
- [ ] 語音總控「動作」頁掃描得到 SoundFlow（21 個動作顯示為規劃中）：需要共用簽章版
- [x] v0.2.0：音檔分頁輸入信箱 → 收到 6 碼驗證碼信（信裡要有數字，不是只有連結）→ 登入後看到 5 集 Podcast
- [x] v0.2.0：關掉網路重開 App，仍看到上次的列表（標「離線」）；往下拉可重新整理
- [x] v0.2.0：設定 → 帳號「測試」顯示讀到幾集；登出後音檔分頁回到登入畫面
- [ ] v0.3.0：點卡片開始播放（第一次會問通知權限），迷你播放列出現；點迷你播放列打開全螢幕播放頁
- [ ] v0.3.0：關螢幕、切到別的 App 仍繼續播；通知列與鎖定畫面有播放／暫停／上下首；藍牙耳機按鍵有效；來電或拔耳機會暫停
- [ ] v0.3.0：倍速改成 1.5×，切到下一集仍是 1.5×；YouTube 分頁的倍速獨立（之後有 YouTube 音檔時驗證）
- [ ] v0.3.0：拖曳進度、倒轉 15／快轉 30、單曲循環、隨機播放、跳過靜音都正常
- [ ] v0.3.0：睡眠計時 15 分鐘（或「本集結束」）到時音量淡出後暫停
- [ ] v0.3.0：聽到一半關掉 App，再點同一集從中斷處（往回 3 秒）繼續；聽完的卡片有勾勾、標題變淡、進度條消失
- [ ] v0.3.0：從最近使用的 App 滑掉：播放中會繼續、暫停中則通知消失
- [ ] v0.4.0：YouTube App → 分享 → 選單裡有「SoundFlow 轉成音檔」→ 點了馬上回到 YouTube，跳出「已送到 SoundFlow」
- [ ] v0.4.0：SoundFlow 的 YouTube 分頁顯示「N100 X 分鐘前在線」與「轉檔中」，幾分鐘後音檔出現、可播放（含背景播放）
- [ ] v0.4.0：分享同一支影片兩次 → 顯示「已經在排隊」或「已經在 YouTube 分頁」
- [ ] v0.4.0：分享私人或會員限定影片 → 顯示失敗原因，可按「重試」或「移除」
- [ ] v0.4.0：飛航模式下分享 → 顯示「先存在手機」，關掉飛航模式打開 App 後自動送出
- [ ] v0.4.0：「貼上網址」可以手動加入

## 下一步

1. 倬實機確認 v0.4.0（分享一支短影片走完整流程）與 v0.3.0 → 打 tag；接著 v0.5.0 桌面小工具。N100 紀錄：`ssh n100 'journalctl --user -u soundflow-worker -f'`
2. CI 注意事項（不影響編譯）：GitHub 提示 upload-artifact@v4、setup-gradle@v4 用的 Node 20 已淘汰；ubuntu-latest 將換成 Ubuntu 26。之後一起升級 action 版本
3. 如果驗證碼信只有連結沒有數字：Supabase 後台 Authentication → Email Templates → Magic Link 內文要有 `{{ .Token }}`（43 號語音記帳已經用驗證碼登入，應該已設定）
4. SOP 第 5 步接下來（2026-10-26 調整順序）：v0.5.0 桌面小工具 → v0.6.0 清單／待播／多選／垃圾桶 → v0.7.0 快取與 Drive 封存 → v0.8.0 Agent API 實作 → 備份、健康檢查

## 交接紀錄

### 2026-10-26（第 2 段）／電腦 A
- 完成：v0.4.1。倬回報驗證碼是 8 碼、App 只能輸入 6 碼 → 改成接受 6～10 碼；LAB 版分享名稱改成「SoundFlow LAB 轉成音檔」（`share_label` 改由 flavor 的 resValue 提供，原 strings.xml 移到 `_trash/`）
- 原因：驗證碼長度是 Supabase 專案層級設定，AI_News 被 43 號記帳網站設成 8 碼；v0.2.0 實機測試時沒踩到（推測當時長度還是 6，或倬沿用已登入狀態，原因待確認）
- 未決定：驗證碼信件範本目前是記帳網站的文字，是三個服務共用；要改成中性文字（例如「倬的服務登入驗證碼」）需要在 Supabase 後台修改，且會影響記帳網站，等倬決定

### 2026-10-26／電腦 A
- 完成：倬回報 YouTube 分享選單沒有 SoundFlow（原排 v0.5.0，尚未實作），要求提前 → v0.4.0。Supabase 套用 migration `soundflow_youtube_jobs`；App 新增 `share/ShareActivity.kt`、`data/YouTube.kt`（網址解析、工作 JSON、N100 狀態文字）、`data/JobRepository.kt`、`ui/JobsPanel.kt`，PlaybackService 改用 ResolvingDataSource 對私人 bucket 帶權杖；`worker/` N100 服務程式。JVM 測試 29 項、worker 測試 5 項通過；release 編譯通過；未登入時新增 yt_job 401、私人 bucket 讀不到（400）
- 決定＋原因：YouTube 音檔放**私人** bucket（只給白名單成員、不公開散布，比照 YouTube 條款風險最小化）；App 播放時每個請求才向 AuthRepository 取權杖（長時間播放權杖過期也能續）；N100 每 15 秒輪詢而非 Realtime（標準庫就能寫、斷線不必重連邏輯，最多慢 15 秒）；yt-dlp 用 N100 既有的 Node 22 當 JS 執行環境（2025.11 起 YouTube 需要，不必另裝 Deno）、`pip install "yt-dlp[default]"` 帶 yt-dlp-ejs；位元率依長度 64／48／32 kbps、超過 3 小時拒絕（免費方案單檔 50 MB）；暫時性錯誤自動重試一次
- 決定（倬同日確認）：N100 金鑰不用 dotenvx，改放 `~/.config/soundflow/worker.env`（600），原因：N100 沒裝 dotenvx，裝了就要把所有專案共用的私鑰放到 N100。同日 N100 安裝並啟動，心跳正常
- 放棄＋原因：公開 bucket（任何拿到網址的人都能下載 YouTube 音檔）；Supabase Realtime（要處理 websocket 斷線）；Deno（N100 已有 Node）
- 未完成：實機驗證 v0.3.0／v0.4.0（N100 端尚未實際轉過一支影片）；tag
- 環境注意：N100 要有 service role key（倬執行 `install.sh set-key` 自己輸入）；`.gitattributes` 讓 .sh／.service／.py 一律 LF，否則複製到 N100 會出錯

### 2026-10-25（第 2 段）／電腦 A
- 完成：倬實機確認 v0.2.0 → 打 tag，正式 Release 有 SoundFlow／SoundFlow-LAB v0.2.0 兩個 APK。v0.3.0：Media3 1.5.1（`player/PlaybackService.kt`、`player/PlayerConnection.kt`）、迷你播放列與全螢幕播放（`ui/PlayerViews.kt`）、`data/ProgressRepository.kt`（progress.json）、SettingsRepository 加依分頁倍速、卡片顯示進度與已聽完。JVM 測試 23 項通過（新增 PlaybackLogicTest 9 項），prod／lab release 編譯通過
- 決定＋原因：播放服務與 App 同 process，直接用 Graph 的 Repository（不必跨 process 傳資料）；睡眠計時與跳過靜音做成 MediaSession 自訂指令（關掉 App 畫面也照常運作，之後小工具與語音動作也能用）；倍速「播放時改了就成為該分頁的預設」（F12 不必另做設定頁就能用）；進度先只存手機（同步到 Supabase 要新增 sf_progress 資料表，動 AI_News 前要先問倬）；續播往回 3 秒、播到 95% 或剩 30 秒算聽完、重聽超過 5 秒改回聽到一半
- 放棄＋原因：自己用 MediaPlayer 刻播放器（音訊焦點、通知、藍牙都要自己處理，容易出錯）；波形進度條（沒有真實波形資料，畫假的會誤導，改用一般進度條）
- 未完成：實機驗證；v0.3.0 tag；進度雲端同步、手動標記已聽、搖一搖延長睡眠、倒轉快轉秒數設定
- 環境注意：無新增環境變數；Media3 的通知圖示目前用 Media3 預設圖示

### 2026-10-25／電腦 A
- 完成：v0.2.0。Supabase（AI_News）套用 migration `soundflow_init`：私有 schema `soundflow_private`（is_member、觸發器函式、nlm_job 快照 `nlm_job_backup_20261025`）、`sf_member`（白名單，已加入倬的信箱，信箱不寫進 repo）、`sf_track`（RLS：白名單成員可讀與更新）、nlm_job 的 AFTER 觸發器、補入 5 集。App：信箱驗證碼登入、Podcast 兩欄網格、下拉重新整理、離線 JSON 快取、設定頁帳號（測試連線、登出）。JVM 測試 14 項通過，prod／lab release 編譯通過
- 驗證：未登入以 publishable key 讀 sf_track 回 `[]`（RLS 有效）；nlm 公開音檔可 Range 讀取（206）；觸發器在交易內實測（新增同步、使用者改名在原標題更新時保留）後回滾，無殘留；Supabase 安全檢查只剩 sf_member「有 RLS 無 policy」（刻意：用戶端讀不到白名單）
- 決定＋原因：登入用信箱驗證碼（倬選；與 43 號同一套，之後 Edge Function 可直接驗 JWT）；App 只讀 sf_track、不讀 nlm_job（不必改 27 號的 RLS，網站完全不受影響）；直接打 Supabase REST（OkHttp）不用官方 SDK（依賴少、錯誤訊息好控制）；publishable key 寫在 build.gradle.kts（本來就是公開金鑰，資料靠 RLS＋登入保護）；本機快取先用 JSON 檔，之後換 Room
- 放棄＋原因：同步密語登入（不會過期、外洩要兩邊一起換）；在 nlm_job 加 SoundFlow 的 RLS policy（會動到 27 號的權限設定）；把 nlm_job 備份成本機 JSON（內容多為單字卡約 30 KB，改在資料庫私有 schema 建快照，還原一行 SQL）
- 未完成：實機驗證；v0.2.0 tag（共用簽章已於同日完成：腳本清單原本漏了 APK-SoundFlow，補上後倬重跑，CI 重編並核對指紋一致）
- 環境注意：新增白名單成員要在 Supabase SQL Editor 執行 `insert into public.sf_member (email) values ('<小寫信箱>')`，而且該信箱必須已是 Supabase Auth 使用者（App 寄驗證碼時 create_user=false）

### 2026-10-23（第 5 段）／電腦 A
- 完成：倬確認設計；本機骨架 v0.1.0。`gradlew testProdDebugUnitTest assembleRelease` 通過（ThemeTest 4 項含 3 主題 WCAG 對比度、AgentManifestTest 3 項），產出 prod／lab 兩個 release APK（debug 簽章）
- 決定＋原因：沿用語音總控的 Gradle 設定（libs.versions.toml、prod／lab flavor、appVersion 推導 versionCode），各 APP 一致好維護；describe 讀 assets 裡的 manifest，不在 Kotlin 重寫 21 個動作（唯一來源、不會不一致）；字型先用系統字型（打包 IBM Plex 要下載檔案，先問倬）；hoonsor-kit 沒有適用 Android 的模組，未使用
- 放棄＋原因：Google 可下載字型（需要 GMS 憑證陣列資源，憑證內容不能自己編）；複製語音總控的 test_agent_compat_check.py（那是語音總控專用的測試，已移到 `_trash/`）
- 未完成：共用簽章、APP-REGISTRY、字型（GitHub repo 已建立並 push，CI 通過）
- 環境注意：本機要用 JDK 21（`C:/Users/hoonsor/.jdks/jbr-21.0.11`）；`local.properties` 是本機檔（不進 Git），B 電腦要自己建（`sdk.dir=C:/Users/<使用者>/AppData/Local/Android/Sdk`）

### 2026-10-23（第 4 段）／電腦 A
- 完成：倬選 C 訊號格；新增 F50 佈景主題（3 種配色只換色票）；畫布 C 主畫面改成可切換配色（theme 參數），新增設定（佈景主題可點即時切換）、單集動作與重新命名、播放清單、空狀態與離線、健康檢查、夜航／琥珀配色對照共 7 張；寫 DESIGN.md 草案（色票 12 個代號 × 3 主題＋共用語意色）
- 決定＋原因：主題只換色票不換字體與版面（倬要的是色調；換字體會讓版面行高跑掉、要測三倍）；小工具讀同一份主題設定（DataStore），避免 App 與小工具顏色不一致；反白 toast 的連結另設 `link` 色，不挪用 `onAccent`（共用色票被挪用是我做空狀態時差點犯的錯）
- 放棄＋原因：淺色主題（倬目前只要求深色 3 種，列為之後再評估）；B 的明體標題（屬於字體，不在主題範圍）
- 未完成：倬確認；第 4 步骨架
- 環境注意：無

### 2026-10-23（第 3 段）／電腦 A
- 完成：倬確認語音 API；Claude Design 畫布建立 3 個方向共 9 張（每方向：主畫面可點 Podcast／YouTube 分頁、全螢幕播放、桌面小工具 4×1／4×2 等），資料 22 集 Podcast＋14 支 YouTube
- 決定＋原因：先只比較 3 個關鍵畫面，選定方向後才補多選／設定／空狀態，避免 3 個方向各做 6 張的浪費；C 方向主畫面另示範長按多選（「選取」→已選 N＋全選＋批次動作），因為兩欄網格與多選是倬的 UX 預設
- 放棄＋原因：B 播放頁的「筆記」分頁（FEATURES 沒有這個功能，避免默默擴大範圍）
- 未完成：倬選方向；其餘畫面；DESIGN.md
- 環境注意：畫布檔案在 Claude 的暫存資料夾，正本以畫布網址為準

### 2026-10-23（第 2 段）／電腦 A
- 完成：倬確認 FEATURES 整份；寫 AGENT_API.md、agent-manifest.json（規劃版，21 個動作）；複製 tools/agent_compat_check.py；語音總控 MANUAL.md 新增 SoundFlow 一章並 push（APK-VoiceHub）
- 決定＋原因：刪除 Podcast＝只從 SoundFlow 隱藏（新知網站照常可播）；封存 30 天；轉檔失敗只在 App 顯示（倬決定）。「哪一集」統一用 track_id → now_playing → query 的順序解析，減少每個動作各寫一套；track_delete 雖是軟刪除仍標 destructive（協定規定刪資料一律 destructive）
- 放棄＋原因：track_search 獨立動作（併入 track_list 的 query，避免 AI 選錯）；playback_repeat／shuffle 分兩個動作（合成 playback_mode_set）；youtube_add 讀剪貼簿（Android 10+ 背景讀不到剪貼簿，改成必須給網址）
- 未完成：倬確認語音 API；版面設計
- 環境注意：語音總控收錄檢查要用 `python tools/manual_coverage_check.py --local D:/dev`（反斜線在 Git Bash 會被吃掉只比對到 1 份）；檢查另發現「倬的鬧鐘」（D:\dev\HoonsorAlarm）缺 11 節，非本專案範圍

### 2026-10-23／電腦 A（推測，路徑為 D:\01-Project）
- 完成：讀 27 號新知雷達的 NotebookLM 流程（`nlm_job` 表、Supabase `nlm` bucket、一集約 10 MB、免費 1 GB）；訪談倬；寫 FEATURES.md、ARCHITECTURE.md
- 決定＋原因：封存用 Google Drive（倬選）；兩層儲存 Supabase＋Drive（網站播放器不用改）；YouTube 轉檔在 N100（24 小時開機）；參考 Spotify、YouTube Music 的操作
- 放棄＋原因：pCloud（直連網址限制未驗證）、只用 Supabase（約 100 集就滿）、電腦 A 轉檔（關機會卡）、GitHub Actions 轉檔（YouTube 封鎖資料中心 IP）
- 未完成：上述任務清單
- 環境注意：程式碼放 `D:\dev\APK-SoundFlow`（Gradle 不支援中文路徑）；尚未建立 GitHub remote
