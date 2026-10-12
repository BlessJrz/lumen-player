package com.lumen.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.library.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val connected: Boolean = false,
    val current: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queue: List<TrackEntity> = emptyList(),
    val index: Int = 0,
    val error: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val hasMedia get() = current != null
}

data class SleepState(val endsAt: Long = 0, val endOfTrack: Boolean = false, val fade: Boolean = true) {
    val active get() = endsAt > 0 || endOfTrack
}

/** UI-side bridge to the playback service. All playback requests go through here. */
class PlayerConnection(private val context: Context, private val library: LibraryRepository, private val scope: CoroutineScope) {
    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()
    private var ticker: Job? = null
    private var sleepJob: Job? = null
    private var queueIds: List<Long> = emptyList()

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { refresh() }
            })
            refresh()
            ticker = scope.launch {
                while (isActive) {
                    controller?.let { p ->
                        if (p.isPlaying) _state.value = _state.value.copy(positionMs = p.currentPosition, durationMs = p.duration.coerceAtLeast(0))
                    }
                    delay(400)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun refresh() {
        val p = controller ?: return
        val ids = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId.toLongOrNull() }
        val curId = p.currentMediaItem?.mediaId?.toLongOrNull()
        scope.launch {
            val queue = if (ids != queueIds) library.tracks(ids).also { queueIds = ids } else _state.value.queue
            val cur = curId?.let { id -> queue.firstOrNull { it.id == id } ?: library.trackNow(id) }
            _state.value = PlayerState(
                connected = true, current = cur, isPlaying = p.isPlaying, buffering = p.playbackState == Player.STATE_BUFFERING,
                positionMs = p.currentPosition, durationMs = p.duration.coerceAtLeast(0), shuffle = p.shuffleModeEnabled,
                repeat = p.repeatMode, speed = p.playbackParameters.speed, queue = queue, index = p.currentMediaItemIndex,
                error = p.playerError?.message,
            )
        }
    }

    /** Refresh the current track's metadata after edits/favorites. */
    fun reloadCurrent() { queueIds = emptyList(); refresh() }

    private fun item(t: TrackEntity) = MediaItem.Builder().setMediaId(t.id.toString()).build()

    fun play(tracks: List<TrackEntity>, start: Int = 0, shuffle: Boolean = false) {
        val p = controller ?: return
        if (tracks.isEmpty()) return
        p.shuffleModeEnabled = shuffle
        p.setMediaItems(tracks.map(::item), if (shuffle) (tracks.indices).random() else start.coerceIn(0, tracks.lastIndex), 0L)
        p.prepare()
        p.play()
    }

    /** Loads a queue without starting playback. Waits briefly for the controller to connect. */
    suspend fun load(tracks: List<TrackEntity>) {
        repeat(50) { if (controller != null) return@repeat; delay(100) }
        val p = controller ?: return
        p.setMediaItems(tracks.map(::item), 0, 0L)
    }

    fun playNext(tracks: List<TrackEntity>) {
        val p = controller ?: return
        if (p.mediaItemCount == 0) return play(tracks)
        p.addMediaItems(p.currentMediaItemIndex + 1, tracks.map(::item))
    }

    fun addToQueue(tracks: List<TrackEntity>) {
        val p = controller ?: return
        if (p.mediaItemCount == 0) return play(tracks)
        p.addMediaItems(tracks.map(::item))
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
    fun next() = controller?.seekToNextMediaItem()
    fun previous() = controller?.let { if (it.currentPosition > 4000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun seekTo(fraction: Float) = controller?.let { if (it.duration > 0) it.seekTo((it.duration * fraction).toLong()) }
    fun seekToMs(ms: Long) = controller?.seekTo(ms)
    fun skipTo(index: Int) = controller?.let { it.seekToDefaultPosition(index); it.play() }
    fun setShuffle(on: Boolean) { controller?.shuffleModeEnabled = on }
    fun cycleRepeat() {
        val p = controller ?: return
        p.repeatMode = when (p.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }
    fun setSpeed(v: Float) { controller?.setPlaybackSpeed(v) }
    fun move(from: Int, to: Int) { controller?.moveMediaItem(from, to) }
    fun remove(index: Int) { controller?.removeMediaItem(index) }
    fun clearUpcoming() { controller?.let { p -> val i = p.currentMediaItemIndex; if (p.mediaItemCount > i + 1) p.removeMediaItems(i + 1, p.mediaItemCount) } }
    fun setVolume(v: Float) { controller?.volume = v.coerceIn(0f, 1f) }
    val volume: Float get() = controller?.volume ?: 1f

    // ---------- Sleep timer ----------

    fun sleepIn(minutes: Int, fade: Boolean) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleep.value = SleepState(endsAt, false, fade)
        sleepJob = scope.launch {
            while (isActive) {
                val left = endsAt - System.currentTimeMillis()
                if (left <= 0) break
                if (fade && left < 30_000) setVolume(left / 30_000f)
                delay(500)
            }
            controller?.pause(); setVolume(1f)
            _sleep.value = SleepState()
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        _sleep.value = SleepState(0, true, false)
        sleepJob = scope.launch {
            val startId = controller?.currentMediaItem?.mediaId
            while (isActive) {
                val p = controller ?: break
                if (p.currentMediaItem?.mediaId != startId) { p.seekToPreviousMediaItem(); p.pause(); break }
                if (p.duration > 0 && p.duration - p.currentPosition < 600) { p.pause(); break }
                delay(250)
            }
            _sleep.value = SleepState()
        }
    }

    fun cancelSleep() { sleepJob?.cancel(); setVolume(1f); _sleep.value = SleepState() }
}
