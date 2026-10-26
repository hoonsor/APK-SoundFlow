package tw.hoonsor.soundflow.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.soundflow.data.supabase.SfException
import tw.hoonsor.soundflow.data.supabase.SupabaseClient
import java.io.File

data class JobsState(
    val jobs: List<YtJob> = emptyList(),
    val worker: WorkerBeat? = null,
    val pendingOffline: Int = 0,
    val error: String? = null,
)

sealed interface EnqueueResult {
    data object Queued : EnqueueResult
    data object AlreadyQueued : EnqueueResult
    data object AlreadyInLibrary : EnqueueResult
    data object SavedOffline : EnqueueResult
}

/** YouTube 轉檔工作的唯一入口：送出、列出、重試、取消；沒網路時先存在手機，之後自動補送 */
class JobRepository(
    private val context: Context,
    private val api: SupabaseClient,
    private val auth: AuthRepository,
) {
    private val pendingFile = File(context.filesDir, "pending_shares.json")
    private val pendingLock = Mutex()
    private val _state = MutableStateFlow(JobsState())
    val state: StateFlow<JobsState> = _state

    /** 分享進來或手動貼上網址時呼叫；網址不對丟 [SfException]（訊息可直接顯示） */
    suspend fun enqueue(shared: String): EnqueueResult {
        val link = YouTubeLink.parse(shared) ?: throw SfException(400, "這不是 YouTube 影片網址")
        return try {
            send(link)
        } catch (e: SfException) {
            if (e.code == 0) { addPending(link.canonicalUrl); EnqueueResult.SavedOffline } else throw e
        }
    }

    private suspend fun send(link: YouTubeLink.Link): EnqueueResult {
        val token = auth.accessToken()
        val existing = JSONArray(api.get("/rest/v1/sf_track?select=id&video_id=eq.${link.videoId}&limit=1", token))
        if (existing.length() > 0) return EnqueueResult.AlreadyInLibrary
        return try {
            api.write("POST", "/rest/v1/yt_job", JobJson.enqueueBody(link), token, action = "送出轉檔")
            EnqueueResult.Queued
        } catch (e: SfException) {
            if (e.code == 409) EnqueueResult.AlreadyQueued else throw e
        }
    }

    /** App 開啟、回到 YouTube 分頁時呼叫：把沒網路時存下的分享補送出去 */
    suspend fun flushPending() {
        pendingLock.withLock {
            val urls = readPending()
            if (urls.isEmpty()) return
            val left = urls.filter { url ->
                val link = YouTubeLink.parse(url) ?: return@filter false
                runCatching { send(link) }.isFailure
            }
            writePending(left)
        }
    }

    suspend fun refresh() {
        try {
            val token = auth.accessToken()
            val jobs = JobJson.parseJobs(
                api.get("/rest/v1/yt_job?select=${JobJson.FIELDS}&status=in.(queued,processing,failed)&order=requested_at.desc&limit=50", token),
            )
            val beats = JobJson.parseBeats(api.get("/rest/v1/sf_worker_heartbeat?select=worker,seen_at,yt_dlp_version&order=seen_at.desc&limit=1", token))
            _state.value = JobsState(jobs, beats.firstOrNull(), readPending().size)
        } catch (e: SfException) {
            _state.update { it.copy(error = e.message, pendingOffline = readPending().size) }
        }
    }

    suspend fun retry(id: String) = setStatus(id, "queued")
    suspend fun cancel(id: String) = setStatus(id, "cancelled")

    private suspend fun setStatus(id: String, status: String) {
        val body = JSONObject().put("status", status).put("error", JSONObject.NULL).toString()
        api.write("PATCH", "/rest/v1/yt_job?id=eq.$id", body, auth.accessToken(), action = if (status == "queued") "重試" else "取消")
        refresh()
    }

    private suspend fun readPending(): List<String> = withContext(Dispatchers.IO) {
        runCatching { pendingFile.takeIf { it.exists() }?.readText()?.let { JobJson.pendingFromJson(it) } }.getOrNull() ?: emptyList()
    }

    private suspend fun writePending(urls: List<String>) = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) pendingFile.delete() else pendingFile.writeText(JobJson.pendingToJson(urls))
    }

    private suspend fun addPending(url: String) = pendingLock.withLock {
        writePending((readPending() + url).distinct())
        _state.update { it.copy(pendingOffline = it.pendingOffline + 1) }
    }
}
