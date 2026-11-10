package tw.hoonsor.soundflow.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.hoonsor.backup.AutoBackup
import tw.hoonsor.soundflow.backup.SoundFlowBackup
import tw.hoonsor.soundflow.backup.SoundFlowBackupSource
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfType

/**
 * 設定頁「備份與還原」：每日自動備份開關、上次備份狀態、立即備份、從備份檔還原。
 * 結果顯示在區塊裡，不用 Toast（ColorOS 有時不顯示 Toast，見 AGENTS.md）。
 */
@Composable
fun BackupRows() {
    val c = Sf.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    var on by remember { mutableStateOf(AutoBackup.isEnabled(ctx)) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var msgOk by remember { mutableStateOf(true) }
    val status = remember(tick, on, busy) { AutoBackup.statusText(ctx) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("每天自動備份", style = SfType.rowTitle, color = c.text)
            Text(status, style = SfType.body, color = c.muted)
        }
        Switch(
            checked = on,
            onCheckedChange = { on = it; AutoBackup.setEnabled(ctx, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
                uncheckedThumbColor = c.muted, uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.border,
            ),
        )
    }
    Text(
        "存到手機的 ${AutoBackup.folder(SoundFlowBackupSource())}，用 Dropsync 同步到 Dropbox。" +
            "內容：收聽進度、設定、上次的播放清單、自己挑的本機音檔封面、還沒送出的分享。" +
            "內容沒變就不另存，只留最近 ${AutoBackup.KEEP} 份；不含登入與金鑰（還原後要重新登入），雲端的曲庫、標籤、清單本來就在 Supabase。",
        style = SfType.body, color = c.muted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SfButton(if (busy) "處理中…" else "立即備份", primary = true, enabled = !busy, onClick = {
            busy = true; msg = null
            scope.launch {
                val err = withContext(Dispatchers.IO) { AutoBackup.runOnce(ctx, true) }
                msgOk = err == null
                msg = err?.let { "備份失敗：$it" } ?: "已備份：${AutoBackup.lastFile(ctx)}"
                busy = false
            }
        }, modifier = Modifier.weight(1f))
        SfButton("從備份檔還原", primary = false, enabled = !busy, onClick = {
            pick.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }, modifier = Modifier.weight(1f))
    }
    msg?.let { Text(it, style = SfType.body, color = if (msgOk) c.accentText else c.danger) }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            containerColor = c.surface,
            title = { Text("用備份檔還原？", color = c.text) },
            text = {
                Text(
                    "設定會換成備份檔的內容；收聽進度與待送出的分享會合併（同一集保留比較新的進度）；" +
                        "封面與上次的播放清單會寫回。登入不受影響。",
                    style = SfType.body, color = c.muted,
                )
            },
            confirmButton = {
                SfButton("還原", primary = true, enabled = !busy, onClick = {
                    restoreUri = null
                    busy = true; msg = null
                    scope.launch {
                        val r = runCatching {
                            val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("打不開這個檔案")
                            input.use { SoundFlowBackup.restore(ctx, it) }
                        }
                        busy = false
                        r.onSuccess {
                            msgOk = true
                            // 佈景主題、檢視方式、進度都是 Flow，畫面會自己更新，不必重開；播放模式下次開始播放時套用
                            msg = "已還原：${it.summary()}"
                        }.onFailure {
                            msgOk = false
                            msg = "還原失敗：${it.message ?: it.javaClass.simpleName}"
                        }
                    }
                })
            },
            dismissButton = { SfButton("取消", primary = false, onClick = { restoreUri = null }) },
        )
    }
}
