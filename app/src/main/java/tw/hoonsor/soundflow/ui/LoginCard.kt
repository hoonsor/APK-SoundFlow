package tw.hoonsor.soundflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.SfJson
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfType

/** 信箱驗證碼登入：輸入信箱 → 寄 6 碼 → 輸入驗證碼。只有白名單信箱能讀到資料 */
@Composable
fun LoginCard(modifier: Modifier = Modifier) {
    val c = Sf.colors
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var codeSent by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var cooldown by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) { delay(1000); cooldown-- }
    }

    fun send() {
        if (!SfJson.isValidEmail(email)) { error = "信箱格式不對"; return }
        busy = true; error = null
        scope.launch {
            runCatching { Graph.auth.sendCode(email) }
                .onSuccess { codeSent = true; cooldown = 60 }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun verify() {
        if (!SfJson.isValidOtp(code)) { error = "驗證碼是 6 位數字"; return }
        busy = true; error = null
        scope.launch {
            runCatching { Graph.auth.verify(email, code) }.onFailure { error = it.message }
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.border, focusedTextColor = c.text, unfocusedTextColor = c.text,
        cursorColor = c.accent, focusedLabelColor = c.accentText, unfocusedLabelColor = c.muted,
        focusedContainerColor = c.bg, unfocusedContainerColor = c.bg,
    )

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("登入 SoundFlow", color = c.text, fontSize = 17.sp, style = SfType.rowTitle)
        Text("用信箱收 6 位數驗證碼登入。只有加進白名單的信箱看得到新知雷達的 Podcast。", color = c.muted, style = SfType.body)
        OutlinedTextField(
            value = email, onValueChange = { email = it; error = null }, singleLine = true, enabled = !busy,
            label = { Text("信箱") }, colors = fieldColors, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
            modifier = Modifier.fillMaxWidth(),
        )
        if (codeSent) {
            OutlinedTextField(
                value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6); error = null }, singleLine = true, enabled = !busy,
                label = { Text("驗證碼（6 位數）") }, colors = fieldColors, shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("驗證碼已寄到 ${SfJson.normalizeEmail(email)}，沒收到請看垃圾郵件匣。", color = c.muted, style = SfType.body)
        }
        error?.let { Text(it, color = c.danger, style = SfType.body) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (codeSent) {
                SfButton(if (cooldown > 0) "重寄（${cooldown}s）" else "重寄驗證碼", primary = false, enabled = !busy && cooldown == 0, onClick = ::send)
                SfButton(if (busy) "登入中…" else "登入", primary = true, enabled = !busy, onClick = ::verify, modifier = Modifier.weight(1f))
            } else {
                SfButton(if (busy) "寄送中…" else "寄驗證碼", primary = true, enabled = !busy, onClick = ::send, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SfButton(label: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sf.colors
    val shape = RoundedCornerShape(12.dp)
    val bg = if (primary) c.accent else c.surface
    val fg = if (primary) c.onAccent else c.text
    Box(
        modifier.height(44.dp).clip(shape).background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .border(1.dp, if (primary) c.accent else c.border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) fg else fg.copy(alpha = 0.6f), fontSize = 14.sp, style = SfType.rowTitle) }
}
