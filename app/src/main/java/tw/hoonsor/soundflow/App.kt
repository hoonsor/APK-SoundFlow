package tw.hoonsor.soundflow

import android.app.Application
import tw.hoonsor.soundflow.data.SettingsRepository

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** 手動依賴注入：整個 App（含 AgentProvider）共用同一組 Repository */
object Graph {
    lateinit var settings: SettingsRepository
        private set

    fun init(app: Application) {
        if (::settings.isInitialized) return
        settings = SettingsRepository(app)
    }
}
