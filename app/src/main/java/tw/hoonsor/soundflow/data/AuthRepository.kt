package tw.hoonsor.soundflow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import tw.hoonsor.soundflow.data.supabase.SfException
import tw.hoonsor.soundflow.data.supabase.SupabaseClient

// 獨立的 DataStore：不在系統備份規則裡（Keystore 金鑰換機無法解密，備份過去也沒用）
private val Context.authStore by preferencesDataStore(name = "auth")

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val email: String) : AuthState
}

/** 登入狀態的唯一入口：信箱驗證碼登入、權杖自動刷新、登出 */
class AuthRepository(
    private val context: Context,
    private val api: SupabaseClient,
    private val box: SecureBox = SecureBox(),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val sessionKey = stringPreferencesKey("session")
    private val mutex = Mutex()
    private var session: Session? = null

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state

    /** 目前登入的信箱在不在 SoundFlow 白名單；null＝還沒確認（例如沒網路） */
    private val _member = MutableStateFlow<Boolean?>(null)
    val member: StateFlow<Boolean?> = _member

    suspend fun load() {
        mutex.withLock {
            val enc = context.authStore.data.first()[sessionKey]
            session = enc?.let { box.decrypt(it) }?.let { decode(it) }
            _state.value = session?.let { AuthState.SignedIn(it.email) } ?: AuthState.SignedOut
        }
        if (session != null) checkMembership()
    }

    suspend fun sendCode(email: String) = api.sendOtp(SfJson.normalizeEmail(email))

    suspend fun verify(email: String, code: String) {
        mutex.withLock {
            val s = SfJson.parseSession(api.verifyOtp(SfJson.normalizeEmail(email), code.trim()), now())
            save(s.copy(email = s.email.ifBlank { SfJson.normalizeEmail(email) }))
        }
        checkMembership()
    }

    /** 白名單外的帳號讀資料只會拿到空陣列、寫入被拒；先問清楚才能給正確提示 */
    suspend fun checkMembership() {
        _member.value = runCatching {
            val raw = api.write("POST", "/rest/v1/rpc/sf_whoami", "{}", accessToken(), action = "確認白名單", prefer = "return=representation")
            SfJson.parseWhoami(raw)
        }.getOrNull() ?: _member.value
    }

    /** 取得有效的 access token；快過期（60 秒內）就先刷新。沒登入或刷新失敗丟 [SfException] */
    suspend fun accessToken(): String = mutex.withLock {
        val s = session ?: throw SfException(401, "請先登入")
        if (s.expiresAtEpochSec - now() > 60) return s.accessToken
        try {
            val fresh = SfJson.parseSession(api.refresh(s.refreshToken), now())
            save(fresh.copy(email = fresh.email.ifBlank { s.email }))
            fresh.accessToken
        } catch (e: SfException) {
            // 只有伺服器明確拒絕才登出；網路問題保留登入，下次再試
            if (e.code in 400..499) clear()
            throw e
        }
    }

    suspend fun signOut() {
        val token = mutex.withLock { session?.accessToken }
        if (token != null) api.signOut(token)
        mutex.withLock { clear() }
    }

    private suspend fun save(s: Session) {
        session = s
        context.authStore.edit { it[sessionKey] = box.encrypt(encode(s)) }
        _state.value = AuthState.SignedIn(s.email)
    }

    private suspend fun clear() {
        session = null
        _member.value = null
        context.authStore.edit { it.remove(sessionKey) }
        _state.value = AuthState.SignedOut
    }

    private fun encode(s: Session) = JSONObject().put("a", s.accessToken).put("r", s.refreshToken)
        .put("e", s.expiresAtEpochSec).put("m", s.email).toString()

    private fun decode(raw: String): Session? = runCatching {
        val o = JSONObject(raw)
        Session(o.getString("a"), o.getString("r"), o.getLong("e"), o.getString("m"))
    }.getOrNull()
}
