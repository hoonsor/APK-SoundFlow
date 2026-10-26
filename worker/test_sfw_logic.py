import re
import unittest

import sfw_logic as L


class LogicTest(unittest.TestCase):
    def test_bitrate_fits_50mb(self):
        for minutes in (5, 60, 100, 101, 140, 141, 180):
            sec = minutes * 60
            kbps = L.choose_bitrate(sec)
            self.assertLess(L.estimate_bytes(sec, kbps), L.MAX_BYTES, f"{minutes} 分鐘 {kbps}k 超過 50 MB")
        self.assertEqual(64, L.choose_bitrate(None))

    def test_object_name(self):
        name = L.object_name("00I5wAy9i3M")
        self.assertRegex(name, r"^yt/00I5wAy9i3M-[0-9a-f]{8}\.opus$")
        with self.assertRaises(ValueError):
            L.object_name("../etc/passwd")

    def test_errors(self):
        self.assertEqual((L.explain_error("ERROR: [youtube] x: Private video. Sign in if you've been granted access")[0]), "這是私人影片，無法轉檔")
        msg, transient = L.explain_error("ERROR: [youtube] x: Sign in to confirm you’re not a bot")
        self.assertTrue(transient)
        self.assertIn("機器人", msg)
        self.assertFalse(L.explain_error("ERROR: Video unavailable")[1])
        msg, transient = L.explain_error("something weird\nERROR: boom")
        self.assertEqual("轉檔失敗：ERROR: boom", msg)
        self.assertFalse(transient)

    def test_check_metadata(self):
        self.assertIsNone(L.check_metadata({"duration": 600}))
        self.assertIn("直播", L.check_metadata({"is_live": True}))
        self.assertIn("太長", L.check_metadata({"duration": 4 * 3600}))
        self.assertIn("首播", L.check_metadata({"live_status": "is_upcoming"}))

    def test_track_row(self):
        row = L.track_row({"title": " 標題 ", "uploader": "頻道", "duration": 312.6, "thumbnail": "https://i.ytimg.com/x.jpg"}, "00I5wAy9i3M", "yt/a.opus", 123)
        self.assertEqual("標題", row["title"])
        self.assertEqual("頻道", row["channel"])
        self.assertEqual(312, row["duration_sec"])
        self.assertEqual("sf", row["storage_bucket"])
        self.assertEqual("youtube", row["kind"])
        self.assertTrue(re.match(r"https://www\.youtube\.com/watch\?v=", row["source_url"]))

    def test_purge_due(self):
        now = "2026-10-30T12:00:00+00:00"
        self.assertTrue(L.purge_due({"deleted_at": "2026-10-23T11:00:00+00:00"}, now))   # 超過 7 天
        self.assertFalse(L.purge_due({"deleted_at": "2026-10-25T12:00:00+00:00"}, now))  # 才 5 天
        self.assertTrue(L.purge_due({"deleted_at": "2026-10-29T12:00:00+00:00", "purge_requested_at": "2026-10-29T13:00:00+00:00"}, now))
        self.assertFalse(L.purge_due({"deleted_at": "2026-10-01T00:00:00+00:00", "purged_at": "2026-10-08T00:00:00+00:00"}, now))
        self.assertFalse(L.purge_due({}, now))

    def test_should_delete_file(self):
        base = {"tier": "supabase", "storage_bucket": "nlm", "storage_path": "a.m4a"}
        self.assertTrue(L.should_delete_file({**base, "kind": "youtube", "storage_bucket": "sf"}))
        self.assertFalse(L.should_delete_file({**base, "kind": "podcast", "delete_source": False}))
        self.assertTrue(L.should_delete_file({**base, "kind": "podcast", "delete_source": True}))
        self.assertFalse(L.should_delete_file({**base, "kind": "youtube", "storage_path": None}))
        self.assertFalse(L.should_delete_file({**base, "kind": "youtube", "tier": "drive"}))


if __name__ == "__main__":
    unittest.main()
