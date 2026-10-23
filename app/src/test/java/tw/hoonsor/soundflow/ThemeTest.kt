package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.ui.theme.Hsl
import tw.hoonsor.soundflow.ui.theme.SfPalettes
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.contrast

/** 三個佈景主題都要達到可讀的對比度（WCAG：一般文字 4.5、大字與圖示 3） */
class ThemeTest {
    private val all = SfThemeId.entries.map { it to SfPalettes.of(it) }

    @Test fun hslConversion() {
        val (r, g, b) = Hsl(232f, 80f, 74f).rgb()
        assertEquals(0x88, Math.round(r * 255).toInt())
        assertEquals(0x96, Math.round(g * 255).toInt())
        assertEquals(0xF2, Math.round(b * 255).toInt())
    }

    @Test fun textContrast() {
        all.forEach { (id, p) ->
            assertTrue("$id text/bg", contrast(p.text, p.bg) >= 4.5)
            assertTrue("$id text/surface", contrast(p.text, p.surface) >= 4.5)
            assertTrue("$id muted/bg", contrast(p.muted, p.bg) >= 4.5)
            assertTrue("$id muted/surface", contrast(p.muted, p.surface) >= 4.5)
            assertTrue("$id onAccent/accent", contrast(p.onAccent, p.accent) >= 4.5)
            assertTrue("$id accentText/accentSoft", contrast(p.accentText, p.accentSoft) >= 4.5)
            assertTrue("$id link/text（反白 toast）", contrast(p.link, p.text) >= 4.5)
            assertTrue("$id danger/surface", contrast(SfSemantic.danger, p.surface) >= 4.5)
        }
    }

    @Test fun accentVisibleOnBackground() {
        all.forEach { (id, p) -> assertTrue("$id accent/bg", contrast(p.accent, p.bg) >= 3.0) }
    }

    @Test fun unknownKeyFallsBackToSignal() {
        assertEquals(SfThemeId.SIGNAL, SfThemeId.fromKey(null))
        assertEquals(SfThemeId.SIGNAL, SfThemeId.fromKey("nope"))
        assertEquals(SfThemeId.AMBER, SfThemeId.fromKey("amber"))
    }
}
