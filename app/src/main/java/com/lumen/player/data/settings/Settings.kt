package com.lumen.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The three Liquid Glass design languages from the Figma concepts. */
enum class Design(val title: String, val blurb: String) {
    AURORA("Aurora Glass", "Frosted glass, glowing gradients, album art tints everything"),
    OBSIDIAN("Obsidian Glass", "Cinematic black, hairline light, volt accent"),
    FLUID("Fluid Glass", "Expressive gradients, vinyl player, morphing pills"),
}

enum class ThemeMode(val title: String) { SYSTEM("Follow system"), DARK("Dark"), LIGHT("Light") }

enum class AudioFormat(val title: String, val ext: String, val ytdlp: String) {
    MP3("MP3 · 320 kbps", "mp3", "mp3"),
    M4A("M4A · AAC", "m4a", "m4a"),
    OPUS("Opus · best", "opus", "opus"),
}

data class AppSettings(
    val design: Design = Design.AURORA,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val artworkTint: Boolean = true,
    val reduceTransparency: Boolean = false,
    val ambientMotion: Boolean = true,
    val gapless: Boolean = true,
    val playbackSpeed: Float = 1f,
    val wifiOnlyDownloads: Boolean = true,
    val storageLimitMb: Int = 4096,
    val scanIntervalMin: Int = 30,
    val autoMetadata: Boolean = true,
    val autoArtwork: Boolean = true,
    val autoLyrics: Boolean = true,
    val writeTagsToFile: Boolean = false,
    val googleApiKey: String = "",
    val googleCx: String = "",
    val importFormat: AudioFormat = AudioFormat.MP3,
    val importFolder: String = "Imported",
    val importToNas: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqBands: List<Int> = List(5) { 0 },
    val lastScan: Long = 0L,
    val onboarded: Boolean = false,
)

private val Context.store by preferencesDataStore("lumen_settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val design = stringPreferencesKey("design")
        val theme = stringPreferencesKey("theme")
        val tint = booleanPreferencesKey("tint")
        val reduce = booleanPreferencesKey("reduce")
        val ambient = booleanPreferencesKey("ambient")
        val gapless = booleanPreferencesKey("gapless")
        val speed = floatPreferencesKey("speed")
        val wifi = booleanPreferencesKey("wifi")
        val limit = intPreferencesKey("limit")
        val scan = intPreferencesKey("scan")
        val autoMeta = booleanPreferencesKey("autoMeta")
        val autoArt = booleanPreferencesKey("autoArt")
        val autoLyr = booleanPreferencesKey("autoLyr")
        val writeTags = booleanPreferencesKey("writeTags")
        val gKey = stringPreferencesKey("gKey")
        val gCx = stringPreferencesKey("gCx")
        val fmt = stringPreferencesKey("fmt")
        val impFolder = stringPreferencesKey("impFolder")
        val impNas = booleanPreferencesKey("impNas")
        val eqOn = booleanPreferencesKey("eqOn")
        val eqBands = stringPreferencesKey("eqBands")
        val lastScan = longPreferencesKey("lastScan")
        val onboarded = booleanPreferencesKey("onboarded")
    }

    val settings: Flow<AppSettings> = context.store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        design = this[K.design]?.let { runCatching { Design.valueOf(it) }.getOrNull() } ?: Design.AURORA,
        themeMode = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        artworkTint = this[K.tint] ?: true,
        reduceTransparency = this[K.reduce] ?: false,
        ambientMotion = this[K.ambient] ?: true,
        gapless = this[K.gapless] ?: true,
        playbackSpeed = this[K.speed] ?: 1f,
        wifiOnlyDownloads = this[K.wifi] ?: true,
        storageLimitMb = this[K.limit] ?: 4096,
        scanIntervalMin = this[K.scan] ?: 30,
        autoMetadata = this[K.autoMeta] ?: true,
        autoArtwork = this[K.autoArt] ?: true,
        autoLyrics = this[K.autoLyr] ?: true,
        writeTagsToFile = this[K.writeTags] ?: false,
        googleApiKey = this[K.gKey] ?: "",
        googleCx = this[K.gCx] ?: "",
        importFormat = this[K.fmt]?.let { runCatching { AudioFormat.valueOf(it) }.getOrNull() } ?: AudioFormat.MP3,
        importFolder = this[K.impFolder] ?: "Imported",
        importToNas = this[K.impNas] ?: true,
        eqEnabled = this[K.eqOn] ?: false,
        eqBands = this[K.eqBands]?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 5 } ?: List(5) { 0 },
        lastScan = this[K.lastScan] ?: 0L,
        onboarded = this[K.onboarded] ?: false,
    )

    suspend fun setDesign(v: Design) = context.store.edit { it[K.design] = v.name }
    suspend fun setThemeMode(v: ThemeMode) = context.store.edit { it[K.theme] = v.name }
    suspend fun setArtworkTint(v: Boolean) = context.store.edit { it[K.tint] = v }
    suspend fun setReduceTransparency(v: Boolean) = context.store.edit { it[K.reduce] = v }
    suspend fun setAmbientMotion(v: Boolean) = context.store.edit { it[K.ambient] = v }
    suspend fun setGapless(v: Boolean) = context.store.edit { it[K.gapless] = v }
    suspend fun setSpeed(v: Float) = context.store.edit { it[K.speed] = v }
    suspend fun setWifiOnly(v: Boolean) = context.store.edit { it[K.wifi] = v }
    suspend fun setStorageLimit(v: Int) = context.store.edit { it[K.limit] = v }
    suspend fun setScanInterval(v: Int) = context.store.edit { it[K.scan] = v }
    suspend fun setAutoMetadata(v: Boolean) = context.store.edit { it[K.autoMeta] = v }
    suspend fun setAutoArtwork(v: Boolean) = context.store.edit { it[K.autoArt] = v }
    suspend fun setAutoLyrics(v: Boolean) = context.store.edit { it[K.autoLyr] = v }
    suspend fun setWriteTags(v: Boolean) = context.store.edit { it[K.writeTags] = v }
    suspend fun setGoogle(key: String, cx: String) = context.store.edit { it[K.gKey] = key.trim(); it[K.gCx] = cx.trim() }
    suspend fun setImportFormat(v: AudioFormat) = context.store.edit { it[K.fmt] = v.name }
    suspend fun setImportFolder(v: String) = context.store.edit { it[K.impFolder] = v }
    suspend fun setImportToNas(v: Boolean) = context.store.edit { it[K.impNas] = v }
    suspend fun setEqEnabled(v: Boolean) = context.store.edit { it[K.eqOn] = v }
    suspend fun setEqBands(v: List<Int>) = context.store.edit { it[K.eqBands] = v.joinToString(",") }
    suspend fun setLastScan(v: Long) = context.store.edit { it[K.lastScan] = v }
    suspend fun setOnboarded(v: Boolean) = context.store.edit { it[K.onboarded] = v }
}
