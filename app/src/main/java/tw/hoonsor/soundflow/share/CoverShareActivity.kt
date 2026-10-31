package tw.hoonsor.soundflow.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.CoverBitmaps
import tw.hoonsor.soundflow.ui.SfButton
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.SoundFlowTheme

/** 分享過來的圖片：圖片內容與解碼結果一起留著，按確認時不必再讀一次 */
private class SharedImage(val bytes: ByteArray, val preview: ImageBitmap)

/**
 * 接收瀏覽器分享的圖片，設為封面（F57）。先在 App 按「到 Google 找圖」，App 才知道要設給哪一首。
 * 任何 App 都能分享到這裡，所以只收 content:// 的圖片，而且一定要倬看過預覽、按確認才套用。
 */
class CoverShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = streamOf(intent)
        val pending = Graph.coverSearch.pending()
        setContent {
            val theme by Graph.settings.theme.collectAsState(initial = SfThemeId.SIGNAL)
            val image by produceState<SharedImage?>(null) {
                value = uri?.let { Graph.covers.read(it) }?.let { bytes ->
                    withContext(Dispatchers.IO) { CoverBitmaps.decode(bytes, 480)?.asImageBitmap() }?.let { SharedImage(bytes, it) }
                }
            }
            SoundFlowTheme(theme) {
                val c = Sf.colors
                val shape = RoundedCornerShape(20.dp)
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val shared = image
                        if (pending == null) {
                            Text("還不知道要設給哪一首", style = SfType.rowTitle, color = c.text)
                            Text("請先在 SoundFlow 的音檔「⋯」→ 找封面 → 到 Google 找圖，再分享圖片過來。", style = SfType.body, color = c.muted)
                            SfButton("知道了", primary = true, onClick = { finish() })
                            return@Column
                        }
                        Text("設為「${pending.title}」的封面？", style = SfType.rowTitle, color = c.text, maxLines = 2)
                        Box(Modifier.widthIn(max = 220.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
                            if (shared != null) Image(shared.preview, contentDescription = "分享的圖片", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else Text(if (uri == null) "沒有收到圖片" else "讀取中…", style = SfType.body, color = c.muted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SfButton("取消", primary = false, onClick = { finish() }, modifier = Modifier.weight(1f))
                            SfButton("設為封面", primary = true, enabled = shared != null, onClick = {
                                val bytes = shared?.bytes ?: return@SfButton
                                val app = applicationContext
                                Graph.appScope.launch {
                                    val ok = Graph.covers.saveFoundBytes(pending.id, bytes)
                                    if (ok) Graph.coverSearch.clearPending()
                                    Toast.makeText(app, if (ok) "已加上封面" else "這張圖片無法使用", Toast.LENGTH_SHORT).show()
                                }
                                finish()
                            }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    private fun streamOf(intent: Intent): Uri? {
        val uri = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        return uri?.takeIf { it.scheme == "content" }
    }
}
