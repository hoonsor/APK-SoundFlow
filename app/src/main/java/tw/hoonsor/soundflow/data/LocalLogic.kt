package tw.hoonsor.soundflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant

/** 匯入的資料夾 */
data class LocalFolder(val uri: String, val name: String)

/** 掃描到的一個本機音檔。tagged＝標題與歌手都來自音檔標籤，不是從檔名猜的（批次找封面只處理這種） */
data class LocalEntry(
    val id: String,
    val uri: String,
    val folderUri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSec: Int?,
    val modifiedMs: Long,
    val size: Long,
    val tagged: Boolean,
)

/** 本機音檔的純邏輯（JVM 測試涵蓋） */
object LocalLogic {
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "mka")

    fun isAudio(name: String, mime: String?): Boolean =
        mime?.startsWith("audio/") == true || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT

    /** 同一個檔案永遠得到同一個 id，進度與封面才接得上 */
    fun trackId(uri: String): String {
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return "local:" + hash.take(24)
    }

    /** 01 - 晴天.mp3 → 晴天 */
    fun titleFromFileName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        val noNumber = base.replace(Regex("^\\d{1,3}[\\s._-]+"), "").trim()
        return noNumber.ifEmpty { base.trim() }.ifEmpty { name }
    }

    fun toTrack(e: LocalEntry, folderName: String): Track = Track(
        id = e.id, kind = TrackKind.LOCAL, title = e.title, originalTitle = e.title, channel = e.artist ?: folderName,
        sourceUrl = null, durationSec = e.durationSec, tier = "local", storageBucket = null, storagePath = null,
        createdAt = Instant.ofEpochMilli(e.modifiedMs), localUri = e.uri, localFolder = e.folderUri,
    )

    /** 合併掃描結果：同一首以 found 為準；已經移除的資料夾不留（掃描途中移除也一樣） */
    fun merge(found: List<LocalEntry>, rest: Collection<LocalEntry>, folders: Set<String>): List<LocalEntry> =
        (found + rest).distinctBy { it.id }.filter { it.folderUri in folders }

    fun toJson(folders: List<LocalFolder>, entries: List<LocalEntry>): String = JSONObject()
        .put("folders", JSONArray().apply { folders.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) } })
        .put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().put("id", e.id).put("uri", e.uri).put("folder", e.folderUri).put("title", e.title)
                    .put("artist", e.artist ?: JSONObject.NULL).put("album", e.album ?: JSONObject.NULL)
                    .put("duration", e.durationSec ?: JSONObject.NULL).put("modified", e.modifiedMs).put("size", e.size).put("tagged", e.tagged))
            }
        }).toString()

    fun fromJson(raw: String): Pair<List<LocalFolder>, List<LocalEntry>> = runCatching {
        val o = JSONObject(raw)
        val f = o.getJSONArray("folders")
        val e = o.getJSONArray("entries")
        val folders = (0 until f.length()).map { i -> f.getJSONObject(i).let { LocalFolder(it.getString("uri"), it.getString("name")) } }
        val entries = (0 until e.length()).map { i ->
            val j = e.getJSONObject(i)
            LocalEntry(
                j.getString("id"), j.getString("uri"), j.getString("folder"), j.getString("title"),
                j.optString("artist").takeIf { !j.isNull("artist") }, j.optString("album").takeIf { !j.isNull("album") },
                if (j.isNull("duration")) null else j.getInt("duration"), j.optLong("modified"), j.optLong("size"), j.optBoolean("tagged"),
            )
        }
        folders to entries
    }.getOrDefault(emptyList<LocalFolder>() to emptyList())
}

/** 封面搜尋的一筆結果 */
data class CoverResult(val title: String, val artist: String, val album: String, val imageUrl: String, val source: String)

/** 封面搜尋的純邏輯：解析回應、判斷批次時有沒有把握（JVM 測試涵蓋） */
object CoverLogic {
    /** iTunes Search API 回傳 100×100 的網址，路徑換一下就有 600×600 */
    fun parseItunes(raw: String): List<CoverResult> {
        val arr = JSONObject(raw).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val art = o.optString("artworkUrl100").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CoverResult(o.optString("trackName"), o.optString("artistName"), o.optString("collectionName"), art.replace("100x100bb", "600x600bb"), "Apple")
        }
    }

    fun parseDeezer(raw: String): List<CoverResult> {
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val album = o.optJSONObject("album") ?: return@mapNotNull null
            val art = album.optString("cover_big").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CoverResult(o.optString("title"), o.optJSONObject("artist")?.optString("name").orEmpty(), album.optString("title"), art, "Deezer")
        }
    }

    /** 比對用：全形轉半形、去大小寫、去括號內的版本註記與標點 */
    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        .replace(Regex("[(\\[（【].*?[)\\]）】]"), "")
        .filter { it.isLetterOrDigit() }

    private fun same(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || (minOf(x.length, y.length) >= 2 && (x in y || y in x))
    }

    /** 批次用：歌名與歌手都對得上才算有把握；完全相同的排前面，對不上回 null（寧可不加，也不要加錯） */
    fun best(title: String, artist: String, results: List<CoverResult>): CoverResult? {
        val hits = results.filter { same(title, it.title) && same(artist, it.artist) }
        // normalize 會拿掉括號，「晴天 (Live)」也算相同；原字串完全一樣的錄音室版要排在前面
        return hits.firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
            ?: hits.firstOrNull { normalize(it.title) == normalize(title) } ?: hits.firstOrNull()
    }
}
