package tw.hoonsor.soundflow.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
) {
    private val file = File(context.filesDir, "local_library.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LocalState())
    val state: StateFlow<LocalState> = _state
    private var loaded = false

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
        rescan()
        return "已匯入「$name」"
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

    /** 重新掃描所有資料夾：新增、刪除、改過的檔案都會反映 */
    suspend fun rescan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, scanned = 0, error = null) }
        val old = _state.value.entries.associateBy { it.id }
        val found = mutableListOf<LocalEntry>()
        var failed: String? = null
        withContext(Dispatchers.IO) {
            _state.value.folders.forEach { folder ->
                runCatching { walk(folder, old, found) }.onFailure { failed = "「${folder.name}」讀不到了，請移除後重新匯入" }
            }
        }
        // 讀不到的資料夾保留舊資料，不要因為一時失敗就把清單清空
        val readable = found.map { it.folderUri }.toSet()
        val kept = if (failed != null) old.values.filter { it.folderUri !in readable } else emptyList()
        val all = (found + kept).distinctBy { it.id }
        val removed = old.keys - all.map { it.id }.toSet()
        if (removed.isNotEmpty()) withContext(Dispatchers.IO) { covers.remove(removed) }
        _state.update { it.copy(entries = all, scanning = false, error = failed) }
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
            }
        }
    }

    private suspend fun readTags(id: String, uri: String, folderUri: String, name: String, modified: Long, size: Long): LocalEntry {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, Uri.parse(uri))
            val title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotEmpty() }
            val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotEmpty() }
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            r.embeddedPicture?.let { covers.saveEmbedded(id, it) }
            LocalEntry(id, uri, folderUri, title ?: LocalLogic.titleFromFileName(name), artist, album, ms?.let { (it / 1000).toInt() }, modified, size, title != null && artist != null)
        } catch (_: Exception) {
            // 讀不到標籤的檔案（格式特殊、損毀）還是列出來，能不能播交給播放器判斷
            LocalEntry(id, uri, folderUri, LocalLogic.titleFromFileName(name), null, null, null, modified, size, false)
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
}
