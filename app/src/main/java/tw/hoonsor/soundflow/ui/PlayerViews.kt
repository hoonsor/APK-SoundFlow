package tw.hoonsor.soundflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.SfFormat
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.player.PlayerUi
import tw.hoonsor.soundflow.player.Repeat
import tw.hoonsor.soundflow.player.Speed
import tw.hoonsor.soundflow.player.TimeText
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.toColor

/** 畫面開著時每 0.5 秒更新播放位置 */
@Composable
fun rememberPlayerUi(): PlayerUi {
    val ui by Graph.player.state.collectAsState()
    LaunchedEffect(ui.isPlaying) {
        while (ui.isPlaying) { Graph.player.refresh(); delay(500) }
    }
    return ui
}

@Composable
private fun Cover(channel: String?, size: Int, radius: Int, tagSize: Int) {
    val style = SourceStyle.of(channel)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(radius.dp)).background(SfSemantic.cover(style.hue).toColor()),
        contentAlignment = Alignment.Center,
    ) { Text(style.tag, color = Color.White.copy(alpha = 0.9f), fontSize = tagSize.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, size: Int = 44, iconSize: Int = 22, tint: Color = Sf.colors.text, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(iconSize.dp)) }
}

// ── 迷你播放列（所有分頁底部） ───────────────────────────────────────
@Composable
fun MiniPlayer(ui: PlayerUi, onExpand: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(58.dp).clip(shape).background(c.surface2).border(1.dp, c.border, shape)
            .clickable(role = Role.Button, onClickLabel = "打開播放頁", onClick = onExpand),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 7.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(ui.channel, 42, 9, 13)
            Column(Modifier.weight(1f)) {
                Text(ui.title, style = SfType.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    ui.error ?: "${TimeText.clock(ui.positionMs)} / ${TimeText.clock(ui.durationMs)} · ${Speed.label(ui.speed)}",
                    style = SfType.number, color = if (ui.error != null) c.danger else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.accent)
                    .clickable(role = Role.Button, onClickLabel = if (ui.isPlaying) "暫停" else "播放") { Graph.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (ui.isPlaying) SfIcons.Pause else SfIcons.Play, contentDescription = if (ui.isPlaying) "暫停" else "播放", tint = c.onAccent, modifier = Modifier.size(20.dp)) }
            RoundIcon(SfIcons.Next, "下一首") { Graph.player.next() }
        }
        val frac = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(frac).height(2.dp).background(c.accent))
    }
}

// ── 全螢幕播放頁 ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FullPlayer(ui: PlayerUi, onClose: () -> Unit) {
    val c = Sf.colors
    BackHandler(onBack = onClose)
    var sheet by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val style = SourceStyle.of(ui.channel)

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(SfIcons.ChevronDown, "收合播放頁", iconSize = 26, onClick = onClose)
            Text(
                "${if (ui.kind == TrackKind.YOUTUBE) "YOUTUBE" else "PODCAST"} · ${ui.index + 1}/${ui.count}",
                style = SfType.number, color = c.muted, modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Cover(ui.channel, 132, 18, 34)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (ui.kind == TrackKind.PODCAST) "NotebookLM · ${style.label}" else style.label,
                    color = c.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.border(1.dp, c.accentLine, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(ui.title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
            }
        }

        // 進度
        val dur = ui.durationMs.coerceAtLeast(1)
        val shown = if (dragging) dragValue else (ui.positionMs.toFloat() / dur).coerceIn(0f, 1f)
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { Graph.player.seekTo((dragValue * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.accent, inactiveTrackColor = c.border),
            modifier = Modifier.padding(top = 18.dp),
        )
        Row {
            Text(TimeText.clock((shown * dur).toLong()), style = SfType.number, color = c.muted, modifier = Modifier.weight(1f))
            Text("-" + TimeText.clock(((1 - shown) * dur).toLong()), style = SfType.number, color = c.muted)
        }
        ui.error?.let { Text(it, style = SfType.body, color = c.danger, modifier = Modifier.padding(top = 6.dp)) }

        // 主要控制
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(SfIcons.Prev, "上一首", size = 48, iconSize = 26) { Graph.player.previous() }
            SeekIcon(SfIcons.Back15, "15", "倒轉 15 秒") { Graph.player.seekBack() }
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(c.accent)
                    .clickable(role = Role.Button, onClickLabel = if (ui.isPlaying) "暫停" else "播放") { Graph.player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) { Icon(if (ui.isPlaying) SfIcons.Pause else SfIcons.Play, contentDescription = if (ui.isPlaying) "暫停" else "播放", tint = c.onAccent, modifier = Modifier.size(28.dp)) }
            SeekIcon(SfIcons.Fwd30, "30", "快轉 30 秒") { Graph.player.seekForward() }
            RoundIcon(SfIcons.Next, "下一首", size = 48, iconSize = 26) { Graph.player.next() }
        }

        // 四格狀態
        val sleepLeft = if (ui.sleepEndsAt > 0) ui.sleepEndsAt - System.currentTimeMillis() else 0
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StateTile(Speed.label(ui.speed), "倍速", active = ui.speed != 1f, modifier = Modifier.weight(1f)) { sheet = "speed" }
            StateTile(
                when (ui.repeat) { Repeat.OFF -> "關"; Repeat.ALL -> "清單"; Repeat.ONE -> "單曲" }, "循環",
                active = ui.repeat != Repeat.OFF, modifier = Modifier.weight(1f),
            ) { Graph.player.cycleRepeat() }
            StateTile(
                when { ui.sleepEndOfTrack -> "本集"; sleepLeft > 0 -> TimeText.sleepLeft(sleepLeft); else -> "關" }, "睡眠計時",
                active = ui.sleepEndOfTrack || sleepLeft > 0, modifier = Modifier.weight(1f),
            ) { sheet = "sleep" }
            StateTile(if (ui.skipSilence) "開" else "關", "跳過靜音", active = ui.skipSilence, modifier = Modifier.weight(1f)) { Graph.player.setSkipSilence(!ui.skipSilence) }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Chip(if (ui.shuffle) "隨機播放：開" else "隨機播放：關", selected = ui.shuffle) { Graph.player.toggleShuffle() }
        }

        // 接下來
        if (ui.upNext.isNotEmpty()) {
            Text("接下來", style = SfType.rowTitle, color = c.text, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Column(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, c.border, RoundedCornerShape(14.dp))) {
                ui.upNext.forEachIndexed { i, n ->
                    Row(
                        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Cover(n.channel, 32, 8, 11)
                        Text(n.title, style = SfType.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(SfFormat.duration((n.durationMs / 1000).toInt()), style = SfType.number, color = c.muted)
                    }
                    if (i < ui.upNext.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                }
            }
        }
    }

    when (sheet) {
        "speed" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = c.surface) { SpeedSheet(ui.speed) }
        "sleep" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = c.surface) { SleepSheet(ui) { sheet = null } }
    }
}

@Composable
private fun SeekIcon(icon: ImageVector, num: String, label: String, onClick: () -> Unit) {
    val c = Sf.colors
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = c.text, modifier = Modifier.size(32.dp))
        Text(num, style = SfType.number, color = c.text, fontSize = 10.sp)
    }
}

@Composable
private fun StateTile(value: String, label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.height(60.dp).clip(shape).background(if (active) c.accentSoft else c.surface)
            .border(1.dp, if (active) c.accentLine else c.border, shape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = SfType.number, fontSize = 15.sp, color = if (active) c.accentText else c.text)
        Text(label, style = SfType.meta, color = c.muted)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.height(36.dp).clip(shape).background(if (selected) c.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) c.accentLine else c.border, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = SfType.body, color = if (selected) c.accentText else c.text) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedSheet(current: Float) {
    val c = Sf.colors
    var v by remember { mutableFloatStateOf(current) }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("播放倍速", style = SfType.rowTitle, color = c.text, modifier = Modifier.weight(1f))
            Text(Speed.label(v), style = SfType.number, fontSize = 18.sp, color = c.accentText)
        }
        Text("會記住成這個分頁的預設倍速（Podcast、YouTube 各自一個）。", style = SfType.body, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Speed.PRESETS.forEach { p -> Chip(Speed.label(p), selected = Speed.clamp(v) == p) { v = p; Graph.player.setSpeed(p) } }
        }
        Slider(
            value = v, valueRange = Speed.MIN..Speed.MAX, steps = ((Speed.MAX - Speed.MIN) / 0.05f).toInt() - 1,
            onValueChange = { v = Speed.clamp(it) }, onValueChangeFinished = { Graph.player.setSpeed(v) },
            colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.accent, inactiveTrackColor = c.border, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
        )
        Row { Text("0.5×", style = SfType.number, color = c.muted, modifier = Modifier.weight(1f)); Text("3×", style = SfType.number, color = c.muted) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepSheet(ui: PlayerUi, onDone: () -> Unit) {
    val c = Sf.colors
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("睡眠計時", style = SfType.rowTitle, color = c.text)
        Text("時間到前 10 秒音量會慢慢變小再暫停。", style = SfType.body, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90).forEach { m -> Chip("$m 分鐘", selected = false) { Graph.player.setSleep(m); onDone() } }
            Chip("本集結束", selected = ui.sleepEndOfTrack) { Graph.player.setSleep(0, endOfTrack = true); onDone() }
        }
        if (ui.sleepEndsAt > 0 || ui.sleepEndOfTrack) {
            SfButton("取消睡眠計時", primary = false, onClick = { Graph.player.setSleep(0); onDone() }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(1.dp))
    }
}
