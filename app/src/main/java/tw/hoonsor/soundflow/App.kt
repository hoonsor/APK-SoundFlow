package tw.hoonsor.soundflow

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.data.AuthRepository
import tw.hoonsor.soundflow.data.SettingsRepository
import tw.hoonsor.soundflow.data.TrackRepository
import tw.hoonsor.soundflow.data.supabase.SupabaseClient

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
    lateinit var auth: AuthRepository
        private set
    lateinit var tracks: TrackRepository
        private set

    /** 跟 App 同壽命的背景工作（讀登入狀態等） */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(app: Application) {
        if (::settings.isInitialized) return
        val supabase = SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
        settings = SettingsRepository(app)
        auth = AuthRepository(app, supabase)
        tracks = TrackRepository(app, supabase, auth)
        appScope.launch { auth.load() }
    }
}
