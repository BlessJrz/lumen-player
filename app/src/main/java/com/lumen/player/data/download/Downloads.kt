package com.lumen.player.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumen.player.LumenApp
import com.lumen.player.R
import com.lumen.player.data.db.DlState
import com.lumen.player.data.db.DownloadEntity
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

data class StorageInfo(val offlineBytes: Long, val limitBytes: Long, val freeBytes: Long, val totalBytes: Long, val artBytes: Long)

class DownloadRepository(
    private val context: Context,
    private val db: LumenDatabase,
    private val settings: SettingsRepository,
) {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "offline").apply { mkdirs() }

    val queue: Flow<List<DownloadEntity>> = db.downloads().active()

    suspend fun enqueue(trackIds: List<Long>, group: String? = null) {
        for (id in trackIds) {
            val t = db.tracks().get(id) ?: continue
            if (t.localPath != null && File(t.localPath).exists()) continue
            val existing = db.downloads().get(id)
            if (existing == null || existing.state == DlState.FAILED || existing.state == DlState.DONE)
                db.downloads().upsert(DownloadEntity(id, DlState.QUEUED, groupName = group))
        }
        kick()
    }

    suspend fun enqueueFolder(path: String) {
        val ids = db.tracks().inFolderTree(path, if (path.isEmpty()) "%" else "$path/%").first().map { it.id }
        enqueue(ids, path.substringAfterLast('/').ifBlank { "Library" })
    }

    suspend fun enqueuePlaylist(id: Long, name: String) = enqueue(db.playlists().trackIds(id), name)

    suspend fun pause(id: Long) { db.downloads().get(id)?.let { db.downloads().upsert(it.copy(state = DlState.PAUSED)) } }
    suspend fun resume(id: Long) { db.downloads().get(id)?.let { db.downloads().upsert(it.copy(state = DlState.QUEUED, error = null)) }; kick() }
    suspend fun cancel(id: Long) { db.downloads().delete(id); File(dir, "$id.part").delete() }
    suspend fun pauseAll() = db.downloads().pauseAll()
    suspend fun resumeAll() { db.downloads().resumeAll(); kick() }

    suspend fun remove(trackIds: List<Long>) = withContext(Dispatchers.IO) {
        for (id in trackIds) {
            db.tracks().get(id)?.localPath?.let { File(it).delete() }
            db.tracks().setLocal(id, null)
            db.downloads().delete(id)
        }
    }

    suspend fun storage(): StorageInfo = withContext(Dispatchers.IO) {
        val used = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val st = StatFs(dir.absolutePath)
        val limit = settings.settings.first().storageLimitMb.toLong() * 1024 * 1024
        StorageInfo(used, limit, st.availableBytes, st.totalBytes, (context.filesDir.resolve("art")).walkTopDown().filter { it.isFile }.sumOf { it.length() })
    }

    /** Re-links offline files after a reinstall/restore and drops records whose files vanished. */
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        for (t in db.tracks().downloadedNow()) if (!File(t.localPath!!).exists()) db.tracks().setLocal(t.id, null)
        dir.listFiles()?.forEach { f ->
            val id = f.nameWithoutExtension.toLongOrNull() ?: return@forEach
            if (f.extension != "part") db.tracks().get(id)?.let { if (it.localPath == null) db.tracks().setLocal(id, f.absolutePath) }
        }
    }

    suspend fun kick() {
        db.downloads().requeueRunning()
        val wifi = settings.settings.first().wifiOnlyDownloads
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("lumen-downloads", ExistingWorkPolicy.KEEP, req)
    }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val c = (ctx.applicationContext as LumenApp).container

    override suspend fun getForegroundInfo(): ForegroundInfo = info("Downloading for offline", 0, 0)

    private fun info(text: String, progress: Int, max: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("Lumen")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
            .build()
        return ForegroundInfo(7701, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { setForeground(getForegroundInfo()) }
        val db = c.db
        val dir = c.downloads.dir
        while (true) {
            val job = db.downloads().nextQueued() ?: break
            val t = db.tracks().get(job.trackId)
            if (t == null) { db.downloads().delete(job.trackId); continue }
            val storage = c.downloads.storage()
            if (storage.offlineBytes + t.size > storage.limitBytes) {
                db.downloads().update(t.id, DlState.FAILED, 0, t.size, "Storage limit reached"); continue
            }
            val ext = t.fileName.substringAfterLast('.', "mp3")
            val part = File(dir, "${t.id}.part")
            try {
                c.nas.call { client ->
                    client.open(t.path).use { f ->
                        val total = f.size
                        RandomAccessFile(part, "rw").use { out ->
                            var pos = out.length().coerceAtMost(total) // resume where a paused download stopped
                            out.seek(pos)
                            val buf = ByteArray(512 * 1024)
                            var lastReport = 0L
                            while (pos < total) {
                                val n = f.read(pos, buf, 0, buf.size)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                pos += n
                                if (pos - lastReport > 1_000_000 || pos == total) {
                                    lastReport = pos
                                    kotlinx.coroutines.runBlocking { db.downloads().update(t.id, DlState.RUNNING, pos, total, null) }
                                    val state = kotlinx.coroutines.runBlocking { db.downloads().get(t.id)?.state }
                                    if (state == null || state == DlState.PAUSED) return@call // paused or cancelled
                                    runCatching { setForegroundAsync(info(t.title, (pos * 100 / total).toInt(), 100)) }
                                }
                            }
                            if (pos >= total) {
                                val final = File(dir, "${t.id}.$ext")
                                part.renameTo(final)
                                kotlinx.coroutines.runBlocking {
                                    db.tracks().setLocal(t.id, final.absolutePath)
                                    db.downloads().update(t.id, DlState.DONE, total, total, null)
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                db.downloads().update(t.id, DlState.FAILED, part.length(), t.size, e.message ?: "Failed")
            }
        }
        Result.success()
    }
}
