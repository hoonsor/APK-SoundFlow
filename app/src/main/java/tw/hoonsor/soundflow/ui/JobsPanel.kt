package tw.hoonsor.soundflow.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.EnqueueResult
import tw.hoonsor.soundflow.data.JobStatus
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.data.WorkerStatus
import tw.hoonsor.soundflow.data.YtJob
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfType
import java.time.Instant

/** YouTube 分頁上方：N100 狀態、轉檔中／排隊／失敗的工作、貼上網址（F04） */
@Composable
fun JobsPanel() {
    val c = Sf.colors
    val state by Graph.jobs.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    val active = state.jobs.any { it.status == JobStatus.QUEUED || it.status == JobStatus.PROCESSING }

    // 有工作在跑時每 8 秒更新；工作完成時順便刷新 YouTube 列表
    LaunchedEffect(Unit) {
        Graph.jobs.flushPending()
        Graph.jobs.refresh()
    }
    LaunchedEffect(active) {
        while (active) {
            delay(8_000)
            val before = Graph.jobs.state.value.jobs.count { it.status != JobStatus.FAILED }
            Graph.jobs.refresh()
            val after = Graph.jobs.state.value.jobs.count { it.status != JobStatus.FAILED }
            if (after < before) Graph.tracks.refresh(TrackKind.YOUTUBE)
        }
    }

    val (online, workerText) = WorkerStatus.text(state.worker, Instant.now())
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surface).border(1.dp, c.border, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (online) c.accent else c.danger))
            Text(workerText, style = SfType.body, color = c.muted, modifier = Modifier.weight(1f))
            SfButton("貼上網址", primary = false, onClick = { showAdd = true })
        }
        if (state.pendingOffline > 0) Text("有 ${state.pendingOffline} 支影片存在手機，連上網路後自動送出", style = SfType.body, color = c.accentText)
        state.error?.let { Text(it, style = SfType.body, color = c.danger) }
        state.jobs.forEach { job -> JobRow(job, onRetry = { scope.launch { runCatching { Graph.jobs.retry(job.id) } } }, onCancel = { scope.launch { runCatching { Graph.jobs.cancel(job.id) } } }) }
        if (state.jobs.isEmpty() && state.error == null) {
            Text("在 YouTube 按「分享 → SoundFlow 轉成音檔」，或按「貼上網址」。", style = SfType.body, color = c.muted)
        }
    }
    if (showAdd) AddUrlDialog { showAdd = false }
}

@Composable
private fun JobRow(job: YtJob, onRetry: () -> Unit, onCancel: () -> Unit) {
    val c = Sf.colors
    val color = when (job.status) {
        JobStatus.FAILED -> c.danger
        JobStatus.PROCESSING -> c.accentText
        else -> c.muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(job.title ?: "取得標題中… ${job.videoId}", style = SfType.cardTitle, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (job.status == JobStatus.FAILED) "失敗：${job.error ?: "原因不明"}" else job.status.label, style = SfType.meta, color = color, maxLines = 2)
        }
        when (job.status) {
            JobStatus.FAILED -> { SfButton("重試", primary = false, onClick = onRetry); SfButton("移除", primary = false, onClick = onCancel) }
            JobStatus.QUEUED -> SfButton("取消", primary = false, onClick = onCancel)
            else -> Unit
        }
    }
}

@Composable
private fun AddUrlDialog(onClose: () -> Unit) {
    val c = Sf.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember {
        val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        mutableStateOf(clip?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { "youtu" in it }.orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = c.surface,
        title = { Text("加入 YouTube 影片", color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; msg = null }, singleLine = true, label = { Text("影片網址") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.border, focusedTextColor = c.text, unfocusedTextColor = c.text),
                    modifier = Modifier.fillMaxWidth(),
                )
                msg?.let { Text(it, style = SfType.body, color = c.muted) }
            }
        },
        confirmButton = {
            SfButton(if (busy) "送出中…" else "送出", primary = true, enabled = !busy && text.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    msg = runCatching { Graph.jobs.enqueue(text) }.fold(
                        onSuccess = {
                            when (it) {
                                EnqueueResult.Queued -> { Graph.jobs.refresh(); onClose(); null }
                                EnqueueResult.AlreadyQueued -> "這支影片已經在排隊了"
                                EnqueueResult.AlreadyInLibrary -> "這支影片已經在 YouTube 分頁了"
                                EnqueueResult.SavedOffline -> "現在沒網路，先存在手機，連上後自動送出"
                            }
                        },
                        onFailure = { it.message },
                    )
                    busy = false
                }
            })
        },
        dismissButton = { SfButton("取消", primary = false, onClick = onClose) },
    )
}
