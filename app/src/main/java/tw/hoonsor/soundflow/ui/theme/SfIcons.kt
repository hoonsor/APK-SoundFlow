package tw.hoonsor.soundflow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 手繪線條圖示（24 格、線寬 1.9、圓角端點），路徑與設計畫布相同 */
object SfIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.9f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, vararg paths: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Grid = stroke(
        "grid",
        "M5,3.5h4a1.5,1.5 0 0 1 1.5,1.5v4a1.5,1.5 0 0 1 -1.5,1.5h-4a1.5,1.5 0 0 1 -1.5,-1.5v-4a1.5,1.5 0 0 1 1.5,-1.5z",
        "M15,3.5h4a1.5,1.5 0 0 1 1.5,1.5v4a1.5,1.5 0 0 1 -1.5,1.5h-4a1.5,1.5 0 0 1 -1.5,-1.5v-4a1.5,1.5 0 0 1 1.5,-1.5z",
        "M5,13.5h4a1.5,1.5 0 0 1 1.5,1.5v4a1.5,1.5 0 0 1 -1.5,1.5h-4a1.5,1.5 0 0 1 -1.5,-1.5v-4a1.5,1.5 0 0 1 1.5,-1.5z",
        "M15,13.5h4a1.5,1.5 0 0 1 1.5,1.5v4a1.5,1.5 0 0 1 -1.5,1.5h-4a1.5,1.5 0 0 1 -1.5,-1.5v-4a1.5,1.5 0 0 1 1.5,-1.5z",
    )
    val Playlist = stroke("playlist", "M4,6h12M4,11h12M4,16h7", "M15,16.5a2.5,2.5 0 1 0 5,0a2.5,2.5 0 1 0 -5,0", "M20,16.5V8")
    val Queue = stroke("queue", "M4,6h16M4,11h16M4,16h9", "M16,15l4,2.5l-4,2.5z")
    val Settings = stroke(
        "settings",
        "M9,12a3,3 0 1 0 6,0a3,3 0 1 0 -6,0",
        "M12,2.8v2.4M12,18.8v2.4M2.8,12h2.4M18.8,12h2.4M5.5,5.5l1.7,1.7M16.8,16.8l1.7,1.7M5.5,18.5l1.7,-1.7M16.8,7.2l1.7,-1.7",
    )
    val Search = stroke("search", "M4.5,11a6.5,6.5 0 1 0 13,0a6.5,6.5 0 1 0 -13,0", "M16,16l4.2,4.2")
    val Check = stroke("check", "M5,12.5l4.5,4.5L19,7.5", width = 3f)
    val Play = fill("play", "M8,5.5v13a1,1 0 0 0 1.5,0.9l10.2,-6.5a1,1 0 0 0 0,-1.7L9.5,4.6A1,1 0 0 0 8,5.5z")
    val Wave = stroke("wave", "M3,13c2.5,-6 4.5,-6 7,0s4.5,6 7,0 3.5,-5 6,-2", width = 2.4f)
}
