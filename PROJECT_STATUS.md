# PROJECT_STATUS — SoundFlow

| 項目 | 內容 |
|---|---|
| 目前版本 | v0.7.0（全幅沉浸小工具、本機音檔、封面；待實機確認，未打 tag）；上一個正式版 v0.5.0 |
| 階段 | SOP 第 5 步分版實作中（倬 2026-10-26 要求 YouTube 分享提前到 v0.4.0，先測它再測播放與小工具） |
| GitHub | https://github.com/hoonsor/APK-SoundFlow（public） |
| 最新測試版 | https://github.com/hoonsor/APK-SoundFlow/releases/tag/latest |
| 最後更新 | 2026-10-31 |

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
- [x] v0.5.0 後端：`sf_label`、`sf_track_label`、`sf_playlist`、`sf_playlist_item`（成員可讀寫）、sf_track 加 `delete_source`／`purge_requested_at`／`purged_at`；`sf_whoami` 改 security invoker；migration `20261026_library.sql`
- [x] v0.5.0 App：檢視模式（精簡列表／兩欄網格，依分頁記住）、依標籤分組（可收合）、排序 8 種、篩選（收聽狀態＋多個標籤）、長按多選（加入清單、貼標籤、標為已聽、刪除）、單集「⋯」選單（播放、加入清單、標籤、已聽／未聽、改名、刪除）、刪除可復原（snackbar）、清單分頁（4 個智慧清單、自訂清單 CRUD、播放全部／隨機、上移／下移、移出）、設定頁「資料管理」（顏色標籤管理、垃圾桶：還原、立即永久刪除、全部清空）
- [x] v0.5.0 N100：worker 0.5.0 每分鐘處理垃圾桶（7 天到期或立即永久刪除）：YouTube 刪 sf 檔；Podcast 只有選了「連新知雷達的音檔一起刪」才刪 nlm 檔；保留 sf_track 列（hidden、purged_at），避免 nlm_job 觸發器加回來；已部署並重啟
- [x] v0.6.0：桌面小工具 `widget/SoundFlowWidget.kt`（Glance 1.1.1，PreferencesGlanceStateDefinition）、`widget/WidgetPickerActivity.kt`（換清單）、`player/QueueSnapshot.kt`（MediaItems 共用建構、清單快照、小工具狀態）；PlaybackService 事件時推送小工具（合併 0.5 秒、播放中每 30 秒刷新進度）、onPlaybackResumption
- [x] v0.7.0：修小工具按鈕（`PlayerConnection` 切回主執行緒）；全幅沉浸四尺寸；手勢控制面板 `widget/WidgetPanelActivity.kt`；播放模式三段式（`PlayMode`，存在設定）；封面 `data/CoverStore.kt`＋`ui/CoverUi.kt`；本機音檔 `data/LocalRepository.kt`、`data/LocalLogic.kt`（`TrackKind.LOCAL`，音軌放在 TrackRepository 的 LOCAL 狀態）；找封面 `data/CoverSearchRepository.kt`
- [ ] 後端：sf_progress（進度雲端同步）、Edge Function drive-token、Google Cloud OAuth
- [ ] N100 封存工作（Supabase → Google Drive）

## 版本歷程

| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.7.0 | 2026/10/31 | （本次） | 修小工具按鈕沒反應；小工具改全幅沉浸（2×1、5×1、2×2、4×2）＋手勢控制面板；播放模式改循環／單曲／隨機；YouTube 封面；本機音檔分頁（整個資料夾匯入）；幫本機音檔找封面（單首與批次，可復原） | 倬實機回報按鈕沒反應、介面不好看；想聽手機裡的音檔、要封面 |
| v0.6.0 | 2026/10/26 | 8573e65 | 桌面小工具（Glance，可調整大小：4×1 精簡、4×2 標準、4×3 大型含接下來 3 集）：播放／暫停、上下首、倒轉 15／快轉 30、倍速循環、循環模式、換清單小視窗；App 沒開或重開機也能直接按播放（清單快照）；支援 Android 13+ 系統「繼續播放」；跟隨佈景主題 | 倬的核心需求：不打開 App，在桌面就能播放與切換 |
| v0.5.0 | 2026/10/26 | 5b75b66 | 清單管理：精簡列表／兩欄網格、依標籤分組、排序（日期／標題／長度／進度）、篩選（收聽狀態、顏色標籤）、長按多選批次動作、顏色標籤與播放清單（雲端同步）、智慧清單、改名、垃圾桶 7 天＋N100 永久刪除實體檔 | 倬要求：檔案變多後要好找、能分組、能真的刪檔；做完再做桌面小工具 |
| v0.4.2 | 2026/10/26 | b719e0c | 登入後確認白名單（新 RPC `sf_whoami`），不在白名單顯示「這個信箱還沒開通」；寫入被 RLS 擋下時錯誤訊息改成白名單原因；分享結果改用畫面底部卡片（不用 Toast） | 倬用 hoonsorasus 信箱登入（不在 sf_member），貼網址顯示「請重新登入」誤導、分享在 ColorOS 上完全沒反應 |
| v0.4.1 | 2026/10/26 | 7a05695 | 驗證碼接受 6～10 位數（AI_News 專案設定為 8 碼）；LAB 版在分享選單顯示「SoundFlow LAB 轉成音檔」 | 倬回報收到 8 碼驗證碼但 App 只能輸入 6 碼；正式版與 LAB 版並存時分享選單分不出來 |
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
- [x] v0.5.0：切換「精簡列表／兩欄網格」與「依標籤分組」，重開 App 仍記得（Podcast、YouTube 各自）
- [x] v0.5.0：排序（加入日期、標題、長度、收聽進度，各兩個方向）與篩選（未聽／聽到一半／已聽完、點標籤）正確
- [x] v0.5.0：設定 → 顏色標籤：新增、改名、改色、刪除；在音檔「⋯」→ 顏色標籤可貼多個，卡片上顯示
- [x] v0.5.0：長按一集進入多選 → 全選／取消、批次加入清單、貼標籤、標為已聽、刪除
- [x] v0.5.0：清單分頁：新增清單、加入音檔、播放全部、隨機播放、編輯（上移／下移、移出、改名、刪除清單）；4 個自動清單內容合理
- [x] v0.5.0：刪除 YouTube 音檔 → 「復原」可救回；在垃圾桶按「立即永久刪除」→ 約 1 分鐘內消失，Supabase Storage 的 sf 檔案也不見
- [x] v0.5.0：刪除 Podcast 時兩個選項都出現；選「只從 SoundFlow 移除」永久刪除後新知網站仍可播
- [x] v0.5.0：用另一個已開通的信箱登入，標籤與清單同步出現（雲端同步）
- [ ] v0.6.0：桌面長按 → 小工具 → SoundFlow（LAB）放上桌面；拉成 4×1、4×2、4×3 版面會切換
- [ ] v0.6.0：播放／暫停、上一首、下一首、倒轉 15、快轉 30 都有反應，標題與進度條會更新
- [ ] v0.6.0：倍速按鈕循環 1×→1.2×→1.3×→1.5×→2×；循環按鈕「關→清單循環→單曲循環」
- [ ] v0.6.0：「換清單」跳出小視窗，選一份清單後直接從頭播放（不打開 App）
- [ ] v0.6.0：4×3 的「接下來」點一集會直接跳過去
- [ ] v0.6.0：把 App 從最近使用滑掉、甚至重開機後，按小工具的播放能從上次的清單與位置接續
- [ ] v0.6.0：App 裡換佈景主題，小工具顏色跟著換

- [ ] v0.7.0：小工具每顆按鈕都有反應（播放、上下首、倒退／快轉、模式、倍速、睡眠計時），App 沒開時也有效
- [ ] v0.7.0：Nova 上 5×1、2×1、2×2、4×2 排版都正常（畫布的格子尺寸是估算）；封面鋪滿、圓角正確
- [ ] v0.7.0：點歌名開控制面板，上下滑切歌、左右滑調倍速、滑桿、換清單都正常
- [ ] v0.7.0：YouTube 列表、播放頁、通知列、小工具都看得到封面
- [ ] v0.7.0：匯入資料夾、播放、重開機後還在；重新掃描；移除資料夾後手機檔案還在
- [ ] v0.7.0：單首找封面（中文歌）、批次找封面、復原這批；沒有標籤的錄音不會被送出查詢
- [ ] v0.7.0：播放模式關掉 App 再開仍記得

## 下一步

1. 倬實機測 v0.7.0（`latest` 預先發行版）→ 通過後打 v0.7.0 tag（v0.3.0、v0.4.0、v0.6.0 以 v0.7.0 整體通過為準）。v0.5.0 已於 2026-10-26 實機通過並打 tag；v0.3.0／v0.4.0 的個別項目倬未逐項回報，以 v0.5.0 整體通過為準（倬 2026-10-26：清單管理完成後就做小工具）。v0.4.0 YouTube 分享 2026-10-26 倬實測轉檔成功。N100 紀錄：`ssh n100 'journalctl --user -u soundflow-worker -f'`
2. CI 注意事項（不影響編譯）：GitHub 提示 upload-artifact@v4、setup-gradle@v4 用的 Node 20 已淘汰；ubuntu-latest 將換成 Ubuntu 26。之後一起升級 action 版本
3. 如果驗證碼信只有連結沒有數字：Supabase 後台 Authentication → Email Templates → Magic Link 內文要有 `{{ .Token }}`（43 號語音記帳已經用驗證碼登入，應該已設定）
4. SOP 第 5 步接下來（2026-10-26 再調整）：v0.7.x 待播佇列、搜尋 → v0.8.0 快取與 Drive 封存 → v0.9.0 Agent API 實作 → 備份、健康檢查

## 交接紀錄

### 2026-10-31（第 3 段）／電腦 A
- 倬確認第 7 列設計，並要求本機音檔能找封面、問封面該放哪裡
- 完成：v0.7.0 全部實作，58 個左右的單元測試與正式版／LAB 版編譯通過；**未實機測試**（手機沒接電腦）
- 決定＋原因：封面存 App（`filesDir/covers`，找到的存 `<id>.found.jpg`，優先顯示，刪掉就回原狀），不寫回音檔：寫入要改授權成讀寫、各格式標籤寫法不同，失敗可能弄壞音檔，且與「可回滾」原則衝突；批次只處理標籤完整的音檔且歌名歌手都對得上：會議錄音這類檔名可能含公務資訊，不能送外部服務，加錯也比沒加糟；播放模式拿掉「不循環」，倬定案只要三種；App 自製封面快取，不引入 Coil，少一個相依套件；小工具背景圖走 Bitmap（480 寬 RGB_565），同一張圖在多個尺寸間系統會共用
- 放棄＋原因：小工具用 FileProvider URI（要授權給桌面程式，Nova 行為難保證）；MusicBrainz（要逐步查 release 再查封面，較慢，iTunes 對中文已夠準）；本機音檔進雲端清單（換手機對不上）
- 查證：iTunes Search API 查繁體中文歌名準確；Deezer 查同一首中文歌沒結果，只當英文歌備援；格式已用真實回應確認
- 環境注意：小工具舊的 4×3 版面已移除；Repeat.OFF 只留給舊資料相容

### 2026-10-31（第 2 段）／電腦 A
- 倬回報：按「換清單」有跳出來，其他按鈕沒反應。換清單走 `actionStartActivity`，不經過 MediaController，符合上一段的主執行緒推測（仍待修完實機確認）
- 倬選定全幅沉浸方向，並提出 F53（Nova 7 列 × 5 欄的 5×1、2×1、2×2、4×2）、F54（本機音檔，整個資料夾匯入）、F55（YouTube 封面）。這一段**沒有改程式**
- 完成：畫布第 7 列新增「Nova 7×5 全幅沉浸四尺寸」與「音檔頁籤」，兩張都可操作
- 決定＋原因：播放模式集中成一顆循環切換鈕（循環→單曲→隨機），一格放得下；倍速鈕點一下換常用倍速，要細調就點歌名開控制面板；本機放在音檔頁第三個分段，分頁一直在，沒匯入就顯示空狀態與匯入按鈕；本機資料夾用 `ACTION_OPEN_DOCUMENT_TREE` 持久授權，不要求 `MANAGE_EXTERNAL_STORAGE`，權限最小
- 查證：`sf_track` 的 3 筆 YouTube 都已有 `thumbnail_url`（i.ytimg.com 的 webp），舊資料不用回填；App 目前沒有取這個欄位（`SfJson.TRACK_FIELDS`、`Track` 要補）；5 筆 Podcast 沒有封面，維持波形圖案
- 實作提醒：RemoteViews 傳 bitmap 有大小上限，小工具封面要縮到約 480px 寬，或用 FileProvider 的 URI 給 Glance；Glance 圖示只能用 drawable，15／30 的數字要做成向量路徑；進度只在狀態改變時更新，不會每秒走
- 未完成：倬確認設計；實機格子尺寸未量，畫布以每格約 76×104 dp 估算，靠 `SizeMode.Responsive` 吸收誤差

### 2026-10-31／電腦 A
- 倬實機回報 v0.6.0：小工具按了沒反應（已關電池最佳化）、介面不好看；要求先出設計，確認後才改程式。這一段**沒有改程式**
- 完成：設計畫布新增第 6 列：小工具 1 封面卡、2 全幅沉浸、3 極簡膠囊、手勢控制面板（可操作）
- 決定＋原因：Android 桌面小工具（RemoteViews／Glance）只支援點一下，不支援滑動、長按拖曳與滑桿，長按也由桌面程式接管（用來移動小工具）；所以手勢放進「點歌名跳出的控制面板」（透明 Activity，手勢與滑桿都能做）
- 放棄＋原因：在小工具上直接滑動（系統不提供）；StackView（可滑但 App 收不到換頁事件，無法切歌）；懸浮視窗（可做全手勢，但要「顯示在其他 App 上層」權限、會蓋在所有畫面上、ColorOS 容易殺掉）
- 未完成：倬選方案；修小工具無反應
- 按鈕無反應的推測（未驗證，手機沒接電腦、沒有 logcat）：Glance 的 ActionCallback 不在主執行緒，`PlayerConnection.exec`／`togglePlayNow` 直接操作 MediaController 會被 Media3 以「wrong thread」擋下；修法是包進 `withContext(Dispatchers.Main)`。要確認倬按「換清單」有沒有反應（它走另一條路，能幫忙判斷）

### 2026-10-26（第 5 段）／電腦 A
- 完成：v0.5.0 實機通過並打 tag。v0.6.0 桌面小工具（見任務清單）。JVM 測試 43 項通過（新增 WidgetLogicTest 4 項：倍速循環、版面判斷、狀態與快照 JSON）
- 決定＋原因：一個可調整大小的小工具＋Responsive 版面，而不是三個小工具（使用者拉大拉小就好，維護一份）；小工具狀態由播放服務推送到 Glance 的 PreferencesGlanceStateDefinition，同時存 widget_state.json 給新放上的小工具用；按鈕走 PlayerConnection（ActionCallback 在 App process 執行），播放器空的時從 last_queue.json 接續（F26）；同一份快照也給 MediaSession.onPlaybackResumption；進度條只在事件與播放中每 30 秒刷新（小工具更新很耗電）
- 放棄＋原因：每秒更新進度條（耗電、Glance 也不建議）；三個分開的小工具（重複維護）；小工具透明度設定（F27 的一部分，先固定 95%）
- 未完成：實機驗證；小工具在挑選清單裡的預覽圖（目前用 Glance 預設的載入畫面）
- 環境注意：Glance 小工具的圖示是 res/drawable 的 ic_w_*.xml（Glance 不能直接用 Compose 的 ImageVector），改 SfIcons 時記得一起改

### 2026-10-26（第 4 段）／電腦 A
- 完成：倬實測 YouTube 轉檔成功；v0.5.0 清單管理（見任務清單）。新增 `data/Library.kt`（Label、Playlist、ViewMode、SortSpec、ListFilter、LibraryLogic）、`data/LibraryRepository.kt`、`ui/LibraryUi.kt`（標籤、清單、刪除、改名、單集選單）、`ui/PlaylistsScreen.kt`、`ui/ManageScreens.kt`（垃圾桶、標籤管理）；TracksScreen 重寫；SfColors 加 `SfLabelColors`（8 色）。JVM 測試 39 項、worker 測試 7 項通過；語音動作 track_delete 說明改 7 天、加選填參數 delete_source（相容檢查通過），語音總控說明書同步
- 決定＋原因（倬選）：Podcast 刪除時讓倬選是否連新知雷達音檔一起刪；垃圾桶 7 天；標籤與清單雲端同步；檢視模式只做精簡列表、兩欄網格、依標籤分組（不做大卡片）
- 決定＋原因（我）：永久刪除由 N100（service role）做，App 不開放刪 Storage 的權限；永久刪除後保留 sf_track 列（hidden＋purged_at），否則 nlm_job 觸發器會把 Podcast 加回來；清單排序用上移／下移，不做拖曳（Compose 原生沒有穩定的拖曳排序，第三方套件要另外評估）；標籤顏色存代號、顏色值只在 SfColors（符合 DESIGN 的單一色票）
- 放棄＋原因：大卡片模式（倬沒選）；在 App 直接刪 Storage 檔（要給用戶端刪檔權限，風險大）
- 未完成：實機驗證；搜尋（F30 的搜尋部分）；待播佇列（F18）
- 環境注意：N100 worker 已更新到 0.5.0（`~/apps/soundflow-worker`）；連新知雷達音檔一起刪時，網站上該卡片的 Podcast 播放器會失效（27 號網站沒有改）

### 2026-10-26（第 3 段）／電腦 A
- 完成：v0.4.2。倬用 `hoonsorasus@gmail.com` 登入（Auth 使用者有，但不在 sf_member）→ 讀不到 N100 心跳（顯示「還沒上線過」）、貼網址被 RLS 擋下卻顯示「請重新登入」、分享的 Toast 在 ColorOS 上沒出現。新增 migration `soundflow_whoami`（`public.sf_whoami()`，只回自己的信箱與是否成員；未登入呼叫 401）；App 登入後確認白名單、顯示開通狀態；錯誤訊息修正；ShareActivity 改 Compose 底部卡片。JVM 測試 30 項通過
- 白名單：倬同日明確同意後，`hoonsorasus@gmail.com` 已加進 sf_member（目前 2 位成員）；第一次嘗試時被自動權限檢查擋下，因為授予存取權需要倬明確同意

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
