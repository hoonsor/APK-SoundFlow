package tw.hoonsor.soundflow.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.Label
import tw.hoonsor.soundflow.data.LibraryLogic
import tw.hoonsor.soundflow.data.ListFilter
import tw.hoonsor.soundflow.data.LocalFolder
import tw.hoonsor.soundflow.data.SettingsRepository
import tw.hoonsor.soundflow.data.SfFormat
import tw.hoonsor.soundflow.data.SortKey
import tw.hoonsor.soundflow.data.SortSpec
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.data.TrackListState
import tw.hoonsor.soundflow.data.ViewMode
import tw.hoonsor.soundflow.player.TrackProgress
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.toColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 各種對話框／面板目前開哪一個 */
private sealed interface Sheet {
    data class Actions(val track: Track) : Sheet
    data class Labels(val ids: Set<String>) : Sheet
    data class Playlist(val ids: List<String>) : Sheet
    data class Delete(val tracks: List<Track>) : Sheet
    data class Rename(val track: Track) : Sheet
    data class FindCover(val track: Track) : Sheet
    data class CoverBatch(val tracks: List<Track>) : Sheet
}

@Composable
fun TracksScreen() {
    val auth by Graph.auth.state.collectAsState()
    val member by Graph.auth.member.collectAsState()
    var kind by rememberSaveable { mutableStateOf(TrackKind.PODCAST) }
    val state by Graph.tracks.state(kind).collectAsState()
    val signedIn = auth is AuthState.SignedIn
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }

    LaunchedEffect(kind, signedIn) {
        selection = emptySet()
        if (kind == TrackKind.LOCAL) {
            Graph.local.load()
            Graph.local.rescan()     // 只有檔案變動時才重讀標籤，平常很快
        } else if (signedIn) {
            Graph.tracks.loadCache(kind)
            Graph.library.loadCache()
            launch { Graph.library.refresh() }
            Graph.tracks.refresh(kind)
        }
    }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selection.isNotEmpty()) {
                SelectionBar(selection.size, onAll = { selection = state.items.map { it.id }.toSet() }, onCancel = { selection = emptySet() })
            } else {
                Header(state, signedIn || kind == TrackKind.LOCAL)
            }
            KindTabs(kind) { kind = it }
            val browser = @Composable {
                TrackBrowser(
                    kind, state, selection,
                    onSelectionChange = { selection = it },
                    onSheet = { sheet = it },
                    onToast = { toast(it) },
                    modifier = Modifier.weight(1f),
                )
            }
            // 本機音檔不需要登入
            if (kind == TrackKind.LOCAL) browser() else when (val a = auth) {
                AuthState.Loading -> Unit
                AuthState.SignedOut -> Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) { LoginCard() }
                is AuthState.SignedIn -> if (member == false) NotMemberCard(a.email) else browser()
            }
            if (selection.isNotEmpty() && kind == TrackKind.LOCAL) {
                LocalSelectionActions(
                    onCover = { sheet = Sheet.CoverBatch(Graph.tracks.find(selection)); selection = emptySet() },
                    onPlayed = {
                        val ids = selection
                        scope.launch { ids.forEach { Graph.progress.markPlayed(it, true) } }
                        selection = emptySet(); toast("已標成聽完（${ids.size} 首）")
                    },
                )
            } else if (selection.isNotEmpty()) {
                SelectionActions(
                    onPlaylist = { sheet = Sheet.Playlist(selection.toList()) },
                    onLabels = { sheet = Sheet.Labels(selection) },
                    onPlayed = {
                        val ids = selection
                        scope.launch { ids.forEach { Graph.progress.markPlayed(it, true) } }
                        selection = emptySet(); toast("已標成聽完（${ids.size} 集）")
                    },
                    onDelete = { sheet = Sheet.Delete(Graph.tracks.find(selection)) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp))
    }

    when (val s = sheet) {
        is Sheet.Actions -> {
            val played = Graph.progress.get(s.track.id)?.played == true
            val cloud = s.track.kind != TrackKind.LOCAL
            // 本機音檔不在雲端：不能進雲端同步的清單與標籤，檔案也由手機管理
            TrackActionsSheet(
                s.track, played, onDismiss = { sheet = null },
                onPlay = { Graph.player.playOrToggle(state.items, state.items.indexOf(s.track)) },
                onPlaylist = if (cloud) ({ sheet = Sheet.Playlist(listOf(s.track.id)) }) else null,
                onLabels = if (cloud) ({ sheet = Sheet.Labels(setOf(s.track.id)) }) else null,
                onTogglePlayed = { scope.launch { Graph.progress.markPlayed(s.track.id, !played) } },
                onRename = if (cloud) ({ sheet = Sheet.Rename(s.track) }) else null,
                onDelete = if (cloud) ({ sheet = Sheet.Delete(listOf(s.track)) }) else null,
                onFindCover = if (cloud) null else ({ sheet = Sheet.FindCover(s.track) }),
            )
        }
        is Sheet.FindCover -> CoverSearchSheet(s.track, onDismiss = { sheet = null }, onDone = { toast(it) })
        is Sheet.CoverBatch -> CoverBatchDialog(s.tracks) { sheet = null }
        is Sheet.Labels -> LabelPickerSheet(s.ids, onDismiss = { sheet = null; selection = emptySet() })
        is Sheet.Playlist -> PlaylistPickerSheet(s.ids, onDismiss = { sheet = null; selection = emptySet() }, onDone = { toast(it) })
        is Sheet.Rename -> RenameDialog(s.track, onDismiss = { sheet = null }, onDone = { toast(it) })
        is Sheet.Delete -> DeleteDialog(s.tracks, onDismiss = { sheet = null }) { deleteSource ->
            val ids = s.tracks.map { it.id }
            sheet = null; selection = emptySet()
            scope.launch { deleteWithUndo(ids, deleteSource, snackbar, this) }
        }
        null -> Unit
    }
}

private suspend fun deleteWithUndo(ids: List<String>, deleteSource: Boolean, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val ok = runCatching { Graph.tracks.moveToTrash(ids, deleteSource) }
    if (ok.isFailure) { snackbar.showSnackbar(ok.exceptionOrNull()?.message ?: "刪除失敗"); return }
    val r = snackbar.showSnackbar("已移到垃圾桶（${ids.size} 集）", actionLabel = "復原", duration = SnackbarDuration.Long)
    if (r == SnackbarResult.ActionPerformed) scope.launch { runCatching { Graph.tracks.restore(ids) } }
}

@Composable
private fun Header(state: TrackListState, signedIn: Boolean) {
    val c = Sf.colors
    val total = state.items.sumOf { it.durationSec ?: 0 }
    val status = when {
        !signedIn -> "尚未登入"
        state.loading && state.items.isEmpty() -> "同步中…"
        state.items.isEmpty() -> "沒有音檔"
        else -> "${state.items.size} 集 · ${SfFormat.total(total)} · ${syncedLabel(state.syncedAt)}"
    }
    Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SoundFlow", style = SfType.pageTitle, color = c.text)
            Text(status, style = SfType.number, color = c.muted)
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onAll: () -> Unit, onCancel: () -> Unit) {
    val c = Sf.colors
    Row(
        Modifier.fillMaxWidth().background(c.accentSoft).padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBox(SfIcons.Close, "取消選取", onClick = onCancel)
        Text("已選 $count", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        SfButton("全選", primary = false, onClick = onAll)
    }
}

@Composable
private fun SelectionActions(onPlaylist: () -> Unit, onLabels: () -> Unit, onPlayed: () -> Unit, onDelete: () -> Unit) {
    val c = Sf.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActionTile(SfIcons.Playlist, "加入清單", c.text, Modifier.weight(1f), onPlaylist)
        ActionTile(SfIcons.Tag, "貼標籤", c.text, Modifier.weight(1f), onLabels)
        ActionTile(SfIcons.Check, "標為已聽", c.text, Modifier.weight(1f), onPlayed)
        ActionTile(SfIcons.Trash, "刪除", c.danger, Modifier.weight(1f), onDelete)
    }
}

@Composable
private fun LocalSelectionActions(onCover: () -> Unit, onPlayed: () -> Unit) {
    val c = Sf.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ActionTile(SfIcons.Image, "找封面", c.text, Modifier.weight(1f), onCover)
        ActionTile(SfIcons.Check, "標為已聽", c.text, Modifier.weight(1f), onPlayed)
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.height(54.dp).clip(shape).border(1.dp, if (color == c.danger) c.dangerLine else c.border, shape).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, style = SfType.meta, color = color)
    }
}

@Composable
private fun IconBox(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val c = Sf.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (active) c.accentSoft else Color.Transparent)
            .border(1.dp, if (active) c.accentLine else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (active) c.accentText else c.text, modifier = Modifier.size(20.dp)) }
}

private fun syncedLabel(at: Instant?): String {
    if (at == null) return "離線（顯示上次的資料）"
    val min = Duration.between(at, Instant.now()).toMinutes()
    return if (min < 1) "剛剛同步" else "$min 分鐘前同步"
}

@Composable
private fun KindTabs(kind: TrackKind, onChange: (TrackKind) -> Unit) {
    val c = Sf.colors
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(TrackKind.PODCAST to "Podcast", TrackKind.YOUTUBE to "YouTube", TrackKind.LOCAL to "本機").forEach { (k, label) ->
            val sel = k == kind
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.accent else Color.Transparent).clickable(role = Role.Tab) { onChange(k) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) c.onAccent else c.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 篩選列＋排序／檢視切換＋清單（F30、F31、F51、F52） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackBrowser(
    kind: TrackKind,
    state: TrackListState,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onSheet: (Sheet) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier,
) {
    val isLocal = kind == TrackKind.LOCAL
    val localState by Graph.local.state.collectAsState()
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = LocalDate.now(ZoneId.of("Asia/Taipei"))
    val prefs by Graph.settings.viewPrefs(kind).collectAsState(initial = SettingsRepository.ViewPrefs())
    val progress by Graph.progress.state.collectAsState()
    val lib by Graph.library.state.collectAsState()
    val nowPlaying by Graph.player.state.collectAsState()
    var filter by rememberSaveable(kind) { mutableStateOf(ListFilter.ALL) }
    var labelFilter by rememberSaveable(kind) { mutableStateOf(setOf<String>()) }
    var collapsed by remember(kind) { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }

    val base = if (isLocal && folder != null) state.items.filter { it.localFolder == folder } else state.items
    val visible = LibraryLogic.sort(LibraryLogic.filter(base, filter, labelFilter, lib.trackLabels, progress), prefs.sort, progress)
    val groups = if (prefs.groupByLabel) LibraryLogic.group(visible, lib.labels, lib.trackLabels) else null
    val labelsById = lib.labels.associateBy { it.id }
    val selecting = selection.isNotEmpty()

    // Android 13+ 要通知權限，通知列才看得到播放控制；拒絕也照樣能播
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun play(t: Track) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Graph.player.playOrToggle(visible, visible.indexOf(t))
    }
    fun tap(t: Track) {
        if (selecting) onSelectionChange(if (t.id in selection) selection - t.id else selection + t.id) else play(t)
    }
    fun longPress(t: Track) = onSelectionChange(selection + t.id)
    fun setPrefs(p: SettingsRepository.ViewPrefs) = scope.launch { Graph.settings.setViewPrefs(kind, p) }

    if (isLocal && localState.folders.isEmpty()) {
        LocalEmpty(localState.scanning, modifier, onToast)
        return
    }
    Column(modifier) {
        if (isLocal) LocalFolderBar(localState.folders, folder, base, { folder = it }, onToast) { onSheet(Sheet.CoverBatch(base)) }
        // 篩選：收聽狀態＋顏色標籤
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ListFilter.entries.forEach { f -> Chip(f.label, selected = filter == f) { filter = f } }
            if (!isLocal && lib.labels.isNotEmpty()) Box(Modifier.width(1.dp).height(32.dp).background(Sf.colors.border))
            if (!isLocal) lib.labels.forEach { l ->
                SelectableLabel(l, l.id in labelFilter) { labelFilter = if (l.id in labelFilter) labelFilter - l.id else labelFilter + l.id }
            }
        }
        // 排序與檢視方式
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { sortMenu = true }.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(SfIcons.Sort, contentDescription = null, tint = Sf.colors.muted, modifier = Modifier.size(18.dp))
                    Text(prefs.sort.label, style = SfType.body, color = Sf.colors.text)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, containerColor = Sf.colors.surface2) {
                    SortKey.entries.forEach { k ->
                        listOf(true, false).forEach { desc ->
                            val spec = SortSpec(k, desc)
                            DropdownMenuItem(
                                text = { Text(spec.label, color = if (spec == prefs.sort) Sf.colors.accentText else Sf.colors.text) },
                                onClick = { sortMenu = false; setPrefs(prefs.copy(sort = spec)) },
                            )
                        }
                    }
                }
            }
            Text("${visible.size} 集", style = SfType.number, color = Sf.colors.muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
            IconBox(SfIcons.Grid, "兩欄網格", active = prefs.mode == ViewMode.GRID) { setPrefs(prefs.copy(mode = ViewMode.GRID)) }
            IconBox(SfIcons.ListRows, "精簡列表", active = prefs.mode == ViewMode.LIST) { setPrefs(prefs.copy(mode = ViewMode.LIST)) }
            if (!isLocal) IconBox(SfIcons.Layers, "依標籤分組", active = prefs.groupByLabel) { setPrefs(prefs.copy(groupByLabel = !prefs.groupByLabel)) }
        }

        PullToRefreshBox(
            isRefreshing = state.loading || (isLocal && localState.scanning),
            onRefresh = { scope.launch { if (isLocal) Graph.local.rescan() else { Graph.tracks.refresh(kind); Graph.library.refresh() } } },
            modifier = Modifier.fillMaxSize(),
        ) {
            val list = prefs.mode == ViewMode.LIST
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (list) 1 else 2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(if (list) 6.dp else 10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (kind == TrackKind.YOUTUBE && !selecting) item(span = { GridItemSpan(maxLineSpan) }) { JobsPanel() }
                localState.error?.takeIf { isLocal }?.let { msg -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorBanner(msg) { scope.launch { Graph.local.rescan() } } } }
                state.error?.let { msg -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorBanner(msg) { scope.launch { Graph.tracks.refresh(kind) } } } }
                lib.error?.let { msg -> item(span = { GridItemSpan(maxLineSpan) }) { ErrorBanner(msg) { Graph.library.clearError(); scope.launch { Graph.library.refresh() } } } }
                if (state.items.isEmpty() && !state.loading && state.error == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (isLocal) {
                            EmptyCard(if (localState.scanning) "掃描中…（${localState.scanned} 首）" else "這些資料夾裡沒有音檔", "支援 MP3、M4A、FLAC、OGG、Opus、WAV。往下拉可重新掃描。")
                        } else if (kind == TrackKind.PODCAST) {
                            EmptyCard("還沒有 Podcast", "在新知雷達網站按「送進 NotebookLM」，產生完就會出現在這裡（往下拉可重新整理）。")
                        } else {
                            EmptyCard("在 YouTube 按「分享」送過來", "影片下方點「分享」→ 選「SoundFlow 轉成音檔」，N100 轉好後（通常幾分鐘）出現在這裡。")
                        }
                    }
                } else if (visible.isEmpty() && state.items.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { EmptyCard("沒有符合的音檔", "換一個篩選條件或顏色標籤試試。") }
                }

                fun LazyGridScope.tracks(groupKey: String, items: List<Track>) = items(items, key = { "$groupKey/${it.id}" }) { t ->
                    val p = progress[t.id]
                    val labels = lib.trackLabels[t.id].orEmpty().mapNotNull { labelsById[it] }
                    val playing = nowPlaying.currentId == t.id
                    val sel = t.id in selection
                    if (list) TrackRow(t, today, p, labels, playing, selecting, sel, { tap(t) }, { longPress(t) }, { onSheet(Sheet.Actions(t)) })
                    else TrackCard(t, today, p, labels, playing, selecting, sel, { tap(t) }, { longPress(t) }, { onSheet(Sheet.Actions(t)) })
                }

                if (groups == null) {
                    tracks("all", visible)
                } else {
                    groups.forEach { g ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header/${g.key}") {
                            GroupHeader(g.label, g.title, g.tracks.size, g.key in collapsed) {
                                collapsed = if (g.key in collapsed) collapsed - g.key else collapsed + g.key
                            }
                        }
                        if (g.key !in collapsed) tracks(g.key, g.tracks)
                    }
                }
            }
        }
    }
}

/** 還沒匯入任何資料夾（設計畫布第 7 列） */
@Composable
private fun LocalEmpty(scanning: Boolean, modifier: Modifier, onToast: (String) -> Unit) {
    val c = Sf.colors
    val pick = rememberFolderPicker(onToast)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(SfIcons.Folder, contentDescription = null, tint = c.accentText, modifier = Modifier.size(44.dp))
        Text("還沒有匯入資料夾", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("一次選一個資料夾，裡面的音檔才會出現在這裡。沒匯入的檔案 SoundFlow 看不到。", style = SfType.body, color = c.muted)
        SfButton(if (scanning) "掃描中…" else "匯入資料夾", primary = true, enabled = !scanning, onClick = pick)
    }
}

@Composable
private fun rememberFolderPicker(onToast: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { onToast(runCatching { Graph.local.addFolder(uri) }.getOrElse { "這個資料夾無法匯入" }) }
    }
    return { launcher.launch(null) }
}

/** 資料夾切換＋再匯入＋找封面；長按資料夾可重新掃描或移除 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalFolderBar(
    folders: List<LocalFolder>, selected: String?, shown: List<Track>, onSelect: (String?) -> Unit, onToast: (String) -> Unit, onBatch: () -> Unit,
) {
    val c = Sf.colors
    val scope = rememberCoroutineScope()
    val pick = rememberFolderPicker(onToast)
    val version by Graph.covers.version.collectAsState()
    val batch by Graph.coverSearch.batch.collectAsState()
    val missing = remember(shown, version) { shown.count { Graph.covers.fileFor(it.id) == null } }
    var menu by remember { mutableStateOf<String?>(null) }
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip("全部", selected = selected == null) { onSelect(null) }
            folders.forEach { f ->
                val on = f.uri == selected
                val shape = RoundedCornerShape(8.dp)
                Box {
                    Box(
                        Modifier.height(32.dp).clip(shape).background(if (on) c.text else Color.Transparent).border(1.dp, if (on) c.text else c.border, shape)
                            .combinedClickable(onLongClickLabel = "資料夾動作", onClick = { onSelect(if (on) null else f.uri) }, onLongClick = { menu = f.uri })
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(f.name, style = SfType.body, color = if (on) c.bg else c.muted, maxLines = 1) }
                    DropdownMenu(expanded = menu == f.uri, onDismissRequest = { menu = null }, containerColor = c.surface2) {
                        DropdownMenuItem(text = { Text("重新掃描", color = c.text) }, onClick = { menu = null; scope.launch { Graph.local.rescan() } })
                        DropdownMenuItem(text = { Text("移除資料夾（不刪手機檔案）", color = c.danger) }, onClick = {
                            menu = null
                            if (on) onSelect(null)
                            scope.launch { Graph.local.removeFolder(f.uri); onToast("已移除「${f.name}」，手機裡的檔案沒有動") }
                        })
                    }
                }
            }
            Chip("＋ 匯入", selected = false, onClick = pick)
            val b = batch
            if (b?.running == true) Chip("找封面中 ${b.done}/${b.total}", selected = false, onClick = onBatch)
            else if (missing > 0 || b != null) Chip(if (b != null) "找封面結果" else "找封面（$missing）", selected = false, onClick = onBatch)
        }
        Text("長按資料夾可重新掃描或移除。", style = SfType.meta, color = c.muted, modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp))
    }
}

@Composable
private fun GroupHeader(label: Label?, title: String, count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    val c = Sf.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClickLabel = if (collapsed) "展開" else "收合", onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (label != null) LabelChip(label, small = false) else Text(title, style = SfType.rowTitle, color = c.muted)
        Text("$count 集", style = SfType.number, color = c.muted, modifier = Modifier.weight(1f))
        Icon(if (collapsed) SfIcons.ArrowDown else SfIcons.ArrowUp, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Cover(t: Track, modifier: Modifier, tagSize: Int) {
    val style = SourceStyle.of(t.channel)
    val cover = rememberCover(t.id, t.thumbnailUrl)
    Box(modifier.background(SfSemantic.cover(style.hue).toColor()), contentAlignment = Alignment.Center) {
        if (cover != null) Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(style.tag, color = Color.White.copy(alpha = 0.9f), fontSize = tagSize.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    val c = Sf.colors
    if (selected) {
        Box(modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(SfIcons.Check, contentDescription = "已選取", tint = c.onAccent, modifier = Modifier.size(14.dp))
        }
    } else {
        Box(modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).border(2.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(11.dp)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCard(
    t: Track, today: LocalDate, p: TrackProgress?, labels: List<Label>, playing: Boolean, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onMore: () -> Unit,
) {
    val c = Sf.colors
    val style = SourceStyle.of(t.channel)
    val shape = RoundedCornerShape(14.dp)
    val hot = selected || playing
    val cover = rememberCover(t.id, t.thumbnailUrl)
    Column(
        Modifier.clip(shape).background(if (hot) c.accentSoft else c.surface).border(1.dp, if (hot) c.accent else c.border, shape)
            .combinedClickable(onClickLabel = if (selecting) "選取" else "播放", onLongClickLabel = "多選", onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp).background(SfSemantic.cover(style.hue).toColor())) {
            if (cover != null) {
                Image(cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            }
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                Text(style.label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart))
                if (selecting) SelectMark(selected, Modifier.align(Alignment.TopEnd))
                else if (p?.played == true) Icon(SfIcons.Check, contentDescription = "已聽完", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
                if (cover == null) Text(style.tag, color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
                Text(
                    SfFormat.duration(t.durationSec), style = SfType.number, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (p != null && p.inProgress) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p.fraction).height(3.dp).background(c.accent))
        }
        Column(Modifier.padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                t.title, style = SfType.cardTitle, color = if (p?.played == true) c.muted else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 35.dp).padding(end = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${style.label} · ${SfFormat.relativeDate(t.createdAt, today)}", style = SfType.meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!selecting) IconBox(SfIcons.More, "更多動作", onClick = onMore)
            }
            if (labels.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { labels.take(2).forEach { LabelChip(it) }; if (labels.size > 2) Text("+${labels.size - 2}", style = SfType.meta, color = c.muted) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    t: Track, today: LocalDate, p: TrackProgress?, labels: List<Label>, playing: Boolean, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onMore: () -> Unit,
) {
    val c = Sf.colors
    val style = SourceStyle.of(t.channel)
    val shape = RoundedCornerShape(12.dp)
    val hot = selected || playing
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (hot) c.accentSoft else c.surface).border(1.dp, if (hot) c.accent else c.border, shape)
            .combinedClickable(onClickLabel = if (selecting) "選取" else "播放", onLongClickLabel = "多選", onClick = onClick, onLongClick = onLongClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // YouTube 封面是 16:9，其他是正方形
        val coverW = if (t.kind == TrackKind.YOUTUBE) 85 else 48
        Box {
            Cover(t, Modifier.width(coverW.dp).height(48.dp).clip(RoundedCornerShape(10.dp)), 15)
            if (selecting) SelectMark(selected, Modifier.align(Alignment.Center))
            if (p != null && p.inProgress) Box(Modifier.align(Alignment.BottomStart).width((coverW * p.fraction).dp).height(3.dp).background(c.accent))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(t.title, style = SfType.cardTitle, color = if (p?.played == true) c.muted else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p?.played == true) Icon(SfIcons.Check, contentDescription = "已聽完", tint = c.muted, modifier = Modifier.size(12.dp))
                Text(
                    "${style.label} · ${SfFormat.relativeDate(t.createdAt, today)} · ${SfFormat.duration(t.durationSec)}",
                    style = SfType.meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                labels.take(2).forEach { LabelChip(it) }
            }
        }
        if (!selecting) IconBox(SfIcons.More, "更多動作", onClick = onMore)
    }
}

/** 登入的信箱不在 sf_member 白名單：讀到的都是空的、送出會被拒，直接講清楚 */
@Composable
private fun NotMemberCard(email: String) {
    val c = Sf.colors
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.padding(12.dp).fillMaxWidth().clip(shape).background(c.dangerSoft).border(1.dp, c.dangerLine, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("這個信箱還沒開通 SoundFlow", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "目前登入的是 $email，它不在 SoundFlow 的白名單，所以看不到 Podcast，也不能送出 YouTube 影片。" +
                "請登出改用已開通的信箱，或在 Supabase 把這個信箱加進 sf_member。",
            color = c.text, style = SfType.body,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SfButton("重新檢查", primary = false, onClick = { scope.launch { Graph.auth.checkMembership() } })
            SfButton("登出", primary = true, onClick = { scope.launch { Graph.auth.signOut(); Graph.tracks.clear(); Graph.library.clear() } }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.dangerSoft).border(1.dp, c.dangerLine, shape).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = c.danger, style = SfType.body, modifier = Modifier.weight(1f))
        SfButton("重試", primary = false, onClick = onRetry)
    }
}

@Composable
fun EmptyCard(title: String, body: String) {
    val c = Sf.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SfIcons.Wave, contentDescription = null, tint = c.accent, modifier = Modifier.size(56.dp))
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(body, color = c.muted, style = SfType.body)
    }
}
