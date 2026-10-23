# PROJECT_STATUS — SoundFlow

| 項目 | 內容 |
|---|---|
| 目前版本 | v0.0.0（規劃中，尚無程式碼） |
| 階段 | SOP 第 1 步完成（FEATURES 已確認）；第 2 步語音 API 規劃完成，待倬確認 |
| 最後更新 | 2026-10-23 |

## 任務

- [x] 意圖訪談：儲存空間、暫存區、轉檔主機、參考 App
- [x] 倬確認 `docs/FEATURES.md`（2026-10-23 整份定案）
- [x] 第 2 步：語音 API 規劃（21 個動作；`docs/AGENT_API.md`、`docs/agent-manifest.json`、語音總控說明書已新增一章）
- [ ] 倬確認語音 API
- [ ] 第 3 步：版面設計 2～3 案（Claude Design）→ `docs/DESIGN.md`
- [ ] 第 4 步：骨架（GitHub repo、共用簽章、CI、LAB 版）
- [ ] 後端：Supabase 資料表與觸發器、Edge Function、Google Cloud OAuth
- [ ] N100 轉檔服務與封存工作

## 下一步

等倬確認 `docs/AGENT_API.md` 的 21 個動作，再進入第 3 步版面設計（Claude Design 做 2～3 案：主畫面兩分頁、全螢幕播放頁、桌面小工具、清單、設定、空狀態，資料 20 筆以上）。

## 交接紀錄

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
