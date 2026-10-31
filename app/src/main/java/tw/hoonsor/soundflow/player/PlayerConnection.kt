package tw.hoonsor.soundflow.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tw.hoonsor.soundflow.BuildConfig
import tw.hoonsor.soundflow.data.ProgressRepository
import tw.hoonsor.soundflow.data.SfUrls
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind

/** 畫面需要的播放狀態（每 0.5 秒與事件發生時更新） */
data class PlayerUi(
    val currentId: String? = null,
    val title: String = "",
    val channel: String? = null,
    val kind: TrackKind? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val repeat: Repeat = Repeat.OFF,
    val shuffle: Boolean = false,
    /** 目前這集的封面網址（YouTube）；本機與已快取的封面由 CoverStore 依 id 找 */
    val thumbnailUrl: String? = null,
    val prevTitle: String? = null,
    val index: Int = 0,
    val count: Int = 0,
    val upNext: List<UpNext> = emptyList(),
    val sleepEndsAt: Long = 0,
    val sleepEndOfTrack: Boolean = false,
    val skipSilence: Boolean = false,
    val error: String? = null,
) {
    val mode: PlayMode get() = PlayMode.of(repeat, shuffle)
}

data class UpNext(val id: String, val title: String, val channel: String?, val durationMs: Long, val thumbnailUrl: String?)

/** 套用播放模式（App 端的控制器與播放服務的播放器共用） */
fun Player.applyMode(m: PlayMode) {
    repeatMode = if (m == PlayMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    shuffleModeEnabled = m == PlayMode.SHUFFLE
}

/**
 * App 端的播放控制入口：連到 PlaybackService 的 MediaController。
 * 畫面只呼叫這裡，不直接碰 Media3。
 */
class PlayerConnection(
    private val context: Context,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null
    private val connectLock = Mutex()
    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state

    private suspend fun controller(): MediaController = connectLock.withLock {
        controller?.takeIf { it.isConnected }?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val c = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
            })
            .buildAsync().await()
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = refresh()
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(error = playbackErrorText(error))
            }
        })
        controller = c
        refresh()
        c
    }

    /** App 開啟時呼叫：服務還在播的話，迷你播放列立刻出現 */
    fun connect() { scope.launch { runCatching { controller() } } }

    fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val extras = c.sessionExtras
        val count = c.mediaItemCount
        val idx = c.currentMediaItemIndex
        val repeat = when (c.repeatMode) {
            Player.REPEAT_MODE_ONE -> Repeat.ONE
            Player.REPEAT_MODE_ALL -> Repeat.ALL
            else -> Repeat.OFF
        }
        val upNext = Queue.upNext(count, idx, 3, repeat == Repeat.ALL && !c.shuffleModeEnabled).map { i ->
            val it = c.getMediaItemAt(i)
            val m = it.mediaMetadata
            UpNext(it.mediaId, m.title?.toString().orEmpty(), m.extras?.getString(MediaItems.EXTRA_CHANNEL), m.durationMs ?: 0, m.extras?.getString(MediaItems.EXTRA_THUMB))
        }
        val prev = c.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { c.getMediaItemAt(it).mediaMetadata.title?.toString() }
        _state.value = PlayerUi(
            currentId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            channel = item?.mediaMetadata?.extras?.getString(MediaItems.EXTRA_CHANNEL),
            kind = item?.mediaMetadata?.extras?.getString(PlaybackService.EXTRA_KIND)?.let { k -> TrackKind.entries.firstOrNull { it.key == k } },
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: (item?.mediaMetadata?.durationMs ?: 0),
            speed = c.playbackParameters.speed,
            repeat = repeat,
            shuffle = c.shuffleModeEnabled,
            thumbnailUrl = item?.mediaMetadata?.extras?.getString(MediaItems.EXTRA_THUMB),
            prevTitle = prev,
            index = idx,
            count = count,
            upNext = upNext,
            sleepEndsAt = extras.getLong(PlaybackService.EXTRA_SLEEP_ENDS_AT),
            sleepEndOfTrack = extras.getBoolean(PlaybackService.EXTRA_SLEEP_END_OF_TRACK),
            skipSilence = extras.getBoolean(PlaybackService.EXTRA_SKIP_SILENCE),
            error = if (c.playerError != null) _state.value.error else null,
        )
    }

    /**
     * 播放一個清單，從 startIndex 那集開始；會接續上次的進度。
     * source 是清單名稱（Podcast、YouTube、清單名），小工具會顯示；同時存成快照，重開機後小工具能接續（F26）
     */
    fun playList(tracks: List<Track>, startIndex: Int, source: String = "") = scope.launch { startList(tracks, startIndex, source, null) }

    private suspend fun startList(tracks: List<Track>, startIndex: Int, source: String, positionMs: Long?) {
        val playable = tracks.mapNotNull { t -> MediaItems.of(t)?.let { t to it } }
        if (playable.isEmpty()) {
            _state.value = _state.value.copy(error = "這些音檔還不能播放（封存到 Drive 的要等之後的版本）")
            return
        }
        val wanted = tracks.getOrNull(startIndex)?.id
        val start = playable.indexOfFirst { it.first.id == wanted }.coerceAtLeast(0)
        val pos = positionMs ?: ProgressRules.resumePosition(progress.get(playable[start].first.id))
        val label = source.ifBlank {
            when (playable[start].first.kind) { TrackKind.PODCAST -> "Podcast"; TrackKind.YOUTUBE -> "YouTube"; TrackKind.LOCAL -> "本機音檔" }
        }
        withContext(Dispatchers.IO) { QueueSnapshot.save(context, QueueSnapshot(label, playable.map { it.first }, start, pos)) }
        val c = controller()
        c.setMediaItems(playable.map { it.second }, start, pos)
        c.prepare()
        c.play()
    }

    /** 播放器是空的（App 剛啟動、手機重開機）時，從快照接續上次的清單與位置 */
    private suspend fun resumeFromSnapshot(): Boolean {
        val snap = withContext(Dispatchers.IO) { QueueSnapshot.load(context) } ?: return false
        startList(snap.tracks, snap.index, snap.source, snap.positionMs)
        return true
    }

    /** 點到正在播的那集：切換播放／暫停；點到別集：從那集開始播整個清單 */
    fun playOrToggle(tracks: List<Track>, index: Int, source: String = "") {
        val id = tracks.getOrNull(index)?.id
        if (id != null && id == _state.value.currentId) togglePlay() else playList(tracks, index, source)
    }

    fun togglePlay() = scope.launch { runCatching { togglePlayNow() } }

    /**
     * 小工具的動作要等指令送出才結束，所以提供 suspend 版本。
     * Glance 的 ActionCallback 不在主執行緒，MediaController 又只能在主執行緒操作，一律切回主執行緒
     * （v0.6.0 按鈕沒反應的原因：Media3 以 wrong thread 擋下，換清單走 Activity 所以沒事）
     */
    suspend fun togglePlayNow() = withContext(Dispatchers.Main.immediate) {
        val c = controller()
        when {
            c.mediaItemCount == 0 -> resumeFromSnapshot()
            c.isPlaying -> c.pause()
            else -> { if (c.playbackState == Player.STATE_ENDED) c.seekTo(0); c.play() }
        }
        refresh()
    }

    /** 小工具：在播放器有內容時執行指令；空的就先從快照接續（不自動播放的指令會被略過） */
    suspend fun exec(block: (MediaController) -> Unit) = withContext(Dispatchers.Main.immediate) {
        val c = controller()
        if (c.mediaItemCount == 0 && !resumeFromSnapshot()) return@withContext
        block(c)
        refresh()
    }

    /** 小工具與播放頁共用的三段式播放模式 */
    suspend fun cycleModeNow() = exec { it.applyMode(modeOf(it).next()) }

    /** 小工具的睡眠計時鈕：關 → 15 → 30 → 60 → 關 */
    suspend fun cycleSleepNow() = exec {
        val ends = it.sessionExtras.getLong(PlaybackService.EXTRA_SLEEP_ENDS_AT)
        val left = if (ends > 0) ends - System.currentTimeMillis() else 0
        sendSleep(it, SleepCycle.nextMinutes(left), false)
    }

    private fun modeOf(p: Player) = PlayMode.of(if (p.repeatMode == Player.REPEAT_MODE_ONE) Repeat.ONE else Repeat.ALL, p.shuffleModeEnabled)

    private fun sendSleep(c: MediaController, minutes: Int, endOfTrack: Boolean) {
        c.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY), Bundle().apply {
            putInt(PlaybackService.ARG_MINUTES, minutes); putBoolean(PlaybackService.ARG_END_OF_TRACK, endOfTrack)
        })
    }
    fun seekBack() = withController { it.seekBack() }
    fun seekForward() = withController { it.seekForward() }
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun setSpeed(v: Float) = withController { it.setPlaybackSpeed(Speed.clamp(v)) }
    fun cycleMode() = withController { it.applyMode(modeOf(it).next()) }

    /** 播放整份清單；shuffle：true＝隨機、false＝照順序、null＝維持目前模式（小工具換清單） */
    fun playAll(tracks: List<Track>, shuffle: Boolean?, source: String = "") {
        if (tracks.isEmpty()) return
        val random = shuffle ?: _state.value.shuffle
        val start = if (random) tracks.indices.random() else 0
        if (shuffle != null) withController {
            val mode = modeOf(it)
            it.applyMode(if (shuffle) PlayMode.SHUFFLE else if (mode == PlayMode.SHUFFLE) PlayMode.ALL else mode)
        }
        playList(tracks, start, source)
    }
    fun setSleep(minutes: Int, endOfTrack: Boolean = false) = withController { sendSleep(it, minutes, endOfTrack) }
    fun setSkipSilence(enabled: Boolean) = withController {
        it.sendCustomCommand(SessionCommand(PlaybackService.CMD_SKIP_SILENCE, Bundle.EMPTY), Bundle().apply { putBoolean(PlaybackService.ARG_ENABLED, enabled) })
    }
    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun withController(block: (MediaController) -> Unit) {
        scope.launch { runCatching { block(controller()); refresh() } }
    }

    companion object {
        fun playbackErrorText(e: PlaybackException): String = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "網路斷了，連上後按播放繼續"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "找不到這個音檔，可能已經被搬走或刪除"
            else -> "無法播放這個音檔（${e.errorCodeName}）"
        }
    }
}
