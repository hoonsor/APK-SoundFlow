package tw.hoonsor.soundflow.agent

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.agent.AgentRequest
import tw.hoonsor.agent.HoonsorAgent
import tw.hoonsor.agent.HoonsorAgentProvider
import tw.hoonsor.soundflow.BuildConfig
import tw.hoonsor.soundflow.Graph

/**
 * Hoonsor Agent API（規格：APK-VoiceHub docs/AGENT_PROTOCOL.md）。
 * describe 直接回傳 docs/agent-manifest.json（編譯時複製進 assets，見 app/build.gradle.kts），
 * 只把 version 換成目前版本；所以 manifest 與 describe 不可能不一致。
 * 目前所有動作都是規劃中（manifest 的 status = planned），execute 一律回覆尚未完成。
 */
class AgentProvider : HoonsorAgentProvider() {

    // ContentProvider 比 Application.onCreate 先建立，所以這裡自己初始化
    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { Graph.init(it) }
        return true
    }

    private val manifest: JSONObject by lazy {
        val raw = context!!.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
        JSONObject(raw).put("version", BuildConfig.VERSION_NAME)
    }

    override fun describe(): JSONObject = JSONObject(manifest.toString())

    override fun health(): JSONObject =
        JSONObject().put("ok", false).put("issues", JSONArray().put("語音動作還在規劃中，之後的版本才能用"))

    override fun execute(request: AgentRequest): JSONObject {
        val names = AgentManifest.actionNames(manifest)
        if (request.action !in names) {
            return HoonsorAgent.fail(HoonsorAgent.NOT_FOUND, "SoundFlow 沒有「${request.action}」這個動作")
        }
        return HoonsorAgent.fail(HoonsorAgent.INTERNAL, "SoundFlow 的語音控制還沒做好，之後的版本就能用")
    }

    companion object {
        const val MANIFEST_ASSET = "agent-manifest.json"
    }
}

/** 純邏輯，JVM 測試用 */
object AgentManifest {
    fun actionNames(manifest: JSONObject): Set<String> {
        val arr = manifest.optJSONArray("actions") ?: return emptySet()
        return (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }.toSet()
    }
}
