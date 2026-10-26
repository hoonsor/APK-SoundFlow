# SoundFlow Agent API（規劃中）

> **狀態：規劃中。** App 尚未實作，以下動作都還不能呼叫。
> 協定本身（方法、錯誤碼、risk、相容規則、撰寫指南）以語音總控的 [AGENT_PROTOCOL.md](https://github.com/hoonsor/APK-VoiceHub/blob/main/docs/AGENT_PROTOCOL.md) 為準，這裡只寫本 APP 的動作。
> 機器可讀版：`docs/agent-manifest.json`（實作時 `describe` 必須與它完全相同，以 JVM 測試比對）。
> 套件名 `tw.hoonsor.soundflow`；provider authority `tw.hoonsor.soundflow.agent`（LAB 版 `tw.hoonsor.soundflow.lab.agent`）。

讀者：接手實作的 AI、語音總控的開發者。

## 1. 實作要點

- 所有動作走 `data/` 的 Repository（音軌、清單、進度）與播放服務的 `MediaController`，不另寫寫入邏輯
- 播放類動作（`track_play`、`playback_*`、`sleep_timer_set`、`playlist_play`、`queue_add`）要在 App 沒開時也能用：provider 透過 `MediaController` 連上 `MediaSessionService`，服務會自己啟動前景通知
- 需要連網的動作覆寫 `timeoutMs`：`youtube_add`、`track_rename`、`track_delete`、`track_restore`、`track_mark_played`、`youtube_job_list`、`status_get` → 15 秒；其餘預設 5 秒。查詢類先讀本機快取（Room），背景再同步
- `backup_import` 需要使用者選檔，確認後回 `NEEDS_UI`（開 SAF 選檔畫面）
- ColorOS「關聯啟動」可能擋住語音總控喚醒本 App：健康檢查頁要引導開啟

## 2. 共用參數：怎麼指定「哪一集」

`track_play`、`queue_add`、`playlist_track_add`、`track_rename`、`track_mark_played`、`track_delete`、`track_restore` 都用同一套規則，依序：

1. `track_id`：精確指定（由 `track_list` 取得）
2. `now_playing: true`：目前播放（或暫停中）的這集
3. `query`：標題、頻道模糊比對（不分大小寫、忽略全半形）；**剛好一筆**才執行，多筆回 `AMBIGUOUS`（`data.candidates` 放最多 5 個標題），零筆回 `NOT_FOUND`
4. 都沒給：`track_rename`、`track_mark_played`、`track_delete` 預設當成 `now_playing`；沒有正在播放的回 `INVALID_PARAMS`「現在沒有在播，要指定哪一集？」

`kind` 可縮小 `query` 的範圍（`podcast`／`youtube`）。

## 3. 動作一覽

| 動作 | 標題 | risk | 用途 |
|---|---|---|---|
| `track_list` | 查詢音檔 | read | 依分頁、收聽狀態、關鍵字列出 |
| `track_play` | 播放音檔 | reversible | 播指定的一集或最新／接續 |
| `now_playing_get` | 目前播放 | read | 播放狀態、剩餘時間、倍速、計時 |
| `playback_control` | 播放控制 | reversible | 播放、暫停、上下首、停止 |
| `playback_seek` | 快轉倒轉 | reversible | 相對秒數或跳到指定位置 |
| `playback_speed_set` | 調整倍速 | reversible | 0.5～3.0 倍 |
| `playback_mode_set` | 循環與隨機 | reversible | 不循環／清單循環／單曲循環、隨機 |
| `sleep_timer_set` | 睡眠計時 | reversible | N 分鐘或本集結束停止 |
| `queue_add` | 加入待播 | reversible | 下一首播放或排到最後 |
| `playlist_list` | 查詢清單 | read | 自訂與智慧清單 |
| `playlist_play` | 播放清單 | reversible | 播某個清單 |
| `playlist_track_add` | 加入清單 | reversible | 加進自訂清單（可順便建立） |
| `track_rename` | 重新命名 | reversible | 改顯示名稱或還原原標題 |
| `track_mark_played` | 標記已聽 | reversible | 已聽／未聽 |
| `track_delete` | 刪除音檔 | destructive | 移到垃圾桶（7 天可還原，之後連雲端音檔永久刪除） |
| `track_restore` | 還原音檔 | reversible | 從垃圾桶還原 |
| `youtube_add` | 加入 YouTube | reversible | 送網址給 N100 抽音軌 |
| `youtube_job_list` | 轉檔進度 | read | 排隊／轉檔中／失敗 |
| `status_get` | 系統狀態 | read | 各層儲存用量、N100 上線時間 |
| `backup_export` | 匯出備份 | reversible | 清單、進度、設定 → zip |
| `backup_import` | 匯入備份 | destructive | 開選檔畫面匯入 |

參數的 JSON Schema 與例句以 `docs/agent-manifest.json` 為準，下面只寫回傳與錯誤。

## 4. 各動作的回傳與錯誤

### track_list
- summary：「Podcast 有 3 集沒聽：最新的是〈Claude Code 新功能〉。」（最多唸 3 個標題）
- data：`{total, items:[{id, kind, title, channel, duration_sec, position_sec, played, added_at}]}`

### track_play
- summary：「開始播〈Claude Code 新功能〉，從 12 分 30 秒繼續。」
- undo：`{action:"playback_control", params:{command:"pause"}}`
- 錯誤：`NOT_FOUND`「找不到講 Gemini 的那集，要不要先列出最新的？」；`AMBIGUOUS`；音檔在 Drive 但權杖取得失敗 → `INTERNAL`「雲端音檔暫時拿不到，先播已下載的好嗎？」

### now_playing_get
- summary：「正在播〈Claude Code 新功能〉，還剩 8 分鐘，1.5 倍速。」；沒在播：「現在沒有在播。」
- data：`{track, position_sec, duration_sec, playing, speed, repeat, shuffle, sleep_timer_remaining_sec}`

### playback_control
- summary：「暫停了。」「下一集：〈…〉。」；已經是最後一集時 next：「已經是最後一集了。」（ok:true）
- undo：pause ↔ play

### playback_seek
- summary：「倒轉 30 秒。」「跳到第 10 分鐘。」
- 錯誤：兩個參數都給或都沒給 → `INVALID_PARAMS`；超過長度 → 跳到結尾前 5 秒
- undo：跳回原位置

### playback_speed_set
- summary：「改成 1.5 倍速。」
- 超出範圍自動夾到 0.5／3.0 並在 summary 說明；undo 回原倍速

### playback_mode_set
- summary：「改成單曲循環。」「隨機播放已開啟。」；undo 回原設定

### sleep_timer_set
- summary：「30 分鐘後停止播放。」「這集播完就停。」「睡眠計時已取消。」

### queue_add
- summary：「〈…〉會在這集之後播。」；undo：從待播移除

### playlist_list
- summary：「有 4 個自訂清單：通勤、學習、睡前、健身。」
- data：`{custom:[{name, count, total_sec}], smart:[...]}`

### playlist_play
- summary：「開始播通勤清單，共 12 集。」
- 錯誤：`NOT_FOUND`「沒有叫通勤的清單，你有：學習、睡前。」；清單是空的 → `NOT_FOUND`「通勤清單是空的。」

### playlist_track_add
- summary：「已把〈…〉加到通勤清單。」；已在清單中：「〈…〉本來就在通勤清單裡。」
- 錯誤：清單不存在且 `create` 未給 → `NOT_FOUND`「沒有叫睡前的清單，要幫你建一個嗎？」；對智慧清單加入 → `INVALID_PARAMS`
- undo：從清單移除

### track_rename
- summary：「已改名為〈Claude 新功能整理〉。」；undo：改回舊名
- Podcast 改名只存在 SoundFlow 的 `sf_track.title`，不寫回 `nlm_job`

### track_mark_played
- summary：「〈…〉標成已聽完。」；undo：改回原狀態

### track_delete（destructive）
- 第一次呼叫回 `NEEDS_CONFIRMATION`，message：「確定要把〈…〉移到垃圾桶嗎？7 天內可以還原。」（Podcast 帶 delete_source=true 時：「…7 天後連新知雷達的音檔一起刪，網站上也聽不到，確定嗎？」）
- 成功 summary：「已移到垃圾桶，7 天內說『還原』就能救回來。」
- undo：`{action:"track_restore", params:{track_id}}`
- 正在播的那集被刪 → 先跳下一集

### track_restore
- summary：「已還原〈…〉。」；`NOT_FOUND`「垃圾桶裡沒有這集，可能超過 7 天已經清掉了。」

### youtube_add
- summary：「已送出，轉好會出現在 YouTube 分頁。」
- 錯誤：不是 yt-dlp 能處理的網址 → `INVALID_PARAMS`「這不是 YouTube 影片網址。」；同一支已存在 → ok:true，「這支已經在 YouTube 分頁了。」；連不上 Supabase → 存到本機待送，summary「現在沒網路，連上後會自動送出。」

### youtube_job_list
- summary：「還有 2 支在轉檔，1 支失敗：影片需要登入才能看。」
- data：`{items:[{id, url, title?, status, error?, requested_at}]}`

### status_get
- summary：「雲端暫存用了 420 MB、Drive 3.2 GB、手機快取 1.1 GB；轉檔主機 2 分鐘前還在線。」
- N100 超過 15 分鐘沒心跳時加一句「轉檔主機好像離線了，YouTube 會先排隊。」
- data：`{supabase_bytes, drive_bytes, cache_bytes, worker_seen_at, yt_dlp_version}`

### backup_export
- summary：「備份好了，存在下載資料夾的 SoundFlow-backup-20261023-2130.zip。」
- data：`{path, leavesApp:false}`

### backup_import（destructive）
- 確認後回 `NEEDS_UI`（`data.intentUri` 開 App 的匯入畫面，帶 `mode`），`data.leavesApp: true`
- 確認訊息：「匯入會用備份檔{合併／取代}目前的播放清單與進度，匯入前會先自動備份。確定嗎？」

## 5. 健康檢查（health）可能的 issues

- `notification_permission`：沒有通知權限（背景播放通知列會不顯示）
- `battery_optimization`：沒有電池最佳化豁免（ColorOS 可能中斷背景播放）
- `not_signed_in`：Supabase 未登入
- `worker_offline`：N100 超過 15 分鐘沒心跳
