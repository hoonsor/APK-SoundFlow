package tw.hoonsor.soundflow.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class LocalState(
    val folders: List<LocalFolder> = emptyList(),
    val entries: List<LocalEntry> = emptyList(),
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val error: String? = null,
)

/**
 * 本機音檔（F54）：倬一次匯入一個資料夾，只掃那些資料夾。
 * 用系統的資料夾選擇器取得長期授權，不要求「讀取全部檔案」權限；只讀不寫，檔案也不上傳。
 * 索引存在手機（local_library.json），下次打開不必重掃；標籤只有檔案變動時才重讀。
 */
class LocalRepository(
    private val context: Context,
    private val tracks: TrackRepository,
    private val covers: CoverStore,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "local_library.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LocalState())
    val state: StateFlow<LocalState> = _state
    private var loaded = false
    private var rerun = false

    suspend fun load() = mutex.withLock {
        if (loaded) return
        val raw = withContext(Dispatchers.IO) { runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull() }
        val (folders, entries) = raw?.let { LocalLogic.fromJson(it) } ?: (emptyList<LocalFolder>() to emptyList())
        _state.update { it.copy(folders = folders, entries = entries) }
        publish()
        loaded = true
    }

    fun entry(id: String): LocalEntry? = _state.value.entries.firstOrNull { it.id == id }

    /** 匯入一個資料夾；已經匯入過的不重複加 */
    suspend fun addFolder(uri: Uri): String {
        load()
        val key = uri.toString()
        if (_state.value.folders.any { it.uri == key }) return "這個資料夾已經匯入過了"
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val name = docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "手機" }
        _state.update { it.copy(folders = it.folders + LocalFolder(key, name)) }
        persist()   // 先存起來：掃描途中關掉 App，資料夾也不會不見
        if (_state.value.scanning) rerun = true else rescan()
        return "已匯入「$name」，正在掃描"
    }

    /** 只取消匯入：手機裡的檔案不動 */
    suspend fun removeFolder(uri: String) {
        val gone = _state.value.entries.filter { it.folderUri == uri }.map { it.id }
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        _state.update { s -> s.copy(folders = s.folders.filterNot { it.uri == uri }, entries = s.entries.filterNot { it.folderUri == uri }) }
        withContext(Dispatchers.IO) { covers.remove(gone) }
        persist()
        publish()
    }

    /**
     * 重新掃描所有資料夾：新增、刪除、改過的檔案都會反映。
     * 掃描跑在 App 層級，不跟著畫面走：匯入第一個資料夾後畫面就換版，綁在畫面上的工作會被取消。
     */
    fun rescan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, scanned = 0, error = null) }
        scope.launch {
            try {
                do { rerun = false; scanOnce() } while (rerun)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(error = "掃描失敗，請往下拉再試一次") }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    private suspend fun scanOnce() {
        val old = _state.value.entries.associateBy { it.id }
        val found = mutableListOf<LocalEntry>()
        val unreadable = mutableListOf<LocalFolder>()
        withContext(Dispatchers.IO) {
            _state.value.folders.forEach { folder ->
                runCatching { walk(folder, old, found) }.onFailure { if (it is CancellationException) throw it else unreadable += folder }
            }
        }
        // 讀不到的資料夾保留舊資料，不要因為一時失敗就把清單清空
        val lost = unreadable.map { it.uri }.toSet()
        val all = LocalLogic.merge(found, old.values.filter { it.folderUri in lost }, _state.value.folders.map { it.uri }.toSet())
        val removed = old.keys - all.map { it.id }.toSet()
        if (removed.isNotEmpty()) withContext(Dispatchers.IO) { covers.remove(removed) }
        _state.update { it.copy(entries = all, error = unreadable.firstOrNull()?.let { f -> "「${f.name}」讀不到了，請移除後重新匯入" }) }
        persist()
        publish()
    }

    /** 掃到一半就先把已掃到的音檔放進清單，資料夾很大時不必等全部掃完 */
    private suspend fun flush(found: List<LocalEntry>, old: Map<String, LocalEntry>) {
        _state.update { s -> s.copy(entries = LocalLogic.merge(found, old.values, s.folders.map { it.uri }.toSet())) }
        persist()
        publish()
    }

    private suspend fun walk(folder: LocalFolder, old: Map<String, LocalEntry>, out: MutableList<LocalEntry>) {
        val tree = Uri.parse(folder.uri)
        val pending = ArrayDeque(listOf(DocumentsContract.getTreeDocumentId(tree)))
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE,
        )
        var flushedAt = System.currentTimeMillis()
        while (pending.isNotEmpty()) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeFirst())
            val rows = mutableListOf<Array<Any?>>()
            context.contentResolver.query(children, cols, null, null, null)?.use { c ->
                while (c.moveToNext()) rows += arrayOf(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4))
            } ?: error("query failed")
            rows.forEach { r ->
                val docId = r[0] as String
                val name = r[1] as? String ?: return@forEach
                val mime = r[2] as? String
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { pending += docId; return@forEach }
                if (!LocalLogic.isAudio(name, mime)) return@forEach
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId).toString()
                val id = LocalLogic.trackId(uri)
                val modified = r[3] as Long
                val size = r[4] as Long
                val same = old[id]?.takeIf { it.modifiedMs == modified && it.size == size }
                out += same ?: readTags(id, uri, folder.uri, name, modified, size)
                _state.update { it.copy(scanned = out.size) }
                if (same == null && System.currentTimeMillis() - flushedAt > FLUSH_MS) {
                    flush(out, old)
                    flushedAt = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * 讀標籤的原生程式遇到特殊檔案或雲端檔可能卡住，而且無法中斷：限時等待，超過就先只列檔名，繼續掃下一首。
     * 卡住的那次讀取留在背景自己結束；要到檔案變動才會再讀，不會每次掃描都卡一次。
     */
    private suspend fun readTags(id: String, uri: String, folderUri: String, name: String, modified: Long, size: Long): LocalEntry {
        val job = scope.async(Dispatchers.IO) { readTagsNow(id, uri, folderUri, name, modified, size) }
        return withTimeoutOrNull(TAG_TIMEOUT_MS) { job.await() } ?: untagged(id, uri, folderUri, name, modified, size)
    }

    private fun untagged(id: String, uri: String, folderUri: String, name: String, modified: Long, size: Long) =
        LocalEntry(id, uri, folderUri, LocalLogic.titleFromFileName(name), null, null, null, modified, size, false)

    private suspend fun readTagsNow(id: String, uri: String, folderUri: String, name: String, modified: Long, size: Long): LocalEntry {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uri))
            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() }
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() }
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            r.embeddedPicture?.let { covers.saveEmbedded(id, it) }
            LocalEntry(id, uri, folderUri, title ?: LocalLogic.titleFromFileName(name), artist, album, ms?.let { (it / 1000).toInt() }, modified, size, title != null && artist != null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // 讀不到標籤的檔案（格式特殊、損毀、封面圖太大）還是列出來，能不能播交給播放器判斷
            untagged(id, uri, folderUri, name, modified, size)
        } finally {
            runCatching { r.release() }
        }
    }

    private fun publish() {
        val s = _state.value
        val names = s.folders.associate { it.uri to it.name }
        tracks.setLocal(s.entries.map { LocalLogic.toTrack(it, names[it.folderUri].orEmpty()) }.sortedByDescending { it.createdAt })
    }

    private suspend fun persist() {
        val s = _state.value
        val json = LocalLogic.toJson(s.folders, s.entries)
        withContext(Dispatchers.IO) { runCatching { file.writeText(json) } }
    }

    companion object {
        private const val TAG_TIMEOUT_MS = 8_000L
        private const val FLUSH_MS = 3_000L
    }
}
