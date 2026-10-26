package tw.hoonsor.soundflow.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.EnqueueResult
import tw.hoonsor.soundflow.ui.MainActivity

/**
 * 在 YouTube 按「分享 → SoundFlow」會進到這裡（F03）。沒有畫面：
 * 馬上關閉回到 YouTube，不打斷看影片；背景送出轉檔工作後用 Toast 告訴結果。
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(application)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)
        val app = applicationContext
        finish()
        Graph.appScope.launch {
            val auth = Graph.auth.state.first { it != AuthState.Loading }
            if (auth !is AuthState.SignedIn) {
                Toast.makeText(app, "請先打開 SoundFlow 登入，再分享一次", Toast.LENGTH_LONG).show()
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return@launch
            }
            val msg = runCatching { Graph.jobs.enqueue(shared.orEmpty()) }.fold(
                onSuccess = {
                    when (it) {
                        EnqueueResult.Queued -> "已送到 SoundFlow，轉好會出現在 YouTube 分頁"
                        EnqueueResult.AlreadyQueued -> "這支影片已經在排隊了"
                        EnqueueResult.AlreadyInLibrary -> "這支影片已經在 SoundFlow 的 YouTube 分頁了"
                        EnqueueResult.SavedOffline -> "現在沒網路，先存在手機，連上後會自動送出"
                    }
                },
                onFailure = { it.message ?: "送出失敗" },
            )
            Toast.makeText(app, msg, Toast.LENGTH_LONG).show()
        }
    }
}
