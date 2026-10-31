package tw.hoonsor.soundflow.ui

import android.content.Intent
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.CoverBitmaps
import tw.hoonsor.soundflow.data.CoverLogic
import tw.hoonsor.soundflow.data.CoverResult
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfType

private val bitmaps = LruCache<String, ImageBitmap>(80)

/** 讀封面（小圖，列表與播放頁共用）；YouTube 還沒下載過就順便下載 */
@Composable
fun rememberCover(id: String?, thumbnailUrl: String? = null): ImageBitmap? {
    val version by Graph.covers.version.collectAsState()
    val cached = remember(id, version) { id?.let { Graph.covers.fileFor(it) }?.let { bitmaps.get(it.path + it.lastModified()) } }
    val image by produceState(cached, id, version) {
        if (id == null) { value = null; return@produceState }
        val file = Graph.covers.fileFor(id)
        if (file == null) {
            value = null
            thumbnailUrl?.let { Graph.covers.ensureThumb(id, it) }
            return@produceState
        }
        val key = file.path + file.lastModified()
        value = bitmaps.get(key) ?: withContext(Dispatchers.IO) { CoverBitmaps.decodeFile(file.path, 320)?.asImageBitmap() }?.also { bitmaps.put(key, it) }
    }
    return image
}

/** 搜尋結果的預覽圖 */
@Composable
private fun rememberRemoteImage(url: String): ImageBitmap? {
    val image by produceState(bitmaps.get(url), url) {
        if (value != null) return@produceState
        value = Graph.covers.previewBytes(url)?.let { bytes -> withContext(Dispatchers.IO) { CoverBitmaps.decode(bytes, 240)?.asImageBitmap() } }?.also { bitmaps.put(url, it) }
    }
    return image
}

/** 幫一首本機音檔找封面：先給關鍵字、看候選圖，點一張才套用（F56） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverSearchSheet(track: Track, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = Sf.colors
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(Graph.coverSearch.defaultQuery(track)) }
    var results by remember { mutableStateOf<List<CoverResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val hasFound = remember { Graph.covers.hasFound(track.id) }

    fun search() {
        val q = query.trim()
        searching = true; failed = false
        job?.cancel()
        job = scope.launch {
            val r = Graph.coverSearch.searchWide(q)
            failed = r == null
            results = r
            searching = false
            val more = Graph.coverSearch.searchMusicBrainz(q)
            if (more.isNotEmpty()) results = (results.orEmpty() + more).distinctBy { it.imageUrl }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            onDismiss()
            Graph.appScope.launch {
                val ok = Graph.covers.read(uri)?.let { Graph.covers.saveFoundBytes(track.id, it) } == true
                onDone(if (ok) "已加上封面" else "這張圖片無法使用")
            }
        }
    }
    // 從瀏覽器分享圖片回來、設好封面後，這個面板就不用留著了
    val version by Graph.covers.version.collectAsState()
    val coverAtOpen = remember { Graph.covers.fileFor(track.id)?.lastModified() }
    LaunchedEffect(version) { if (Graph.covers.fileFor(track.id)?.lastModified() != coverAtOpen) onDismiss() }

    // 標籤完整的音檔直接查；只有檔名的（可能是錄音）等倬看過關鍵字才送出
    LaunchedEffect(Unit) { if (Graph.local.entry(track.id)?.tagged == true) search() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("幫這首找封面", style = SfType.rowTitle, color = c.text)
            Text("搜尋時只把下面的文字送去 Apple、Deezer 與 MusicBrainz，音檔不會上傳。", style = SfType.body, color = c.muted)
            SfTextField(query, { query = it.take(100) }, "歌手 歌名")
            SfButton(if (searching) "搜尋中…" else "搜尋", primary = true, enabled = !searching && query.isNotBlank(), onClick = { search() }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SfButton("到 Google 找圖", primary = false, enabled = query.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                    Graph.coverSearch.setPending(track)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CoverLogic.googleImagesUrl(query.trim())))) }
                        .onFailure { onDone("手機上找不到瀏覽器") }
                })
                SfButton("從手機選圖", primary = false, modifier = Modifier.weight(1f), onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            Text("Google：在圖片上長按 → 分享 → 選 SoundFlow 封面。沒有分享選項就先下載，再按「從手機選圖」。", style = SfType.meta, color = c.muted)
            if (failed) Text("連不上搜尋服務，請確認網路後再試一次。", style = SfType.body, color = c.danger)
            results?.let { list ->
                if (list.isEmpty()) Text("沒有找到，試試改短一點的關鍵字。", style = SfType.body, color = c.muted)
                list.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { r ->
                            ResultTile(r, Modifier.weight(1f)) {
                                onDismiss()
                                Graph.appScope.launch { onDone(if (Graph.covers.saveFound(track.id, r.imageUrl)) "已加上封面" else "下載封面失敗，請再試一次") }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (hasFound) {
                Text("拿掉找到的封面，回到原狀", color = c.danger, style = SfType.body, modifier = Modifier.clickable(role = Role.Button) {
                    Graph.covers.removeFound(listOf(track.id)); onDone("已拿掉封面"); onDismiss()
                }.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ResultTile(r: CoverResult, modifier: Modifier, onClick: () -> Unit) {
    val c = Sf.colors
    val image = rememberRemoteImage(r.imageUrl)
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.clip(shape).clickable(role = Role.Button, onClickLabel = "用這張封面", onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(c.surface2).border(1.dp, c.border, shape)) {
            image?.let { Image(it, contentDescription = "${r.artist} ${r.album}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(r.album.ifBlank { r.title }, style = SfType.meta, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        Text("${r.artist} · ${r.source}", style = SfType.meta, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 批次找封面（F56）：只處理還沒有封面、標籤完整的音檔；歌名與歌手都對得上才加。
 * 在背景跑，關掉這個視窗也會繼續；結束後可以一鍵復原這一批。
 */
@Composable
fun CoverBatchDialog(tracks: List<Track>, onDismiss: () -> Unit) {
    val c = Sf.colors
    val batch by Graph.coverSearch.batch.collectAsState()
    val missing = remember(tracks) { tracks.filter { Graph.covers.fileFor(it.id) == null } }
    val tagged = remember(missing) { missing.count { Graph.local.entry(it.id)?.tagged == true } }
    val b = batch
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface,
        title = { Text("批次找封面", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    b == null -> {
                        Text("${missing.size} 首沒有封面，其中 $tagged 首有完整的歌名與歌手標籤，會自動查詢；歌名和歌手都對得上才加。", style = SfType.body, color = c.text)
                        Text("封面存在 SoundFlow 裡，不會改動音檔，隨時可以復原。查詢時只送出歌手與歌名；沒有標籤的檔案（例如錄音）不會送出。為了不被擋，每首約 3 秒。", style = SfType.body, color = c.muted)
                    }
                    b.running -> {
                        LinearProgressIndicator(progress = { if (b.total > 0) b.done.toFloat() / b.total else 0f }, color = c.accent, trackColor = c.border, modifier = Modifier.fillMaxWidth())
                        Text("${b.done} / ${b.total} · 找到 ${b.found} 張", style = SfType.number, color = c.text)
                        Text("關掉這個視窗也會繼續。", style = SfType.body, color = c.muted)
                    }
                    else -> {
                        Text("加上 ${b.found} 張封面", style = SfType.rowTitle, color = c.text)
                        Text("對不上而略過 ${b.unsure} 首、沒有標籤 ${b.noInfo} 首。這些可以在該首的「⋯」手動找。", style = SfType.body, color = c.muted)
                        if (b.failed) Text("過程中有連線失敗，可以稍後再跑一次。", style = SfType.body, color = c.danger)
                    }
                }
            }
        },
        confirmButton = {
            when {
                b == null -> SfButton("開始", primary = true, enabled = tagged > 0, onClick = { Graph.coverSearch.startBatch(missing) })
                b.running -> SfButton("背景執行", primary = true, onClick = onDismiss)
                else -> SfButton("完成", primary = true, onClick = { Graph.coverSearch.clearBatch(); onDismiss() })
            }
        },
        dismissButton = {
            when {
                b == null -> SfButton("取消", primary = false, onClick = onDismiss)
                b.running -> SfButton("停止", primary = false, onClick = { Graph.coverSearch.cancelBatch() })
                b.found > 0 -> SfButton("復原這批", primary = false, onClick = { Graph.coverSearch.undoBatch(); onDismiss() })
                else -> Unit
            }
        },
    )
}
