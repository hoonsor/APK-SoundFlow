package tw.hoonsor.soundflow

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.data.AuthRepository
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.JobRepository
import tw.hoonsor.soundflow.data.ProgressRepository
import tw.hoonsor.soundflow.data.SettingsRepository
import tw.hoonsor.soundflow.data.TrackRepository
import tw.hoonsor.soundflow.data.supabase.SupabaseClient
import tw.hoonsor.soundflow.player.PlayerConnection

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
    lateinit var progress: ProgressRepository
        private set
    lateinit var jobs: JobRepository
        private set
    lateinit var player: PlayerConnection
        private set

    /** 跟 App 同壽命的背景工作（讀登入狀態等） */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun init(app: Application) {
        if (::settings.isInitialized) return
        val supabase = SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
        settings = SettingsRepository(app)
        auth = AuthRepository(app, supabase)
        tracks = TrackRepository(app, supabase, auth)
        progress = ProgressRepository(app)
        jobs = JobRepository(app, supabase, auth)
        player = PlayerConnection(app, progress, appScope)
        appScope.launch {
            auth.load()
            // 沒網路時存下的 YouTube 分享，登入狀態確定後補送
            if (auth.state.value is AuthState.SignedIn) runCatching { jobs.flushPending() }
        }
        appScope.launch { progress.load() }
    }
}
