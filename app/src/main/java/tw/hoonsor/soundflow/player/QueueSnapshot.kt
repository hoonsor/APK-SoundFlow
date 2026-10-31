package tw.hoonsor.soundflow.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.soundflow.BuildConfig
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.SfJson
import tw.hoonsor.soundflow.data.SfUrls
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import java.io.File

/** 把音軌轉成 Media3 的 MediaItem（App 端與播放服務共用，確保兩邊一致） */
object MediaItems {
    const val EXTRA_CHANNEL = "sf.channel"
    const val EXTRA_THUMB = "sf.thumb"

    fun of(t: Track): MediaItem? {
        val url = SfUrls.audio(BuildConfig.SUPABASE_URL, t) ?: return null
        val style = SourceStyle.of(t.channel)
        val artist = when (t.kind) { TrackKind.PODCAST -> "新知雷達 · ${style.label}"; else -> style.label }
        val album = when (t.kind) { TrackKind.PODCAST -> "Podcast"; TrackKind.YOUTUBE -> "YouTube"; TrackKind.LOCAL -> "本機音檔" }
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(Graph.covers.artworkUri(t))
                    .setDurationMs(t.durationSec?.let { it * 1000L })
                    .setIsPlayable(true)
                    .setExtras(Bundle().apply {
                        putString(PlaybackService.EXTRA_KIND, t.kind.key)
                        putString(EXTRA_CHANNEL, t.channel)
                        putString(EXTRA_THUMB, t.thumbnailUrl)
                    })
                    .build(),
            )
            .build()
    }
}

/**
 * 最後播放的清單快照（F26）：App 沒開、手機重開機後，按小工具的播放能從這裡接續。
 * 清單在 App 端開始播放時寫入；目前第幾集與位置由播放服務定期更新。
 */
data class QueueSnapshot(val source: String, val tracks: List<Track>, val index: Int, val positionMs: Long) {
    fun toJson(): String = JSONObject().put("source", source).put("index", index).put("position", positionMs)
        .put("tracks", JSONArray(SfJson.tracksToJson(tracks))).toString()

    companion object {
        fun fromJson(raw: String): QueueSnapshot? = runCatching {
            val o = JSONObject(raw)
            val tracks = SfJson.parseTracks(o.getJSONArray("tracks").toString())
            if (tracks.isEmpty()) return null
            QueueSnapshot(o.optString("source"), tracks, o.optInt("index").coerceIn(0, tracks.lastIndex), o.optLong("position").coerceAtLeast(0))
        }.getOrNull()

        private fun file(context: Context) = File(context.filesDir, "last_queue.json")

        fun load(context: Context): QueueSnapshot? = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()?.let { fromJson(it) }

        fun save(context: Context, s: QueueSnapshot) { runCatching { file(context).writeText(s.toJson()) } }

        /** 服務只更新目前第幾集與位置（清單本身不變） */
        fun updatePosition(context: Context, mediaId: String?, positionMs: Long) {
            val s = load(context) ?: return
            val idx = s.tracks.indexOfFirst { it.id == mediaId }.takeIf { it >= 0 } ?: return
            if (idx != s.index || positionMs != s.positionMs) save(context, s.copy(index = idx, positionMs = positionMs))
        }
    }
}

/** 小工具要顯示的內容（播放服務寫、小工具讀；存成檔案，App 沒開也讀得到） */
data class WidgetState(
    val title: String = "",
    val channel: String? = null,
    val kind: String? = null,
    val source: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val repeat: Repeat = Repeat.OFF,
    val upNext: List<String> = emptyList(),
    /** upNext 每一集在播放佇列裡的位置（大型小工具點一下直接跳過去） */
    val upNextIndex: List<Int> = emptyList(),
    val updatedAt: Long = 0,
    val shuffle: Boolean = false,
    val index: Int = 0,
    val sleepEndsAt: Long = 0,
    /** 封面檔案路徑與修改時間（換了封面才重新解碼） */
    val coverPath: String? = null,
    val coverStamp: Long = 0,
) {
    val mode: PlayMode get() = PlayMode.of(repeat, shuffle)
    val hasTrack: Boolean get() = title.isNotEmpty()
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun toJson(): String = JSONObject().put("title", title).put("channel", channel ?: JSONObject.NULL).put("kind", kind ?: JSONObject.NULL)
        .put("source", source).put("playing", playing).put("position", positionMs).put("duration", durationMs)
        .put("speed", speed.toDouble()).put("repeat", repeat.name).put("upNext", JSONArray(upNext)).put("upNextIndex", JSONArray(upNextIndex)).put("updatedAt", updatedAt)
        .put("shuffle", shuffle).put("index", index).put("sleepEndsAt", sleepEndsAt).put("coverPath", coverPath ?: JSONObject.NULL).put("coverStamp", coverStamp).toString()

    companion object {
        fun fromJson(raw: String): WidgetState = runCatching {
            val o = JSONObject(raw)
            val next = o.optJSONArray("upNext") ?: JSONArray()
            val nextIdx = o.optJSONArray("upNextIndex") ?: JSONArray()
            WidgetState(
                title = o.optString("title"),
                channel = o.optString("channel").takeIf { !o.isNull("channel") && it.isNotEmpty() },
                kind = o.optString("kind").takeIf { !o.isNull("kind") && it.isNotEmpty() },
                source = o.optString("source"),
                playing = o.optBoolean("playing"),
                positionMs = o.optLong("position"),
                durationMs = o.optLong("duration"),
                speed = o.optDouble("speed", 1.0).toFloat(),
                repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.OFF),
                upNext = (0 until next.length()).map { next.getString(it) },
                upNextIndex = (0 until nextIdx.length()).map { nextIdx.getInt(it) },
                updatedAt = o.optLong("updatedAt"),
                shuffle = o.optBoolean("shuffle"),
                index = o.optInt("index"),
                sleepEndsAt = o.optLong("sleepEndsAt"),
                coverPath = o.optString("coverPath").takeIf { !o.isNull("coverPath") && it.isNotEmpty() },
                coverStamp = o.optLong("coverStamp"),
            )
        }.getOrDefault(WidgetState())

        private fun file(context: Context) = File(context.filesDir, "widget_state.json")
        fun load(context: Context): WidgetState = runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull()?.let { fromJson(it) } ?: WidgetState()
        fun save(context: Context, s: WidgetState) { runCatching { file(context).writeText(s.toJson()) } }
    }
}

/** 小工具版面（Nova 7 列 × 5 欄，每格約 76×104 dp）：2×1、5×1、2×2、4×2 */
enum class WidgetLayout { MINI, BAR, SQUARE, FULL;
    companion object {
        fun forSizeDp(w: Float, h: Float): WidgetLayout = when {
            h < 120f -> if (w < 280f) MINI else BAR
            else -> if (w < 220f) SQUARE else FULL
        }
    }
}
