package com.lumen.player.data.youtube

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.db.MetaSource
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.download.DownloadRepository
import com.lumen.player.data.meta.MetadataRepository
import com.lumen.player.data.nas.NasManager
import com.lumen.player.data.nas.joinPath
import com.lumen.player.data.scan.FilenameParser
import com.lumen.player.data.scan.LibraryScanner
import com.lumen.player.data.settings.AudioFormat
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class VideoMeta(val url: String, val id: String, val title: String, val channel: String, val durationSec: Int, val thumbnail: String?)

enum class ImportStep(val label: String) { FETCH("Fetch"), DOWNLOAD("Download"), CONVERT("Convert"), SAVE("Save"), TAG("Tag") }

sealed interface ImportState {
    data object Idle : ImportState
    data object Preparing : ImportState
    data class Fetching(val url: String) : ImportState
    data class Ready(val meta: VideoMeta) : ImportState
    data class Running(val meta: VideoMeta, val step: ImportStep, val progress: Float, val detail: String) : ImportState
    data class Done(val meta: VideoMeta, val trackId: Long, val savedTo: String) : ImportState
    data class Failed(val message: String, val meta: VideoMeta? = null) : ImportState
}

data class ImportHistoryItem(val title: String, val savedTo: String, val at: Long)

/**
 * Imports audio from a YouTube link the user owns or is authorized to download: yt-dlp fetches the audio,
 * FFmpeg converts it (MP3 by default), then the file is saved to the chosen NAS folder (or the phone),
 * scanned, and enriched with metadata.
 */
class YouTubeImporter(
    private val context: Context,
    private val db: LumenDatabase,
    private val nas: NasManager,
    private val scanner: LibraryScanner,
    private val meta: MetadataRepository,
    private val downloads: DownloadRepository,
    private val scope: CoroutineScope,
    val account: YouTubeAccount,
) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    private val _history = MutableStateFlow<List<ImportHistoryItem>>(emptyList())
    val history: StateFlow<List<ImportHistoryItem>> = _history.asStateFlow()

    private val initLock = Mutex()
    @Volatile private var initialized = false
    private var job: Job? = null
    private val processId = "lumen-import"

    private suspend fun ensureInit() = initLock.withLock {
        if (initialized) return@withLock
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
        }
        initialized = true
    }

    fun extractUrl(text: String): String? =
        Regex("""https?://(?:www\.|m\.|music\.)?(?:youtube\.com/(?:watch\?[^\s]*v=|shorts/|live/)|youtu\.be/)[\w\-]{6,}[^\s]*""")
            .find(text)?.value

    fun fetch(link: String) {
        val url = extractUrl(link) ?: link.trim()
        job?.cancel()
        job = scope.launch {
            try {
                _state.value = ImportState.Preparing
                ensureInit()
                _state.value = ImportState.Fetching(url)
                val info = withContext(Dispatchers.IO) { YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply { addOption("--no-playlist"); withCookies() }) }
                _state.value = ImportState.Ready(
                    VideoMeta(url, info.id ?: "", info.title ?: "Untitled", info.uploader ?: "", info.duration, info.thumbnail)
                )
            } catch (t: Throwable) {
                _state.value = ImportState.Failed(friendly(t))
            }
        }
    }

    /** Keeps the bundled yt-dlp current — YouTube changes often and old versions stop working. */
    fun updateEngine(onDone: (String) -> Unit) {
        scope.launch {
            val msg = runCatching {
                ensureInit()
                withContext(Dispatchers.IO) { YoutubeDL.getInstance().updateYoutubeDL(context) }
                "Import engine is up to date"
            }.getOrElse { "Update failed: ${it.message}" }
            onDone(msg)
        }
    }

    private fun YoutubeDLRequest.withCookies() { account.cookieFile?.let { addOption("--cookies", it.absolutePath) } }

    fun start(m: VideoMeta, format: AudioFormat, toNas: Boolean, nasFolder: String, title: String, artist: String) {
        job?.cancel()
        job = scope.launch {
            try {
                ensureInit()
                val (id, savedTo) = download(m, format, toNas, nasFolder, title, artist)
                _state.value = ImportState.Done(m, id, savedTo)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) { _state.value = ImportState.Idle; throw t }
                _state.value = ImportState.Failed(friendly(t), m)
            }
        }
    }

    /** Downloads, converts and saves one video. Returns the new track id and where it was saved. */
    private suspend fun download(m: VideoMeta, format: AudioFormat, toNas: Boolean, nasFolder: String, title: String, artist: String): Pair<Long, String> {
        val work = File(context.cacheDir, "yt").apply { deleteRecursively(); mkdirs() }
        try {
            _state.value = ImportState.Running(m, ImportStep.DOWNLOAD, 0f, "Starting…")
            val req = YoutubeDLRequest(m.url).apply {
                addOption("--no-playlist")
                addOption("-x")
                addOption("--audio-format", format.ytdlp)
                addOption("--audio-quality", "0")
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                addOption("-o", File(work, "%(title).120B.%(ext)s").absolutePath)
                withCookies()
            }
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(req, processId) { progress, _, line ->
                    val step = when {
                        line.contains("[ExtractAudio]") || line.contains("[ffmpeg]") || line.contains("Destination") && line.contains(".${format.ext}") -> ImportStep.CONVERT
                        line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") -> ImportStep.TAG
                        else -> ImportStep.DOWNLOAD
                    }
                    _state.value = ImportState.Running(m, step, (progress / 100f).coerceIn(0f, 1f), line.take(80))
                }
            }
            val out = work.listFiles()?.filter { it.extension.equals(format.ext, true) }?.maxByOrNull { it.length() }
                ?: work.listFiles()?.filter { it.extension !in setOf("jpg", "webp", "png", "part", "json") }?.maxByOrNull { it.length() }
                ?: error("Conversion produced no audio file")
            val niceName = sanitize(if (artist.isNotBlank()) "$artist - $title" else title.ifBlank { out.nameWithoutExtension }) + "." + out.extension
            val id = saveImported(m, out, niceName, toNas, nasFolder, title, artist)
            val savedTo = if (toNas) "NAS › ${nasFolder.replace("/", " › ")}" else "This phone · Offline"
            _history.value = listOf(ImportHistoryItem(title.ifBlank { m.title }, savedTo, System.currentTimeMillis())) + _history.value
            account.markImported(m.id)
            return id to savedTo
        } finally {
            work.deleteRecursively()
        }
    }

    // ---------- Liked videos (needs the person's own YouTube sign-in) ----------

    enum class LikeSource(val label: String, val url: String) {
        YOUTUBE("YouTube", "https://www.youtube.com/playlist?list=LL"),
        MUSIC("YouTube Music", "https://music.youtube.com/playlist?list=LM"),
    }

    /** One page of the signed-in user's liked videos, newest first. */
    suspend fun likes(source: LikeSource, start: Int, count: Int): List<VideoMeta> {
        ensureInit()
        val cookies = account.cookieFile ?: error("Sign in to YouTube first")
        val req = YoutubeDLRequest(source.url).apply {
            addOption("--flat-playlist")
            addOption("-J")
            addOption("--playlist-start", (start + 1).toString())
            addOption("--playlist-end", (start + count).toString())
            addOption("--cookies", cookies.absolutePath)
        }
        val out = withContext(Dispatchers.IO) { YoutubeDL.getInstance().execute(req, "lumen-likes").out }
        val json = org.json.JSONObject(out.substring(out.indexOf('{')))
        val entries = json.optJSONArray("entries") ?: return emptyList()
        return (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val channel = e.optString("channel").ifBlank { e.optString("uploader") }.removeSuffix(" - Topic")
            VideoMeta("https://www.youtube.com/watch?v=$id", id, e.optString("title", "Untitled"), channel,
                e.optDouble("duration", 0.0).toInt(), "https://i.ytimg.com/vi/$id/mqdefault.jpg")
        }
    }

    data class BatchState(val total: Int = 0, val done: Int = 0, val failed: Int = 0, val current: String? = null) {
        val active get() = current != null
    }
    private val _batch = MutableStateFlow(BatchState())
    val batch: StateFlow<BatchState> = _batch.asStateFlow()

    /** Imports several videos one after another, using the title/artist guessed from each video. */
    fun importMany(items: List<VideoMeta>, format: AudioFormat, toNas: Boolean, nasFolder: String) {
        if (items.isEmpty()) return
        job?.cancel()
        job = scope.launch {
            var done = 0; var failed = 0
            try {
                ensureInit()
                items.forEach { m ->
                    _batch.value = BatchState(items.size, done, failed, m.title)
                    val p = FilenameParser.parse(m.title + ".x")
                    val artist = p.artist.ifBlank { m.channel }
                    try { download(m, format, toNas, nasFolder, p.title.ifBlank { m.title }, artist); done++ }
                    catch (t: Throwable) { if (t is kotlinx.coroutines.CancellationException) throw t; failed++ }
                }
                _state.value = ImportState.Idle
            } finally {
                _batch.value = BatchState(items.size, done, failed, null)
            }
        }
    }

    /** Supported alternative: import an audio file the user already has (any app / Files). */
    fun importFile(uri: Uri, toNas: Boolean, nasFolder: String) {
        job?.cancel()
        job = scope.launch {
            val m = VideoMeta(uri.toString(), "", displayName(uri) ?: "Audio file", "", 0, null)
            try {
                _state.value = ImportState.Running(m, ImportStep.SAVE, 0.2f, "Copying file…")
                val tmp = File(context.cacheDir, "pick-" + sanitize(m.title))
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                }
                val p = FilenameParser.parse(tmp.name.removePrefix("pick-"))
                val id = saveImported(m, tmp, sanitize(tmp.name.removePrefix("pick-")), toNas, nasFolder, p.title, p.artist)
                tmp.delete()
                _state.value = ImportState.Done(m, id, if (toNas) "NAS › ${nasFolder.replace("/", " › ")}" else "This phone · Offline")
            } catch (t: Throwable) {
                _state.value = ImportState.Failed(friendly(t), m)
            }
        }
    }

    private suspend fun saveImported(m: VideoMeta, file: File, name: String, toNas: Boolean, nasFolder: String, title: String, artist: String): Long =
        withContext(Dispatchers.IO) {
            val parsed = FilenameParser.parse(name)
            val relPath: String
            var localPath: String? = null
            if (toNas) {
                _state.value = ImportState.Running(m, ImportStep.SAVE, 0.9f, "Saving to your NAS…")
                relPath = joinPath(nasFolder, name)
                nas.call { it.upload(file, relPath) }
            } else {
                relPath = joinPath("_phone", name)
                val dest = File(downloads.dir, "import-${System.currentTimeMillis()}.${file.extension}")
                file.copyTo(dest, overwrite = true)
                localPath = dest.absolutePath
            }
            _state.value = ImportState.Running(m, ImportStep.TAG, 0.95f, "Reading tags & matching metadata…")
            db.tracks().byPath(relPath)?.let { db.tracks().update(it.copy(localPath = localPath ?: it.localPath, tagsRead = false, enriched = false)) }
                ?: db.tracks().insertAll(listOf(TrackEntity(
                    path = relPath, folder = relPath.substringBeforeLast('/', ""), fileName = name, size = file.length(),
                    mtime = System.currentTimeMillis(), title = title.ifBlank { parsed.title }, artist = artist.ifBlank { parsed.artist },
                    titleSource = MetaSource.FILENAME, localPath = localPath, seenScan = 0,
                )))
            if (toNas) runCatching { scanner.readTags() }
            scanner.rebuildFolders()
            val track = db.tracks().byPath(relPath)!!
            runCatching { meta.enrichPending(5) }
            track.id
        }

    fun cancel() {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
        job?.cancel()
        _state.value = ImportState.Idle
    }

    fun reset() { if (_state.value !is ImportState.Running) _state.value = ImportState.Idle }

    private fun displayName(uri: Uri): String? = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun sanitize(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), " ").replace(Regex("""\s+"""), " ").trim().take(150)

    private fun friendly(t: Throwable): String {
        val m = t.message ?: t.javaClass.simpleName
        return when {
            m.contains("Sign in to confirm", true) || m.contains("bot", true) -> "YouTube asked for sign-in for this video. Try updating the import engine in Settings, or use “Pick an audio file”."
            m.contains("Private video", true) -> "This video is private."
            m.contains("Video unavailable", true) -> "This video is unavailable."
            m.contains("NAS", true) || m.contains("connect", true) -> "Couldn’t reach your NAS: $m"
            else -> m.lineSequence().lastOrNull { it.isNotBlank() }?.take(200) ?: m
        }
    }
}
