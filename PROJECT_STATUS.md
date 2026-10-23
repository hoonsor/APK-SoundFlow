# PROJECT_STATUS — SoundFlow

| 項目 | 內容 |
|---|---|
| 目前版本 | v0.0.0（規劃中，尚無程式碼） |
| 階段 | SOP 第 1、2 步完成並經倬確認；第 3 步版面設計 3 個方向已上畫布，待倬選定 |
| 最後更新 | 2026-10-23 |

## 任務

- [x] 意圖訪談：儲存空間、暫存區、轉檔主機、參考 App
- [x] 倬確認 `docs/FEATURES.md`（2026-10-23 整份定案）
- [x] 第 2 步：語音 API 規劃（21 個動作；`docs/AGENT_API.md`、`docs/agent-manifest.json`、語音總控說明書已新增一章）
- [x] 倬確認語音 API（2026-10-23 定案）
- [x] 第 3 步之一：3 個方向（A 夜航、B 琥珀電台、C 訊號格）× 主畫面／全螢幕播放／桌面小工具，設計畫布 https://claude.ai/artifact/UTp8JU3oHebkETK5NfyYUi
- [ ] 第 3 步之二：倬選定方向 → 補多選、編輯（改名）、設定、健康檢查、空狀態、清單頁 → 寫 `docs/DESIGN.md`（色票只有一份）
- [ ] 第 4 步：骨架（GitHub repo、共用簽章、CI、LAB 版）
- [ ] 後端：Supabase 資料表與觸發器、Edge Function、Google Cloud OAuth
- [ ] N100 轉檔服務與封存工作

## 下一步

等倬在設計畫布選定方向（或混搭，例如 C 的主畫面＋A 的播放頁），再補齊其餘畫面並寫 `docs/DESIGN.md`。確認前不寫功能程式（SOP 規定）。

## 交接紀錄

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
