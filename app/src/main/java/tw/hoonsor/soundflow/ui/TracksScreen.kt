package tw.hoonsor.soundflow.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.core.content.ContextCompat
import tw.hoonsor.soundflow.player.TrackProgress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.AuthState
import tw.hoonsor.soundflow.data.SfFormat
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.data.TrackListState
import tw.hoonsor.soundflow.ui.theme.Sf
import tw.hoonsor.soundflow.ui.theme.SfIcons
import tw.hoonsor.soundflow.ui.theme.SfSemantic
import tw.hoonsor.soundflow.ui.theme.SfType
import tw.hoonsor.soundflow.ui.theme.toColor
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun TracksScreen() {
    val auth by Graph.auth.state.collectAsState()
    var kind by rememberSaveable { mutableStateOf(TrackKind.PODCAST) }
    val state by Graph.tracks.state(kind).collectAsState()
    val signedIn = auth is AuthState.SignedIn

    LaunchedEffect(kind, signedIn) {
        if (signedIn) {
            Graph.tracks.loadCache(kind)
            Graph.tracks.refresh(kind)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header(state, signedIn)
        KindTabs(kind) { kind = it }
        val member by Graph.auth.member.collectAsState()
        when (val a = auth) {
            AuthState.Loading -> Unit
            AuthState.SignedOut -> Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) { LoginCard() }
            is AuthState.SignedIn ->
                if (member == false) NotMemberCard(a.email) else TrackGrid(kind, state)
        }
    }
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
        listOf(TrackKind.PODCAST to "Podcast", TrackKind.YOUTUBE to "YouTube").forEach { (k, label) ->
            val sel = k == kind
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.accent else Color.Transparent).clickable(role = Role.Tab) { onChange(k) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) c.onAccent else c.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackGrid(kind: TrackKind, state: TrackListState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val today = LocalDate.now(ZoneId.of("Asia/Taipei"))
    val progress by Graph.progress.state.collectAsState()
    val nowPlaying by Graph.player.state.collectAsState()
    // Android 13+ 要通知權限，通知列才看得到播放控制；拒絕也照樣能播
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun play(index: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        Graph.player.playOrToggle(state.items, index)
    }
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { scope.launch { Graph.tracks.refresh(kind) } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (kind == TrackKind.YOUTUBE) {
                item(span = { GridItemSpan(2) }) { JobsPanel() }
            }
            state.error?.let { msg ->
                item(span = { GridItemSpan(2) }) { ErrorBanner(msg) { scope.launch { Graph.tracks.refresh(kind) } } }
            }
            if (state.items.isEmpty() && !state.loading && state.error == null) {
                item(span = { GridItemSpan(2) }) {
                    if (kind == TrackKind.PODCAST) {
                        EmptyCard(
                            "還沒有 Podcast",
                            "在新知雷達網站按「送進 NotebookLM」，產生完就會出現在這裡（往下拉可重新整理）。如果網站上明明有，請確認你的信箱已加進 SoundFlow 白名單。",
                        )
                    } else {
                        EmptyCard("在 YouTube 按「分享」送過來", "影片下方點「分享」→ 選「SoundFlow 轉成音檔」，N100 轉好後（通常幾分鐘）出現在這裡。")
                    }
                }
            }
            itemsIndexed(state.items, key = { _, t -> t.id }) { i, t ->
                TrackCard(t, today, progress[t.id], playing = nowPlaying.currentId == t.id) { play(i) }
            }
        }
    }
}

@Composable
private fun TrackCard(t: Track, today: LocalDate, p: TrackProgress?, playing: Boolean, onClick: () -> Unit) {
    val c = Sf.colors
    val style = SourceStyle.of(t.channel)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(if (playing) c.accentSoft else c.surface).border(1.dp, if (playing) c.accent else c.border, shape)
            .clickable(role = Role.Button, onClickLabel = if (playing) "播放或暫停" else "播放", onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(86.dp).background(SfSemantic.cover(style.hue).toColor())) {
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                Text(style.label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart))
                if (p?.played == true) {
                    Icon(SfIcons.Check, contentDescription = "已聽完", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(16.dp))
                }
                Text(style.tag, color = Color.White.copy(alpha = 0.9f), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
                Text(
                    SfFormat.duration(t.durationSec), style = SfType.number, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (p != null && p.inProgress) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p.fraction).height(3.dp).background(c.accent))
            }
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                t.title, style = SfType.cardTitle, color = if (p?.played == true) c.muted else c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 35.dp),
            )
            Text(
                "${style.label} · ${SfFormat.relativeDate(t.createdAt, today)}", style = SfType.meta, color = c.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
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
            SfButton("登出", primary = true, onClick = { scope.launch { Graph.auth.signOut(); Graph.tracks.clear() } }, modifier = Modifier.weight(1f))
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
