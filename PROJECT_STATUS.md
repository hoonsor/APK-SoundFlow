# PROJECT_STATUS — SoundFlow

| 項目 | 內容 |
|---|---|
| 目前版本 | v0.2.0（Supabase 登入＋Podcast 列表；共用簽章；尚未打 tag，待實機確認） |
| 階段 | SOP 第 5 步分版實作中 |
| GitHub | https://github.com/hoonsor/APK-SoundFlow（public） |
| 最新測試版 | https://github.com/hoonsor/APK-SoundFlow/releases/tag/latest |
| 最後更新 | 2026-10-25 |

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
- [x] v0.2.0 App：信箱驗證碼登入（權杖 Keystore AES-GCM 加密）、Podcast 兩欄網格、下拉重新整理、離線快取、錯誤橫幅與重試、設定頁帳號（測試連線、登出）
- [ ] 打包 IBM Plex Sans／Mono 字型（要下載 ttf，先問倬）
- [ ] 更新 Claude Project 的 `APP-REGISTRY.md` 一列（本機沒有這份檔案，要在 claude.ai 的 Project 更新）
- [ ] 後端：yt_job、sf_progress、sf_playlist 資料表（用到的版本再建）、Edge Function drive-token、Google Cloud OAuth
- [ ] N100 轉檔服務與封存工作

## 版本歷程

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.2.0 | 2026/10/25 | （本次） | 信箱驗證碼登入、Podcast 列表（兩欄網格、下拉重新整理、離線快取）、設定頁帳號與測試連線；Supabase 新增 sf_track 與 nlm_job 同步觸發器 | 先讓新知雷達產生的 Podcast 出現在 App 裡，播放（v0.3.0）才有東西可播 |
| v0.1.0 | 2026/10/23 | 7cdaf63 | 骨架：正式版＋LAB 版、3 種佈景主題可切換、底部四分頁、Agent API describe、CI | SOP 第 4 步；先把編譯、簽章、發布管線跑通再做功能 |

## 待實機驗證

- [ ] 正式版與 LAB 版可同時安裝，名稱分別為 SoundFlow／SoundFlow LAB
- [ ] 設定 → 佈景主題三選一，點了立即換色，重開 App 後仍記得
- [ ] 語音總控「動作」頁掃描得到 SoundFlow（21 個動作顯示為規劃中）：需要共用簽章版
- [ ] v0.2.0：音檔分頁輸入信箱 → 收到 6 碼驗證碼信（信裡要有數字，不是只有連結）→ 登入後看到 5 集 Podcast
- [ ] v0.2.0：關掉網路重開 App，仍看到上次的列表（標「離線」）；往下拉可重新整理
- [ ] v0.2.0：設定 → 帳號「測試」顯示讀到幾集；登出後音檔分頁回到登入畫面

## 下一步

1. 倬實機確認 v0.2.0（先解除安裝舊的 debug 簽章版，再裝 latest 的 LAB 版），通過後打 `v0.2.0` tag
2. CI 注意事項（不影響編譯）：GitHub 提示 upload-artifact@v4、setup-gradle@v4 用的 Node 20 已淘汰；ubuntu-latest 將換成 Ubuntu 26。之後一起升級 action 版本
3. 如果驗證碼信只有連結沒有數字：Supabase 後台 Authentication → Email Templates → Magic Link 內文要有 `{{ .Token }}`（43 號語音記帳已經用驗證碼登入，應該已設定）
4. SOP 第 5 步接下來：v0.3.0 Media3 背景播放＋迷你播放列＋全螢幕播放 → v0.4.0 桌面小工具 → v0.5.0 YouTube 分享＋N100 轉檔服務 → v0.6.0 清單／待播／多選／垃圾桶 → v0.7.0 快取與 Drive 封存 → v0.8.0 Agent API 實作 → 備份、健康檢查

## 交接紀錄

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
