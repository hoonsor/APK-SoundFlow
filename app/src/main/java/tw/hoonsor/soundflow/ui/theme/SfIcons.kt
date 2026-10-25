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
    val Pause = fill("pause", "M7,5h2.6a1.2,1.2 0 0 1 1.2,1.2v11.6a1.2,1.2 0 0 1 -1.2,1.2H7a1.2,1.2 0 0 1 -1.2,-1.2V6.2A1.2,1.2 0 0 1 7,5z",
        "M14.4,5H17a1.2,1.2 0 0 1 1.2,1.2v11.6A1.2,1.2 0 0 1 17,19h-2.6a1.2,1.2 0 0 1 -1.2,-1.2V6.2A1.2,1.2 0 0 1 14.4,5z")
    val Next = fill("next", "M5,6.2v11.6a0.8,0.8 0 0 0 1.2,0.7l8.6,-5.8a0.8,0.8 0 0 0 0,-1.4L6.2,5.5A0.8,0.8 0 0 0 5,6.2z",
        "M17.5,5.5h1.6a1,1 0 0 1 1,1v11a1,1 0 0 1 -1,1h-1.6a1,1 0 0 1 -1,-1v-11a1,1 0 0 1 1,-1z")
    val Prev = fill("prev", "M19,6.2v11.6a0.8,0.8 0 0 1 -1.2,0.7l-8.6,-5.8a0.8,0.8 0 0 1 0,-1.4l8.6,-5.8a0.8,0.8 0 0 1 1.2,0.7z",
        "M4.9,5.5h1.6a1,1 0 0 1 1,1v11a1,1 0 0 1 -1,1H4.9a1,1 0 0 1 -1,-1v-11a1,1 0 0 1 1,-1z")
    val Back15 = stroke("back15", "M4.5,12a7.5,7.5 0 1 0 2.2,-5.3", "M4,4v4h4", width = 1.7f)
    val Fwd30 = stroke("fwd30", "M19.5,12a7.5,7.5 0 1 1 -2.2,-5.3", "M20,4v4h-4", width = 1.7f)
    val ChevronDown = stroke("chevronDown", "M6,9.5l6,6 6,-6", width = 2f)
    val Repeat = stroke("repeat", "M17,3.5L20.5,7 17,10.5", "M3.5,12V10a3,3 0 0 1 3,-3h14", "M7,20.5L3.5,17 7,13.5", "M20.5,12v2a3,3 0 0 1 -3,3h-14", width = 1.8f)
    val Shuffle = stroke("shuffle", "M3.5,7h3a5,5 0 0 1 4,2l3,4a5,5 0 0 0 4,2h3", "M17.5,12.5l3,2.5 -3,2.5",
        "M3.5,17h3a5,5 0 0 0 3.2,-1.2M14.3,8.2A5,5 0 0 1 17.5,7h3", "M17.5,4.5l3,2.5 -3,2.5", width = 1.8f)
    val Moon = stroke("moon", "M19,14.5A7.5,7.5 0 0 1 9.5,5a7.5,7.5 0 1 0 9.5,9.5z", width = 1.8f)
}
