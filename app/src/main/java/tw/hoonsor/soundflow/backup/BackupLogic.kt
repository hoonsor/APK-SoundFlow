package tw.hoonsor.soundflow.backup

import org.json.JSONArray
import org.json.JSONObject
import tw.hoonsor.soundflow.player.TrackProgress
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 備份檔（zip）的純邏輯：決定放哪些檔、打包、解包、設定的編碼、進度合併。
 * 不碰 Android API，全部有 JVM 測試（BackupTest）。
 *
 * zip 內容：
 * - manifest.json：格式名、格式版本、APP 版本、內容清單
 * - settings.json：DataStore "settings" 的內容（每個值記型別）
 * - progress.json：收聽進度（只存在手機，雲端沒有）
 * - last_queue.json：上次的播放清單快照（小工具與「繼續播放」用）
 * - pending_shares.json：沒網路時存下、還沒送出的 YouTube 分享（有才放）
 * - covers/<id>.found.jpg：倬幫本機音檔挑的封面（從網路或手機選的，雲端沒有）
 *
 * 刻意不放：登入（DataStore "auth"＋SecureBox 金鑰）、雲端快取（library.json、tracks_*.json）、
 * 可重抓的封面（covers/<id>.jpg）、本機音檔索引（local_library.json，資料夾授權換機或重裝就失效）、
 * 小工具狀態（widget_state.json）、找封面的暫存（SharedPreferences cover_pending）。
 */
object BackupLogic {
    const val FORMAT = "soundflow-backup"
    const val VERSION = 1

    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val PROGRESS = "progress.json"
    const val QUEUE = "last_queue.json"
    const val PENDING = "pending_shares.json"
    const val COVER_DIR = "covers/"

    /** 直接從 filesDir 複製的檔案（相對路徑） */
    val PLAIN_FILES = listOf(PROGRESS, QUEUE, PENDING)

    /** 單一項目上限：封面最大幾百 KB、JSON 更小；超過就當作不是自己的備份檔 */
    private const val MAX_ENTRY = 10_000_000
    private const val MAX_TOTAL = 300_000_000L

    /** zip 項目的時間固定，內容一樣時整個檔案就一樣，AutoBackup 才認得出「沒變不另存」 */
    private const val FIXED_TIME = 1_262_304_000_000L // 2010-01-01

    private val COVER = Regex("""covers/[A-Za-z0-9_-]+\.found\.jpg""")

    /** filesDir 裡哪些檔要備份（傳入相對路徑）。登入、金鑰、快取一律不放 */
    fun selectFiles(relativePaths: Collection<String>): List<String> =
        relativePaths.map { it.replace('\\', '/') }.filter { it in PLAIN_FILES || COVER.matches(it) }.distinct().sorted()

    /** 還原時只接受這些名稱：擋掉 ../ 之類的路徑，也不會寫到登入資料 */
    fun isAllowedEntry(name: String): Boolean = name == MANIFEST || name == SETTINGS || name in PLAIN_FILES || COVER.matches(name)

    fun manifest(appVersion: String, contents: List<String>): ByteArray =
        JSONObject().put("format", FORMAT).put("version", VERSION).put("app", "SoundFlow").put("appVersion", appVersion)
            .put("contents", JSONArray(contents)).toString(2).toByteArray(Charsets.UTF_8)

    /** 打包：manifest 先放，其餘依名稱排序；同樣的輸入一定得到同樣的位元組 */
    fun pack(out: OutputStream, entries: Map<String, ByteArray>, appVersion: String) {
        val names = entries.keys.filter { it != MANIFEST }.sorted()
        names.forEach { require(isAllowedEntry(it)) { "不能放進備份的項目：$it" } }
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
                zip.write(bytes)
                zip.closeEntry()
            }
            put(MANIFEST, manifest(appVersion, names))
            names.forEach { put(it, entries.getValue(it)) }
        }
    }

    /** 解包並檢查格式；不是 SoundFlow 的備份檔丟 IllegalArgumentException（訊息可直接顯示） */
    fun unpack(input: InputStream): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        buf.write(chunk, 0, n)
                        if (buf.size() > MAX_ENTRY) throw IllegalArgumentException("備份檔裡的 $name 太大")
                    }
                    total += buf.size()
                    if (total > MAX_TOTAL) throw IllegalArgumentException("備份檔太大")
                    if (isAllowedEntry(name)) out[name] = buf.toByteArray() // 其他名稱直接略過，不寫進手機
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw IllegalArgumentException("這不是 zip 備份檔")
        }
        val m = out[MANIFEST]?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
            ?: throw IllegalArgumentException("這不是 SoundFlow 的備份檔（沒有 manifest.json）")
        require(m.optString("format") == FORMAT) { "這不是 SoundFlow 的備份檔" }
        require(m.optInt("version", 0) in 1..VERSION) { "備份檔的格式比這個版本新，請先更新 SoundFlow" }
        return out
    }

    // ---------------- 設定（DataStore Preferences） ----------------

    /** 每個值記型別，還原時才放得回同一種 key */
    fun encodeSettings(all: Map<String, Any?>): ByteArray {
        val prefs = JSONObject()
        all.toSortedMap().forEach { (k, v) ->
            val (t, value) = when (v) {
                is String -> "s" to v
                is Boolean -> "b" to v
                is Int -> "i" to v
                is Long -> "l" to v
                is Float -> "f" to v.toDouble()
                is Double -> "d" to v
                is Set<*> -> "ss" to JSONArray(v.map { it.toString() }.sorted())
                else -> return@forEach
            }
            prefs.put(k, JSONObject().put("t", t).put("v", value))
        }
        return JSONObject().put("prefs", prefs).toString(2).toByteArray(Charsets.UTF_8)
    }

    fun decodeSettings(bytes: ByteArray): Map<String, Any> {
        val prefs = JSONObject(String(bytes, Charsets.UTF_8)).optJSONObject("prefs") ?: throw IllegalArgumentException("備份檔的設定內容壞了")
        val out = linkedMapOf<String, Any>()
        prefs.keys().forEach { k ->
            val o = prefs.optJSONObject(k) ?: return@forEach
            out[k] = when (o.optString("t")) {
                "s" -> o.getString("v")
                "b" -> o.getBoolean("v")
                "i" -> o.getInt("v")
                "l" -> o.getLong("v")
                "f" -> o.getDouble("v").toFloat()
                "d" -> o.getDouble("v")
                "ss" -> o.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> return@forEach
            }
        }
        return out
    }

    // ---------------- 收聽進度 ----------------

    /** 還原進度時合併：同一集取比較新的那筆，備份之後才聽的進度不會被舊備份蓋掉 */
    fun mergeProgress(current: Map<String, TrackProgress>, backup: Map<String, TrackProgress>): Map<String, TrackProgress> {
        val out = current.toMutableMap()
        backup.forEach { (id, p) -> if ((out[id]?.updatedAt ?: Long.MIN_VALUE) < p.updatedAt) out[id] = p }
        return out
    }
}
