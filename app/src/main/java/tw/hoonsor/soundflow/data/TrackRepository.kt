package tw.hoonsor.soundflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.hoonsor.soundflow.data.supabase.SfException
import tw.hoonsor.soundflow.data.supabase.SupabaseClient
import java.io.File
import java.time.Instant

data class TrackListState(
    val items: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 最後一次成功從雲端取得的時間；null＝目前顯示的是本機快取或尚未載入 */
    val syncedAt: Instant? = null,
)

/**
 * 音軌的唯一讀寫入口。v0.2.0 只有讀：從 sf_track 取清單，並存一份 JSON 快取，離線時也能看到列表。
 * （之後換成 Room，介面不變）
 */
class TrackRepository(
    private val context: Context,
    private val api: SupabaseClient,
    private val auth: AuthRepository,
) {
    private val states = TrackKind.entries.associateWith { MutableStateFlow(TrackListState()) }

    fun state(kind: TrackKind): StateFlow<TrackListState> = states.getValue(kind)

    private fun cacheFile(kind: TrackKind) = File(context.filesDir, "tracks_${kind.key}.json")

    /** 先讀本機快取（很快），畫面不會空白 */
    suspend fun loadCache(kind: TrackKind) {
        val cached = withContext(Dispatchers.IO) {
            runCatching { cacheFile(kind).takeIf { it.exists() }?.readText()?.let { SfJson.parseTracks(it) } }.getOrNull()
        } ?: return
        states.getValue(kind).update { if (it.items.isEmpty()) it.copy(items = cached) else it }
    }

    suspend fun refresh(kind: TrackKind) {
        val flow = states.getValue(kind)
        flow.update { it.copy(loading = true, error = null) }
        try {
            val path = "/rest/v1/sf_track?select=${SfJson.TRACK_FIELDS}&kind=eq.${kind.key}" +
                "&hidden=is.false&deleted_at=is.null&order=created_at.desc&limit=500"
            val list = SfJson.parseTracks(api.get(path, auth.accessToken()))
            withContext(Dispatchers.IO) { cacheFile(kind).writeText(SfJson.tracksToJson(list)) }
            flow.value = TrackListState(items = list, syncedAt = Instant.now())
        } catch (e: SfException) {
            flow.update { it.copy(loading = false, error = e.message) }
        }
    }

    /** 登出時清掉快取，避免下一個登入的人看到 */
    suspend fun clear() {
        withContext(Dispatchers.IO) { TrackKind.entries.forEach { cacheFile(it).delete() } }
        states.values.forEach { it.value = TrackListState() }
        _trash.value = TrackListState()
    }

    /** 從所有分頁的清單裡找音軌（多選、播放清單用） */
    fun find(ids: Collection<String>): List<Track> {
        val all = states.values.flatMap { it.value.items }.associateBy { it.id }
        return ids.mapNotNull { all[it] }
    }

    fun allTracks(): List<Track> = states.values.flatMap { it.value.items }

    private fun inList(ids: Collection<String>) = ids.joinToString(",", "(", ")")

    private suspend fun patch(ids: Collection<String>, body: JSONObject, action: String) {
        api.write("PATCH", "/rest/v1/sf_track?id=in.${inList(ids)}", body.toString(), auth.accessToken(), action)
    }

    /** 從畫面清單拿掉（刪除後馬上消失，不必等重新整理） */
    private fun removeLocal(ids: Set<String>) = states.values.forEach { f -> f.update { it.copy(items = it.items.filterNot { t -> t.id in ids }) } }

    /** 改名只改 SoundFlow 的 title；restore＝改回原標題（F05、F06） */
    suspend fun rename(t: Track, title: String?) {
        val newTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: t.originalTitle
        patch(listOf(t.id), JSONObject().put("title", newTitle), "改名")
        states.getValue(t.kind).update { s -> s.copy(items = s.items.map { if (it.id == t.id) it.copy(title = newTitle) else it }) }
    }

    /** 移到垃圾桶（7 天後 N100 永久刪除）；deleteSource 只對 Podcast 有意義：連新知雷達的音檔一起刪 */
    suspend fun moveToTrash(ids: Collection<String>, deleteSource: Boolean) {
        patch(ids, JSONObject().put("deleted_at", Instant.now().toString()).put("delete_source", deleteSource), "刪除")
        removeLocal(ids.toSet())
    }

    suspend fun restore(ids: Collection<String>) {
        patch(ids, JSONObject().put("deleted_at", JSONObject.NULL).put("delete_source", false).put("purge_requested_at", JSONObject.NULL), "還原")
        _trash.update { s -> s.copy(items = s.items.filterNot { it.id in ids }) }
        TrackKind.entries.forEach { refresh(it) }
    }

    /** 不等 7 天，請 N100 下一輪（約 15 秒內）就永久刪除 */
    suspend fun purgeNow(ids: Collection<String>) {
        patch(ids, JSONObject().put("purge_requested_at", Instant.now().toString()), "永久刪除")
        _trash.update { s -> s.copy(items = s.items.filterNot { it.id in ids }) }
    }

    private val _trash = MutableStateFlow(TrackListState())
    val trash: StateFlow<TrackListState> = _trash

    suspend fun refreshTrash() {
        _trash.update { it.copy(loading = true, error = null) }
        try {
            val path = "/rest/v1/sf_track?select=${SfJson.TRACK_FIELDS}&deleted_at=not.is.null&purged_at=is.null" +
                "&purge_requested_at=is.null&order=deleted_at.desc&limit=500"
            _trash.value = TrackListState(items = SfJson.parseTracks(api.get(path, auth.accessToken())), syncedAt = Instant.now())
        } catch (e: SfException) {
            _trash.update { it.copy(loading = false, error = e.message) }
        }
    }
}
