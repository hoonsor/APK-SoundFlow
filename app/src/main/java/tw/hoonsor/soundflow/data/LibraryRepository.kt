package tw.hoonsor.soundflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.soundflow.data.supabase.SfException
import tw.hoonsor.soundflow.data.supabase.SupabaseClient
import java.io.File

/**
 * 顏色標籤與播放清單的唯一讀寫入口（雲端同步：sf_label、sf_track_label、sf_playlist、sf_playlist_item）。
 * 寫入採「先改畫面、再送雲端」；雲端失敗就重新載入，並把錯誤放進 state.error 給畫面顯示。
 */
class LibraryRepository(
    context: Context,
    private val api: SupabaseClient,
    private val auth: AuthRepository,
) {
    private val cache = File(context.filesDir, "library.json")
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    suspend fun loadCache() {
        val cached = withContext(Dispatchers.IO) { runCatching { cache.takeIf { it.exists() }?.readText() }.getOrNull() }
        cached?.let { LibraryLogic.fromCache(it) }?.let { c -> _state.update { if (it.labels.isEmpty() && it.playlists.isEmpty()) c else it } }
    }

    suspend fun refresh() {
        try {
            val token = auth.accessToken()
            val labels = LibraryLogic.parseLabels(api.get("/rest/v1/sf_label?select=id,name,color,sort_order&order=sort_order,created_at", token))
            val links = LibraryLogic.parseTrackLabels(api.get("/rest/v1/sf_track_label?select=track_id,label_id", token))
            val playlists = LibraryLogic.parsePlaylists(
                api.get("/rest/v1/sf_playlist?select=id,name,sort_order,sf_playlist_item(track_id,sort_order)&order=sort_order,created_at", token),
            )
            val s = LibraryState(labels, links, playlists)
            _state.value = s
            withContext(Dispatchers.IO) { runCatching { cache.writeText(LibraryLogic.toCache(s)) } }
        } catch (e: SfException) {
            _state.update { it.copy(error = e.message) }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** 改畫面 → 送雲端；失敗就以雲端為準重新載入 */
    private suspend fun mutate(local: (LibraryState) -> LibraryState, remote: suspend (String) -> Unit) {
        _state.update(local)
        try {
            remote(auth.accessToken())
        } catch (e: SfException) {
            refresh()
            _state.update { it.copy(error = e.message) }
            return
        }
        withContext(Dispatchers.IO) { runCatching { cache.writeText(LibraryLogic.toCache(_state.value)) } }
    }

    private fun inList(ids: Collection<String>) = ids.joinToString(",", "(", ")")

    // ── 標籤 ─────────────────────────────────────────────────────
    suspend fun createLabel(name: String, color: String): Label? {
        return try {
            val order = (_state.value.labels.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val body = JSONObject().put("name", name.trim()).put("color", color).put("sort_order", order).toString()
            val created = LibraryLogic.parseLabels(api.write("POST", "/rest/v1/sf_label", body, auth.accessToken(), "新增標籤", "return=representation")).first()
            _state.update { it.copy(labels = it.labels + created) }
            created
        } catch (e: SfException) {
            _state.update { it.copy(error = e.message) }; null
        }
    }

    suspend fun updateLabel(id: String, name: String, color: String) = mutate(
        { s -> s.copy(labels = s.labels.map { if (it.id == id) it.copy(name = name.trim(), color = color) else it }) },
        { t -> api.write("PATCH", "/rest/v1/sf_label?id=eq.$id", JSONObject().put("name", name.trim()).put("color", color).toString(), t, "修改標籤") },
    )

    suspend fun deleteLabel(id: String) = mutate(
        { s -> s.copy(labels = s.labels.filterNot { it.id == id }, trackLabels = s.trackLabels.mapValues { it.value - id }) },
        { t -> api.write("DELETE", "/rest/v1/sf_label?id=eq.$id", "", t, "刪除標籤") },
    )

    /** 批次貼上或撕下一個標籤 */
    suspend fun setLabel(trackIds: Collection<String>, labelId: String, on: Boolean) = mutate(
        { s ->
            val m = s.trackLabels.toMutableMap()
            trackIds.forEach { t -> m[t] = if (on) m[t].orEmpty() + labelId else m[t].orEmpty() - labelId }
            s.copy(trackLabels = m)
        },
        { t ->
            if (on) {
                val rows = JSONArray(trackIds.map { JSONObject().put("track_id", it).put("label_id", labelId) }).toString()
                api.write("POST", "/rest/v1/sf_track_label?on_conflict=track_id,label_id", rows, t, "貼標籤", "resolution=ignore-duplicates,return=minimal")
            } else {
                api.write("DELETE", "/rest/v1/sf_track_label?label_id=eq.$labelId&track_id=in.${inList(trackIds)}", "", t, "移除標籤")
            }
        },
    )

    // ── 播放清單 ─────────────────────────────────────────────────
    suspend fun createPlaylist(name: String): Playlist? {
        return try {
            val order = (_state.value.playlists.maxOfOrNull { it.sortOrder } ?: 0) + 1
            val body = JSONObject().put("name", name.trim()).put("sort_order", order).toString()
            val raw = api.write("POST", "/rest/v1/sf_playlist?select=id,name,sort_order", body, auth.accessToken(), "新增清單", "return=representation")
            val created = LibraryLogic.parsePlaylists(raw).first()
            _state.update { it.copy(playlists = it.playlists + created) }
            created
        } catch (e: SfException) {
            _state.update { it.copy(error = e.message) }; null
        }
    }

    suspend fun renamePlaylist(id: String, name: String) = mutate(
        { s -> s.copy(playlists = s.playlists.map { if (it.id == id) it.copy(name = name.trim()) else it }) },
        { t -> api.write("PATCH", "/rest/v1/sf_playlist?id=eq.$id", JSONObject().put("name", name.trim()).toString(), t, "修改清單") },
    )

    suspend fun deletePlaylist(id: String) = mutate(
        { s -> s.copy(playlists = s.playlists.filterNot { it.id == id }) },
        { t -> api.write("DELETE", "/rest/v1/sf_playlist?id=eq.$id", "", t, "刪除清單") },
    )

    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        val current = _state.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val toAdd = trackIds.filterNot { it in current.trackIds }
        if (toAdd.isEmpty()) return
        val start = current.trackIds.size
        mutate(
            { s -> s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(trackIds = it.trackIds + toAdd) else it }) },
            { t ->
                val rows = JSONArray(toAdd.mapIndexed { i, id -> JSONObject().put("playlist_id", playlistId).put("track_id", id).put("sort_order", start + i) }).toString()
                api.write("POST", "/rest/v1/sf_playlist_item?on_conflict=playlist_id,track_id", rows, t, "加入清單", "resolution=ignore-duplicates,return=minimal")
            },
        )
    }

    suspend fun removeFromPlaylist(playlistId: String, trackIds: Collection<String>) = mutate(
        { s -> s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(trackIds = it.trackIds - trackIds.toSet()) else it }) },
        { t -> api.write("DELETE", "/rest/v1/sf_playlist_item?playlist_id=eq.$playlistId&track_id=in.${inList(trackIds)}", "", t, "移出清單") },
    )

    /** 上移／下移：把整份順序重寫回雲端（清單通常不到幾十集） */
    suspend fun movePlaylistItem(playlistId: String, from: Int, to: Int) {
        val p = _state.value.playlists.firstOrNull { it.id == playlistId } ?: return
        val ids = LibraryLogic.move(p.trackIds, from, to)
        if (ids == p.trackIds) return
        mutate(
            { s -> s.copy(playlists = s.playlists.map { if (it.id == playlistId) it.copy(trackIds = ids) else it }) },
            { t ->
                val rows = JSONArray(ids.mapIndexed { i, id -> JSONObject().put("playlist_id", playlistId).put("track_id", id).put("sort_order", i) }).toString()
                api.write("POST", "/rest/v1/sf_playlist_item?on_conflict=playlist_id,track_id", rows, t, "調整順序", "resolution=merge-duplicates,return=minimal")
            },
        )
    }

    /** 登出時清掉，避免下一個登入的人看到 */
    suspend fun clear() {
        _state.value = LibraryState()
        withContext(Dispatchers.IO) { cache.delete() }
    }
}
