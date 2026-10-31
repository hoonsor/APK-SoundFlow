package tw.hoonsor.soundflow.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
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
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import kotlinx.coroutines.flow.first
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.R
import tw.hoonsor.soundflow.data.CoverBitmaps
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.player.PlayMode
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
 * 桌面小工具「全幅沉浸」（F53，設計畫布第 7 列）：封面鋪滿＋暗色遮罩，依大小切換 2×1、5×1、2×2、4×2。
 * 內容由播放服務寫入（[push]）；按鈕交給 [PlayerConnection]，播放器是空的時會從快照接續（重開機也能直接按播放）。
 * 系統限制：小工具只收得到「點一下」，滑動與拖曳都放在點歌名開的 [WidgetPanelActivity]。
 */
class SoundFlowWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_MINI, SIZE_BAR, SIZE_SQUARE, SIZE_FULL))
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
        private val SIZE_MINI = DpSize(110.dp, 60.dp)
        private val SIZE_BAR = DpSize(280.dp, 60.dp)
        private val SIZE_SQUARE = DpSize(110.dp, 130.dp)
        private val SIZE_FULL = DpSize(220.dp, 130.dp)
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

private val White = Color.White
private val WhiteSoft = Color(0xEBFFFFFF)
private val OnWhite = Color(0xFF111A2E)
private val ChipBg = Color(0x29FFFFFF)
private val TrackBg = Color(0x47FFFFFF)
private val Scrim = Color(0x85090D18)

/** 背景圖先縮小並用 RGB_565：小工具的畫面要經過系統傳給桌面程式，圖太大會傳不過去 */
@Composable
private fun rememberBackdrop(s: WidgetState): Bitmap? = remember(s.coverPath, s.coverStamp) {
    s.coverPath?.let { CoverBitmaps.decodeFile(it, 480, Bitmap.Config.RGB_565) }
}

@Composable
private fun Content(s: WidgetState, c: SfColors) {
    val size = LocalSize.current
    val layout = WidgetLayout.forSizeDp(size.width.value, size.height.value)
    val backdrop = rememberBackdrop(s)
    val tint = SfSemantic.cover(SourceStyle.of(s.channel).hue).toColor()
    Box(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(cp(tint))) {
        if (backdrop != null) {
            Image(ImageProvider(backdrop), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
        } else {
            Image(ImageProvider(R.drawable.ic_w_wave_art), contentDescription = null, contentScale = ContentScale.Crop, colorFilter = ColorFilter.tint(cp(c.accent.copy(alpha = 0.45f))), modifier = GlanceModifier.fillMaxSize())
        }
        Box(GlanceModifier.fillMaxSize().background(cp(Scrim))) {}
        if (!s.hasTrack) Empty()
        else when (layout) {
            WidgetLayout.MINI -> Mini(s)
            WidgetLayout.BAR -> Bar(s)
            WidgetLayout.SQUARE -> Square(s)
            WidgetLayout.FULL -> Full(s)
        }
    }
}

@Composable
private fun Empty() {
    Column(GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
        Text("SoundFlow", style = TextStyle(color = cp(White), fontSize = 15.sp, fontWeight = FontWeight.Bold))
        Text("打開 App 選一首開始播放", maxLines = 2, style = TextStyle(color = cp(WhiteSoft), fontSize = 12.sp))
    }
}

/** 2×1：歌名、上一首、播放、下一首 */
@Composable
private fun Mini(s: WidgetState) {
    Column(GlanceModifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
        Title(s, 13, 1, GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp))
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(R.drawable.ic_w_prev, "上一首", actionRunCallback<PrevAction>(), GlanceModifier.defaultWeight())
            PlayButton(s, 44)
            Icon(R.drawable.ic_w_next, "下一首", actionRunCallback<NextAction>(), GlanceModifier.defaultWeight())
        }
    }
}

/** 5×1：歌名；換清單、模式、倍速＋上一首、播放、下一首 */
@Composable
private fun Bar(s: WidgetState) {
    Column(GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 6.dp)) {
        Title(s, 14, 1, GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ListChip(s, 92)
            Spacer(GlanceModifier.width(4.dp))
            ModeButton(s, 38)
            Spacer(GlanceModifier.width(4.dp))
            SpeedButton(s, 52, 38)
            Spacer(GlanceModifier.defaultWeight())
            Icon(R.drawable.ic_w_prev, "上一首", actionRunCallback<PrevAction>())
            PlayButton(s, 44)
            Icon(R.drawable.ic_w_next, "下一首", actionRunCallback<NextAction>())
        }
    }
}

/** 2×2：歌名；上一首、播放、下一首；換清單、模式、倍速（按鈕與 5×1 相同） */
@Composable
private fun Square(s: WidgetState) {
    Column(GlanceModifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Title(s, 15, 2, GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp))
        Text(
            "${s.source} · 第 ${s.index + 1} 首", maxLines = 1,
            style = TextStyle(color = cp(WhiteSoft), fontSize = 11.sp), modifier = GlanceModifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(R.drawable.ic_w_prev, "上一首", actionRunCallback<PrevAction>(), GlanceModifier.defaultWeight(), 24)
            PlayButton(s, 46)
            Icon(R.drawable.ic_w_next, "下一首", actionRunCallback<NextAction>(), GlanceModifier.defaultWeight(), 24)
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Round(R.drawable.ic_w_playlist, "切換播放清單：${s.source}", actionStartActivity<WidgetPickerActivity>(), 40)
            Spacer(GlanceModifier.defaultWeight())
            ModeButton(s, 40)
            Spacer(GlanceModifier.defaultWeight())
            SpeedButton(s, 46, 40)
        }
    }
}

/** 4×2：上排換清單、模式、倍速、睡眠、面板；中間歌名、時間、進度；下排倒退、上下首、播放、快轉 */
@Composable
private fun Full(s: WidgetState) {
    val sleepLeft = s.sleepEndsAt - System.currentTimeMillis()
    Column(GlanceModifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ListChip(s, 92)
            Spacer(GlanceModifier.defaultWeight())
            ModeButton(s, 38)
            Spacer(GlanceModifier.width(5.dp))
            SpeedButton(s, 52, 38)
            Spacer(GlanceModifier.width(5.dp))
            Round(R.drawable.ic_w_moon, "睡眠計時", actionRunCallback<SleepAction>(), 38, active = sleepLeft > 0)
            Spacer(GlanceModifier.width(5.dp))
            Round(R.drawable.ic_w_sliders, "開啟控制面板", actionStartActivity<WidgetPanelActivity>(), 38)
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Title(s, 16, 1, GlanceModifier.fillMaxWidth())
            val sleepText = if (sleepLeft > 0) " · ${TimeText.sleepLeft(sleepLeft)}後停止" else ""
            Text(
                "第 ${s.index + 1} 首 · ${TimeText.clock(s.positionMs)} / ${TimeText.clock(s.durationMs)}$sleepText", maxLines = 1,
                style = TextStyle(color = cp(WhiteSoft), fontSize = 11.sp),
            )
            Spacer(GlanceModifier.height(7.dp))
            LinearProgressIndicator(progress = s.fraction, modifier = GlanceModifier.fillMaxWidth().height(3.dp), color = cp(White), backgroundColor = cp(TrackBg))
        }
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Seek(R.drawable.ic_w_back15, "15", "倒退 15 秒", actionRunCallback<Back15Action>())
            Icon(R.drawable.ic_w_prev, "上一首", actionRunCallback<PrevAction>(), GlanceModifier.defaultWeight(), 26)
            PlayButton(s, 48)
            Icon(R.drawable.ic_w_next, "下一首", actionRunCallback<NextAction>(), GlanceModifier.defaultWeight(), 26)
            Seek(R.drawable.ic_w_fwd30, "30", "快轉 30 秒", actionRunCallback<Fwd30Action>())
        }
    }
}

/** 點歌名開手勢控制面板 */
@Composable
private fun Title(s: WidgetState, size: Int, lines: Int, modifier: GlanceModifier) {
    Text(
        s.title, maxLines = lines, modifier = modifier.clickable(actionStartActivity<WidgetPanelActivity>()),
        style = TextStyle(color = cp(White), fontSize = size.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun PlayButton(s: WidgetState, size: Int) {
    Box(
        GlanceModifier.size(size.dp).cornerRadius((size / 2).dp).background(cp(White)).clickable(actionRunCallback<PlayPauseAction>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play),
            contentDescription = if (s.playing) "暫停" else "播放",
            colorFilter = ColorFilter.tint(cp(OnWhite)), modifier = GlanceModifier.size((size * 0.45).dp),
        )
    }
}

@Composable
private fun Icon(icon: Int, label: String, action: Action, modifier: GlanceModifier = GlanceModifier.width(40.dp), iconSize: Int = 22) {
    Box(modifier.height(44.dp).clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), contentDescription = label, colorFilter = ColorFilter.tint(cp(White)), modifier = GlanceModifier.size(iconSize.dp))
    }
}

/** 倒退／快轉：圖示中間疊秒數 */
@Composable
private fun RowScope.Seek(icon: Int, seconds: String, label: String, action: Action) {
    Box(GlanceModifier.defaultWeight().height(48.dp).clickable(action), contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), contentDescription = label, colorFilter = ColorFilter.tint(cp(White)), modifier = GlanceModifier.size(30.dp))
        Text(seconds, style = TextStyle(color = cp(White), fontSize = 9.sp, fontWeight = FontWeight.Bold))
    }
}

/** 圓形半透明按鈕；active＝使用中（反白） */
@Composable
private fun Round(icon: Int, label: String, action: Action, size: Int, active: Boolean = false) {
    Box(
        GlanceModifier.size(size.dp).cornerRadius((size / 2).dp).background(cp(if (active) White else ChipBg)).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = label, colorFilter = ColorFilter.tint(cp(if (active) OnWhite else White)), modifier = GlanceModifier.size(18.dp))
    }
}

@Composable
private fun ModeButton(s: WidgetState, size: Int) {
    val icon = when (s.mode) {
        PlayMode.ALL -> R.drawable.ic_w_repeat
        PlayMode.ONE -> R.drawable.ic_w_repeat_one
        PlayMode.SHUFFLE -> R.drawable.ic_w_shuffle
    }
    Round(icon, "播放模式：${s.mode.label}", actionRunCallback<ModeAction>(), size)
}

@Composable
private fun SpeedButton(s: WidgetState, width: Int, height: Int) {
    Box(
        GlanceModifier.width(width.dp).height(height.dp).cornerRadius((height / 2).dp).background(cp(ChipBg)).clickable(actionRunCallback<SpeedAction>()),
        contentAlignment = Alignment.Center,
    ) { Text(Speed.label(s.speed), maxLines = 1, style = TextStyle(color = cp(White), fontSize = 12.sp, fontWeight = FontWeight.Medium)) }
}

@Composable
private fun ListChip(s: WidgetState, width: Int) {
    Row(
        GlanceModifier.width(width.dp).height(38.dp).cornerRadius(19.dp).background(cp(ChipBg)).clickable(actionStartActivity<WidgetPickerActivity>())
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(R.drawable.ic_w_playlist), contentDescription = "切換播放清單", colorFilter = ColorFilter.tint(cp(White)), modifier = GlanceModifier.size(15.dp))
        Spacer(GlanceModifier.width(5.dp))
        Text(s.source, maxLines = 1, style = TextStyle(color = cp(White), fontSize = 12.sp, fontWeight = FontWeight.Medium))
    }
}

// ── 按鈕動作：Glance 在 App 的 process 裡執行；PlayerConnection 負責切回主執行緒，需要時從快照接續 ──
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

class ModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.cycleModeNow()
}

class SleepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) = Graph.player.cycleSleepNow()
}
