package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.data.JobJson
import tw.hoonsor.soundflow.data.JobStatus
import tw.hoonsor.soundflow.data.SfUrls
import tw.hoonsor.soundflow.data.WorkerBeat
import tw.hoonsor.soundflow.data.WorkerStatus
import tw.hoonsor.soundflow.data.YouTubeLink
import java.time.Instant

class YouTubeTest {
    private fun id(s: String?) = YouTubeLink.parse(s)?.videoId

    @Test fun shareFormats() {
        assertEquals("00I5wAy9i3M", id("https://youtu.be/00I5wAy9i3M?si=AbCdEf123"))
        assertEquals("00I5wAy9i3M", id("超強的做動畫技能\nhttps://youtu.be/00I5wAy9i3M"))
        assertEquals("00I5wAy9i3M", id("https://www.youtube.com/watch?v=00I5wAy9i3M&t=42s"))
        assertEquals("00I5wAy9i3M", id("https://m.youtube.com/watch?feature=share&v=00I5wAy9i3M"))
        assertEquals("00I5wAy9i3M", id("https://youtube.com/shorts/00I5wAy9i3M?feature=share"))
        assertEquals("00I5wAy9i3M", id("https://www.youtube.com/live/00I5wAy9i3M"))
        assertEquals("00I5wAy9i3M", id("https://music.youtube.com/watch?v=00I5wAy9i3M&list=RD"))
        assertEquals("https://www.youtube.com/watch?v=00I5wAy9i3M", YouTubeLink.parse("https://youtu.be/00I5wAy9i3M")?.canonicalUrl)
    }

    @Test fun rejects() {
        assertNull(id(null))
        assertNull(id("只是一段文字"))
        assertNull(id("https://www.youtube.com/playlist?list=PL123"))
        assertNull(id("https://www.youtube.com/@channel"))
        assertNull(id("https://example.com/watch?v=00I5wAy9i3M"))
        assertNull(id("https://youtu.be/short"))
    }

    @Test fun parseJobs() {
        val raw = """[{"id":"j1","url":"https://www.youtube.com/watch?v=00I5wAy9i3M","video_id":"00I5wAy9i3M","status":"failed",
            "error":"這是私人影片，無法轉檔","title":null,"requested_at":"2026-10-26T03:00:00.123+00:00"},
            {"id":"j2","url":"u","video_id":"v","status":"processing","error":null,"title":"標題","requested_at":"2026-10-26T03:01:00+00:00"},
            {"id":"j3","url":"u","video_id":"v","status":"weird","requested_at":"2026-10-26T03:01:00+00:00"}]"""
        val jobs = JobJson.parseJobs(raw)
        assertEquals(2, jobs.size)
        assertEquals(JobStatus.FAILED, jobs[0].status)
        assertEquals("這是私人影片，無法轉檔", jobs[0].error)
        assertNull(jobs[0].title)
        assertEquals("標題", jobs[1].title)
        assertNull(jobs[1].error)
    }

    @Test fun pendingRoundTrip() {
        val urls = listOf("https://youtu.be/00I5wAy9i3M", "https://youtu.be/K9kk0e5xgCI")
        assertEquals(urls, JobJson.pendingFromJson(JobJson.pendingToJson(urls)))
        assertEquals(emptyList<String>(), JobJson.pendingFromJson("壞掉"))
    }

    @Test fun workerStatus() {
        val now = Instant.parse("2026-10-26T04:00:00Z")
        assertFalse(WorkerStatus.text(null, now).first)
        assertTrue(WorkerStatus.text(WorkerBeat("n100", now.minusSeconds(120), "2026.10.01"), now).first)
        val (online, text) = WorkerStatus.text(WorkerBeat("n100", now.minusSeconds(3 * 3600), null), now)
        assertFalse(online)
        assertTrue(text.contains("3 小時"))
    }

    @Test fun privateBucketUsesAuthenticatedUrl() {
        assertTrue(SfUrls.needsAuth("https://x.supabase.co/storage/v1/object/authenticated/sf/yt/a.opus"))
        assertFalse(SfUrls.needsAuth("https://x.supabase.co/storage/v1/object/public/nlm/a.m4a"))
    }
}
