package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.soundflow.data.CoverLogic
import tw.hoonsor.soundflow.data.CoverResult
import tw.hoonsor.soundflow.data.LocalEntry
import tw.hoonsor.soundflow.data.LocalFolder
import tw.hoonsor.soundflow.data.LocalLogic
import tw.hoonsor.soundflow.data.SfJson
import tw.hoonsor.soundflow.data.SfUrls
import tw.hoonsor.soundflow.data.TrackKind

class LocalLogicTest {
    private val entry = LocalEntry("local:1", "content://a/1", "content://tree/music", "晴天", "周杰倫", "葉惠美", 269, 1_700_000_000_000, 123, true)

    @Test fun audioFiles() {
        assertTrue(LocalLogic.isAudio("a.MP3", null))
        assertTrue(LocalLogic.isAudio("a.bin", "audio/flac"))
        assertFalse(LocalLogic.isAudio("cover.jpg", "image/jpeg"))
    }

    @Test fun idIsStable() {
        assertEquals(LocalLogic.trackId("content://x/1"), LocalLogic.trackId("content://x/1"))
        assertFalse(LocalLogic.trackId("content://x/1") == LocalLogic.trackId("content://x/2"))
        assertTrue(LocalLogic.trackId("content://x/1").startsWith("local:"))
    }

    @Test fun titleFromFile() {
        assertEquals("晴天", LocalLogic.titleFromFileName("01 - 晴天.mp3"))
        assertEquals("2026-10-24 會議錄音", LocalLogic.titleFromFileName("2026-10-24 會議錄音.m4a"))
        assertEquals("七里香", LocalLogic.titleFromFileName("七里香.flac"))
        assertEquals("123", LocalLogic.titleFromFileName("123.mp3"))
    }

    @Test fun mergeKeepsScannedVersionAndDropsRemovedFolders() {
        val scanned = entry.copy(title = "新標題")
        val other = entry.copy(id = "local:2")
        val gone = entry.copy(id = "local:3", folderUri = "content://tree/removed")
        val all = LocalLogic.merge(listOf(scanned), listOf(entry, other, gone), setOf(entry.folderUri))
        assertEquals(listOf(scanned, other), all)
    }

    @Test fun trackRoundTripsThroughSnapshotJson() {
        val t = LocalLogic.toTrack(entry, "音樂")
        assertEquals(TrackKind.LOCAL, t.kind)
        assertEquals("周杰倫", t.channel)
        assertEquals(t, SfJson.parseTracks(SfJson.tracksToJson(listOf(t))).single())
        assertEquals("content://a/1", SfUrls.audio("https://x", t))
        assertEquals("音樂", LocalLogic.toTrack(entry.copy(artist = null), "音樂").channel)   // 沒有歌手就顯示資料夾名
    }

    @Test fun libraryJson() {
        val folders = listOf(LocalFolder("content://tree/music", "音樂"))
        val entries = listOf(entry, entry.copy(id = "local:2", artist = null, album = null, durationSec = null, tagged = false))
        assertEquals(folders to entries, LocalLogic.fromJson(LocalLogic.toJson(folders, entries)))
        assertEquals(emptyList<LocalEntry>(), LocalLogic.fromJson("壞掉").second)
    }

    @Test fun thumbnailFallback() {
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", SfUrls.thumbnailFallback("https://i.ytimg.com/vi_webp/abc/maxresdefault.webp"))
        assertNull(SfUrls.thumbnailFallback("https://example.com/a.jpg"))
    }

    @Test fun parseItunes() {
        val raw = """{"resultCount":1,"results":[{"trackName":"晴天","artistName":"周杰倫","collectionName":"葉惠美","artworkUrl100":"https://a/100x100bb.jpg"},{"trackName":"沒圖"}]}"""
        assertEquals(listOf(CoverResult("晴天", "周杰倫", "葉惠美", "https://a/600x600bb.jpg", "Apple")), CoverLogic.parseItunes(raw))
    }

    @Test fun parseDeezer() {
        val raw = """{"data":[{"title":"Yellow","artist":{"name":"Coldplay"},"album":{"title":"Parachutes","cover_big":"https://d/500.jpg"}}]}"""
        assertEquals(listOf(CoverResult("Yellow", "Coldplay", "Parachutes", "https://d/500.jpg", "Deezer")), CoverLogic.parseDeezer(raw))
        assertEquals(emptyList<CoverResult>(), CoverLogic.parseDeezer("{}"))
    }

    private fun r(title: String, artist: String) = CoverResult(title, artist, "專輯", "https://x/$title", "Apple")

    @Test fun batchPicksExactTitleOverLiveVersion() {
        val hit = CoverLogic.best("晴天", "周杰倫", listOf(r("晴天 (Live)", "周杰倫"), r("晴天", "周杰倫")))
        assertEquals("https://x/晴天", hit?.imageUrl)
    }

    @Test fun batchSkipsWhenArtistDiffers() {
        assertNull(CoverLogic.best("晴天", "周杰倫", listOf(r("晴天", "其他歌手"))))
        assertNull(CoverLogic.best("晴天", "周杰倫", listOf(r("雨天", "周杰倫"))))
        assertNull(CoverLogic.best("晴天", "周杰倫", emptyList()))
    }

    @Test fun matchingIgnoresWidthCaseAndPunctuation() {
        assertEquals("yellow", CoverLogic.normalize("ＹＥＬＬＯＷ (Remastered)"))
        assertTrue(CoverLogic.best("Yellow!", "coldplay", listOf(r("YELLOW", "Coldplay"))) != null)
        assertTrue(CoverLogic.best("Yellow", "Jay Chou", listOf(r("Yellow", "周杰倫 Jay Chou"))) != null)   // 歌手名包含
    }
}
