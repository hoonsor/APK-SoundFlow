package tw.hoonsor.soundflow.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.player.Speed
import tw.hoonsor.soundflow.player.WidgetState
import tw.hoonsor.soundflow.ui.rememberCover
import tw.hoonsor.soundflow.ui.rememberPlayerUi
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.SoundFlowTheme
import tw.hoonsor.soundflow.ui.theme.toColor

/**
 * 手勢控制面板（F53，設計畫布第 6 列）：點小工具的歌名跳出。
 * 小工具本身收不到滑動，上下滑切歌、左右滑調倍速、滑桿都放在這個透明畫面。
 */
class WidgetPanelActivity : ComponentActivity() {
    private val presets = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.player.connect()
        setContent {
            val theme by Graph.settings.theme.collectAsState(initial = SfThemeId.SIGNAL)
            SoundFlowTheme(theme) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) { Panel() }
            }
        }
    }

    @Composable
    private fun Panel() {
        val c = Sf.colors
        val ui = rememberPlayerUi()
        val context = LocalContext.current
        val source = remember(ui.currentId) { WidgetState.load(context).source }
        val (_, choices) = rememberWidgetChoices()
        val localFolders by Graph.local.state.collectAsState()
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        var dragSpeed by remember(ui.speed) { mutableFloatStateOf(ui.speed) }
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.border))
            GestureCard(ui.currentId, ui.thumbnailUrl, ui.title, ui.channel, source, ui.index, ui.prevTitle, ui.upNext.firstOrNull()?.title, ui.speed)
            Text("在上面的卡片：往上滑下一首、往下滑上一首、左右滑調倍速", style = SfType.meta, color = c.accentText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Control(SfIcons.Prev, "上一首") { Graph.player.previous() }
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(34.dp)).background(c.accent)
                        .clickable(role = Role.Button, onClickLabel = if (ui.isPlaying) "暫停" else "播放") { Graph.player.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (ui.isPlaying) SfIcons.Pause else SfIcons.Play, contentDescription = if (ui.isPlaying) "暫停" else "播放", tint = c.onAccent, modifier = Modifier.size(28.dp)) }
                Control(SfIcons.Next, "下一首") { Graph.player.next() }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("播放速度", style = SfType.rowTitle, color = c.text, modifier = Modifier.weight(1f))
                Text(Speed.label(dragSpeed), style = SfType.number, fontSize = 20.sp, color = c.accentText)
            }
            Slider(
                value = dragSpeed, valueRange = Speed.MIN..Speed.MAX, steps = ((Speed.MAX - Speed.MIN) / 0.05f).toInt() - 1,
                onValueChange = { dragSpeed = Speed.clamp(it) }, onValueChangeFinished = { Graph.player.setSpeed(dragSpeed) },
                colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.accent, inactiveTrackColor = c.border, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { v -> Pick(Speed.label(v), selected = Speed.clamp(dragSpeed) == v, modifier = Modifier.weight(1f)) { Graph.player.setSpeed(v) } }
            }

            Text("播放清單", style = SfType.rowTitle, color = c.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                choices.filter { it.second.isNotEmpty() }.forEach { (name, tracks) ->
                    Pick(name, selected = name == source, modifier = Modifier) { Graph.player.playAll(tracks, shuffle = null, source = name); finish() }
                }
                if (localFolders.folders.isNotEmpty()) Pick("本機資料夾 ›", selected = false, modifier = Modifier) {
                    startActivity(Intent(this@WidgetPanelActivity, WidgetPickerActivity::class.java).putExtra(WidgetPickerActivity.EXTRA_FOLDERS, true))
                    finish()
                }
            }
        }
    }

    @Composable
    private fun GestureCard(id: String?, thumb: String?, title: String, channel: String?, source: String, index: Int, prev: String?, next: String?, speed: Float) {
        val c = Sf.colors
        val density = LocalDensity.current
        val currentSpeed by rememberUpdatedState(speed)
        val style = SourceStyle.of(channel)
        val cover = rememberCover(id, thumb)
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape)
                .pointerInput(Unit) {
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDrag = { change, drag -> change.consume(); total += drag },
                        onDragEnd = {
                            val dx = with(density) { total.x.toDp().value }
                            val dy = with(density) { total.y.toDp().value }
                            if (abs(dy) > abs(dx) && abs(dy) > 24f) { if (dy < 0) Graph.player.next() else Graph.player.previous() }
                            else if (abs(dx) > 16f) Graph.player.setSpeed(currentSpeed + dx / 160f)
                        },
                    )
                }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Neighbor(prev)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(SfSemantic.cover(style.hue).toColor()), contentAlignment = Alignment.Center) {
                    if (cover != null) Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(style.tag, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("$source · 第 ${index + 1} 首", style = SfType.meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Neighbor(next)
        }
    }

    @Composable
    private fun Neighbor(title: String?) {
        Text(title ?: " ", style = SfType.meta, color = Sf.colors.muted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }

    @Composable
    private fun Control(icon: ImageVector, label: String, onClick: () -> Unit) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = Sf.colors.text, modifier = Modifier.size(28.dp))
        }
    }

    @Composable
    private fun Pick(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val c = Sf.colors
        val shape = RoundedCornerShape(10.dp)
        Box(
            modifier.height(40.dp).clip(shape).background(if (selected) c.accentSoft else Color.Transparent)
                .border(1.dp, if (selected) c.accentLine else c.border, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = SfType.body, color = if (selected) c.accentText else c.text, maxLines = 1) }
    }
}
