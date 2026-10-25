package tw.hoonsor.soundflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.SoundFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by Graph.settings.theme.collectAsState(initial = SfThemeId.SIGNAL)
            val scope = rememberCoroutineScope()
            SoundFlowTheme(theme) {
                SoundFlowApp(
                    theme = theme,
                    onThemeChange = { id -> scope.launch { Graph.settings.setTheme(id) } },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 服務還在播（例如從通知列點回來）時，迷你播放列要立刻出現
        Graph.player.connect()
    }
}
