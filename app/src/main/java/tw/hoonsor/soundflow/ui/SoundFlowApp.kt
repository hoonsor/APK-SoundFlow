package tw.hoonsor.soundflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.BuildConfig
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfPalettes
import tw.hoonsor.soundflow.ui.theme.SfThemeId
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.toColor

private enum class Tab(val label: String, val icon: ImageVector) {
    TRACKS("音檔", SfIcons.Grid),
    PLAYLISTS("清單", SfIcons.Playlist),
    QUEUE("待播", SfIcons.Queue),
    SETTINGS("設定", SfIcons.Settings),
}

@Composable
fun SoundFlowApp(theme: SfThemeId, onThemeChange: (SfThemeId) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TRACKS) }
    val c = Sf.colors
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.TRACKS -> TracksScreen()
                Tab.PLAYLISTS -> PlaceholderScreen("播放清單", "自訂清單與自動清單（最新加入、未聽完、聽到一半、最近播放）會在這裡。")
                Tab.QUEUE -> PlaceholderScreen("待播", "按「下一首播放」或「加入待播」的音檔會排在這裡。")
                Tab.SETTINGS -> SettingsScreen(theme, onThemeChange)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Tab.entries.forEach { t ->
                val selected = t == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { tab = t }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, contentDescription = null, tint = if (selected) c.accentText else c.muted, modifier = Modifier.size(23.dp))
                    Text(t.label, color = if (selected) c.accentText else c.muted, fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(title, style = SfType.pageTitle, color = Sf.colors.text, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 14.dp))
        EmptyCard("還沒有內容", body)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = Sf.colors
    Text(title, style = SfType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun SettingsScreen(theme: SfThemeId, onThemeChange: (SfThemeId) -> Unit) {
    val c = Sf.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("設定", style = SfType.pageTitle, color = c.text, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 14.dp))
        Section("外觀") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("佈景主題", style = SfType.rowTitle, color = c.text, modifier = Modifier.weight(1f))
                Text("點了立即套用", style = SfType.body, color = c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SfThemeId.entries.forEach { id -> ThemeCard(id, selected = id == theme, onClick = { onThemeChange(id) }, modifier = Modifier.weight(1f)) }
            }
        }
        Section("帳號與雲端") { AccountRows() }
        Text(
            "SoundFlow v${BuildConfig.VERSION_NAME} · ${context.packageName}",
            style = SfType.number, color = c.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun AccountRows() {
    val c = Sf.colors
    val auth by Graph.auth.state.collectAsState()
    val scope = rememberCoroutineScope()
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }
    when (val a = auth) {
        is AuthState.SignedIn -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Supabase 帳號", style = SfType.rowTitle, color = c.text)
                    Text("已登入 · ${a.email}", style = SfType.body, color = c.muted)
                }
                SfButton(if (testing) "測試中…" else "測試", primary = false, enabled = !testing, onClick = {
                    testing = true; testResult = null
                    scope.launch {
                        Graph.tracks.refresh(TrackKind.PODCAST)
                        val s = Graph.tracks.state(TrackKind.PODCAST).value
                        testResult = s.error ?: "連線正常，讀到 ${s.items.size} 集 Podcast"
                        testing = false
                    }
                })
            }
            testResult?.let { Text(it, style = SfType.body, color = if (it.startsWith("連線正常")) c.accentText else c.danger) }
            SfButton("登出", primary = false, onClick = {
                scope.launch { Graph.auth.signOut(); Graph.tracks.clear(); testResult = null }
            }, modifier = Modifier.fillMaxWidth())
        }
        AuthState.SignedOut -> Text("尚未登入。到「音檔」分頁用信箱驗證碼登入。", style = SfType.body, color = c.muted)
        AuthState.Loading -> Text("讀取登入狀態中…", style = SfType.body, color = c.muted)
    }
}

/** 主題卡片用該主題自己的顏色畫預覽，不受目前主題影響 */
@Composable
private fun ThemeCard(id: SfThemeId, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = SfPalettes.of(id)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(p.bg.toColor())
            .border(2.dp, (if (selected) p.accent else p.border).toColor(), shape)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) {
                Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(p.surface.toColor())
                    .border(1.dp, p.border.toColor(), RoundedCornerShape(6.dp)))
            }
        }
        Row(
            Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(6.dp)).background(p.surface2.toColor()).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(p.accent.toColor()))
            Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(p.accent.toColor()))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(id.label, color = p.text.toColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selected) Icon(SfIcons.Check, contentDescription = "使用中", tint = p.text.toColor(), modifier = Modifier.size(14.dp))
        }
    }
}
