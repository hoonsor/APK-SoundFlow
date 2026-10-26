package tw.hoonsor.soundflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.time.Instant

/** 從分享的文字裡找出 YouTube 影片 id（純邏輯，JVM 測試涵蓋） */
object YouTubeLink {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val URL = Regex("https?://\\S+")

    data class Link(val videoId: String) {
        val canonicalUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
    }

    /** 分享內容可能是「標題＋網址」或只有網址；只認 YouTube 的影片網址（播放清單、頻道不收） */
    fun parse(shared: String?): Link? {
        if (shared.isNullOrBlank()) return null
        URL.findAll(shared).forEach { m ->
            idFromUrl(m.value.trimEnd(')', '」', '。', ',', '，'))?.let { return Link(it) }
        }
        return null
    }

    private fun idFromUrl(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val segs = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = when (host) {
            "youtu.be" -> segs.firstOrNull()
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when (segs.firstOrNull()) {
                "watch" -> query(uri.rawQuery, "v")
                "shorts", "live", "embed", "v" -> segs.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { ID.matches(it) }
    }

    private fun query(q: String?, key: String): String? =
        q?.split('&')?.map { it.split('=', limit = 2) }?.firstOrNull { it[0] == key }?.getOrNull(1)
}

enum class JobStatus(val key: String, val label: String) {
    QUEUED("queued", "排隊中"), PROCESSING("processing", "轉檔中"), DONE("done", "完成"),
    FAILED("failed", "失敗"), CANCELLED("cancelled", "已取消");

    companion object { fun of(k: String) = entries.firstOrNull { it.key == k } }
}

data class YtJob(
    val id: String,
    val url: String,
    val videoId: String,
    val status: JobStatus,
    val error: String?,
    val title: String?,
    val requestedAt: Instant,
)

data class WorkerBeat(val worker: String, val seenAt: Instant, val ytDlpVersion: String?)

object JobJson {
    const val FIELDS = "id,url,video_id,status,error,title,requested_at"

    fun parseJobs(raw: String): List<YtJob> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val status = JobStatus.of(o.optString("status")) ?: return@mapNotNull null
            YtJob(
                id = o.getString("id"), url = o.optString("url"), videoId = o.optString("video_id"), status = status,
                error = o.optString("error").takeIf { !o.isNull("error") && it.isNotBlank() },
                title = o.optString("title").takeIf { !o.isNull("title") && it.isNotBlank() },
                requestedAt = runCatching { Instant.parse(SfJson.normalizeTimestamp(o.getString("requested_at"))) }.getOrDefault(Instant.EPOCH),
            )
        }
    }

    fun parseBeats(raw: String): List<WorkerBeat> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            WorkerBeat(
                o.getString("worker"),
                runCatching { Instant.parse(SfJson.normalizeTimestamp(o.getString("seen_at"))) }.getOrDefault(Instant.EPOCH),
                o.optString("yt_dlp_version").takeIf { !o.isNull("yt_dlp_version") && it.isNotBlank() },
            )
        }
    }

    fun pendingToJson(urls: List<String>): String = JSONArray(urls).toString()
    fun pendingFromJson(raw: String): List<String> = runCatching {
        val a = JSONArray(raw); (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun enqueueBody(link: YouTubeLink.Link): String = JSONObject().put("url", link.canonicalUrl).put("video_id", link.videoId).toString()
}

/** N100 狀態文字：5 分鐘回報一次，超過 15 分鐘沒消息就當離線 */
object WorkerStatus {
    fun text(beat: WorkerBeat?, now: Instant): Pair<Boolean, String> {
        if (beat == null) return false to "N100 轉檔主機還沒上線過，送出的影片會先排隊"
        val min = java.time.Duration.between(beat.seenAt, now).toMinutes()
        return when {
            min <= 15 -> true to (if (min < 1) "N100 剛剛在線" else "N100 $min 分鐘前在線")
            min < 120 -> false to "N100 已離線 $min 分鐘，送出的影片會先排隊"
            else -> false to "N100 已離線 ${min / 60} 小時，送出的影片會先排隊"
        }
    }
}
