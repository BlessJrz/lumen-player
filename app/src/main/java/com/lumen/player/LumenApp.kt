package com.lumen.player

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.download.DownloadRepository
import com.lumen.player.data.library.LibraryRepository
import com.lumen.player.data.meta.ArtworkStore
import com.lumen.player.data.meta.MetadataRepository
import com.lumen.player.data.nas.NasConfigStore
import com.lumen.player.data.nas.NasManager
import com.lumen.player.data.scan.LibraryScanner
import com.lumen.player.data.scan.ScanState
import com.lumen.player.data.settings.SettingsRepository
import com.lumen.player.data.youtube.YouTubeImporter
import com.lumen.player.playback.OutputRouter
import com.lumen.player.playback.PlayerConnection
import com.lumen.player.playback.PlayerHolder
import com.lumen.player.playback.QueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-rolled dependency container — one instance for the whole process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    val db = LumenDatabase.build(context)
    val settings = SettingsRepository(context)
    val nas = NasManager(NasConfigStore(context), http)
    val art = ArtworkStore(context)
    val scanner = LibraryScanner(nas, db, art)
    val library = LibraryRepository(db)
    val metadata = MetadataRepository(context, db, http, art, nas, settings)
    val downloads = DownloadRepository(context, db, settings)
    val ytAccount = com.lumen.player.data.youtube.YouTubeAccount(context)
    val importer = YouTubeImporter(context, db, nas, scanner, metadata, downloads, appScope, ytAccount)
    val playerHolder = PlayerHolder()
    val queueStore = QueueStore(context)
    val player = PlayerConnection(context, library, appScope)
    val output = OutputRouter(context, playerHolder)

    /** Full refresh: scan → tags → online metadata. Safe to call repeatedly. */
    fun refreshLibrary() {
        appScope.launch {
            val r = scanner.scan()
            if (r is ScanState.Finished) {
                settings.setLastScan(r.at)
                metadata.enrichPending(400)
            }
        }
    }

    fun schedulePeriodicScan(minutes: Int) {
        val req = PeriodicWorkRequestBuilder<ScanWorker>(maxOf(15, minutes).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("lumen-scan", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun scanNowInBackground() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "lumen-scan-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }
}

/** Background library sync: picks up songs added to / removed from the NAS. */
class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as LumenApp).container
        if (!c.nas.config.isConfigured) return Result.success()
        val r = c.scanner.scan()
        if (r is ScanState.Finished) { c.settings.setLastScan(r.at); c.metadata.enrichPending(150) }
        return if (r is ScanState.Failed) Result.retry() else Result.success()
    }
}

class LumenApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.downloads.reconcile()
            val s = container.settings.settings.first()
            if (container.nas.config.isConfigured) container.schedulePeriodicScan(s.scanIntervalMin)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).crossfade(220).respectCacheHeaders(false).build()
}
