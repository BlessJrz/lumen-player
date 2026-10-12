package com.lumen.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lumen.player.LumenApp
import com.lumen.player.MainActivity
import com.lumen.player.data.db.PlayEntity
import com.lumen.player.data.db.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Builds the playable MediaItem for a track: offline copy if downloaded, otherwise streamed from the NAS. */
fun TrackEntity.toMediaItem(): MediaItem {
    val local = localPath?.let { File(it) }?.takeIf { it.exists() }
    val uri = local?.let { Uri.fromFile(it) } ?: NasUri.of(path)
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.ifBlank { "Unknown artist" })
                .setAlbumTitle(album.ifBlank { folder.substringAfterLast('/') })
                .setAlbumArtist(albumArtist)
                .setGenre(genre)
                .setTrackNumber(trackNo)
                .setArtworkUri(artPath?.let { Uri.fromFile(File(it)) })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var equalizer: Equalizer? = null
    private var currentPlayId = 0L
    private var progressJob: Job? = null
    private var retries = 0

    private val c get() = (application as LumenApp).container

    override fun onCreate() {
        super.onCreate()
        val dataSource = DefaultDataSource.Factory(this, NasDataSource.Factory(c.nas))
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true) // pause when headphones/Bluetooth disconnect
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        c.playerHolder.player = player

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra("open", "player").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Controllers send ids only; resolve them to playable items from the library.
                override fun onAddMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = scope.future(Dispatchers.IO) {
                    val ids = mediaItems.mapNotNull { it.mediaId.toLongOrNull() }
                    val found = c.library.tracks(ids).associateBy { it.id }
                    mediaItems.mapNotNull { mi -> mi.mediaId.toLongOrNull()?.let { found[it] }?.toMediaItem() }.toMutableList()
                }
            })
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retries = 0
                val id = mediaItem?.mediaId?.toLongOrNull() ?: return
                scope.launch(Dispatchers.IO) {
                    c.db.tracks().bumpPlay(id, System.currentTimeMillis())
                    currentPlayId = c.db.plays().insert(PlayEntity(trackId = id, playedAt = System.currentTimeMillis()))
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                progressJob?.cancel()
                if (isPlaying) progressJob = scope.launch {
                    while (isActive) { saveProgress(); delay(15_000) }
                } else scope.launch { saveProgress() }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) { attachEq(audioSessionId) }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // NAS dropped (e.g. left home Wi-Fi): reconnect (LAN → Tailscale) and retry a couple of times,
                // then move on to the next song instead of looping forever.
                c.nas.invalidate()
                retries++
                scope.launch {
                    delay(1500L * retries)
                    if (retries <= 2) { player.prepare(); player.play() }
                    else if (player.hasNextMediaItem()) { retries = 0; player.seekToNextMediaItem(); player.prepare(); player.play() }
                }
            }
        })

        scope.launch {
            c.settings.settings.collectLatest { s ->
                player.setPlaybackSpeed(s.playbackSpeed)
                player.pauseAtEndOfMediaItems = false
                applyEq(s.eqEnabled, s.eqBands)
            }
        }
        restoreQueue()
    }

    private suspend fun saveProgress() {
        if (currentPlayId == 0L) return
        val pos = player.currentPosition
        val dur = player.duration
        val done = dur > 0 && pos > dur - 15_000
        val id = currentPlayId
        kotlinx.coroutines.withContext(Dispatchers.IO) { c.db.plays().progress(id, pos, done) }
        c.queueStore.save(player)
    }

    private fun restoreQueue() {
        scope.launch {
            val saved = c.queueStore.load() ?: return@launch
            val tracks = kotlinx.coroutines.withContext(Dispatchers.IO) { c.library.tracks(saved.ids) }
            if (tracks.isEmpty() || player.mediaItemCount > 0) return@launch
            player.setMediaItems(tracks.map { it.toMediaItem() }, saved.index.coerceIn(0, tracks.lastIndex), saved.positionMs)
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeat
            player.prepare()
        }
    }

    private fun attachEq(sessionId: Int) {
        runCatching { equalizer?.release() }
        equalizer = runCatching { Equalizer(0, sessionId) }.getOrNull()
        c.playerHolder.equalizer = equalizer
        scope.launch {
            val s = c.settings.settings.first()
            applyEq(s.eqEnabled, s.eqBands)
        }
    }

    private fun applyEq(enabled: Boolean, bands: List<Int>) {
        val eq = equalizer ?: return
        runCatching {
            eq.enabled = enabled
            val n = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            for (i in 0 until n) {
                val gainDb = bands.getOrElse((i * bands.size) / maxOf(n, 1)) { 0 }
                val mb = (gainDb * 100).coerceIn(range[0].toInt(), range[1].toInt())
                eq.setBandLevel(i.toShort(), mb.toShort())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        runCatching { c.queueStore.save(player) }
        session?.run { player.release(); release() }
        session = null
        runCatching { equalizer?.release() }
        c.playerHolder.player = null
        scope.cancel()
        super.onDestroy()
    }
}

@Suppress("unused")
private fun <T> immediate(v: T): ListenableFuture<T> = Futures.immediateFuture(v)
