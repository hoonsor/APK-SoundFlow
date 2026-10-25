package tw.hoonsor.soundflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tw.hoonsor.soundflow.player.ProgressRules
import tw.hoonsor.soundflow.player.TrackProgress
import java.io.File

/**
 * 收聽進度的唯一寫入入口（播放服務與畫面共用）。v0.3.0 只存在手機（progress.json）；
 * 同步到 Supabase（sf_progress）之後的版本再做。
 */
class ProgressRepository(context: Context) {
    private val file = File(context.filesDir, "progress.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow<Map<String, TrackProgress>>(emptyMap())
    val state: StateFlow<Map<String, TrackProgress>> = _state
    private var loaded = false

    suspend fun load() = mutex.withLock {
        if (loaded) return
        val raw = withContext(Dispatchers.IO) { runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull() }
        _state.value = raw?.let { ProgressRules.fromJson(it) } ?: emptyMap()
        loaded = true
    }

    fun get(id: String): TrackProgress? = _state.value[id]

    /** 播放中定期呼叫；播到快結束自動標成已聽完 */
    suspend fun update(id: String, positionMs: Long, durationMs: Long) {
        val played = ProgressRules.nextPlayed(_state.value[id]?.played == true, positionMs, durationMs)
        save { it + (id to TrackProgress(positionMs, durationMs, played, System.currentTimeMillis())) }
    }

    suspend fun markPlayed(id: String, played: Boolean) {
        val old = _state.value[id]
        save { it + (id to TrackProgress(if (played) old?.positionMs ?: 0 else 0, old?.durationMs ?: 0, played, System.currentTimeMillis())) }
    }

    private suspend fun save(change: (Map<String, TrackProgress>) -> Map<String, TrackProgress>) = mutex.withLock {
        _state.update(change)
        val json = ProgressRules.toJson(_state.value)
        withContext(Dispatchers.IO) { runCatching { file.writeText(json) } }
    }
}
