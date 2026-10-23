package tw.hoonsor.soundflow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
}
