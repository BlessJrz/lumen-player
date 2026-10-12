package com.lumen.player.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.Equalizer
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/** In-process handle to the service's player for features the MediaController API doesn't cover (EQ, output device). */
class PlayerHolder {
    @Volatile var player: ExoPlayer? = null
    @Volatile var equalizer: Equalizer? = null

    fun eqBandFrequencies(): List<Int> = runCatching {
        val eq = equalizer ?: return@runCatching listOf(60, 230, 910, 3600, 14000)
        (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 }
    }.getOrDefault(listOf(60, 230, 910, 3600, 14000))
}

data class OutputDevice(val id: Int, val name: String, val kind: String, val info: AudioDeviceInfo?)

/** Audio output picker backed by AudioManager + ExoPlayer.setPreferredAudioDevice. */
class OutputRouter(private val context: Context, private val holder: PlayerHolder) {
    private val am = context.getSystemService(AudioManager::class.java)

    fun devices(): List<OutputDevice> {
        val list = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).mapNotNull { d ->
            val kind = when (d.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "phone"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "bluetooth"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> "headphones"
                AudioDeviceInfo.TYPE_HDMI -> "cast"
                else -> null
            } ?: return@mapNotNull null
            val name = when (kind) {
                "phone" -> "This phone"
                else -> d.productName?.toString()?.takeIf { it.isNotBlank() } ?: kind.replaceFirstChar { it.uppercase() }
            }
            OutputDevice(d.id, name, kind, d)
        }
        return list.distinctBy { it.name + it.kind }
    }

    @OptIn(UnstableApi::class)
    fun select(d: OutputDevice?) { holder.player?.setPreferredAudioDevice(d?.info) }

    @OptIn(UnstableApi::class)
    fun currentId(): Int? = holder.player?.let { p ->
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }?.id
            ?: am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }?.id
    }
}

/** Remembers the queue so it survives restarts. */
class QueueStore(context: Context) {
    data class Saved(val ids: List<Long>, val index: Int, val positionMs: Long, val shuffle: Boolean, val repeat: Int)
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)

    fun save(p: Player) {
        if (p.mediaItemCount == 0) return
        val ids = (0 until p.mediaItemCount).mapNotNull { p.getMediaItemAt(it).mediaId.toLongOrNull() }
        prefs.edit().putString("ids", ids.joinToString(",")).putInt("index", p.currentMediaItemIndex)
            .putLong("pos", p.currentPosition).putBoolean("shuffle", p.shuffleModeEnabled).putInt("repeat", p.repeatMode).apply()
    }

    fun load(): Saved? {
        val ids = prefs.getString("ids", null)?.split(",")?.mapNotNull { it.toLongOrNull() }?.takeIf { it.isNotEmpty() } ?: return null
        return Saved(ids, prefs.getInt("index", 0), prefs.getLong("pos", 0), prefs.getBoolean("shuffle", false), prefs.getInt("repeat", Player.REPEAT_MODE_OFF))
    }
}
