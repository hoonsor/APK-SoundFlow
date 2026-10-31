package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.player.PlayMode
import tw.hoonsor.soundflow.player.QueueSnapshot
import tw.hoonsor.soundflow.player.Repeat
import tw.hoonsor.soundflow.player.SleepCycle
import tw.hoonsor.soundflow.player.Speed
import tw.hoonsor.soundflow.player.WidgetLayout
import tw.hoonsor.soundflow.player.WidgetState
import java.time.Instant

class WidgetLogicTest {
    @Test fun speedCycle() {
        assertEquals(1.25f, Speed.nextWidget(1.0f), 0f)
        assertEquals(1.5f, Speed.nextWidget(1.3f), 0f)
        assertEquals(1.0f, Speed.nextWidget(2.0f), 0f)       // 繞回
        assertEquals(1.5f, Speed.nextWidget(1.35f), 0f)      // 不在清單上：跳到比它大的下一個
        assertEquals(1.0f, Speed.nextWidget(2.5f), 0f)
    }

    @Test fun layoutBySize() {
        // Nova 7 列 × 5 欄：每格約 76×104 dp
        assertEquals(WidgetLayout.MINI, WidgetLayout.forSizeDp(146f, 98f))     // 2×1
        assertEquals(WidgetLayout.BAR, WidgetLayout.forSizeDp(374f, 98f))      // 5×1
        assertEquals(WidgetLayout.SQUARE, WidgetLayout.forSizeDp(146f, 202f))  // 2×2
        assertEquals(WidgetLayout.FULL, WidgetLayout.forSizeDp(298f, 202f))    // 4×2
        assertEquals(WidgetLayout.BAR, WidgetLayout.forSizeDp(298f, 98f))      // 4×1 用長條版面
        assertEquals(WidgetLayout.MINI, WidgetLayout.forSizeDp(222f, 98f))     // 3×1 放不下長條
    }

    @Test fun playModeCycle() {
        assertEquals(PlayMode.ONE, PlayMode.ALL.next())
        assertEquals(PlayMode.SHUFFLE, PlayMode.ONE.next())
        assertEquals(PlayMode.ALL, PlayMode.SHUFFLE.next())
        assertEquals(PlayMode.ALL, PlayMode.of(Repeat.ALL, false))
        assertEquals(PlayMode.ALL, PlayMode.of(Repeat.OFF, false))    // 舊版的「不循環」當成循環
        assertEquals(PlayMode.ONE, PlayMode.of(Repeat.ONE, false))
        assertEquals(PlayMode.SHUFFLE, PlayMode.of(Repeat.ALL, true))
        assertEquals(PlayMode.ALL, PlayMode.fromKey(null))
        assertEquals(PlayMode.SHUFFLE, PlayMode.fromKey("SHUFFLE"))
    }

    @Test fun sleepCycle() {
        assertEquals(15, SleepCycle.nextMinutes(0))
        assertEquals(30, SleepCycle.nextMinutes(15 * 60_000L))
        assertEquals(60, SleepCycle.nextMinutes(30 * 60_000L))
        assertEquals(0, SleepCycle.nextMinutes(60 * 60_000L))
    }

    @Test fun widgetStateRoundTrip() {
        val s = WidgetState("超強的做動畫技能", "youtube", "podcast", "最新加入", true, 60_000, 320_000, 1.3f, Repeat.ONE,
            listOf("下一集", "再下一集"), listOf(3, 4), 123, shuffle = true, index = 2, sleepEndsAt = 99, coverPath = "/x/a.jpg", coverStamp = 5)
        assertEquals(s, WidgetState.fromJson(s.toJson()))
        assertEquals(WidgetState(), WidgetState.fromJson("壞掉"))
        assertFalse(WidgetState().hasTrack)
        assertTrue(s.hasTrack)
        assertEquals(0.1875f, s.fraction, 0.0001f)
    }

    @Test fun snapshotRoundTrip() {
        val tracks = listOf("a", "b").mapIndexed { i, id ->
            Track(id, TrackKind.PODCAST, "標題$id", "標題$id", "github", null, 300, "supabase", "nlm", "$id.m4a", Instant.parse("2026-10-2${i}T00:00:00Z"))
        }
        val snap = QueueSnapshot("通勤", tracks, 1, 42_000)
        assertEquals(snap, QueueSnapshot.fromJson(snap.toJson()))
        assertNull(QueueSnapshot.fromJson("""{"source":"x","index":0,"position":0,"tracks":[]}"""))   // 空清單不算
        assertEquals(1, QueueSnapshot.fromJson(snap.copy(index = 9).toJson())!!.index)              // 索引越界夾回最後一集
    }
}
