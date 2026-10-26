package tw.hoonsor.soundflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.Label
import tw.hoonsor.soundflow.data.LibraryLogic
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfType
import java.time.Instant

@Composable
private fun SubHeader(title: String, sub: String?, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    val c = Sf.colors
    Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallIcon(SfIcons.Back, "返回", onClick = onBack)
        Column(Modifier.weight(1f)) {
            Text(title, style = SfType.pageTitle, color = c.text)
            sub?.let { Text(it, style = SfType.body, color = c.muted) }
        }
        action?.invoke()
    }
}

/**
 * 垃圾桶（F32）：7 天後 N100 自動永久刪除（連雲端實體檔）；可還原或立即永久刪除。
 * Podcast 依刪除時的選擇：只從 SoundFlow 移除，或連新知雷達的音檔一起刪。
 */
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val c = Sf.colors
    val trash by Graph.tracks.trash.collectAsState()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<List<Track>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { Graph.tracks.refreshTrash() }

    Column(Modifier.fillMaxSize()) {
        SubHeader("垃圾桶", "保留 ${LibraryLogic.TRASH_DAYS} 天，之後連雲端音檔一起永久刪除", onBack) {
            if (trash.items.isNotEmpty()) SfButton("全部清空", primary = false, onClick = { confirm = trash.items })
        }
        message?.let { Text(it, style = SfType.body, color = c.accentText, modifier = Modifier.padding(horizontal = 16.dp)) }
        trash.error?.let { Text(it, style = SfType.body, color = c.danger, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (trash.items.isEmpty() && !trash.loading) {
            Column(Modifier.padding(12.dp)) { EmptyCard("垃圾桶是空的", "刪除的音檔會在這裡保留 ${LibraryLogic.TRASH_DAYS} 天。") }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(trash.items, key = { it.id }) { t ->
                val days = t.deletedAt?.let { LibraryLogic.trashDaysLeft(it, Instant.now()) } ?: LibraryLogic.TRASH_DAYS
                val what = when {
                    t.kind == TrackKind.YOUTUBE -> "會刪除音檔"
                    t.deleteSource -> "會連新知雷達的音檔一起刪"
                    else -> "只從 SoundFlow 移除"
                }
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = SfType.cardTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${SourceStyle.of(t.channel).label} · 剩 $days 天 · $what", style = SfType.meta, color = c.muted)
                    }
                    SfButton("還原", primary = false, onClick = {
                        scope.launch { runCatching { Graph.tracks.restore(listOf(t.id)) }.onSuccess { message = "已還原「${t.title}」" }.onFailure { message = it.message } }
                    })
                    SmallIcon(SfIcons.Trash, "立即永久刪除", tint = c.danger) { confirm = listOf(t) }
                }
            }
        }
    }
    confirm?.let { list ->
        AlertDialog(
            onDismissRequest = { confirm = null }, containerColor = c.surface,
            title = { Text(if (list.size == 1) "永久刪除這集？" else "永久刪除 ${list.size} 集？", color = c.text) },
            text = { Text("N100 會在幾十秒內刪除雲端的音檔，之後無法還原。", color = c.text, style = SfType.body) },
            confirmButton = {
                SfButton("永久刪除", primary = true, onClick = {
                    confirm = null
                    scope.launch { runCatching { Graph.tracks.purgeNow(list.map { it.id }) }.onSuccess { message = "已送出永久刪除（${list.size} 集）" }.onFailure { message = it.message } }
                })
            },
            dismissButton = { SfButton("取消", primary = false, onClick = { confirm = null }) },
        )
    }
}

/** 標籤管理（F52）：新增、改名、改色、刪除 */
@Composable
fun LabelsScreen(onBack: () -> Unit) {
    val c = Sf.colors
    val lib by Graph.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Label?>(null) }
    var creating by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { Graph.library.refresh() }

    Column(Modifier.fillMaxSize()) {
        SubHeader("顏色標籤", "用來分類與篩選；一集可以貼多個", onBack) { SfButton("＋ 新增", primary = false, onClick = { creating = true }) }
        lib.error?.let { Text(it, style = SfType.body, color = c.danger, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (lib.labels.isEmpty()) Column(Modifier.padding(12.dp)) { EmptyCard("還沒有標籤", "按「＋ 新增」建立，例如「AI 工具」「健身」「英文」。") }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lib.labels, key = { it.id }) { l ->
                val count = lib.trackLabels.count { l.id in it.value }
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape)
                        .clickable(role = Role.Button, onClickLabel = "編輯") { editing = l }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LabelChip(l, small = false)
                    Text("$count 集", style = SfType.number, color = c.muted, modifier = Modifier.weight(1f))
                    Text("編輯", style = SfType.body, color = c.accentText)
                }
            }
        }
    }
    if (creating) LabelEditDialog(null, onDismiss = { creating = false }, onSave = { n, col -> creating = false; scope.launch { Graph.library.createLabel(n, col) } })
    editing?.let { l ->
        LabelEditDialog(
            l, onDismiss = { editing = null },
            onSave = { n, col -> editing = null; scope.launch { Graph.library.updateLabel(l.id, n, col) } },
            onDelete = { editing = null; scope.launch { Graph.library.deleteLabel(l.id) } },
        )
    }
}
