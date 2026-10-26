package tw.hoonsor.soundflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tw.hoonsor.soundflow.data.Label
import tw.hoonsor.soundflow.data.LibraryLogic
import tw.hoonsor.soundflow.data.LibraryState
import tw.hoonsor.soundflow.data.ListFilter
import tw.hoonsor.soundflow.data.Playlist
import tw.hoonsor.soundflow.data.SortKey
import tw.hoonsor.soundflow.data.SortSpec
import tw.hoonsor.soundflow.data.Track
import tw.hoonsor.soundflow.data.TrackKind
import tw.hoonsor.soundflow.player.TrackProgress
import java.time.Instant

class LibraryLogicTest {
    private fun t(id: String, title: String, day: Int, dur: Int) = Track(
        id, TrackKind.PODCAST, title, title, "github", null, dur, "supabase", "nlm", "$id.m4a", Instant.parse("2026-10-%02dT00:00:00Z".format(day)),
    )

    private val a = t("a", "Claude 新功能", 19, 300)
    private val b = t("b", "AI Agent 失敗案例", 21, 900)
    private val c = t("c", "MCP 一年回顧", 20, 600)
    private val tracks = listOf(a, b, c)
    private val progress = mapOf(
        "a" to TrackProgress(300_000, 300_000, played = true, updatedAt = 30),
        "c" to TrackProgress(120_000, 600_000, played = false, updatedAt = 50),
    )

    private fun ids(list: List<Track>) = list.map { it.id }

    @Test fun sorting() {
        assertEquals(listOf("b", "c", "a"), ids(LibraryLogic.sort(tracks, SortSpec(SortKey.ADDED, true), progress)))
        assertEquals(listOf("a", "c", "b"), ids(LibraryLogic.sort(tracks, SortSpec(SortKey.ADDED, false), progress)))
        assertEquals(listOf("b", "a", "c"), ids(LibraryLogic.sort(tracks, SortSpec(SortKey.TITLE, false), progress)))   // AI < Claude < MCP
        assertEquals(listOf("b", "c", "a"), ids(LibraryLogic.sort(tracks, SortSpec(SortKey.DURATION, true), progress)))
        assertEquals(listOf("a", "c", "b"), ids(LibraryLogic.sort(tracks, SortSpec(SortKey.PROGRESS, true), progress)))
    }

    @Test fun filtering() {
        val links = mapOf("a" to setOf("L1"), "b" to setOf("L1", "L2"))
        assertEquals(listOf("b"), ids(LibraryLogic.filter(tracks, ListFilter.UNPLAYED, emptySet(), links, progress)))
        assertEquals(listOf("c"), ids(LibraryLogic.filter(tracks, ListFilter.IN_PROGRESS, emptySet(), links, progress)))
        assertEquals(listOf("a"), ids(LibraryLogic.filter(tracks, ListFilter.PLAYED, emptySet(), links, progress)))
        assertEquals(listOf("a", "b"), ids(LibraryLogic.filter(tracks, ListFilter.ALL, setOf("L1"), links, progress)))
        assertEquals(listOf("b"), ids(LibraryLogic.filter(tracks, ListFilter.ALL, setOf("L2"), links, progress)))
    }

    @Test fun grouping() {
        val labels = listOf(Label("L2", "健身", "green", 2), Label("L1", "AI", "blue", 1))
        val links = mapOf("a" to setOf("L1"), "b" to setOf("L1", "L2"), "x" to setOf("已刪除的標籤"))
        val groups = LibraryLogic.group(tracks, labels, links)
        assertEquals(listOf("AI", "健身", "未分類"), groups.map { it.title })
        assertEquals(listOf("a", "b"), ids(groups[0].tracks))
        assertEquals(listOf("b"), ids(groups[1].tracks))      // 貼兩個標籤 → 出現在兩組
        assertEquals(listOf("c"), ids(groups[2].tracks))
        assertNull(groups[2].label)
        assertEquals(listOf("未分類"), LibraryLogic.group(tracks, emptyList(), emptyMap()).map { it.title })
    }

    @Test fun smartLists() {
        assertEquals(listOf("b", "c"), ids(LibraryLogic.smartUnfinished(tracks, progress)))
        assertEquals(listOf("c"), ids(LibraryLogic.smartInProgress(tracks, progress)))
        assertEquals(listOf("c", "a"), ids(LibraryLogic.smartRecent(tracks, progress)))
        assertEquals(listOf("b", "c", "a"), ids(LibraryLogic.smartLatest(tracks)))
    }

    @Test fun trashDays() {
        val deleted = Instant.parse("2026-10-20T12:00:00Z")
        assertEquals(7L, LibraryLogic.trashDaysLeft(deleted, deleted.plusSeconds(3600)))
        assertEquals(4L, LibraryLogic.trashDaysLeft(deleted, deleted.plusSeconds(3 * 86400 + 60)))
        assertEquals(0L, LibraryLogic.trashDaysLeft(deleted, deleted.plusSeconds(9 * 86400)))
    }

    @Test fun move() {
        assertEquals(listOf("b", "a", "c"), LibraryLogic.move(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("c", "a", "b"), LibraryLogic.move(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b", "c"), LibraryLogic.move(listOf("a", "b", "c"), 0, 5))
    }

    @Test fun parseAndCache() {
        val playlists = LibraryLogic.parsePlaylists(
            """[{"id":"p2","name":"學習","sort_order":2,"sf_playlist_item":[{"track_id":"b","sort_order":1},{"track_id":"a","sort_order":0}]},
               {"id":"p1","name":"通勤","sort_order":1,"sf_playlist_item":[]}]""",
        )
        assertEquals(listOf("通勤", "學習"), playlists.map { it.name })
        assertEquals(listOf("a", "b"), playlists[1].trackIds)
        val links = LibraryLogic.parseTrackLabels("""[{"track_id":"a","label_id":"L1"},{"track_id":"a","label_id":"L2"},{"track_id":"b","label_id":"L1"}]""")
        assertEquals(setOf("L1", "L2"), links["a"])
        val s = LibraryState(listOf(Label("L1", "AI", "blue", 1)), links, listOf(Playlist("p1", "通勤", 1, listOf("b", "a"))))
        assertEquals(s, LibraryLogic.fromCache(LibraryLogic.toCache(s)))
        assertNull(LibraryLogic.fromCache("壞掉"))
    }

    @Test fun sortLabels() {
        assertEquals("加入日期（新到舊）", SortSpec().label)
        assertEquals("標題（A→Z）", SortSpec(SortKey.TITLE, false).label)
    }
}
