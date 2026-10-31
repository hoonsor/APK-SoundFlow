package tw.hoonsor.soundflow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 封面檔的唯一讀寫入口。全部存在 App 自己的資料夾，不改動音檔本身（F55、F56）：
 * - <id>.jpg：YouTube 封面、本機音檔內嵌的封面
 * - <id>.found.jpg：幫本機音檔找到的封面（優先顯示；刪掉就回到原狀，所以隨時可復原）
 */
class CoverStore(context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val _version = MutableStateFlow(0L)

    /** 封面有變動時加一，畫面依此重新讀取 */
    val version: StateFlow<Long> = _version

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun base(id: String) = File(dir, "${safe(id)}.jpg")
    private fun found(id: String) = File(dir, "${safe(id)}.found.jpg")

    fun fileFor(id: String): File? = found(id).takeIf { it.exists() } ?: base(id).takeIf { it.exists() }
    fun hasFound(id: String) = found(id).exists()

    /** 通知列、鎖定畫面的封面：有存檔用存檔，否則用網址 */
    fun artworkUri(t: Track): Uri? = fileFor(t.id)?.let { Uri.fromFile(it) } ?: t.thumbnailUrl?.let { Uri.parse(it) }

    /** 下載 YouTube 封面；沒有 maxresdefault 的影片改抓 hqdefault */
    suspend fun ensureThumb(id: String, url: String) {
        if (base(id).exists()) return
        val ok = save(base(id), fetch(url)) || SfUrls.thumbnailFallback(url)?.let { save(base(id), fetch(it)) } == true
        if (ok) bump()
    }

    suspend fun prefetch(tracks: List<Track>) = tracks.forEach { t -> t.thumbnailUrl?.let { ensureThumb(t.id, it) } }

    suspend fun saveEmbedded(id: String, bytes: ByteArray) { if (save(base(id), bytes)) bump() }

    /** 套用找到的封面；下載或解碼失敗回 false，原本的狀態不變 */
    suspend fun saveFound(id: String, url: String): Boolean {
        val ok = save(found(id), fetch(url))
        if (ok) bump()
        return ok
    }

    fun removeFound(ids: Collection<String>) {
        ids.forEach { found(it).delete() }
        bump()
    }

    /** 取消匯入資料夾時清掉這些音檔的封面 */
    fun remove(ids: Collection<String>) {
        ids.forEach { found(it).delete(); base(it).delete() }
        bump()
    }

    /** 搜尋結果的預覽圖（不存檔） */
    suspend fun previewBytes(url: String): ByteArray? = fetch(url)

    private fun bump() = _version.update { it + 1 }

    private suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body ?: return@use null
                if (body.contentLength() > MAX_DOWNLOAD) null else body.bytes()
            }
        }.getOrNull()
    }

    /** 統一縮成寬度最多 640 的 JPEG；先寫暫存檔再改名，不會留下壞檔 */
    private suspend fun save(target: File, bytes: ByteArray?): Boolean = withContext(Dispatchers.IO) {
        if (bytes == null) return@withContext false
        runCatching {
            val bmp = CoverBitmaps.decode(bytes, MAX_WIDTH) ?: return@runCatching false
            val tmp = File(target.path + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            tmp.renameTo(target)
        }.getOrDefault(false)
    }

    companion object {
        private const val MAX_WIDTH = 640
        private const val MAX_DOWNLOAD = 8_000_000L
    }
}

/** 解碼時就縮小，避免大圖吃光記憶體 */
object CoverBitmaps {
    private fun sample(w: Int, max: Int): Int {
        var s = 1
        while (w / (s * 2) >= max) s *= 2
        return s
    }

    private fun scaled(b: Bitmap, max: Int): Bitmap =
        if (b.width <= max) b else Bitmap.createScaledBitmap(b, max, (b.height.toFloat() * max / b.width).toInt().coerceAtLeast(1), true)

    fun decode(bytes: ByteArray, maxWidth: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, maxWidth); inPreferredConfig = config }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.let { scaled(it, maxWidth) }
    }

    fun decodeFile(path: String, maxWidth: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? = runCatching {
        decode(File(path).readBytes(), maxWidth, config)
    }.getOrNull()
}
