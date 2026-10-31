package tw.hoonsor.soundflow.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.LibraryLogic
import tw.hoonsor.soundflow.data.LocalLogic
import tw.hoonsor.soundflow.data.SettingsRepository
import tw.hoonsor.soundflow.data.SfFormat
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.SoundFlowTheme

/** 小工具能切換的清單：智慧清單、分頁全部、本機資料夾、自訂清單（換清單視窗與控制面板共用）。只用手機上的快取 */
@Composable
internal fun rememberWidgetChoices(): Pair<Boolean, List<Pair<String, List<Track>>>> {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        TrackKind.CLOUD.forEach { Graph.tracks.loadCache(it) }
        Graph.library.loadCache()
        Graph.progress.load()
        Graph.local.load()
        ready = true
    }
    val pod by Graph.tracks.state(TrackKind.PODCAST).collectAsState()
    val yt by Graph.tracks.state(TrackKind.YOUTUBE).collectAsState()
    val local by Graph.tracks.state(TrackKind.LOCAL).collectAsState()
    val lib by Graph.library.state.collectAsState()
    val progress by Graph.progress.state.collectAsState()
    val all = pod.items + yt.items
    val byId = all.associateBy { it.id }
    val choices = listOf(
        "最新加入" to LibraryLogic.smartLatest(all),
        "未聽完" to LibraryLogic.smartUnfinished(all, progress),
        "聽到一半" to LibraryLogic.smartInProgress(all, progress),
        "最近播放" to LibraryLogic.smartRecent(all, progress),
        "Podcast 全部" to pod.items,
        "YouTube 全部" to yt.items,
    ) + lib.playlists.map { p -> p.name to p.trackIds.mapNotNull { byId[it] } } +
        (if (local.items.isNotEmpty()) listOf("本機全部" to local.items) else emptyList())
    return ready to choices
}

/** 一層層點進本機資料夾，選一層來播；子資料夾可能很多，不適合攤成一長串（F58） */
@Composable
private fun LocalFolderChoices(onClose: () -> Unit, onPick: (String, List<Track>) -> Unit) {
    val c = Sf.colors
    val roots by Graph.local.state.collectAsState()
    val local by Graph.tracks.state(TrackKind.LOCAL).collectAsState()
    val prefs by Graph.settings.viewPrefs(TrackKind.LOCAL).collectAsState(initial = SettingsRepository.ViewPrefs())
    val progress by Graph.progress.state.collectAsState()
    var picked by remember { mutableStateOf<String?>(null) }
    var dir by remember { mutableStateOf("") }
    val root = picked ?: roots.folders.singleOrNull()?.uri
    val rootName = roots.folders.firstOrNull { it.uri == root }?.name.orEmpty()
    val name = if (dir.isEmpty()) rootName else LocalLogic.dirName(dir)
    fun up() {
        when {
            dir.isNotEmpty() -> dir = LocalLogic.parentDir(dir)
            picked != null && roots.folders.size > 1 -> picked = null
            else -> onClose()
        }
    }
    fun play(label: String, tracks: List<Track>) = onPick("本機 · $label", LibraryLogic.sort(tracks, prefs.sort, progress))
    BackHandler { up() }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "回上一層") { up() }.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(SfIcons.Back, contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
        Text(if (root == null) "本機資料夾" else name, style = SfType.rowTitle, color = c.text)
    }
    if (root == null) {
        roots.folders.forEach { f -> FolderChoice(f.name, local.items.count { it.localFolder == f.uri }, true) { picked = f.uri; dir = "" } }
        return
    }
    val here = LocalLogic.inDir(local.items, root, dir)
    val subs = LocalLogic.subDirs(local.items, root, dir)
    FolderChoice("播放這一層", here.size, false, enabled = here.isNotEmpty()) { play(name, here) }
    if (subs.isNotEmpty()) {
        val all = LocalLogic.underDir(local.items, root, dir)
        FolderChoice("含子資料夾一起播", all.size, false) { play(name, all) }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(c.border).size(1.dp))
        subs.forEach { d -> FolderChoice(d.name, d.count, true) { dir = d.path } }
    }
}

@Composable
private fun FolderChoice(name: String, count: Int, opens: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Sf.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (opens) SfIcons.Folder else SfIcons.Playlist, contentDescription = null, tint = if (enabled) c.accentText else c.border, modifier = Modifier.size(20.dp))
        Text(name, style = SfType.rowTitle, color = if (enabled) c.text else c.muted, modifier = Modifier.weight(1f))
        Text(if (opens) "$count 首 ›" else "$count 首", style = SfType.number, color = c.muted)
    }
}

/**
 * 小工具的「換清單」：透明畫面＋底部清單，點一個就從頭播放那份清單，不必打開 App（F25）。
 * 只用手機上的快取（音軌、標籤與清單、進度），沒網路也能選。
 */
class WidgetPickerActivity : ComponentActivity() {
    companion object {
        /** 直接打開本機資料夾（控制面板用） */
        const val EXTRA_FOLDERS = "folders"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by Graph.settings.theme.collectAsState(initial = SfThemeId.SIGNAL)
            val (ready, choices) = rememberWidgetChoices()
            val local by Graph.local.state.collectAsState()
            var browsing by remember { mutableStateOf(intent.getBooleanExtra(EXTRA_FOLDERS, false)) }

            SoundFlowTheme(theme) {
                val c = Sf.colors
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().heightIn(max = 520.dp).clip(shape).background(c.surface)
                            .border(1.dp, c.border, shape).verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (browsing) {
                            LocalFolderChoices(onClose = { browsing = false }) { name, tracks ->
                                Graph.player.playAll(tracks, shuffle = null, source = name)
                                finish()
                            }
                            return@Column
                        }
                        Text("切換播放清單", style = SfType.rowTitle, color = c.text, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                        if (ready && choices.all { it.second.isEmpty() }) Text("手機上還沒有音檔資料，請先打開 SoundFlow 一次。", style = SfType.body, color = c.muted, modifier = Modifier.padding(6.dp))
                        choices.forEachIndexed { i, (name, tracks) ->
                            if (i == 6) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(c.border).size(1.dp))
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button, enabled = tracks.isNotEmpty()) {
                                        Graph.player.playAll(tracks, shuffle = null, source = name)
                                        finish()
                                    }.padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(SfIcons.Playlist, contentDescription = null, tint = if (tracks.isEmpty()) c.border else c.accentText, modifier = Modifier.size(20.dp))
                                Text(name, style = SfType.rowTitle, color = if (tracks.isEmpty()) c.muted else c.text, modifier = Modifier.weight(1f))
                                Text("${tracks.size} 集 · ${SfFormat.total(tracks.sumOf { it.durationSec ?: 0 })}", style = SfType.number, color = c.muted)
                            }
                        }
                        if (local.folders.isNotEmpty()) FolderChoice("本機資料夾", local.entries.size, true) { browsing = true }
                    }
                }
            }
        }
    }
}
