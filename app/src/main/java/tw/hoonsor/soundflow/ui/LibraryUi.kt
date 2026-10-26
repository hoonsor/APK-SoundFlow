package tw.hoonsor.soundflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.Label
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfLabelColors
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.toColor

/** 小標籤（卡片、列表上顯示） */
@Composable
fun LabelChip(label: Label, small: Boolean = true) {
    val lc = SfLabelColors.of(label.color)
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(lc.bg.toColor()).padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(if (small) 6.dp else 8.dp).clip(RoundedCornerShape(4.dp)).background(lc.dot.toColor()))
        Text(label.name, color = lc.text.toColor(), fontSize = if (small) 10.sp else 13.sp, maxLines = 1)
    }
}

/** 可點選的篩選／選擇用標籤 */
@Composable
fun SelectableLabel(label: Label, selected: Boolean, onClick: () -> Unit) {
    val lc = SfLabelColors.of(label.color)
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(32.dp).clip(shape).background(if (selected) lc.bg.toColor() else Color.Transparent)
            .border(1.dp, if (selected) lc.dot.toColor() else Sf.colors.border, shape)
            .clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(lc.dot.toColor()))
        Text(label.name, color = if (selected) lc.text.toColor() else Sf.colors.text, style = SfType.body, maxLines = 1)
    }
}

@Composable
fun SfTextField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = Sf.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) }, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.border, focusedTextColor = c.text, unfocusedTextColor = c.text,
            cursorColor = c.accent, focusedLabelColor = c.accentText, unfocusedLabelColor = c.muted,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 新增或編輯標籤：名稱＋8 色色票，即時預覽 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelEditDialog(initial: Label?, onDismiss: () -> Unit, onSave: (name: String, color: String) -> Unit, onDelete: (() -> Unit)? = null) {
    val c = Sf.colors
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: SfLabelColors.all.first().key) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text(if (initial == null) "新增標籤" else "編輯標籤", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SfTextField(name, { name = it.take(30) }, "標籤名稱")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SfLabelColors.all.forEach { lc ->
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(lc.bg.toColor())
                                .border(2.dp, if (lc.key == color) lc.dot.toColor() else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable(role = Role.RadioButton, onClickLabel = lc.name) { color = lc.key },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(lc.dot.toColor())) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("預覽", style = SfType.body, color = c.muted)
                    LabelChip(Label("", name.ifBlank { "標籤" }, color, 0), small = false)
                }
                if (onDelete != null) {
                    Text("刪除標籤", color = c.danger, style = SfType.body, modifier = Modifier.clickable(role = Role.Button, onClick = onDelete).padding(vertical = 6.dp))
                }
            }
        },
        confirmButton = { SfButton("儲存", primary = true, enabled = name.isNotBlank(), onClick = { onSave(name, color) }) },
        dismissButton = { SfButton("取消", primary = false, onClick = onDismiss) },
    )
}

/** 幫一或多集貼／撕標籤；也可以直接新增標籤 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LabelPickerSheet(trackIds: Set<String>, onDismiss: () -> Unit) {
    val c = Sf.colors
    val lib by Graph.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (trackIds.size == 1) "顏色標籤" else "幫 ${trackIds.size} 集貼標籤", style = SfType.rowTitle, color = c.text)
            Text("點一下貼上，再點一下撕下；可以貼多個。", style = SfType.body, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lib.labels.forEach { l ->
                    // 多選時：全部都有才算「已貼」
                    val on = trackIds.isNotEmpty() && trackIds.all { l.id in lib.trackLabels[it].orEmpty() }
                    SelectableLabel(l, on) { scope.launch { Graph.library.setLabel(trackIds, l.id, !on) } }
                }
                Chip("＋ 新增標籤", selected = false) { creating = true }
            }
            lib.error?.let { Text(it, color = c.danger, style = SfType.body) }
            SfButton("完成", primary = true, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
    if (creating) {
        LabelEditDialog(null, onDismiss = { creating = false }, onSave = { name, color ->
            creating = false
            scope.launch { Graph.library.createLabel(name, color)?.let { Graph.library.setLabel(trackIds, it.id, true) } }
        })
    }
}

/** 把一或多集加進播放清單；也可以直接新增清單 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(trackIds: List<String>, onDismiss: () -> Unit, onDone: (String) -> Unit = {}) {
    val c = Sf.colors
    val lib by Graph.library.state.collectAsState()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (trackIds.size == 1) "加入播放清單" else "把 ${trackIds.size} 集加入播放清單", style = SfType.rowTitle, color = c.text)
            lib.playlists.forEach { p ->
                val has = trackIds.all { it in p.trackIds }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, enabled = !has) {
                        scope.launch { Graph.library.addToPlaylist(p.id, trackIds); onDone("已加入「${p.name}」") }
                        onDismiss()
                    }.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(SfIcons.Playlist, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
                    Text(p.name, style = SfType.rowTitle, color = c.text, modifier = Modifier.weight(1f))
                    Text(if (has) "已在清單中" else "${p.trackIds.size} 集", style = SfType.meta, color = c.muted)
                }
            }
            if (lib.playlists.isEmpty()) Text("還沒有播放清單。", style = SfType.body, color = c.muted)
            SfButton("＋ 新增播放清單", primary = false, onClick = { creating = true }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (creating) {
        NameDialog("新增播放清單", "", "清單名稱", onDismiss = { creating = false }) { name ->
            creating = false
            scope.launch {
                Graph.library.createPlaylist(name)?.let { Graph.library.addToPlaylist(it.id, trackIds); onDone("已加入「${it.name}」") }
            }
            onDismiss()
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, fieldLabel: String, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null, onSave: (String) -> Unit) {
    val c = Sf.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text(title, color = c.text) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { SfTextField(text, { text = it.take(200) }, fieldLabel); extra?.invoke() } },
        confirmButton = { SfButton("儲存", primary = true, enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) },
        dismissButton = { SfButton("取消", primary = false, onClick = onDismiss) },
    )
}

/** 改名（F05、F06）：只改 SoundFlow 的名稱；可還原原標題 */
@Composable
fun RenameDialog(track: Track, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = Sf.colors
    val scope = rememberCoroutineScope()
    NameDialog(
        "重新命名", track.title, "顯示名稱", onDismiss,
        extra = {
            Text("原標題：${track.originalTitle}", style = SfType.meta, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (track.title != track.originalTitle) {
                Text("還原原標題", color = c.accentText, style = SfType.body, modifier = Modifier.clickable(role = Role.Button) {
                    scope.launch { runCatching { Graph.tracks.rename(track, null) }.onSuccess { onDone("已還原原標題") }.onFailure { onDone(it.message ?: "還原失敗") } }
                    onDismiss()
                }.padding(vertical = 6.dp))
            }
        },
    ) { name ->
        scope.launch { runCatching { Graph.tracks.rename(track, name) }.onSuccess { onDone("已改名") }.onFailure { onDone(it.message ?: "改名失敗") } }
        onDismiss()
    }
}

/**
 * 刪除確認（F32）：進垃圾桶 7 天後永久刪除。
 * 有 Podcast 時讓倬選「只從 SoundFlow 移除」或「連新知雷達的音檔一起刪」（倬 2026-10-26 決定）。
 */
@Composable
fun DeleteDialog(tracks: List<Track>, onDismiss: () -> Unit, onConfirm: (deleteSource: Boolean) -> Unit) {
    val c = Sf.colors
    val hasPodcast = tracks.any { it.kind == TrackKind.PODCAST }
    var deleteSource by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text(if (tracks.size == 1) "刪除這集？" else "刪除 ${tracks.size} 集？", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("會先移到垃圾桶，7 天內都能還原；7 天後連雲端的音檔一起永久刪除。", style = SfType.body, color = c.text)
                if (hasPodcast) {
                    Text("Podcast 是新知雷達產生的，永久刪除時要：", style = SfType.body, color = c.muted)
                    ChoiceRow("只從 SoundFlow 移除", "新知雷達網站上照常可以播", selected = !deleteSource) { deleteSource = false }
                    ChoiceRow("連新知雷達的音檔一起刪", "網站上也聽不到，最省空間", selected = deleteSource) { deleteSource = true }
                }
            }
        },
        confirmButton = {
            Box(
                Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(c.danger)
                    .clickable(role = Role.Button) { onConfirm(deleteSource) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text("移到垃圾桶", color = c.bg, style = SfType.rowTitle, fontSize = 14.sp) }
        },
        dismissButton = { SfButton("取消", primary = false, onClick = onDismiss) },
    )
}

@Composable
private fun ChoiceRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) c.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) c.accentLine else c.border, shape).clickable(role = Role.RadioButton, onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).border(2.dp, if (selected) c.accent else c.muted, RoundedCornerShape(9.dp)).padding(4.dp)) {
            if (selected) Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(c.accent))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = SfType.rowTitle, fontSize = 14.sp, color = c.text)
            Text(sub, style = SfType.meta, color = c.muted)
        }
    }
}

/** 單集的「更多」選單：與多選的批次動作一致（倬的 UX 預設） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    played: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlaylist: () -> Unit,
    onLabels: () -> Unit,
    onTogglePlayed: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Sf.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            Text(track.title, style = SfType.rowTitle, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
            listOf(
                Triple(SfIcons.Play, "播放", onPlay),
                Triple(SfIcons.Playlist, "加入播放清單", onPlaylist),
                Triple(SfIcons.Tag, "顏色標籤", onLabels),
                Triple(SfIcons.Check, if (played) "標為未聽" else "標為已聽完", onTogglePlayed),
                Triple(SfIcons.Pencil, "重新命名", onRename),
            ).forEach { (icon, label, action) -> SheetRow(icon, label, c.text) { onDismiss(); action() } }
            SheetRow(SfIcons.Trash, "移到垃圾桶", c.danger) { onDismiss(); onDelete() }
        }
    }
}

@Composable
private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, style = SfType.rowTitle)
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Sf.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.height(32.dp).clip(shape).background(if (selected) c.text else Color.Transparent)
            .border(1.dp, if (selected) c.text else c.border, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = SfType.body, color = if (selected) c.bg else c.muted, maxLines = 1) }
}
