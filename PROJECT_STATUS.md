# PROJECT_STATUS — SoundFlow

| 項目 | 內容 |
|---|---|
| 目前版本 | v0.0.0（規劃中，尚無程式碼） |
| 階段 | Android SOP 第 1 步「意圖訪談」完成，`docs/FEATURES.md` 待倬確認 |
| 最後更新 | 2026-10-23 |

## 任務

- [x] 意圖訪談：儲存空間、暫存區、轉檔主機、參考 App
- [ ] 倬確認 `docs/FEATURES.md`（勾選建議項目、回答「待倬決定的細節」）
- [ ] 第 2 步：語音 API 規劃（`docs/AGENT_API.md`、`docs/agent-manifest.json`、語音總控說明書新增一章）
- [ ] 第 3 步：版面設計 2～3 案（Claude Design）→ `docs/DESIGN.md`
- [ ] 第 4 步：骨架（GitHub repo、共用簽章、CI、LAB 版）
- [ ] 後端：Supabase 資料表與觸發器、Edge Function、Google Cloud OAuth
- [ ] N100 轉檔服務與封存工作

## 下一步

等倬確認 `docs/FEATURES.md`，再進入第 2 步語音 API 規劃。

## 交接紀錄

### 2026-10-23／電腦 A（推測，路徑為 D:\01-Project）
- 完成：讀 27 號新知雷達的 NotebookLM 流程（`nlm_job` 表、Supabase `nlm` bucket、一集約 10 MB、免費 1 GB）；訪談倬；寫 FEATURES.md、ARCHITECTURE.md
- 決定＋原因：封存用 Google Drive（倬選）；兩層儲存 Supabase＋Drive（網站播放器不用改）；YouTube 轉檔在 N100（24 小時開機）；參考 Spotify、YouTube Music 的操作
- 放棄＋原因：pCloud（直連網址限制未驗證）、只用 Supabase（約 100 集就滿）、電腦 A 轉檔（關機會卡）、GitHub Actions 轉檔（YouTube 封鎖資料中心 IP）
- 未完成：上述任務清單
- 環境注意：程式碼放 `D:\dev\APK-SoundFlow`（Gradle 不支援中文路徑）；尚未建立 GitHub remote
