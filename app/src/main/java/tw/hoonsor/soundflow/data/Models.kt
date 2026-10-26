package tw.hoonsor.soundflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TrackKind(val key: String) { PODCAST("podcast"), YOUTUBE("youtube") }

/** sf_track 的一列（只取 App 需要的欄位） */
data class Track(
    val id: String,
    val kind: TrackKind,
    val title: String,
    val originalTitle: String,
    val channel: String?,
    val sourceUrl: String?,
    val durationSec: Int?,
    val tier: String,
    val storageBucket: String?,
    val storagePath: String?,
    val createdAt: Instant,
)

/** 登入後的工作階段；refresh token 會輪替，每次刷新都要存新的 */
data class Session(val accessToken: String, val refreshToken: String, val expiresAtEpochSec: Long, val email: String)

/** 純邏輯：JSON 解析與顯示格式，JVM 測試涵蓋 */
object SfJson {
    const val TRACK_FIELDS = "id,kind,title,original_title,channel,source_url,duration_sec,tier,storage_bucket,storage_path,created_at"

    fun parseTracks(raw: String): List<Track> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i -> parseTrack(arr.getJSONObject(i)) }
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    fun parseTrack(o: JSONObject): Track? {
        val kind = when (o.optString("kind")) {
            "podcast" -> TrackKind.PODCAST
            "youtube" -> TrackKind.YOUTUBE
            else -> return null
        }
        val title = o.str("title") ?: return null
        return Track(
            id = o.str("id") ?: return null,
            kind = kind,
            title = title,
            originalTitle = o.str("original_title") ?: title,
            channel = o.str("channel"),
            sourceUrl = o.str("source_url"),
            durationSec = if (o.isNull("duration_sec") || !o.has("duration_sec")) null else o.optInt("duration_sec"),
            tier = o.str("tier") ?: "supabase",
            storageBucket = o.str("storage_bucket"),
            storagePath = o.str("storage_path"),
            createdAt = runCatching { Instant.parse(normalizeTimestamp(o.optString("created_at"))) }.getOrDefault(Instant.EPOCH),
        )
    }

    /** PostgREST 回傳 2026-10-19T04:05:00.362+00:00；Instant.parse 要的是 Z 或帶偏移的 ISO 格式 */
    fun normalizeTimestamp(s: String): String = java.time.OffsetDateTime.parse(s).toInstant().toString()

    fun tracksToJson(list: List<Track>): String = JSONArray().apply {
        list.forEach { t ->
            put(JSONObject().put("id", t.id).put("kind", t.kind.key).put("title", t.title).put("original_title", t.originalTitle)
                .put("channel", t.channel ?: JSONObject.NULL).put("source_url", t.sourceUrl ?: JSONObject.NULL)
                .put("duration_sec", t.durationSec ?: JSONObject.NULL).put("tier", t.tier)
                .put("storage_bucket", t.storageBucket ?: JSONObject.NULL).put("storage_path", t.storagePath ?: JSONObject.NULL)
                .put("created_at", t.createdAt.toString()))
        }
    }.toString()

    /** GoTrue /verify 與 /token 的回應 */
    fun parseSession(raw: String, nowEpochSec: Long): Session {
        val o = JSONObject(raw)
        val expiresAt = if (o.has("expires_at")) o.getLong("expires_at") else nowEpochSec + o.optLong("expires_in", 3600)
        return Session(
            accessToken = o.getString("access_token"),
            refreshToken = o.getString("refresh_token"),
            expiresAtEpochSec = expiresAt,
            email = o.optJSONObject("user")?.optString("email").orEmpty(),
        )
    }

    /** rpc/sf_whoami 回傳 {"email": "...", "member": true}（PostgREST 對純量 json 可能再包一層字串） */
    fun parseWhoami(raw: String): Boolean {
        val text = raw.trim().let { if (it.startsWith("\"")) JSONArray("[$it]").getString(0) else it }
        return JSONObject(text).optBoolean("member", false)
    }

    fun normalizeEmail(s: String) = s.trim().lowercase()
    fun isValidEmail(s: String) = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(normalizeEmail(s))
    /** 驗證碼長度是 Supabase 專案層級的設定（AI_News 與記帳網站共用，目前 8 碼），App 不寫死：接受 6～10 位數 */
    const val OTP_MAX = 10
    fun isValidOtp(s: String) = Regex("^\\d{6,$OTP_MAX}$").matches(s.trim())
}

object SfUrls {
    /** 音檔串流網址；tier = drive 的封存檔要等 v0.7（Drive 權杖）才能播，先回 null */
    fun audio(baseUrl: String, t: Track): String? {
        if (t.tier != "supabase") return null
        val bucket = t.storageBucket ?: return null
        val path = t.storagePath ?: return null
        val enc = path.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        // sf bucket 是私人的：播放服務送出請求時會自動帶上登入權杖（PlaybackService 的 ResolvingDataSource）
        val access = if (bucket in PRIVATE_BUCKETS) "authenticated" else "public"
        return "$baseUrl/storage/v1/object/$access/$bucket/$enc"
    }

    val PRIVATE_BUCKETS = setOf("sf")

    fun needsAuth(url: String) = "/storage/v1/object/authenticated/" in url
}

/** 顯示用格式 */
object SfFormat {
    /** 秒 → 9:47、1:02:15 */
    fun duration(sec: Int?): String {
        if (sec == null || sec < 0) return "—"
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** 今天／昨天／10/19（同一年）／2025/10/19 */
    fun relativeDate(at: Instant, today: LocalDate, zone: ZoneId = ZoneId.of("Asia/Taipei")): String {
        val d = at.atZone(zone).toLocalDate()
        return when {
            d == today -> "今天"
            d == today.minusDays(1) -> "昨天"
            d.year == today.year -> d.format(DateTimeFormatter.ofPattern("M/d"))
            else -> d.format(DateTimeFormatter.ofPattern("yyyy/M/d"))
        }
    }

    /** 總長度：27 分、1 小時 05 分 */
    fun total(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600 + 59) / 60
        return if (h > 0) "$h 小時 %02d 分".format(m) else "$m 分"
    }
}

/** 封面色塊：依來源決定色相與縮寫（docs/DESIGN.md 第 2 節 coverHue） */
data class SourceStyle(val label: String, val tag: String, val hue: Float) {
    companion object {
        fun of(channel: String?): SourceStyle = when (channel?.lowercase()) {
            "github" -> SourceStyle("GitHub", "GH", 250f)
            "youtube" -> SourceStyle("YouTube", "YT", 8f)
            // 新知雷達的 source 代號：youtube、github、community（Hacker News／Reddit）、skill
            "community" -> SourceStyle("社群", "社", 18f)
            "skill" -> SourceStyle("Skill", "SK", 160f)
            "arxiv" -> SourceStyle("arXiv", "arX", 205f)
            "reddit" -> SourceStyle("Reddit", "R", 18f)
            "hackernews", "hacker news", "hn" -> SourceStyle("Hacker News", "HN", 48f)
            "blog", "部落格", "rss" -> SourceStyle("部落格", "文", 35f)
            null, "" -> SourceStyle("其他", "·", 215f)
            else -> {
                // YouTube 頻道名等任意字串：用名稱雜湊出穩定的色相，縮寫取第一個字
                val hue = ((channel.hashCode() % 360) + 360) % 360
                SourceStyle(channel, channel.take(1), hue.toFloat())
            }
        }
    }
}
