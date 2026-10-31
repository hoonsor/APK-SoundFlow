package tw.hoonsor.soundflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant

/** 匯入的資料夾 */
data class LocalFolder(val uri: String, val name: String)

/** 目前這一層底下的一個子資料夾；count 含更深層的音檔 */
data class LocalDir(val name: String, val path: String, val count: Int)

/**
 * 掃描到的一個本機音檔。tagged＝標題與歌手都來自音檔標籤，不是從檔名猜的（批次找封面只處理這種）。
 * dir＝相對於匯入資料夾的路徑（第一季/外傳），空字串＝匯入資料夾本身。
 */
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
    val dir: String = "",
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
        createdAt = Instant.ofEpochMilli(e.modifiedMs), localUri = e.uri, localFolder = e.folderUri, localDir = e.dir.ifEmpty { null },
    )

    /** 合併掃描結果：同一首以 found 為準；已經移除的資料夾不留（掃描途中移除也一樣） */
    fun merge(found: List<LocalEntry>, rest: Collection<LocalEntry>, folders: Set<String>): List<LocalEntry> =
        (found + rest).distinctBy { it.id }.filter { it.folderUri in folders }

    fun childDir(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"
    fun parentDir(dir: String) = dir.substringBeforeLast('/', "")
    fun dirName(dir: String) = dir.substringAfterLast('/')

    /** 只有這一層的音檔，不含子資料夾 */
    fun inDir(tracks: List<Track>, folder: String, dir: String): List<Track> =
        tracks.filter { it.localFolder == folder && it.localDir.orEmpty() == dir }

    /** 這一層加上底下所有子資料夾的音檔 */
    fun underDir(tracks: List<Track>, folder: String, dir: String): List<Track> = tracks.filter {
        val d = it.localDir.orEmpty()
        it.localFolder == folder && (dir.isEmpty() || d == dir || d.startsWith("$dir/"))
    }

    /** 這一層的子資料夾：沒有音檔的資料夾不會出現 */
    fun subDirs(tracks: List<Track>, folder: String, dir: String): List<LocalDir> {
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        return tracks.filter { it.localFolder == folder }.mapNotNull { t -> t.localDir.orEmpty().takeIf { it.startsWith(prefix) && it.length > prefix.length } }
            .groupingBy { it.removePrefix(prefix).substringBefore('/') }.eachCount()
            .map { (name, count) -> LocalDir(name, prefix + name, count) }.sortedBy { it.name.lowercase() }
    }

    fun toJson(folders: List<LocalFolder>, entries: List<LocalEntry>): String = JSONObject()
        .put("folders", JSONArray().apply { folders.forEach { put(JSONObject().put("uri", it.uri).put("name", it.name)) } })
        .put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().put("id", e.id).put("uri", e.uri).put("folder", e.folderUri).put("title", e.title)
                    .put("artist", e.artist ?: JSONObject.NULL).put("album", e.album ?: JSONObject.NULL)
                    .put("duration", e.durationSec ?: JSONObject.NULL).put("modified", e.modifiedMs).put("size", e.size).put("tagged", e.tagged).put("dir", e.dir))
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
                if (j.isNull("duration")) null else j.getInt("duration"), j.optLong("modified"), j.optLong("size"), j.optBoolean("tagged"), j.optString("dir"),
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

    fun parseItunesOrEmpty(raw: String) = runCatching { parseItunes(raw) }.getOrDefault(emptyList())
    fun parseDeezerOrEmpty(raw: String) = runCatching { parseDeezer(raw) }.getOrDefault(emptyList())

    fun parseDeezer(raw: String): List<CoverResult> {
        val arr = JSONObject(raw).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val album = o.optJSONObject("album") ?: return@mapNotNull null
            val art = album.optString("cover_big").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CoverResult(o.optString("title"), o.optJSONObject("artist")?.optString("name").orEmpty(), album.optString("title"), art, "Deezer")
        }
    }

    /** MusicBrainz 只有音樂資料，圖在 Cover Art Archive；同一張專輯的不同版本只留一筆 */
    fun parseMusicBrainz(raw: String): List<CoverResult> {
        val arr = JSONObject(raw).optJSONArray("releases") ?: return emptyList()
        val seen = mutableSetOf<String>()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val id = o.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val group = o.optJSONObject("release-group")?.optString("id").orEmpty().ifEmpty { id }
            if (!seen.add(group)) return@mapNotNull null
            val artist = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
            CoverResult(o.optString("title"), artist, o.optString("title"), "https://coverartarchive.org/release/$id/front-500", "MusicBrainz")
        }
    }

    fun googleImagesUrl(query: String) = "https://www.google.com/search?udm=2&q=" + URLEncoder.encode(query, "UTF-8")

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
