package tw.hoonsor.soundflow.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.EnqueueResult
import tw.hoonsor.soundflow.ui.MainActivity
import tw.hoonsor.soundflow.ui.SfButton
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.SoundFlowTheme

/**
 * 在 YouTube 按「分享 → SoundFlow 轉成音檔」會進到這裡（F03）。
 * 畫面下方跳一張小卡片顯示「送出中 → 結果」，2 秒後自動關閉回到 YouTube。
 * 不用 Toast：ColorOS 常常不顯示背景 App 的 Toast（v0.4.0 實測分享後完全沒反應）。
 */
class ShareActivity : ComponentActivity() {
    private sealed interface Ui {
        data object Sending : Ui
        data class Done(val ok: Boolean, val message: String) : Ui
        data object NeedLogin : Ui
    }

    private val ui = MutableStateFlow<Ui>(Ui.Sending)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(application)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)

        // 送出放在 App 層級的 scope：使用者提早點掉卡片，工作照樣送出
        Graph.appScope.launch {
            val auth = Graph.auth.state.first { it != AuthState.Loading }
            if (auth !is AuthState.SignedIn) { ui.value = Ui.NeedLogin; return@launch }
            ui.value = runCatching { Graph.jobs.enqueue(shared.orEmpty()) }.fold(
                onSuccess = {
                    when (it) {
                        EnqueueResult.Queued -> Ui.Done(true, "已送出，轉好會出現在 YouTube 分頁")
                        EnqueueResult.AlreadyQueued -> Ui.Done(true, "這支影片已經在排隊了")
                        EnqueueResult.AlreadyInLibrary -> Ui.Done(true, "這支影片已經在 YouTube 分頁了")
                        EnqueueResult.SavedOffline -> Ui.Done(true, "現在沒網路，先存在手機，連上後自動送出")
                    }
                },
                onFailure = { Ui.Done(false, it.message ?: "送出失敗") },
            )
        }

        setContent {
            val theme by Graph.settings.theme.collectAsState(initial = SfThemeId.SIGNAL)
            val state by ui.collectAsState()
            LaunchedEffect(state) {
                if (state is Ui.Done) { delay(if ((state as Ui.Done).ok) 2_000 else 4_000); finish() }
            }
            SoundFlowTheme(theme) {
                val c = Sf.colors
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val shape = RoundedCornerShape(18.dp)
                    Column(
                        Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().clip(shape).background(c.surface)
                            .border(1.dp, c.border, shape).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when (val s = state) {
                                Ui.Sending -> CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                                is Ui.Done -> Icon(if (s.ok) SfIcons.Check else SfIcons.Wave, contentDescription = null,
                                    tint = if (s.ok) c.accent else c.danger, modifier = Modifier.size(22.dp))
                                Ui.NeedLogin -> Icon(SfIcons.Wave, contentDescription = null, tint = c.danger, modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text("SoundFlow", color = c.muted, style = SfType.meta)
                                Text(
                                    when (val s = state) {
                                        Ui.Sending -> "送出中…"
                                        is Ui.Done -> s.message
                                        Ui.NeedLogin -> "還沒登入 SoundFlow，登入後再分享一次"
                                    },
                                    color = if ((state as? Ui.Done)?.ok == false || state == Ui.NeedLogin) c.danger else c.text,
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (state == Ui.NeedLogin) {
                            SfButton("打開 SoundFlow 登入", primary = true, onClick = {
                                startActivity(Intent(this@ShareActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
