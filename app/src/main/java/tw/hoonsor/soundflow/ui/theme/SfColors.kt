package tw.hoonsor.soundflow.ui.theme

import kotlin.math.abs
import kotlin.math.pow

/**
 * 色票唯一來源（對應 docs/DESIGN.md 第 2 節）。畫面上不可以直接寫色碼。
 * 用 HSL 三元組存放，純 Kotlin，方便 JVM 測試對比度；Compose 端用 [toColor] 轉換。
 */
data class Hsl(val h: Float, val s: Float, val l: Float) {
    /** sRGB 0..1 */
    fun rgb(): Triple<Double, Double, Double> {
        val sat = s / 100.0
        val lig = l / 100.0
        val c = (1 - abs(2 * lig - 1)) * sat
        val hp = (h % 360) / 60.0
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0.0)
            hp < 2 -> Triple(x, c, 0.0)
            hp < 3 -> Triple(0.0, c, x)
            hp < 4 -> Triple(0.0, x, c)
            hp < 5 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        val m = lig - c / 2
        return Triple(r1 + m, g1 + m, b1 + m)
    }

    /** WCAG 相對亮度 */
    fun luminance(): Double {
        fun lin(v: Double) = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        val (r, g, b) = rgb()
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }
}

/** WCAG 對比度（1..21） */
fun contrast(a: Hsl, b: Hsl): Double {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

enum class SfThemeId(val key: String, val label: String) {
    SIGNAL("signal", "訊號格"),
    NIGHT("night", "夜航"),
    AMBER("amber", "琥珀電台");

    companion object {
        fun fromKey(key: String?): SfThemeId = entries.firstOrNull { it.key == key } ?: SIGNAL
    }
}

data class SfPalette(
    val bg: Hsl,
    val surface: Hsl,
    val surface2: Hsl,
    val border: Hsl,
    val text: Hsl,
    val muted: Hsl,
    val accent: Hsl,
    val onAccent: Hsl,
    val accentSoft: Hsl,
    val accentLine: Hsl,
    val accentText: Hsl,
    val link: Hsl,
)

/** 三個主題共用的語意色 */
object SfSemantic {
    val danger = Hsl(14f, 80f, 72f)
    val dangerSoft = Hsl(14f, 30f, 13f)
    val dangerLine = Hsl(14f, 40f, 32f)
    val warnSoft = Hsl(40f, 40f, 16f)
    val warnText = Hsl(40f, 80f, 78f)

    /** 封面色塊：H 依來源，S 30、L 30 */
    fun cover(hue: Float) = Hsl(hue, 30f, 30f)
}

/**
 * 顏色標籤色票（F52）：雲端只存代號，顏色值只在這裡。三個佈景主題共用（都是深色底）。
 * dot＝小圓點、text＝標籤文字、bg＝標籤底色（ThemeTest 檢查 text 對 bg 與各主題 surface 的對比度）
 */
data class LabelColor(val key: String, val name: String, val hue: Float) {
    val dot get() = Hsl(hue, 60f, 62f)
    val text get() = Hsl(hue, 70f, 78f)
    val bg get() = Hsl(hue, 30f, 17f)
}

object SfLabelColors {
    val all = listOf(
        LabelColor("blue", "藍", 212f),
        LabelColor("teal", "青", 172f),
        LabelColor("green", "綠", 135f),
        LabelColor("amber", "琥珀", 38f),
        LabelColor("coral", "珊瑚", 12f),
        LabelColor("pink", "粉", 330f),
        LabelColor("violet", "紫", 268f),
        LabelColor("slate", "灰", 215f),
    )

    fun of(key: String?): LabelColor = all.firstOrNull { it.key == key } ?: all.first()
}

object SfPalettes {
    val signal = SfPalette(
        bg = Hsl(215f, 22f, 7f), surface = Hsl(215f, 18f, 11f), surface2 = Hsl(232f, 24f, 16f), border = Hsl(215f, 14f, 20f),
        text = Hsl(210f, 25f, 94f), muted = Hsl(212f, 12f, 66f),
        accent = Hsl(232f, 80f, 74f), onAccent = Hsl(232f, 50f, 12f), accentSoft = Hsl(232f, 30f, 14f),
        accentLine = Hsl(232f, 40f, 40f), accentText = Hsl(232f, 80f, 82f), link = Hsl(232f, 60f, 38f),
    )
    val night = SfPalette(
        bg = Hsl(222f, 18f, 8f), surface = Hsl(222f, 16f, 12f), surface2 = Hsl(222f, 14f, 16f), border = Hsl(222f, 12f, 22f),
        text = Hsl(220f, 20f, 94f), muted = Hsl(220f, 10f, 66f),
        accent = Hsl(162f, 52f, 56f), onAccent = Hsl(162f, 60f, 10f), accentSoft = Hsl(162f, 30f, 14f),
        accentLine = Hsl(162f, 40f, 40f), accentText = Hsl(162f, 52f, 72f), link = Hsl(162f, 70f, 22f),
    )
    val amber = SfPalette(
        bg = Hsl(28f, 14f, 8f), surface = Hsl(28f, 12f, 12f), surface2 = Hsl(30f, 18f, 16f), border = Hsl(28f, 10f, 22f),
        text = Hsl(36f, 30f, 93f), muted = Hsl(32f, 12f, 68f),
        accent = Hsl(36f, 85f, 62f), onAccent = Hsl(30f, 60f, 10f), accentSoft = Hsl(36f, 40f, 17f),
        accentLine = Hsl(36f, 60f, 45f), accentText = Hsl(36f, 85f, 72f), link = Hsl(30f, 80f, 28f),
    )

    fun of(id: SfThemeId): SfPalette = when (id) {
        SfThemeId.SIGNAL -> signal
        SfThemeId.NIGHT -> night
        SfThemeId.AMBER -> amber
    }
}
