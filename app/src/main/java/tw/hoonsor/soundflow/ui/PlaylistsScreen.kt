package tw.hoonsor.soundflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.LibraryLogic
import tw.hoonsor.soundflow.data.SfFormat
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.toColor

/** 清單分頁：智慧清單（自動）＋自訂播放清單（F28、F29） */
@Composable
fun PlaylistsScreen() {
    val c = Sf.colors
    val auth by Graph.auth.state.collectAsState()
    val lib by Graph.library.state.collectAsState()
    val progress by Graph.progress.state.collectAsState()
    val pod by Graph.tracks.state(TrackKind.PODCAST).collectAsState()
    val yt by Graph.tracks.state(TrackKind.YOUTUBE).collectAsState()
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf<String?>(null) }   // 清單 id 或 smart:<key>
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(auth is AuthState.SignedIn) {
        if (auth is AuthState.SignedIn) {
            Graph.library.loadCache()
            TrackKind.entries.forEach { Graph.tracks.loadCache(it) }
            launch { Graph.library.refresh() }
            TrackKind.entries.forEach { if (Graph.tracks.state(it).value.syncedAt == null) Graph.tracks.refresh(it) }
        }
    }

    val all = pod.items + yt.items
    val smart = listOf(
        "latest" to ("最新加入" to LibraryLogic.smartLatest(all)),
        "unfinished" to ("未聽完" to LibraryLogic.smartUnfinished(all, progress)),
        "inprogress" to ("聽到一半" to LibraryLogic.smartInProgress(all, progress)),
        "recent" to ("最近播放" to LibraryLogic.smartRecent(all, progress)),
    )

    open?.let { id ->
        val byId = all.associateBy { it.id }
        if (id.startsWith("smart:")) {
            val (title, tracks) = smart.first { it.first == id.removePrefix("smart:") }.second
            PlaylistDetail(title, null, tracks, onBack = { open = null })
        } else {
            val p = lib.playlists.firstOrNull { it.id == id }
            // 清單被刪掉（或另一台裝置刪了）：回到清單列表；不在組合過程中直接改狀態
            if (p == null) LaunchedEffect(id) { open = null }
            else PlaylistDetail(p.name, p.id, p.trackIds.mapNotNull { byId[it] }, onBack = { open = null })
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("播放清單", style = SfType.pageTitle, color = c.text, modifier = Modifier.weight(1f))
            SfButton("＋ 新增清單", primary = false, onClick = { creating = true })
        }
        if (auth !is AuthState.SignedIn) { EmptyCard("還沒登入", "到「音檔」分頁登入後就能建立播放清單。"); return }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { Text("自動清單", style = SfType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    smart.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (key, v) ->
                                val (title, tracks) = v
                                SmartTile(title, tracks.size, SfFormat.total(tracks.sumOf { it.durationSec ?: 0 }), Modifier.weight(1f)) { open = "smart:$key" }
                            }
                        }
                    }
                }
            }
            item { Text("我的清單", style = SfType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 12.dp)) }
            if (lib.playlists.isEmpty()) item { EmptyCard("還沒有自訂清單", "按「＋ 新增清單」，或在音檔分頁長按多選後「加入清單」。") }
            itemsIndexed(lib.playlists, key = { _, p -> p.id }) { _, p ->
                val tracks = p.trackIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
                PlaylistRow(p.name, tracks, onOpen = { open = p.id }, onPlay = { Graph.player.playAll(tracks, shuffle = false, source = p.name) })
            }
            lib.error?.let { item { Text(it, color = c.danger, style = SfType.body) } }
        }
    }
    if (creating) {
        NameDialog("新增播放清單", "", "清單名稱", onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch { Graph.library.createPlaylist(name)?.let { open = it.id } }
        }
    }
}

@Composable
private fun SmartTile(title: String, count: Int, total: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.height(76.dp).clip(shape).background(c.surface).border(1.dp, c.border, shape).clickable(role = Role.Button, onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = SfType.rowTitle, color = c.text)
        Text("$count 集 · $total", style = SfType.number, color = c.muted)
    }
}

@Composable
private fun PlaylistRow(name: String, tracks: List<Track>, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).clickable(role = Role.Button, onClick = onOpen).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 2×2 拼貼封面：前四集的來源色
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2)) {
            Column {
                tracks.take(4).chunked(2).forEach { row ->
                    Row { row.forEach { t -> Box(Modifier.size(22.dp).background(SfSemantic.cover(SourceStyle.of(t.channel).hue).toColor())) } }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = SfType.rowTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${tracks.size} 集 · ${SfFormat.total(tracks.sumOf { it.durationSec ?: 0 })}", style = SfType.number, color = c.muted)
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, c.accentLine, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "播放 $name", enabled = tracks.isNotEmpty(), onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) { Icon(SfIcons.Play, contentDescription = "播放 $name", tint = c.accentText, modifier = Modifier.size(16.dp)) }
    }
}

/** 清單內頁：播放全部、隨機播放；自訂清單可改名、刪除、上移／下移、移出清單 */
@Composable
private fun PlaylistDetail(title: String, playlistId: String?, tracks: List<Track>, onBack: () -> Unit) {
    val c = Sf.colors
    val scope = rememberCoroutineScope()
    val nowPlaying by Graph.player.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallIcon(SfIcons.Back, "返回", onClick = onBack)
            Column(Modifier.weight(1f)) {
                Text(title, style = SfType.pageTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} 集 · ${SfFormat.total(tracks.sumOf { it.durationSec ?: 0 })}", style = SfType.number, color = c.muted)
            }
            if (playlistId != null) SfButton(if (editing) "完成" else "編輯", primary = false, onClick = { editing = !editing })
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SfButton("▶ 播放全部", primary = true, enabled = tracks.isNotEmpty(), onClick = { Graph.player.playAll(tracks, shuffle = false, source = title) }, modifier = Modifier.weight(1f))
            SfButton("隨機播放", primary = false, enabled = tracks.isNotEmpty(), onClick = { Graph.player.playAll(tracks, shuffle = true, source = title) }, modifier = Modifier.weight(1f))
        }
        if (editing && playlistId != null) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SfButton("改名", primary = false, onClick = { renaming = true })
                SfButton("刪除清單", primary = false, onClick = { deleting = true })
            }
        }
        if (tracks.isEmpty()) {
            Box(Modifier.padding(12.dp)) {
                EmptyCard("清單是空的", if (playlistId != null) "在音檔分頁長按多選，或點「⋯」→「加入播放清單」。" else "符合條件的音檔會自動出現在這裡。")
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                val playing = nowPlaying.currentId == t.id
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(if (playing) c.accentSoft else c.surface).border(1.dp, if (playing) c.accent else c.border, shape)
                        .clickable(role = Role.Button, enabled = !editing) { Graph.player.playOrToggle(tracks, i, title) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${i + 1}", style = SfType.number, color = c.muted, modifier = Modifier.padding(start = 4.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = SfType.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${SourceStyle.of(t.channel).label} · ${SfFormat.duration(t.durationSec)}", style = SfType.meta, color = c.muted)
                    }
                    if (editing && playlistId != null) {
                        SmallIcon(SfIcons.ArrowUp, "上移", enabled = i > 0) { scope.launch { Graph.library.movePlaylistItem(playlistId, i, i - 1) } }
                        SmallIcon(SfIcons.ArrowDown, "下移", enabled = i < tracks.lastIndex) { scope.launch { Graph.library.movePlaylistItem(playlistId, i, i + 1) } }
                        SmallIcon(SfIcons.Close, "移出清單", tint = c.danger) { scope.launch { Graph.library.removeFromPlaylist(playlistId, listOf(t.id)) } }
                    }
                }
            }
        }
    }
    if (renaming && playlistId != null) {
        NameDialog("清單改名", title, "清單名稱", onDismiss = { renaming = false }) { name ->
            renaming = false; scope.launch { Graph.library.renamePlaylist(playlistId, name) }
        }
    }
    if (deleting && playlistId != null) {
        AlertDialog(
            onDismissRequest = { deleting = false }, containerColor = c.surface,
            title = { Text("刪除「$title」？", color = c.text) },
            text = { Text("只刪除清單本身，裡面的音檔不會被刪。", color = c.text, style = SfType.body) },
            confirmButton = { SfButton("刪除清單", primary = true, onClick = { deleting = false; onBack(); scope.launch { Graph.library.deletePlaylist(playlistId) } }) },
            dismissButton = { SfButton("取消", primary = false, onClick = { deleting = false }) },
        )
    }
}

@Composable
fun SmallIcon(icon: ImageVector, label: String, enabled: Boolean = true, tint: Color = Sf.colors.text, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClickLabel = label, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(20.dp)) }
}
