package com.lumen.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.lumen.player.data.nas.NasManager
import java.io.File

/**
 * On-phone cache of NAS audio (least-recently-used, 2 GB). Songs you've played, and the next songs in the queue,
 * start instantly instead of waiting on the NAS / Tailscale.
 */
@OptIn(UnstableApi::class)
object MediaCache {
    private const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    /** How much of each upcoming song to fetch ahead of time (~1 minute of audio at 320 kbps is 2.4 MB). */
    const val PREFETCH_BYTES = 3L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    fun factory(context: Context, nas: NasManager): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(NasDataSource.Factory(nas))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Blocking: caches the first [bytes] of [uri]. Call from a background thread; cancel via [isCancelled]. */
    fun prefetch(context: Context, nas: NasManager, uri: android.net.Uri, bytes: Long = PREFETCH_BYTES) {
        val ds = factory(context, nas).createDataSource()
        CacheWriter(ds, DataSpec.Builder().setUri(uri).setLength(bytes).build(), null, null).cache()
    }

    fun sizeBytes(context: Context): Long = runCatching { get(context).cacheSpace }.getOrDefault(0)

    fun clear(context: Context) {
        val c = get(context)
        c.keys.toList().forEach { runCatching { c.removeResource(it) } }
    }
}
