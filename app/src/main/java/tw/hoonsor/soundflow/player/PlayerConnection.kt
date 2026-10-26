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
import kotlinx.coroutines.launch
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
            UpNext(m.title?.toString().orEmpty(), m.extras?.getString(EXTRA_CHANNEL), m.durationMs ?: 0)
        }
        _state.value = PlayerUi(
            currentId = item?.mediaId,
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            channel = item?.mediaMetadata?.extras?.getString(EXTRA_CHANNEL),
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

    /** 播放一個清單，從 startIndex 那集開始；會接續上次的進度 */
    fun playList(tracks: List<Track>, startIndex: Int) = scope.launch {
        val playable = tracks.mapNotNull { t -> SfUrls.audio(BuildConfig.SUPABASE_URL, t)?.let { t to it } }
        if (playable.isEmpty()) {
            _state.value = _state.value.copy(error = "這些音檔還不能播放（封存到 Drive 的要等之後的版本）")
            return@launch
        }
        val wanted = tracks.getOrNull(startIndex)?.id
        val start = playable.indexOfFirst { it.first.id == wanted }.coerceAtLeast(0)
        val c = controller()
        c.setMediaItems(playable.map { (t, url) -> mediaItem(t, url) }, start, ProgressRules.resumePosition(progress.get(playable[start].first.id)))
        c.prepare()
        c.play()
    }

    /** 點到正在播的那集：切換播放／暫停；點到別集：從那集開始播整個清單 */
    fun playOrToggle(tracks: List<Track>, index: Int) {
        val id = tracks.getOrNull(index)?.id
        if (id != null && id == _state.value.currentId) togglePlay() else playList(tracks, index)
    }

    fun togglePlay() = withController { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_ENDED) it.seekTo(0); it.play() } }
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
    fun playAll(tracks: List<Track>, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        val start = if (shuffle) tracks.indices.random() else 0
        withController { it.shuffleModeEnabled = shuffle }
        playList(tracks, start)
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

    private fun mediaItem(t: Track, url: String): MediaItem {
        val style = SourceStyle.of(t.channel)
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(if (t.kind == TrackKind.PODCAST) "新知雷達 · ${style.label}" else style.label)
                    .setAlbumTitle(if (t.kind == TrackKind.PODCAST) "Podcast" else "YouTube")
                    .setDurationMs(t.durationSec?.let { it * 1000L })
                    .setIsPlayable(true)
                    .setExtras(Bundle().apply {
                        putString(PlaybackService.EXTRA_KIND, t.kind.key)
                        putString(EXTRA_CHANNEL, t.channel)
                    })
                    .build(),
            )
            .build()
    }

    companion object {
        private const val EXTRA_CHANNEL = "sf.channel"

        fun playbackErrorText(e: PlaybackException): String = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "網路斷了，連上後按播放繼續"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "找不到這個音檔，可能已經被搬走或刪除"
            else -> "無法播放這個音檔（${e.errorCodeName}）"
        }
    }
}
