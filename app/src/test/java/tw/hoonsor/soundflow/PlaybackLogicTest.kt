package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.data.SfJson
import tw.hoonsor.soundflow.data.SfUrls
import tw.hoonsor.soundflow.player.ProgressRules
import tw.hoonsor.soundflow.player.Queue
import tw.hoonsor.soundflow.player.Repeat
import tw.hoonsor.soundflow.player.Speed
import tw.hoonsor.soundflow.player.TimeText
import tw.hoonsor.soundflow.player.TrackProgress

class PlaybackLogicTest {
    @Test fun speed() {
        assertEquals(0.5f, Speed.clamp(0.1f), 0f)
        assertEquals(3.0f, Speed.clamp(9f), 0f)
        assertEquals(1.3f, Speed.clamp(1.31f), 0.0001f)
        assertEquals(1.35f, Speed.clamp(1.34f), 0.0001f)
        assertEquals("1.3×", Speed.label(1.3f))
        assertEquals("1×", Speed.label(1f))
        assertEquals("1.75×", Speed.label(1.75f))
    }

    @Test fun repeatCycles() {
        assertEquals(Repeat.ALL, Repeat.OFF.next())
        assertEquals(Repeat.ONE, Repeat.ALL.next())
        assertEquals(Repeat.OFF, Repeat.ONE.next())
    }

    @Test fun playedRules() {
        assertTrue(ProgressRules.isPlayed(290_000, 300_000))      // 剩 10 秒
        assertTrue(ProgressRules.isPlayed(2_850_000, 3_000_000))  // 95%
        assertFalse(ProgressRules.isPlayed(100_000, 300_000))
        assertFalse(ProgressRules.isPlayed(0, 0))
        assertTrue(ProgressRules.nextPlayed(wasPlayed = true, positionMs = 2_000, durationMs = 300_000))   // 重聽前 5 秒仍算聽完
        assertFalse(ProgressRules.nextPlayed(wasPlayed = true, positionMs = 60_000, durationMs = 300_000)) // 重聽到一半
    }

    @Test fun resumePosition() {
        assertEquals(0L, ProgressRules.resumePosition(null))
        assertEquals(0L, ProgressRules.resumePosition(TrackProgress(200_000, 300_000, played = true, updatedAt = 0)))
        assertEquals(97_000L, ProgressRules.resumePosition(TrackProgress(100_000, 300_000, played = false, updatedAt = 0)))
        assertEquals(0L, ProgressRules.resumePosition(TrackProgress(1_000, 300_000, played = false, updatedAt = 0)))
        assertEquals(0L, ProgressRules.resumePosition(TrackProgress(297_000, 300_000, played = false, updatedAt = 0)))
    }

    @Test fun progressJsonRoundTrip() {
        val m = mapOf("a" to TrackProgress(1, 2, false, 3), "b" to TrackProgress(4, 5, true, 6))
        assertEquals(m, ProgressRules.fromJson(ProgressRules.toJson(m)))
        assertEquals(emptyMap<String, TrackProgress>(), ProgressRules.fromJson("壞掉的檔案"))
    }

    @Test fun progressFraction() {
        assertEquals(0.5f, TrackProgress(150_000, 300_000, false, 0).fraction, 0.001f)
        assertEquals(1f, TrackProgress(0, 300_000, true, 0).fraction, 0f)
        assertFalse(TrackProgress(3_000, 300_000, false, 0).inProgress)
        assertTrue(TrackProgress(60_000, 300_000, false, 0).inProgress)
    }

    @Test fun clockText() {
        assertEquals("06:08", TimeText.clock(368_000))
        assertEquals("1:02:15", TimeText.clock(3_735_000))
        assertEquals("00:00", TimeText.clock(-5))
        assertEquals("30 分", TimeText.sleepLeft(30 * 60_000L))
        assertEquals("29 分", TimeText.sleepLeft(28 * 60_000L + 1))
        assertEquals("45 秒", TimeText.sleepLeft(45_000))
    }

    @Test fun upNext() {
        assertEquals(listOf(2, 3, 4), Queue.upNext(count = 10, current = 1, n = 3, repeatAll = false))
        assertEquals(listOf(9), Queue.upNext(count = 10, current = 8, n = 3, repeatAll = false))
        assertEquals(listOf(9, 0, 1), Queue.upNext(count = 10, current = 8, n = 3, repeatAll = true))
        assertEquals(listOf(1), Queue.upNext(count = 2, current = 0, n = 3, repeatAll = true))
        assertEquals(emptyList<Int>(), Queue.upNext(count = 1, current = 0, n = 3, repeatAll = true))
    }

    @Test fun audioUrl() {
        val raw = """[{"id":"x","kind":"podcast","title":"t","tier":"supabase","storage_bucket":"nlm","storage_path":"yt-00I5wAy9i3M-0816bdf6009c.m4a","created_at":"2026-10-19T04:05:00+00:00"},
                      {"id":"y","kind":"podcast","title":"t","tier":"drive","storage_bucket":null,"storage_path":null,"created_at":"2026-10-19T04:05:00+00:00"},
                      {"id":"z","kind":"youtube","title":"t","tier":"supabase","storage_bucket":"sf","storage_path":"yt/有 空白.opus","created_at":"2026-10-19T04:05:00+00:00"}]"""
        val (a, b, c) = SfJson.parseTracks(raw)
        assertEquals("https://x.supabase.co/storage/v1/object/public/nlm/yt-00I5wAy9i3M-0816bdf6009c.m4a", SfUrls.audio("https://x.supabase.co", a))
        assertNull(SfUrls.audio("https://x.supabase.co", b))   // Drive 封存檔要等 v0.7
        // sf 是私人 bucket：用 authenticated 路徑，播放服務會自動帶登入權杖
        assertEquals("https://x.supabase.co/storage/v1/object/authenticated/sf/yt/%E6%9C%89%20%E7%A9%BA%E7%99%BD.opus", SfUrls.audio("https://x.supabase.co", c))
    }
}
