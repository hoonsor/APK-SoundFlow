package tw.hoonsor.soundflow.backup

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tw.hoonsor.backup.AutoBackup
import tw.hoonsor.soundflow.BuildConfig
import tw.hoonsor.soundflow.Graph
import tw.hoonsor.soundflow.data.JobJson
import tw.hoonsor.soundflow.player.ProgressRules
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * SoundFlow 的備份與還原（內容與排除清單見 [BackupLogic]）。
 * 讀寫一律經過各 Repository，不直接改 DataStore 檔案：DataStore 在記憶體裡有快取，直接蓋檔案不會生效，還可能弄壞檔案。
 */
object SoundFlowBackup {

    /** 寫出備份 zip（AutoBackup 在背景執行緒呼叫，也給「立即備份」用） */
    fun export(ctx: Context, out: OutputStream) {
        Graph.init(ctx.applicationContext as Application)
        val dir = ctx.filesDir
        val entries = linkedMapOf<String, ByteArray>()
        entries[BackupLogic.SETTINGS] = BackupLogic.encodeSettings(runBlocking { Graph.settings.exportAll() })
        BackupLogic.selectFiles(listFiles(dir)).forEach { rel ->
            runCatching { File(dir, rel).readBytes() }.getOrNull()?.let { entries[rel] = it }
        }
        BackupLogic.pack(out, entries, BuildConfig.VERSION_NAME)
    }

    /** filesDir 第一層的檔案與 covers/ 裡的檔案（相對路徑） */
    private fun listFiles(dir: File): List<String> {
        val top = dir.listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
        val covers = File(dir, "covers").listFiles()?.filter { it.isFile }?.map { "covers/${it.name}" }.orEmpty()
        return top + covers
    }

    data class Restored(val settings: Int, val progress: Int, val covers: Int, val pending: Int, val queue: Boolean) {
        fun summary(): String = buildList {
            add("設定 $settings 項")
            add("收聽進度 $progress 集")
            if (covers > 0) add("封面 $covers 張")
            if (pending > 0) add("待送出分享 $pending 筆")
            if (queue) add("上次的播放清單")
        }.joinToString("、")
    }

    /**
     * 從備份檔還原。設定整份取代；收聽進度與待送出分享用合併（不會弄丟備份之後的新資料）；
     * 封面與播放清單快照直接寫回。格式不對丟 IllegalArgumentException。
     */
    suspend fun restore(ctx: Context, input: InputStream): Restored {
        val files = withContext(Dispatchers.IO) { BackupLogic.unpack(input) }
        val dir = ctx.filesDir

        val settings = files[BackupLogic.SETTINGS]?.let { BackupLogic.decodeSettings(it) }
        settings?.let { Graph.settings.importAll(it) }

        val progress = files[BackupLogic.PROGRESS]?.let { ProgressRules.fromJson(String(it, Charsets.UTF_8)) }.orEmpty()
        if (progress.isNotEmpty()) Graph.progress.mergeFrom(progress)

        val pending = files[BackupLogic.PENDING]?.let { JobJson.pendingFromJson(String(it, Charsets.UTF_8)) }.orEmpty()
        if (pending.isNotEmpty()) Graph.jobs.mergePending(pending)

        val coverFiles = files.filterKeys { it.startsWith(BackupLogic.COVER_DIR) }
        withContext(Dispatchers.IO) {
            coverFiles.forEach { (rel, bytes) ->
                val target = File(dir, rel)
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
            }
            files[BackupLogic.QUEUE]?.let { File(dir, BackupLogic.QUEUE).writeBytes(it) }
        }
        if (coverFiles.isNotEmpty()) Graph.covers.reloaded()

        return Restored(settings?.size ?: 0, progress.size, coverFiles.size, pending.size, files.containsKey(BackupLogic.QUEUE))
    }
}

/** 每日自動備份的來源（AndroidManifest 的 tw.hoonsor.backup.SOURCE 指到這裡） */
class SoundFlowBackupSource : AutoBackup.Source {
    /** LAB 版另放一個資料夾，不跟正式版混在一起、互刪不到 */
    override fun appName() = if (BuildConfig.FLAVOR == "lab") "SoundFlow-LAB" else "SoundFlow"
    override fun extension() = "zip"
    override fun mimeType() = "application/zip"
    override fun write(context: Context, out: OutputStream) = SoundFlowBackup.export(context, out)
}
