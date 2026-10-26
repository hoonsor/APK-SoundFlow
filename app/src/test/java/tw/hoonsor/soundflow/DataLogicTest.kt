package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.data.SfFormat
import tw.hoonsor.soundflow.data.SfJson
import tw.hoonsor.soundflow.data.SourceStyle
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.data.supabase.SupabaseErrors
import java.time.Instant
import java.time.LocalDate

class DataLogicTest {
    private val sample = """
        [{"id":"a1","kind":"podcast","title":"超強的做動畫技能","original_title":"超強的做動畫技能","channel":"youtube",
          "source_url":"https://www.youtube.com/watch?v=00I5wAy9i3M","duration_sec":320,"tier":"supabase",
          "storage_bucket":"nlm","storage_path":"yt-00I5wAy9i3M-0816bdf6009c.m4a","created_at":"2026-10-19T04:05:37.238+00:00"},
         {"id":"a2","kind":"podcast","title":"改過的名字","original_title":"CLIProxyAPI","channel":"github",
          "source_url":null,"duration_sec":null,"tier":"supabase","storage_bucket":"nlm","storage_path":"x.m4a","created_at":"2026-10-19T15:53:07+00:00"},
         {"id":"bad","kind":"music","title":"未知種類會被略過","created_at":"2026-10-19T15:53:07+00:00"}]
    """.trimIndent()

    @Test fun parseTracks() {
        val list = SfJson.parseTracks(sample)
        assertEquals(2, list.size)
        val a = list[0]
        assertEquals(TrackKind.PODCAST, a.kind)
        assertEquals(320, a.durationSec)
        assertEquals("nlm", a.storageBucket)
        assertEquals(Instant.parse("2026-10-19T04:05:37.238Z"), a.createdAt)
        assertNull(list[1].durationSec)
        assertNull(list[1].sourceUrl)
        assertEquals("CLIProxyAPI", list[1].originalTitle)
    }

    @Test fun cacheRoundTrip() {
        val list = SfJson.parseTracks(sample)
        assertEquals(list, SfJson.parseTracks(SfJson.tracksToJson(list)))
    }

    @Test fun parseSession() {
        val raw = """{"access_token":"A","refresh_token":"R","expires_in":3600,"expires_at":1790000000,"user":{"email":"me@example.com"}}"""
        val s = SfJson.parseSession(raw, nowEpochSec = 1)
        assertEquals("A", s.accessToken)
        assertEquals("R", s.refreshToken)
        assertEquals(1790000000, s.expiresAtEpochSec)
        assertEquals("me@example.com", s.email)
        val noExpiresAt = SfJson.parseSession("""{"access_token":"A","refresh_token":"R","expires_in":3600}""", nowEpochSec = 100)
        assertEquals(3700, noExpiresAt.expiresAtEpochSec)
    }

    @Test fun validation() {
        assertTrue(SfJson.isValidEmail(" Me@Example.com "))
        assertFalse(SfJson.isValidEmail("me@example"))
        assertEquals("me@example.com", SfJson.normalizeEmail(" Me@Example.com "))
        assertTrue(SfJson.isValidOtp("012345"))
        assertTrue(SfJson.isValidOtp("01234567"))     // AI_News 專案目前是 8 碼
        assertTrue(SfJson.isValidOtp("0123456789"))
        assertFalse(SfJson.isValidOtp("12345"))
        assertFalse(SfJson.isValidOtp("01234567890"))
        assertFalse(SfJson.isValidOtp("12a456"))
    }

    @Test fun formatting() {
        assertEquals("5:20", SfFormat.duration(320))
        assertEquals("1:02:15", SfFormat.duration(3735))
        assertEquals("—", SfFormat.duration(null))
        assertEquals("28 分", SfFormat.total(1642))
        assertEquals("1 小時 05 分", SfFormat.total(3900))
        val today = LocalDate.of(2026, 10, 25)
        assertEquals("今天", SfFormat.relativeDate(Instant.parse("2026-10-24T17:00:00Z"), today)) // 台灣 10/25 01:00
        assertEquals("昨天", SfFormat.relativeDate(Instant.parse("2026-10-24T03:00:00Z"), today))
        assertEquals("10/19", SfFormat.relativeDate(Instant.parse("2026-10-19T04:05:00Z"), today))
        assertEquals("2025/12/31", SfFormat.relativeDate(Instant.parse("2025-12-31T04:00:00Z"), today))
    }

    @Test fun sourceStyle() {
        assertEquals("GH", SourceStyle.of("github").tag)
        assertEquals("社群", SourceStyle.of("community").label)
        assertEquals("其他", SourceStyle.of(null).label)
        val ch = SourceStyle.of("工程師阿哲")
        assertEquals("工", ch.tag)
        assertEquals(ch.hue, SourceStyle.of("工程師阿哲").hue, 0f)   // 同一個頻道顏色固定
        assertTrue(ch.hue in 0f..359f)
    }

    @Test fun errorMessages() {
        assertEquals("寄太多次了，請過幾分鐘再試", SupabaseErrors.message("寄驗證碼", 429, "{}"))
        assertEquals(
            "這個信箱不在 Supabase 的使用者名單，請先在後台加入",
            SupabaseErrors.message("寄驗證碼", 422, """{"code":422,"error_code":"otp_disabled","msg":"Signups not allowed for otp"}"""),
        )
        assertEquals("驗證碼不對或已過期，請重新寄一次", SupabaseErrors.message("驗證", 403, """{"msg":"Token has expired or is invalid"}"""))
        assertEquals("登入已失效，請重新登入", SupabaseErrors.message("更新登入", 400, """{"msg":"Invalid Refresh Token: Already Used"}"""))
        assertTrue(SupabaseErrors.message("讀取資料", 503, "").contains("503"))
        assertEquals(
            "目前登入的信箱不在 SoundFlow 白名單，無法送出轉檔",
            SupabaseErrors.message("送出轉檔", 403, """{"code":"42501","message":"new row violates row-level security policy for table \"yt_job\""}"""),
        )
    }

    @Test fun whoami() {
        assertTrue(SfJson.parseWhoami("""{"email":"me@example.com","member":true}"""))
        assertFalse(SfJson.parseWhoami("""{"email":"x@example.com","member":false}"""))
        assertTrue(SfJson.parseWhoami("\"{\\\"email\\\":\\\"me@example.com\\\",\\\"member\\\":true}\""))
    }
}
