package tw.hoonsor.soundflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
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
    }
}
