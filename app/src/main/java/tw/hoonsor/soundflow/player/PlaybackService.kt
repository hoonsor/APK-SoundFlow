package tw.hoonsor.soundflow.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tw.hoonsor.soundflow.widget.SoundFlowWidget
import tw.hoonsor.soundflow.BuildConfig
import tw.hoonsor.soundflow.data.SfUrls
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.ui.MainActivity

/**
 * 背景播放：ExoPlayer＋MediaSession。通知列、鎖定畫面、藍牙耳機按鍵都由 MediaSession 提供；
 * 之後的桌面小工具、Android Auto、語音動作也都連到這個 session。
 * 跟 App 同一個 process，直接用 Graph 裡的 Repository 存進度與倍速。
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saver: Job? = null
    private var sleepJob: Job? = null
    private var sleepEndsAt = 0L
    private var sleepEndOfTrack = false
    private var skipSilence = false
    /** 套用分頁預設倍速時不要反過來存成「使用者改了倍速」 */
    private var applyingDefaultSpeed = false

    override fun onCreate() {
        super.onCreate()
        Graph.init(application)
        // 私人 bucket（YouTube 音檔）：每次請求才向 AuthRepository 取有效權杖，長時間播放權杖過期也會自動換新
        val http = DefaultHttpDataSource.Factory()
        val authed = ResolvingDataSource.Factory(http) { spec ->
            if (!SfUrls.needsAuth(spec.uri.toString())) return@Factory spec
            val token = runBlocking { runCatching { Graph.auth.accessToken() }.getOrNull() } ?: return@Factory spec
            spec.withAdditionalHeaders(mapOf("Authorization" to "Bearer $token", "apikey" to BuildConfig.SUPABASE_KEY))
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, authed)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)          // 拔耳機、藍牙斷線就暫停
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.addListener(listener)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setCallback(Callback()).setSessionActivity(openApp).build()
        scope.launch { Graph.progress.load() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** 從最近使用的 App 滑掉時：沒在播就整個結束，有在播就繼續（像一般音樂 App） */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveProgress()
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = scheduleWidget()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startSaver() else { saver?.cancel(); saveProgress() }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 自動播到下一集：上一集算聽完；睡眠計時設成「本集結束」就停
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                previousId?.let { id -> scope.launch { Graph.progress.markPlayed(id, true) } }
                if (sleepEndOfTrack) { player.pause(); clearSleep() }
            }
            previousId = mediaItem?.mediaId
            applyKindSpeed(mediaItem)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                player.currentMediaItem?.mediaId?.let { id -> scope.launch { Graph.progress.markPlayed(id, true) } }
                if (sleepEndOfTrack) clearSleep()
            }
        }

        override fun onPlaybackParametersChanged(params: PlaybackParameters) {
            if (applyingDefaultSpeed) return
            val kind = kindOf(player.currentMediaItem) ?: return
            scope.launch { Graph.settings.setSpeed(kind, params.speed) }
        }
    }
    private var previousId: String? = null

    private fun kindOf(item: MediaItem?): TrackKind? =
        item?.mediaMetadata?.extras?.getString(EXTRA_KIND)?.let { k -> TrackKind.entries.firstOrNull { it.key == k } }

    private fun applyKindSpeed(item: MediaItem?) {
        val kind = kindOf(item) ?: return
        scope.launch {
            val v = Graph.settings.speed(kind)
            if (player.playbackParameters.speed != v) {
                applyingDefaultSpeed = true
                player.setPlaybackSpeed(v)
                applyingDefaultSpeed = false
            }
        }
    }

    private fun startSaver() {
        saver?.cancel()
        saver = scope.launch {
            while (isActive) { delay(5_000); saveProgress() }
        }
    }

    private fun saveProgress() {
        val id = player.currentMediaItem?.mediaId ?: return
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: return
        scope.launch { Graph.progress.update(id, pos, dur) }
        scope.launch(Dispatchers.IO) { QueueSnapshot.updatePosition(applicationContext, id, pos) }
        // 進度條每 30 秒刷新一次就好：小工具更新很耗電，播放／切歌等事件另外會立即刷新
        if (System.currentTimeMillis() - lastWidgetTick > 30_000) { lastWidgetTick = System.currentTimeMillis(); scheduleWidget() }
    }
    private var lastWidgetTick = 0L

    // ── 桌面小工具（F24–F27）：事件發生時（合併 0.5 秒內的多次事件）寫狀態檔並刷新小工具 ──
    private var widgetJob: Job? = null

    private fun scheduleWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(500)
            val item = player.currentMediaItem
            val m = item?.mediaMetadata
            val repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> Repeat.ONE
                Player.REPEAT_MODE_ALL -> Repeat.ALL
                else -> Repeat.OFF
            }
            val nextIdx = Queue.upNext(player.mediaItemCount, player.currentMediaItemIndex, 3, repeat == Repeat.ALL && !player.shuffleModeEnabled)
            val next = nextIdx.map { player.getMediaItemAt(it).mediaMetadata.title?.toString().orEmpty() }
            val state = WidgetState(
                title = m?.title?.toString().orEmpty(),
                channel = m?.extras?.getString(MediaItems.EXTRA_CHANNEL),
                kind = m?.extras?.getString(EXTRA_KIND),
                source = withContext(Dispatchers.IO) { QueueSnapshot.load(applicationContext)?.source }.orEmpty(),
                playing = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: (m?.durationMs ?: 0),
                speed = player.playbackParameters.speed,
                repeat = repeat,
                upNext = next,
                upNextIndex = nextIdx,
                updatedAt = System.currentTimeMillis(),
            )
            // 播放器被清空（例如停止）時保留上一次的內容，小工具才看得到「上次聽到哪」
            if (state.hasTrack || !WidgetState.load(applicationContext).hasTrack) {
                runCatching { SoundFlowWidget.push(applicationContext, state) }
            }
        }
    }

    // ── 睡眠計時（F16）：時間到前 10 秒音量淡出再暫停 ─────────────────
    private fun setSleep(minutes: Int, endOfTrack: Boolean) {
        sleepJob?.cancel()
        player.volume = 1f
        sleepEndOfTrack = endOfTrack
        sleepEndsAt = if (!endOfTrack && minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        if (sleepEndsAt > 0) {
            sleepJob = scope.launch {
                while (isActive) {
                    val left = sleepEndsAt - System.currentTimeMillis()
                    if (left <= 0) { player.pause(); player.volume = 1f; clearSleep(); break }
                    if (left < FADE_MS) player.volume = (left.toFloat() / FADE_MS).coerceIn(0.05f, 1f)
                    delay(if (left < FADE_MS) 200 else 1_000)
                }
            }
        }
        publishExtras()
    }

    private fun clearSleep() {
        sleepJob?.cancel()
        sleepEndsAt = 0L
        sleepEndOfTrack = false
        player.volume = 1f
        publishExtras()
    }

    private fun publishExtras() {
        session?.setSessionExtras(Bundle().apply {
            putLong(EXTRA_SLEEP_ENDS_AT, sleepEndsAt)
            putBoolean(EXTRA_SLEEP_END_OF_TRACK, sleepEndOfTrack)
            putBoolean(EXTRA_SKIP_SILENCE, skipSilence)
        })
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .add(SessionCommand(CMD_SKIP_SILENCE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(cmds).build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) = publishExtras()

        /** Android 13+ 系統的「繼續播放」卡片、藍牙耳機按播放時：從快照接續上次的清單 */
        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val snap = QueueSnapshot.load(applicationContext)
            val items = snap?.tracks?.mapNotNull { MediaItems.of(it) }.orEmpty()
            if (snap == null || items.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("沒有可接續的清單"))
            val idx = snap.tracks.getOrNull(snap.index)?.let { t -> items.indexOfFirst { it.mediaId == t.id } }?.takeIf { it >= 0 } ?: 0
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, idx, snap.positionMs))
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SLEEP -> setSleep(args.getInt(ARG_MINUTES), args.getBoolean(ARG_END_OF_TRACK))
                CMD_SKIP_SILENCE -> {
                    skipSilence = args.getBoolean(ARG_ENABLED)
                    player.skipSilenceEnabled = skipSilence
                    publishExtras()
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val EXTRA_KIND = "sf.kind"
        const val EXTRA_SLEEP_ENDS_AT = "sf.sleepEndsAt"
        const val EXTRA_SLEEP_END_OF_TRACK = "sf.sleepEndOfTrack"
        const val EXTRA_SKIP_SILENCE = "sf.skipSilence"
        const val CMD_SLEEP = "sf.sleep"
        const val CMD_SKIP_SILENCE = "sf.skipSilence"
        const val ARG_MINUTES = "minutes"
        const val ARG_END_OF_TRACK = "endOfTrack"
        const val ARG_ENABLED = "enabled"
        private const val FADE_MS = 10_000L
    }
}
