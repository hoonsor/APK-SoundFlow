package tw.hoonsor.soundflow

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.hoonsor.backup.AutoBackup
import tw.hoonsor.soundflow.backup.BackupLogic
import tw.hoonsor.soundflow.player.ProgressRules
import tw.hoonsor.soundflow.player.TrackProgress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 共用模組 android-auto-backup 的純邏輯（檔名、只刪自己的檔、保留 7 份） */
class AutoBackupTest {
    @Test fun fileName() {
        val c = Calendar.getInstance().apply { set(2026, 10, 10, 9, 5) }
        assertEquals("SoundFlow-20261110-0905.zip", AutoBackup.fileName("SoundFlow", "zip", c.time))
    }

    @Test fun onlyOwnFiles() {
        assertTrue(AutoBackup.isBackupName("SoundFlow-20261110-0905.zip", "SoundFlow", "zip"))
        assertTrue(AutoBackup.isBackupName("SoundFlow-20261110-0905 (1).zip", "SoundFlow", "zip"))
        assertFalse(AutoBackup.isBackupName("SoundFlow-LAB-20261110-0905.zip", "SoundFlow", "zip"))
        assertFalse(AutoBackup.isBackupName("SoundFlow-20261110-0905.json", "SoundFlow", "zip"))
        assertFalse(AutoBackup.isBackupName("我的筆記.zip", "SoundFlow", "zip"))
    }

    @Test fun keepNewest() {
        val names = (1..9).map { "SoundFlow-2026110$it-1200.zip" } + "notes.txt"
        val del = AutoBackup.toDelete(names, "SoundFlow", "zip", AutoBackup.KEEP)
        assertEquals(listOf("SoundFlow-20261102-1200.zip", "SoundFlow-20261101-1200.zip"), del)
        assertTrue(AutoBackup.toDelete(names.take(7), "SoundFlow", "zip", 7).isEmpty())
    }
}

class BackupTest {
    /** 模擬 filesDir 的實際內容（含 DataStore 與各種快取） */
    private val filesDir = listOf(
        "progress.json", "last_queue.json", "pending_shares.json", "widget_state.json",
        "library.json", "local_library.json", "tracks_podcast.json", "tracks_youtube.json",
        "datastore/auth.preferences_pb", "datastore/settings.preferences_pb",
        "covers/abc.jpg", "covers/local_1a2b.found.jpg", "covers/x.found.jpg.tmp", "covers\\yt_9.found.jpg",
    )

    @Test fun selectOnlyWhatCannotBeRebuilt() {
        assertEquals(
            listOf("covers/local_1a2b.found.jpg", "covers/yt_9.found.jpg", "last_queue.json", "pending_shares.json", "progress.json"),
            BackupLogic.selectFiles(filesDir),
        )
    }

    @Test fun neverLoginOrSecrets() {
        val picked = BackupLogic.selectFiles(filesDir)
        assertTrue(picked.none { "auth" in it || "datastore" in it || it.endsWith(".preferences_pb") })
        assertFalse(BackupLogic.isAllowedEntry("datastore/auth.preferences_pb"))
        assertFalse(BackupLogic.isAllowedEntry("../shared_prefs/x.xml"))
        assertFalse(BackupLogic.isAllowedEntry("covers/../../datastore/auth.preferences_pb"))
        assertFalse(BackupLogic.isAllowedEntry("library.json"))
    }

    private fun packed(appVersion: String = "0.8.0"): ByteArray {
        val entries = mapOf(
            BackupLogic.SETTINGS to BackupLogic.encodeSettings(mapOf("theme" to "amber", "speed_podcast" to 1.5f)),
            BackupLogic.PROGRESS to ProgressRules.toJson(mapOf("t1" to TrackProgress(60_000, 600_000, false, 100))).toByteArray(),
            "covers/local_1a2b.found.jpg" to byteArrayOf(1, 2, 3),
        )
        return ByteArrayOutputStream().also { BackupLogic.pack(it, entries, appVersion) }.toByteArray()
    }

    private fun names(zip: ByteArray): List<String> = ZipInputStream(ByteArrayInputStream(zip)).use { z ->
        generateSequence { z.nextEntry }.map { it.name }.toList()
    }

    @Test fun zipContents() {
        assertEquals(listOf("manifest.json", "covers/local_1a2b.found.jpg", "progress.json", "settings.json"), names(packed()))
    }

    /** 內容一樣 → 位元組一樣，AutoBackup 的 SHA-256 才認得出「沒變不另存」 */
    @Test fun deterministic() {
        assertArrayEquals(packed(), packed())
    }

    @Test fun refuseForbiddenEntryWhenPacking() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupLogic.pack(ByteArrayOutputStream(), mapOf("datastore/auth.preferences_pb" to byteArrayOf(1)), "0.8.0")
        }
    }

    @Test fun roundTrip() {
        val files = BackupLogic.unpack(ByteArrayInputStream(packed()))
        assertEquals(mapOf("theme" to "amber", "speed_podcast" to 1.5f), BackupLogic.decodeSettings(files.getValue(BackupLogic.SETTINGS)))
        assertEquals(TrackProgress(60_000, 600_000, false, 100), ProgressRules.fromJson(String(files.getValue(BackupLogic.PROGRESS)))["t1"])
        assertArrayEquals(byteArrayOf(1, 2, 3), files.getValue("covers/local_1a2b.found.jpg"))
    }

    @Test fun settingsKeepTypes() {
        val all = mapOf<String, Any>("s" to "x", "b" to true, "i" to 3, "l" to 4L, "f" to 1.25f, "d" to 2.5, "ss" to setOf("a", "b"))
        assertEquals(all, BackupLogic.decodeSettings(BackupLogic.encodeSettings(all)))
    }

    @Test fun unpackDropsUnknownEntries() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun put(n: String, b: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
                put("manifest.json", BackupLogic.manifest("0.8.0", emptyList()))
                put("datastore/auth.preferences_pb", byteArrayOf(9))
                put("../evil.json", byteArrayOf(9))
                put("progress.json", "{}".toByteArray())
            }
        }.toByteArray()
        assertEquals(setOf("manifest.json", "progress.json"), BackupLogic.unpack(ByteArrayInputStream(bytes)).keys)
    }

    @Test fun rejectOtherFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupLogic.unpack(ByteArrayInputStream("not a zip".toByteArray())) }
        val noManifest = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("progress.json")); z.write("{}".toByteArray()); z.closeEntry() }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { BackupLogic.unpack(ByteArrayInputStream(noManifest)) }
        val voiceHub = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write("""{"format":"voicehub-settings","version":1}""".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { BackupLogic.unpack(ByteArrayInputStream(voiceHub)) }
    }

    @Test fun mergeProgressKeepsNewer() {
        val current = mapOf("a" to TrackProgress(5, 10, false, 200), "b" to TrackProgress(1, 10, false, 100))
        val backup = mapOf("a" to TrackProgress(9, 10, true, 150), "b" to TrackProgress(7, 10, false, 300), "c" to TrackProgress(2, 10, false, 50))
        val m = BackupLogic.mergeProgress(current, backup)
        assertEquals(TrackProgress(5, 10, false, 200), m["a"]) // 手機比較新，保留
        assertEquals(TrackProgress(7, 10, false, 300), m["b"]) // 備份比較新，換掉
        assertEquals(TrackProgress(2, 10, false, 50), m["c"]) // 手機沒有，補上
    }
}
