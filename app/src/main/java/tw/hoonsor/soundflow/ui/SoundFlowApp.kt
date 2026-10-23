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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import tw.hoonsor.soundflow.BuildConfig
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
private fun TracksScreen() {
    val c = Sf.colors
    var podcast by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SoundFlow", style = SfType.pageTitle, color = c.text)
                Text("尚未連線到資料庫", style = SfType.number, color = c.muted)
            }
            Icon(SfIcons.Search, contentDescription = "搜尋", tint = c.text, modifier = Modifier.size(44.dp).padding(11.dp))
        }
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(true to "Podcast", false to "YouTube").forEach { (isPod, label) ->
                val sel = podcast == isPod
                Box(
                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) c.accent else c.surface).clickable(role = Role.Tab) { podcast = isPod },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (sel) c.onAccent else c.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (podcast) {
            EmptyCard("新知雷達的 Podcast 會出現在這裡", "在新知雷達網站按「送進 NotebookLM」，產生完就會自動列出。資料庫串接在下一版（v0.2.0）完成。")
        } else {
            EmptyCard("在 YouTube 按「分享」送過來", "影片下方點「分享」→ 選 SoundFlow，N100 轉成音檔後出現在這裡。分享功能在之後的版本完成。")
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    val c = Sf.colors
    Column(
        Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SfIcons.Wave, contentDescription = null, tint = c.accent, modifier = Modifier.size(56.dp))
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(body, color = c.muted, style = SfType.body)
    }
}

@Composable
private fun PlaceholderScreen(title: String, body: String) {
    Column(Modifier.fillMaxSize()) {
        Text(title, style = SfType.pageTitle, color = Sf.colors.text, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
        EmptyCard("還沒有內容", body)
    }
}

@Composable
private fun SettingsScreen(theme: SfThemeId, onThemeChange: (SfThemeId) -> Unit) {
    val c = Sf.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("設定", style = SfType.pageTitle, color = c.text, modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 14.dp))
        Text("外觀", style = SfType.section, color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
                .border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("佈景主題", style = SfType.rowTitle, color = c.text, modifier = Modifier.weight(1f))
                Text("點了立即套用", style = SfType.body, color = c.muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SfThemeId.entries.forEach { id -> ThemeCard(id, selected = id == theme, onClick = { onThemeChange(id) }, modifier = Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "SoundFlow v${BuildConfig.VERSION_NAME} · ${context.packageName}",
            style = SfType.number, color = c.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
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
