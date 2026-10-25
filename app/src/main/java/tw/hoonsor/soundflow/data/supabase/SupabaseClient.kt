package tw.hoonsor.soundflow.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 給畫面顯示的錯誤：message 是一句台灣口語 */
class SfException(val code: Int, message: String) : Exception(message)

/**
 * 直接呼叫 Supabase REST（GoTrue＋PostgREST），不用官方 SDK：依賴少、行為好控制。
 * 只處理連線與錯誤轉換；登入狀態由 AuthRepository 管。
 */
class SupabaseClient(private val baseUrl: String, private val apiKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** 寄 6 碼驗證碼；create_user=false：不在 Supabase 使用者名單的信箱不會被建立帳號 */
    suspend fun sendOtp(email: String) {
        post("/auth/v1/otp", JSONObject().put("email", email).put("create_user", false), action = "寄驗證碼")
    }

    suspend fun verifyOtp(email: String, token: String): String =
        post("/auth/v1/verify", JSONObject().put("type", "email").put("email", email).put("token", token), action = "驗證")

    suspend fun refresh(refreshToken: String): String =
        post("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken), action = "更新登入")

    suspend fun signOut(accessToken: String) {
        runCatching { post("/auth/v1/logout", JSONObject(), action = "登出", bearer = accessToken) }
    }

    /** PostgREST 查詢，例如 path = "/rest/v1/sf_track?select=..." */
    suspend fun get(path: String, bearer: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path)
            .header("apikey", apiKey).header("Authorization", "Bearer $bearer").get().build()
        execute(req, action = "讀取資料")
    }

    private suspend fun post(path: String, body: JSONObject, action: String, bearer: String? = null): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(baseUrl + path)
            .header("apikey", apiKey)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .post(body.toString().toRequestBody(jsonType)).build()
        execute(req, action)
    }

    private fun execute(req: Request, action: String): String {
        val res = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw SfException(0, "連不上 Supabase，請確認網路後再試")
        }
        res.use {
            val text = it.body?.string().orEmpty()
            if (it.isSuccessful) return text
            throw SfException(it.code, SupabaseErrors.message(action, it.code, text))
        }
    }
}

/** GoTrue／PostgREST 錯誤 → 中文（純邏輯，有 JVM 測試） */
object SupabaseErrors {
    fun message(action: String, code: Int, body: String): String {
        val detail = runCatching {
            val o = JSONObject(body)
            listOf("msg", "message", "error_description", "error").firstNotNullOfOrNull { k -> o.optString(k).takeIf { s -> s.isNotBlank() } }
        }.getOrNull().orEmpty()
        val lower = detail.lowercase()
        return when {
            code == 429 || "rate limit" in lower -> "寄太多次了，請過幾分鐘再試"
            "signups not allowed" in lower || "user not found" in lower -> "這個信箱不在 Supabase 的使用者名單，請先在後台加入"
            action == "驗證" && ("expired" in lower || "invalid" in lower) -> "驗證碼不對或已過期，請重新寄一次"
            "refresh token" in lower || (code == 400 && action == "更新登入") -> "登入已失效，請重新登入"
            code == 401 || code == 403 -> "沒有權限$action，請重新登入"
            code >= 500 -> "Supabase 暫時有問題（$code），請稍後再試"
            detail.isNotBlank() -> "$action 失敗：$detail"
            else -> "$action 失敗（$code）"
        }
    }
}
