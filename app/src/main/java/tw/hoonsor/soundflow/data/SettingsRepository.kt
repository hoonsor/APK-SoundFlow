package tw.hoonsor.soundflow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import tw.hoonsor.soundflow.player.PlayMode
import tw.hoonsor.soundflow.player.Speed
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tw.hoonsor.soundflow.ui.theme.SfThemeId

// 檔名 settings.preferences_pb：備份規則（res/xml）依這個檔名，改名要一起改
private val Context.settingsStore by preferencesDataStore(name = "settings")

/** 設定的唯一寫入入口（App 畫面、Agent API、小工具都經過這裡） */
class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")

    val theme: Flow<SfThemeId> = context.settingsStore.data.map { SfThemeId.fromKey(it[themeKey]) }

    suspend fun setTheme(id: SfThemeId) {
        context.settingsStore.edit { it[themeKey] = id.key }
    }

    // 倍速依分頁記住（F12）：Podcast 預設 1.3、YouTube 預設 1.0；播放時改了就成為該分頁的新預設
    private fun speedKey(kind: TrackKind) = floatPreferencesKey("speed_${kind.key}")
    private fun defaultSpeed(kind: TrackKind) = if (kind == TrackKind.PODCAST) 1.3f else 1.0f

    suspend fun speed(kind: TrackKind): Float =
        context.settingsStore.data.first()[speedKey(kind)] ?: defaultSpeed(kind)

    suspend fun setSpeed(kind: TrackKind, v: Float) {
        context.settingsStore.edit { it[speedKey(kind)] = Speed.clamp(v) }
    }

    // 播放模式（循環／單曲／隨機）：App、通知列、小工具共用，重開機後還在
    private val playModeKey = stringPreferencesKey("play_mode")

    suspend fun playMode(): PlayMode = PlayMode.fromKey(context.settingsStore.data.first()[playModeKey])

    suspend fun setPlayMode(m: PlayMode) {
        context.settingsStore.edit { it[playModeKey] = m.name }
    }

    // 檢視方式依分頁記住（F51）：模式、是否依標籤分組、排序
    data class ViewPrefs(val mode: ViewMode = ViewMode.GRID, val groupByLabel: Boolean = false, val sort: SortSpec = SortSpec())

    private fun k(kind: TrackKind, name: String) = stringPreferencesKey("view_${kind.key}_$name")

    fun viewPrefs(kind: TrackKind): Flow<ViewPrefs> = context.settingsStore.data.map { p ->
        ViewPrefs(
            // 本機音檔沒有封面圖的比例高，預設用精簡列表（設計畫布第 7 列）
            mode = p[k(kind, "mode")]?.let { ViewMode.of(it) } ?: if (kind == TrackKind.LOCAL) ViewMode.LIST else ViewMode.GRID,
            groupByLabel = p[k(kind, "group")] == "1",
            sort = SortSpec(SortKey.of(p[k(kind, "sort")]), p[k(kind, "desc")] != "0"),
        )
    }

    suspend fun setViewPrefs(kind: TrackKind, v: ViewPrefs) {
        context.settingsStore.edit {
            it[k(kind, "mode")] = v.mode.key
            it[k(kind, "group")] = if (v.groupByLabel) "1" else "0"
            it[k(kind, "sort")] = v.sort.key.key
            it[k(kind, "desc")] = if (v.sort.descending) "1" else "0"
        }
    }
}
