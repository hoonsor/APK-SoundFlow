package tw.hoonsor.soundflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun Hsl.toColor(alpha: Float = 1f): Color = Color.hsl(h, s / 100f, l / 100f, alpha)

/** Compose 端的色票；欄位與 [SfPalette] 一一對應 */
@Immutable
data class SfColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentLine: Color,
    val accentText: Color,
    val link: Color,
    val danger: Color = SfSemantic.danger.toColor(),
    val dangerSoft: Color = SfSemantic.dangerSoft.toColor(),
    val dangerLine: Color = SfSemantic.dangerLine.toColor(),
)

fun SfPalette.toColors() = SfColors(
    bg = bg.toColor(), surface = surface.toColor(), surface2 = surface2.toColor(), border = border.toColor(),
    text = text.toColor(), muted = muted.toColor(), accent = accent.toColor(), onAccent = onAccent.toColor(),
    accentSoft = accentSoft.toColor(), accentLine = accentLine.toColor(), accentText = accentText.toColor(), link = link.toColor(),
)

/** 字級（docs/DESIGN.md 第 3 節）。IBM Plex 字型尚未打包，先用系統字型 */
object SfType {
    val mono = FontFamily.Monospace
    val pageTitle = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val rowTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val cardTitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.5.sp)
    val body = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val meta = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val section = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val number = TextStyle(fontFamily = mono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

val LocalSf = staticCompositionLocalOf { SfPalettes.signal.toColors() }

object Sf {
    val colors: SfColors
        @Composable get() = LocalSf.current
}

@Composable
fun SoundFlowTheme(themeId: SfThemeId, content: @Composable () -> Unit) {
    val c = SfPalettes.of(themeId).toColors()
    val scheme = darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentText,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted, outline = c.border, error = c.danger,
    )
    CompositionLocalProvider(LocalSf provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
