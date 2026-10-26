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
    val index: Int = 0,
    val count: Int = 0,
    val upNext: List<UpNext> = emptyList(),
    val sleepEndsAt: Long = 0,
    val sleepEndOfTrack: Boolean = false,
    val skipSilence: Boolean = false,
    val error: String? = null,
)

data class UpNext(val title: String, val channel: String?, val durationMs: Long)

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
            val m = c.getMediaItemAt(i).mediaMetadata
            UpNext(m.title?.toString().orEmpty(), m.extras?.getString(MediaItems.EXTRA_CHANNEL), m.durationMs ?: 0)
        }
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
        val label = source.ifBlank { if (playable[start].first.kind == TrackKind.PODCAST) "Podcast" else "YouTube" }
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

    /** 小工具的動作要等指令送出才結束，所以提供 suspend 版本 */
    suspend fun togglePlayNow() {
        val c = controller()
        when {
            c.mediaItemCount == 0 -> resumeFromSnapshot()
            c.isPlaying -> c.pause()
            else -> { if (c.playbackState == Player.STATE_ENDED) c.seekTo(0); c.play() }
        }
        refresh()
    }

    /** 小工具：在播放器有內容時執行指令；空的就先從快照接續（不自動播放的指令會被略過） */
    suspend fun exec(block: (MediaController) -> Unit) {
        val c = controller()
        if (c.mediaItemCount == 0 && !resumeFromSnapshot()) return
        block(c)
        refresh()
    }
    fun seekBack() = withController { it.seekBack() }
    fun seekForward() = withController { it.seekForward() }
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun setSpeed(v: Float) = withController { it.setPlaybackSpeed(Speed.clamp(v)) }
    fun cycleRepeat() = withController {
        it.repeatMode = when (_state.value.repeat.next()) {
            Repeat.OFF -> Player.REPEAT_MODE_OFF
            Repeat.ALL -> Player.REPEAT_MODE_ALL
            Repeat.ONE -> Player.REPEAT_MODE_ONE
        }
    }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** 播放整份清單；shuffle＝隨機播放（播放清單頁的「隨機播放」按鈕） */
    fun playAll(tracks: List<Track>, shuffle: Boolean, source: String = "") {
        if (tracks.isEmpty()) return
        val start = if (shuffle) tracks.indices.random() else 0
        withController { it.shuffleModeEnabled = shuffle }
        playList(tracks, start, source)
    }
    fun setSleep(minutes: Int, endOfTrack: Boolean = false) = withController {
        it.sendCustomCommand(SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY), Bundle().apply {
            putInt(PlaybackService.ARG_MINUTES, minutes); putBoolean(PlaybackService.ARG_END_OF_TRACK, endOfTrack)
        })
    }
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
