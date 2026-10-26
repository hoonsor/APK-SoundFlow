package tw.hoonsor.soundflow.data

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.soundflow.player.TrackProgress
import java.time.Duration
import java.time.Instant

/** 顏色標籤；color 是色票代號（顏色值只在 ui/theme/SfColors.kt 的 SfLabelColors） */
data class Label(val id: String, val name: String, val color: String, val sortOrder: Int)

/** 自訂播放清單；trackIds 依清單內順序 */
data class Playlist(val id: String, val name: String, val sortOrder: Int, val trackIds: List<String>)

data class LibraryState(
    val labels: List<Label> = emptyList(),
    /** trackId → 貼了哪些標籤 */
    val trackLabels: Map<String, Set<String>> = emptyMap(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
)

enum class ViewMode(val key: String, val label: String) {
    GRID("grid", "兩欄網格"), LIST("list", "精簡列表");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: GRID }
}

enum class SortKey(val key: String, val label: String) {
    ADDED("added", "加入日期"), TITLE("title", "標題"), DURATION("duration", "長度"), PROGRESS("progress", "收聽進度");

    companion object { fun of(k: String?) = entries.firstOrNull { it.key == k } ?: ADDED }
}

data class SortSpec(val key: SortKey = SortKey.ADDED, val descending: Boolean = true) {
    val label: String get() = key.label + when (key) {
        SortKey.ADDED -> if (descending) "（新到舊）" else "（舊到新）"
        SortKey.TITLE -> if (descending) "（Z→A）" else "（A→Z）"
        SortKey.DURATION -> if (descending) "（長到短）" else "（短到長）"
        SortKey.PROGRESS -> if (descending) "（多到少）" else "（少到多）"
    }
}

enum class ListFilter(val label: String) { ALL("全部"), UNPLAYED("未聽"), IN_PROGRESS("聽到一半"), PLAYED("已聽完") }

data class TrackGroup(val label: Label?, val tracks: List<Track>) {
    val key: String get() = label?.id ?: "__none__"
    val title: String get() = label?.name ?: "未分類"
}

/** 清單管理的純邏輯（JVM 測試涵蓋）：排序、篩選、分組、智慧清單、垃圾桶天數 */
object LibraryLogic {
    const val TRASH_DAYS = 7L

    fun sort(tracks: List<Track>, spec: SortSpec, progress: Map<String, TrackProgress>): List<Track> {
        val cmp: Comparator<Track> = when (spec.key) {
            SortKey.ADDED -> compareBy { it.createdAt }
            // 中文標題用 Unicode 順序即可（同一來源的標題常有共同字首，排在一起就夠用）
            SortKey.TITLE -> compareBy<Track> { it.title.lowercase() }
            SortKey.DURATION -> compareBy { it.durationSec ?: 0 }
            SortKey.PROGRESS -> compareBy { progress[it.id]?.fraction ?: 0f }
        }
        val ordered = tracks.sortedWith(cmp.thenBy { it.createdAt })
        return if (spec.descending) ordered.reversed() else ordered
    }

    fun filter(
        tracks: List<Track>,
        filter: ListFilter,
        labelFilter: Set<String>,
        trackLabels: Map<String, Set<String>>,
        progress: Map<String, TrackProgress>,
    ): List<Track> = tracks.filter { t ->
        val p = progress[t.id]
        val stateOk = when (filter) {
            ListFilter.ALL -> true
            ListFilter.UNPLAYED -> p == null || (!p.played && !p.inProgress)
            ListFilter.IN_PROGRESS -> p?.inProgress == true
            ListFilter.PLAYED -> p?.played == true
        }
        // 選了多個標籤：符合其中任一個就顯示
        stateOk && (labelFilter.isEmpty() || trackLabels[t.id].orEmpty().any { it in labelFilter })
    }

    /** 依標籤分組：一集貼了兩個標籤就出現在兩組；沒貼的放最後「未分類」；空的組不顯示 */
    fun group(tracks: List<Track>, labels: List<Label>, trackLabels: Map<String, Set<String>>): List<TrackGroup> {
        val groups = labels.sortedBy { it.sortOrder }.map { l -> TrackGroup(l, tracks.filter { l.id in trackLabels[it.id].orEmpty() }) }
        val none = TrackGroup(null, tracks.filter { t -> trackLabels[t.id].orEmpty().none { id -> labels.any { it.id == id } } })
        return (groups + none).filter { it.tracks.isNotEmpty() }
    }

    fun smartLatest(tracks: List<Track>) = tracks.sortedByDescending { it.createdAt }.take(50)
    fun smartUnfinished(tracks: List<Track>, p: Map<String, TrackProgress>) =
        tracks.filter { p[it.id]?.played != true }.sortedByDescending { it.createdAt }
    fun smartInProgress(tracks: List<Track>, p: Map<String, TrackProgress>) =
        tracks.filter { p[it.id]?.inProgress == true }.sortedByDescending { p[it.id]?.updatedAt ?: 0 }
    fun smartRecent(tracks: List<Track>, p: Map<String, TrackProgress>) =
        tracks.filter { (p[it.id]?.updatedAt ?: 0) > 0 }.sortedByDescending { p[it.id]?.updatedAt ?: 0 }.take(30)

    /** 垃圾桶剩幾天會被 N100 永久刪除（最少 0） */
    fun trashDaysLeft(deletedAt: Instant, now: Instant): Long =
        (TRASH_DAYS - Duration.between(deletedAt, now).toHours() / 24).coerceAtLeast(0)

    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    // ── JSON（雲端回應與本機快取共用同一種格式）─────────────────────
    fun parseLabels(raw: String): List<Label> = JSONArray(raw).let { a ->
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Label(o.getString("id"), o.getString("name"), o.optString("color", "teal"), o.optInt("sort_order"))
        }
    }

    fun parseTrackLabels(raw: String): Map<String, Set<String>> = JSONArray(raw).let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.groupBy({ it.getString("track_id") }, { it.getString("label_id") })
            .mapValues { it.value.toSet() }
    }

    /** sf_playlist?select=id,name,sort_order,sf_playlist_item(track_id,sort_order) */
    fun parsePlaylists(raw: String): List<Playlist> = JSONArray(raw).let { a ->
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val items = o.optJSONArray("sf_playlist_item") ?: JSONArray()
            val ids = (0 until items.length()).map { items.getJSONObject(it) }.sortedBy { it.optInt("sort_order") }.map { it.getString("track_id") }
            Playlist(o.getString("id"), o.getString("name"), o.optInt("sort_order"), ids)
        }.sortedBy { it.sortOrder }
    }

    fun toCache(s: LibraryState): String = JSONObject()
        .put("labels", JSONArray(s.labels.map { JSONObject().put("id", it.id).put("name", it.name).put("color", it.color).put("sort_order", it.sortOrder) }))
        .put("track_labels", JSONArray(s.trackLabels.flatMap { (t, ls) -> ls.map { JSONObject().put("track_id", t).put("label_id", it) } }))
        .put("playlists", JSONArray(s.playlists.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("sort_order", p.sortOrder)
                .put("sf_playlist_item", JSONArray(p.trackIds.mapIndexed { i, t -> JSONObject().put("track_id", t).put("sort_order", i) }))
        }))
        .toString()

    fun fromCache(raw: String): LibraryState? = runCatching {
        val o = JSONObject(raw)
        LibraryState(
            labels = parseLabels(o.getJSONArray("labels").toString()),
            trackLabels = parseTrackLabels(o.getJSONArray("track_labels").toString()),
            playlists = parsePlaylists(o.getJSONArray("playlists").toString()),
        )
    }.getOrNull()
}
