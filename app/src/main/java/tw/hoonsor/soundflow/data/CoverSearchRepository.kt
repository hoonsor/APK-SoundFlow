package tw.hoonsor.soundflow.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 按了「到 Google 找圖」：等瀏覽器分享圖片回來時，要設給這一首 */
data class CoverPending(val id: String, val title: String)

data class CoverBatch(
    val total: Int,
    val done: Int = 0,
    val found: Int = 0,
    /** 查到東西但歌名或歌手對不上：寧可不加 */
    val unsure: Int = 0,
    /** 音檔標籤沒有歌名或歌手：沒有可靠的查詢依據，也不把檔名送出去 */
    val noInfo: Int = 0,
    val failed: Boolean = false,
    val running: Boolean = true,
    val foundIds: List<String> = emptyList(),
)

/**
 * 幫本機音檔找封面（F56、F57）。只把查詢文字送去 iTunes Search API、Deezer 與 MusicBrainz，音檔本身不會離開手機。
 * 批次只處理標籤完整的音檔：會議錄音這類沒有歌手標籤的檔案不會被送出去查。
 */
class CoverSearchRepository(
    context: Context,
    private val covers: CoverStore,
    private val local: LocalRepository,
    private val scope: CoroutineScope,
) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val _batch = MutableStateFlow<CoverBatch?>(null)
    val batch: StateFlow<CoverBatch?> = _batch
    private var job: Job? = null
    // 存在手機：逛瀏覽器時 App 可能被系統關掉
    private val prefs = context.getSharedPreferences("cover_pending", Context.MODE_PRIVATE)

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** 批次用，只查一次 Apple：同一首查多區會超過每分鐘 20 次的限制。回 null＝兩個來源都連不上；空清單＝連得上但沒找到 */
    suspend fun search(query: String): List<CoverResult>? = withContext(Dispatchers.IO) {
        val apple = get("https://itunes.apple.com/search?term=${enc(query)}&media=music&entity=song&limit=$LIMIT&country=TW")
        val deezer = get("https://api.deezer.com/search?q=${enc(query)}&limit=$LIMIT")
        if (apple == null && deezer == null) return@withContext null
        val a = apple?.let { runCatching { CoverLogic.parseItunes(it) }.getOrNull() }.orEmpty()
        val d = deezer?.let { runCatching { CoverLogic.parseDeezer(it) }.getOrNull() }.orEmpty()
        (a + d).distinctBy { it.imageUrl }
    }

    /** 單首手動找：Apple 多查日本與美國區，日文歌的候選圖會多很多。回 null＝全部連不上 */
    suspend fun searchWide(query: String): List<CoverResult>? = withContext(Dispatchers.IO) {
        val apple = COUNTRIES.map { country ->
            async { get("https://itunes.apple.com/search?term=${enc(query)}&media=music&entity=song&limit=$LIMIT&country=$country")?.let { CoverLogic.parseItunesOrEmpty(it) } }
        }
        val deezer = async { get("https://api.deezer.com/search?q=${enc(query)}&limit=$LIMIT")?.let { CoverLogic.parseDeezerOrEmpty(it) } }
        val all = (apple + deezer).awaitAll()
        if (all.all { it == null }) null else all.filterNotNull().flatten().distinctBy { it.imageUrl }.take(MAX_RESULTS)
    }

    /** MusicBrainz 回得比較慢，而且不是每張專輯都有圖：另外查，查到再補在後面 */
    suspend fun searchMusicBrainz(query: String): List<CoverResult> = withContext(Dispatchers.IO) {
        val term = query.replace(Regex("[+\\-!(){}\\[\\]^\"~*?:\\\\/]"), " ")
        val raw = get("https://musicbrainz.org/ws/2/release/?query=${enc(term)}&fmt=json&limit=$LIMIT") ?: return@withContext emptyList()
        val list = runCatching { CoverLogic.parseMusicBrainz(raw) }.getOrDefault(emptyList())
        list.take(MB_CHECKS).map { r -> async { r.takeIf { hasImage(it.imageUrl) } } }.awaitAll().filterNotNull()
    }

    private fun hasImage(url: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(url).head().header("User-Agent", USER_AGENT).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    fun setPending(t: Track) = prefs.edit().putString("id", t.id).putString("title", t.title).apply()
    fun clearPending() = prefs.edit().clear().apply()
    fun pending(): CoverPending? = prefs.getString("id", null)?.let { CoverPending(it, prefs.getString("title", null).orEmpty()) }

    /** 單筆搜尋的預設關鍵字（倬按搜尋前看得到、改得了） */
    fun defaultQuery(t: Track): String {
        val artist = local.entry(t.id)?.let { LocalLogic.artistTitle(it).first }
        return listOfNotNull(artist, t.title).joinToString(" ")
    }

    fun startBatch(tracks: List<Track>) {
        if (_batch.value?.running == true) return
        val targets = tracks.filter { covers.fileFor(it.id) == null }
        _batch.value = CoverBatch(total = targets.size, running = targets.isNotEmpty())
        if (targets.isEmpty()) return
        job = scope.launch {
            for ((i, t) in targets.withIndex()) {
                val e = local.entry(t.id)
                if (e?.tagged != true || e.artist == null) {
                    _batch.update { it?.copy(done = i + 1, noInfo = it.noInfo + 1) }
                    continue
                }
                val results = search("${e.artist} ${e.title}")
                val hit = results?.let { CoverLogic.best(e.title, e.artist, it) }
                val saved = hit != null && covers.saveFound(t.id, hit.imageUrl)
                _batch.update {
                    it?.copy(
                        done = i + 1, found = it.found + if (saved) 1 else 0, unsure = it.unsure + if (saved) 0 else 1,
                        failed = it.failed || results == null, foundIds = if (saved) it.foundIds + t.id else it.foundIds,
                    )
                }
                delay(REQUEST_GAP_MS)
            }
            _batch.update { it?.copy(running = false) }
        }
    }

    fun cancelBatch() {
        job?.cancel()
        _batch.update { it?.copy(running = false) }
    }

    /** 復原剛才這批：拿掉找到的封面，回到原狀 */
    fun undoBatch() {
        _batch.value?.foundIds?.let { covers.removeFound(it) }
        _batch.value = null
    }

    fun clearBatch() { _batch.value = null }

    companion object {
        private const val LIMIT = 12
        private const val MAX_RESULTS = 36
        private const val MB_CHECKS = 8
        private const val USER_AGENT = "SoundFlow/1 (https://github.com/hoonsor/APK-SoundFlow)"
        private val COUNTRIES = listOf("TW", "JP", "US")
        /** iTunes Search API 限制約每分鐘 20 次 */
        private const val REQUEST_GAP_MS = 3_200L
    }
}
