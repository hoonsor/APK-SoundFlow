package tw.hoonsor.soundflow

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.agent.AgentManifest
import java.io.File

/**
 * describe 直接回傳 docs/agent-manifest.json，所以這裡檢查 manifest 本身符合協定
 * （APK-VoiceHub docs/AGENT_PROTOCOL.md 第 3、4 節）。
 */
class AgentManifestTest {
    private val manifest: JSONObject by lazy {
        val file = listOf(File("../docs/agent-manifest.json"), File("docs/agent-manifest.json")).first { it.exists() }
        JSONObject(file.readText())
    }

    @Test fun header() {
        assertEquals(1, manifest.getInt("api"))
        assertEquals("tw.hoonsor.soundflow", manifest.getString("package"))
    }

    @Test fun actionsFollowProtocol() {
        val actions = manifest.getJSONArray("actions")
        val names = mutableSetOf<String>()
        for (i in 0 until actions.length()) {
            val a = actions.getJSONObject(i)
            val name = a.getString("name")
            assertTrue("名稱只能用 [a-z0-9_]：$name", Regex("^[a-z0-9_]+$").matches(name))
            assertTrue("重複的動作：$name", names.add(name))
            assertTrue("$name 的 risk", a.getString("risk") in setOf("read", "reversible", "destructive"))
            assertTrue("$name 至少 2 句例句", a.getJSONArray("examples").length() >= 2)
            assertEquals("$name 的 params 要是 object", "object", a.getJSONObject("params").getString("type"))
            val title = a.getString("title")
            // 協定建議 4–8 個中文字；含英文名稱（例如「加入 YouTube」）時放寬到 12 個字元
            assertTrue("$name 的 title 太長或太短：$title", title.length in 4..12)
        }
        assertTrue("至少要有一個 *_list 查詢動作", names.any { it.endsWith("_list") })
        assertEquals(names, AgentManifest.actionNames(manifest))
    }

    @Test fun deletingIsDestructive() {
        val actions = manifest.getJSONArray("actions")
        for (i in 0 until actions.length()) {
            val a = actions.getJSONObject(i)
            if (a.getString("name") in setOf("track_delete", "backup_import")) {
                assertEquals(a.getString("name"), "destructive", a.getString("risk"))
            }
        }
    }
}
