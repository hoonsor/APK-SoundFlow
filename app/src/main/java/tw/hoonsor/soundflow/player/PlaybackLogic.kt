package tw.hoonsor.soundflow.player

import org.json.JSONObject
import kotlin.math.roundToInt

/** 播放相關的純邏輯（JVM 測試涵蓋）；Media3 相關的程式不放這裡 */
object Speed {
    const val MIN = 0.5f
    const val MAX = 3.0f
    val PRESETS = listOf(0.8f, 1.0f, 1.2f, 1.3f, 1.5f, 1.75f, 2.0f)

    /** 夾在 0.5～3.0，並對齊 0.05 */
    fun clamp(v: Float): Float = ((v.coerceIn(MIN, MAX) * 20).roundToInt() / 20f)

    /** 小工具倍速按鈕：點一下換下一個（1.0 → 1.2 → 1.3 → 1.5 → 2.0 → 1.0）；不在清單上的值跳到比它大的下一個 */
    val WIDGET_CYCLE = listOf(1.0f, 1.2f, 1.3f, 1.5f, 2.0f)
    fun nextWidget(v: Float): Float = WIDGET_CYCLE.firstOrNull { it > clamp(v) + 0.001f } ?: WIDGET_CYCLE.first()

    fun label(v: Float): String {
        val s = "%.2f".format(clamp(v)).trimEnd('0').trimEnd('.')
        return "${s}×"
    }
}

enum class Repeat(val label: String) {
    OFF("不循環"), ALL("清單循環"), ONE("單曲循環");

    fun next(): Repeat = entries[(ordinal + 1) % entries.size]
}

/** 每一集的收聽進度 */
data class TrackProgress(val positionMs: Long, val durationMs: Long, val played: Boolean, val updatedAt: Long) {
    val fraction: Float get() = if (played) 1f else if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val inProgress: Boolean get() = !played && positionMs > 5_000
}

object ProgressRules {
    /** 剩不到 30 秒或播到 95% 就算聽完 */
    fun isPlayed(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && (positionMs >= durationMs * 0.95 || durationMs - positionMs <= 30_000)

    /** 聽完的集數重聽：前 5 秒仍算聽完（避免一點開就變回未聽），超過 5 秒改回「聽到一半」 */
    fun nextPlayed(wasPlayed: Boolean, positionMs: Long, durationMs: Long): Boolean =
        isPlayed(positionMs, durationMs) || (wasPlayed && positionMs < 5_000)

    /** 下次從哪裡開始：聽完的從頭；中斷的往回 3 秒，接得比較順 */
    fun resumePosition(p: TrackProgress?): Long = when {
        p == null || p.played -> 0L
        p.durationMs > 0 && p.positionMs >= p.durationMs - 5_000 -> 0L
        else -> (p.positionMs - 3_000).coerceAtLeast(0L)
    }

    fun toJson(map: Map<String, TrackProgress>): String = JSONObject().apply {
        map.forEach { (id, p) ->
            put(id, JSONObject().put("p", p.positionMs).put("d", p.durationMs).put("x", p.played).put("t", p.updatedAt))
        }
    }.toString()

    fun fromJson(raw: String): Map<String, TrackProgress> = runCatching {
        val o = JSONObject(raw)
        o.keys().asSequence().associateWith { id ->
            val j = o.getJSONObject(id)
            TrackProgress(j.optLong("p"), j.optLong("d"), j.optBoolean("x"), j.optLong("t"))
        }
    }.getOrDefault(emptyMap())
}

object TimeText {
    /** 毫秒 → 06:08、1:02:15（播放頁用，分鐘補兩位） */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000).toInt()
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 睡眠計時剩餘：29 分、45 秒 */
    fun sleepLeft(ms: Long): String {
        val sec = (ms.coerceAtLeast(0) + 999) / 1000
        return if (sec >= 60) "${(sec + 59) / 60} 分" else "$sec 秒"
    }
}

object Queue {
    /** 接下來要播的索引（最多 n 個）；清單循環時繞回開頭，不重複目前這集 */
    fun upNext(count: Int, current: Int, n: Int, repeatAll: Boolean): List<Int> {
        if (count <= 1 || current !in 0 until count) return emptyList()
        val out = mutableListOf<Int>()
        var i = current + 1
        while (out.size < n) {
            if (i >= count) { if (!repeatAll) break; i = 0 }
            if (i == current) break
            out += i
            i++
        }
        return out
    }
}
