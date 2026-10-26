package tw.hoonsor.soundflow.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.background
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.R
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.player.Repeat
import tw.hoonsor.soundflow.player.Speed
import tw.hoonsor.soundflow.player.TimeText
import tw.hoonsor.soundflow.player.WidgetLayout
import tw.hoonsor.soundflow.player.WidgetState
import tw.hoonsor.soundflow.ui.MainActivity
import tw.hoonsor.soundflow.ui.theme.SfColors
import tw.hoonsor.soundflow.ui.theme.SfPalettes
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.toColor
import tw.hoonsor.soundflow.ui.theme.toColors

/**
 * 桌面小工具（F24–F27）。一個可調整大小的小工具，依高度切換三種版面：
 * 精簡 4×1（標題＋上一首／播放／下一首）、標準 4×2（再加倒轉、快轉、倍速、循環、換清單）、大型 4×3（再加接下來 3 集）。
 * 內容由播放服務寫入（[push]）；按鈕交給 [PlayerConnection]，播放器是空的時會從快照接續（重開機也能直接按播放）。
 */
class SoundFlowWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_COMPACT, SIZE_STANDARD, SIZE_LARGE))
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 新放上桌面的小工具還沒有狀態：先用最後一次存的檔案
        val fallback = WidgetState.load(context)
        val fallbackTheme = Graph.settings.theme.first()
        provideContent {
            val prefs = currentState<Preferences>()
            val state = prefs[KEY_STATE]?.let { WidgetState.fromJson(it) } ?: fallback
            val theme = SfThemeId.fromKey(prefs[KEY_THEME] ?: fallbackTheme.key)
            Content(state, SfPalettes.of(theme).toColors())
        }
    }

    companion object {
        private val SIZE_COMPACT = DpSize(250.dp, 60.dp)
        private val SIZE_STANDARD = DpSize(250.dp, 130.dp)
        private val SIZE_LARGE = DpSize(250.dp, 200.dp)
        private val KEY_STATE = stringPreferencesKey("state")
        private val KEY_THEME = stringPreferencesKey("theme")

        /** 寫入狀態並刷新所有小工具（播放服務、切換佈景主題時呼叫） */
        suspend fun push(context: Context, state: WidgetState = WidgetState.load(context)) {
            WidgetState.save(context, state)
            val theme = Graph.settings.theme.first().key
            val widget = SoundFlowWidget()
            GlanceAppWidgetManager(context).getGlanceIds(SoundFlowWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[KEY_STATE] = state.toJson(); it[KEY_THEME] = theme }
                widget.update(context, id)
            }
        }
    }
}

class SoundFlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SoundFlowWidget()
}

// ── 版面 ──────────────────────────────────────────────────────────
private fun cp(c: Color) = ColorProvider(c)

@Composable
private fun Content(s: WidgetState, c: SfColors) {
    val layout = WidgetLayout.forHeightDp(LocalSize.current.height.value)
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(cp(c.bg.copy(alpha = 0.95f))).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!s.hasTrack) { Empty(c); return@Column }
        when (layout) {
            WidgetLayout.COMPACT -> Compact(s, c)
            WidgetLayout.STANDARD -> { Header(s, c); Spacer(GlanceModifier.height(6.dp)); MainControls(s, c); Spacer(GlanceModifier.height(6.dp)); ExtraRow(s, c) }
            WidgetLayout.LARGE -> { Header(s, c); Spacer(GlanceModifier.height(6.dp)); MainControls(s, c); Spacer(GlanceModifier.height(6.dp)); ExtraRow(s, c); UpNext(s, c) }
        }
    }
}

@Composable
private fun Empty(c: SfColors) {
    Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
        Text("SoundFlow", style = TextStyle(color = cp(c.text), fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.width(10.dp))
        Text("打開 App 選一集開始播放", style = TextStyle(color = cp(c.muted), fontSize = 12.sp))
    }
}

@Composable
private fun Cover(s: WidgetState, size: Int) {
    val style = SourceStyle.of(s.channel)
    Box(
        GlanceModifier.size(size.dp).cornerRadius(10.dp).background(cp(SfSemantic.cover(style.hue).toColor())).clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) { Text(style.tag, style = TextStyle(color = cp(Color.White), fontSize = (size / 3).sp, fontWeight = FontWeight.Bold)) }
}

@Composable
private fun Header(s: WidgetState, c: SfColors) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cover(s, 44)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(s.title, maxLines = 1, style = TextStyle(color = cp(c.text), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text(
                "${TimeText.clock(s.positionMs)} / ${TimeText.clock(s.durationMs)} · ${s.source}",
                maxLines = 1, style = TextStyle(color = cp(c.muted), fontSize = 11.sp),
            )
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(progress = s.fraction, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = cp(c.accent), backgroundColor = cp(c.border))
        }
    }
}

@Composable
private fun Compact(s: WidgetState, c: SfColors) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cover(s, 40)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(s.title, maxLines = 1, style = TextStyle(color = cp(c.text), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            LinearProgressIndicator(progress = s.fraction, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = cp(c.accent), backgroundColor = cp(c.border))
        }
        IconButton(R.drawable.ic_w_prev, c.text, actionRunCallback<PrevAction>())
        PlayButton(s, c, 44)
        IconButton(R.drawable.ic_w_next, c.text, actionRunCallback<NextAction>())
    }
}

@Composable
private fun MainControls(s: WidgetState, c: SfColors) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(R.drawable.ic_w_back15, c.text, actionRunCallback<Back15Action>(), modifier = GlanceModifier.defaultWeight())
        IconButton(R.drawable.ic_w_prev, c.text, actionRunCallback<PrevAction>(), modifier = GlanceModifier.defaultWeight())
        PlayButton(s, c, 48)
        IconButton(R.drawable.ic_w_next, c.text, actionRunCallback<NextAction>(), modifier = GlanceModifier.defaultWeight())
        IconButton(R.drawable.ic_w_fwd30, c.text, actionRunCallback<Fwd30Action>(), modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun ExtraRow(s: WidgetState, c: SfColors) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pill(Speed.label(s.speed), active = s.speed != 1f, c = c, action = actionRunCallback<SpeedAction>(), modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(6.dp))
        Pill(
            when (s.repeat) { Repeat.OFF -> "循環：關"; Repeat.ALL -> "清單循環"; Repeat.ONE -> "單曲循環" },
            active = s.repeat != Repeat.OFF, c = c, action = actionRunCallback<RepeatAction>(), modifier = GlanceModifier.defaultWeight(),
            icon = R.drawable.ic_w_repeat,
        )
        Spacer(GlanceModifier.width(6.dp))
        Pill("換清單", active = false, c = c, action = actionStartActivity<WidgetPickerActivity>(), modifier = GlanceModifier.defaultWeight(), icon = R.drawable.ic_w_playlist)
    }
}

@Composable
private fun UpNext(s: WidgetState, c: SfColors) {
    if (s.upNext.isEmpty()) return
    Spacer(GlanceModifier.height(6.dp))
    s.upNext.forEachIndexed { i, title ->
        val target = s.upNextIndex.getOrNull(i) ?: return@forEachIndexed
        Row(
            GlanceModifier.fillMaxWidth().height(34.dp).clickable(actionRunCallback<JumpAction>(actionParametersOf(JumpAction.INDEX to target))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 2}", style = TextStyle(color = cp(c.muted), fontSize = 11.sp))
            Spacer(GlanceModifier.width(10.dp))
            Text(title, maxLines = 1, style = TextStyle(color = cp(c.text), fontSize = 13.sp))
        }
    }
}

@Composable
private fun PlayButton(s: WidgetState, c: SfColors, size: Int) {
    Box(
        GlanceModifier.size(size.dp).cornerRadius(14.dp).background(cp(c.accent)).clickable(actionRunCallback<PlayPauseAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
            contentDescription = if (s.playing) "暫停" else "播放",
            colorFilter = ColorFilter.tint(cp(c.onAccent)), modifier = GlanceModifier.size((size / 2.2).dp),
        )
    }
}

@Composable
private fun IconButton(icon: Int, tint: Color, action: androidx.glance.action.Action, modifier: GlanceModifier = GlanceModifier) {
    Box(modifier.height(44.dp).width(40.dp).clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(cp(tint)), modifier = GlanceModifier.size(22.dp))
    }
}

@Composable
private fun Pill(label: String, active: Boolean, c: SfColors, action: androidx.glance.action.Action, modifier: GlanceModifier, icon: Int? = null) {
    Row(
        modifier.height(36.dp).cornerRadius(10.dp).background(cp(if (active) c.accentSoft else c.surface)).clickable(action).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(ImageProvider(icon), contentDescription = null, colorFilter = ColorFilter.tint(cp(if (active) c.accentText else c.text)), modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(label, maxLines = 1, style = TextStyle(color = cp(if (active) c.accentText else c.text), fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

// ── 按鈕動作：Glance 在 App 的 process 裡執行；PlayerConnection 會在需要時從快照接續 ──
class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.togglePlayNow()
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        Graph.player.exec { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
}

class PrevAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.exec { it.seekToPrevious() }
}

class Back15Action : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.exec { it.seekBack() }
}

class Fwd30Action : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.exec { it.seekForward() }
}

class SpeedAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) =
        Graph.player.exec { it.setPlaybackSpeed(Speed.nextWidget(it.playbackParameters.speed)) }
}

class RepeatAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.exec {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

class JumpAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[INDEX] ?: return
        Graph.player.exec { if (target in 0 until it.mediaItemCount) { it.seekTo(target, 0); it.play() } }
    }

    companion object { val INDEX = ActionParameters.Key<Int>("index") }
}
