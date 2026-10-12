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

    private val enginePrefs = context.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)

    private suspend fun ensureInit() = initLock.withLock {
        if (!initialized) {
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(context)
                    FFmpeg.getInstance().init(context)
                } catch (t: Throwable) {
                    // Usually leftovers from an older engine version (e.g. after an app update). Start clean once.
                    android.util.Log.w("LumenImport", "Engine init failed, resetting", t)
                    File(context.noBackupFilesDir, "youtubedl-android").deleteRecursively()
                    File(context.filesDir, "youtubedl-android").deleteRecursively()
                    File(context.applicationInfo.dataDir, "shared_prefs").listFiles()
                        ?.filter { it.name.contains("youtubedl", true) || it.name.contains("ffmpeg", true) }?.forEach { it.delete() }
                    enginePrefs.edit().remove("updated").apply()
                    try {
                        YoutubeDL.getInstance().init(context)
                        FFmpeg.getInstance().init(context)
                    } catch (t2: Throwable) {
                        throw IllegalStateException("Import engine couldn’t start: " + causeChain(t2), t2)
                    }
                }
            }
            initialized = true
        }
        // YouTube changes constantly; the yt-dlp bundled with the app goes stale. Refresh it every 2 days.
        val last = enginePrefs.getLong("updated", 0)
        if (System.currentTimeMillis() - last > 2L * 24 * 3600 * 1000) {
            val ok = runCatching { withContext(Dispatchers.IO) { YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE) } }.isSuccess
            if (ok) enginePrefs.edit().putLong("updated", System.currentTimeMillis()).apply()
        }
    }

    /** Options every yt-dlp call gets. (The engine library itself adds its bundled QuickJS JavaScript runtime.) */
    private fun YoutubeDLRequest.common(cookies: Boolean) {
        // Keep yt-dlp's cache: solved YouTube JavaScript challenges are reused instead of re-solved every time
        // (re-solving on a phone takes a long while). Setting it also stops the library adding --no-cache-dir.
        addOption("--cache-dir", File(context.filesDir, "ytdlp-cache").apply { mkdirs() }.absolutePath)
        addOption("--socket-timeout", "20")
        // Mobile networks hand out both IPv6 and IPv4. YouTube's audio links only work from the address that asked
        // for them; if the download leaves over the other one, YouTube answers "HTTP 403 Forbidden". Pin IPv4.
        addOption("--force-ipv4")
        addOption("--retries", "5")
        addOption("--extractor-retries", "3")
        if (cookies) withCookies()
    }

    private fun causeChain(t: Throwable): String =
        generateSequence(t) { it.cause }.take(4).map { "${it.javaClass.simpleName}: ${it.message ?: ""}" }.joinToString(" ← ")

    /** CI self-test: starts the engine, updates yt-dlp and fetches info for a public video; results go to logcat. */
    fun selfTest() {
        scope.launch {
            val tag = "LumenImport"
            try {
                ensureInit(); android.util.Log.i(tag, "SELFTEST init ok")
                val v = withContext(Dispatchers.IO) { YoutubeDL.getInstance().version(context) }
                android.util.Log.i(tag, "SELFTEST yt-dlp version $v")
                val m = VideoMeta("https://www.youtube.com/watch?v=jNQXAC9IVRw", "jNQXAC9IVRw", "Me at the zoo", "jawed", 19, null)
                attempts.filter { !it.cookies }.forEach { a ->
                    val work = File(context.cacheDir, "selftest").apply { deleteRecursively(); mkdirs() }
                    val t0 = System.currentTimeMillis()
                    runCatching { fetchAudio(m, AudioFormat.MP3, work, a) }
                        .onSuccess { android.util.Log.i(tag, "SELFTEST attempt ${a.label}: OK ${it.name} ${it.length()} bytes in ${(System.currentTimeMillis() - t0) / 1000}s") }
                        .onFailure { android.util.Log.e(tag, "SELFTEST attempt ${a.label}: ${ytError(it)}") }
                }
            } catch (t: Throwable) {
                android.util.Log.e(tag, "SELFTEST failed: " + causeChain(t), t)
            }
        }
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
                val info = withContext(Dispatchers.IO) { YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply { addOption("--no-playlist"); common(cookies = false) }) }
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
    /** Ways to ask YouTube for the audio, tried in order until one works (YouTube treats clients differently). */
    private data class Attempt(val cookies: Boolean, val clients: String?, val label: String)
    private val attempts = listOf(
        Attempt(false, "android_vr", "fast"),              // no JavaScript challenge to solve → quickest
        Attempt(false, null, "standard"),
        Attempt(false, "tv_simply,mweb", "TV / mobile web"),
        Attempt(true, null, "signed in"),
        Attempt(true, "tv,mweb,web_safari", "signed in · TV/mobile web"),
    )

    private suspend fun download(m: VideoMeta, format: AudioFormat, toNas: Boolean, nasFolder: String, title: String, artist: String): Pair<Long, String> {
        val work = File(context.cacheDir, "yt").apply { deleteRecursively(); mkdirs() }
        try {
            val preferred = enginePrefs.getInt("attempt", 0)
            val order = (listOf(preferred) + attempts.indices).distinct()
                .map { attempts[it] to it }.filter { (a, _) -> !a.cookies || account.cookieFile != null }
            val errors = mutableListOf<String>()
            var out: File? = null
            for ((a, idx) in order) {
                work.listFiles()?.forEach { it.delete() }
                try {
                    var timedOut = false
                    val watchdog = scope.launch { kotlinx.coroutines.delay(4 * 60_000L); timedOut = true; runCatching { YoutubeDL.getInstance().destroyProcessById(processId) } }
                    out = try { fetchAudio(m, format, work, a) }
                    catch (t: Throwable) { if (timedOut) throw java.util.concurrent.TimeoutException("took too long"); throw t }
                    finally { watchdog.cancel() }
                    enginePrefs.edit().putInt("attempt", idx).apply()
                    break
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    android.util.Log.w("LumenImport", "Attempt '${a.label}' failed for ${m.url}", t)
                    errors += "${a.label}: ${ytError(t)}"
                }
            }
            val file = out ?: throw IllegalStateException(errors.joinToString("\n"))
            val niceName = sanitize(if (artist.isNotBlank()) "$artist - $title" else title.ifBlank { file.nameWithoutExtension }) + "." + file.extension
            val id = saveImported(m, file, niceName, toNas, nasFolder, title, artist)
            val savedTo = if (toNas) "NAS › ${nasFolder.replace("/", " › ")}" else "This phone · Offline"
            _history.value = listOf(ImportHistoryItem(title.ifBlank { m.title }, savedTo, System.currentTimeMillis())) + _history.value
            account.markImported(m.id)
            return id to savedTo
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun fetchAudio(m: VideoMeta, format: AudioFormat, work: File, a: Attempt): File {
        _state.value = ImportState.Running(m, ImportStep.DOWNLOAD, 0f, "Starting (${a.label})…")
        val req = YoutubeDLRequest(m.url).apply {
            addOption("--no-playlist")
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--audio-format", format.ytdlp)
            addOption("--audio-quality", "0")
            addOption("--embed-metadata")
            addOption("--embed-thumbnail")
            addOption("--convert-thumbnails", "jpg")
            addOption("-o", File(work, "%(title).120B.%(ext)s").absolutePath)
            a.clients?.let { addOption("--extractor-args", "youtube:player_client=$it") }
            common(a.cookies)
        }
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(req, processId) { progress, _, line ->
                val step = when {
                    line.contains("[ExtractAudio]") || line.contains("[ffmpeg]") || line.contains("Destination") && line.contains(".${format.ext}") -> ImportStep.CONVERT
                    line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") -> ImportStep.TAG
                    else -> ImportStep.DOWNLOAD
                }
                val detail = when {
                    line.contains("JS challenge", true) || line.contains("jsc", true) -> "Unlocking the audio stream (first time can take a minute)…"
                    line.contains("Downloading webpage") || line.contains("player") -> "Talking to YouTube (${a.label})…"
                    line.contains("[download]") -> line.substringAfter("[download]").trim().take(70)
                    else -> line.take(80)
                }
                _state.value = ImportState.Running(m, step, (progress / 100f).coerceIn(0f, 1f), detail)
            }
        }
        return work.listFiles()?.filter { it.extension.equals(format.ext, true) }?.maxByOrNull { it.length() }
            ?: work.listFiles()?.filter { it.extension !in setOf("jpg", "webp", "png", "part", "json", "ytdl") }?.maxByOrNull { it.length() }
            ?: error("Conversion produced no audio file")
    }

    /** The meaningful line out of yt-dlp's output: its last "ERROR:" line, without the boilerplate. */
    private fun ytError(t: Throwable): String {
        val m = t.message ?: t.javaClass.simpleName
        val line = m.lineSequence().lastOrNull { it.trimStart().startsWith("ERROR:") }
            ?: m.lineSequence().lastOrNull { it.isNotBlank() && !it.trimStart().startsWith("File ") && !it.trimStart().startsWith("at ") } ?: m
        return line.removePrefix("ERROR:").trim()
            .replace(Regex("""^\[youtube\] [\w-]+: """), "")
            .substringBefore(" Use --cookies").substringBefore(" See  https").take(220)
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
            common(cookies = false)
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

    data class BatchState(val total: Int = 0, val done: Int = 0, val failed: Int = 0, val current: String? = null, val errors: List<Pair<String, String>> = emptyList()) {
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
            val errors = mutableListOf<Pair<String, String>>()
            try {
                _batch.value = BatchState(items.size, 0, 0, "Preparing the import engine…")
                ensureInit()
                items.forEach { m ->
                    _batch.value = BatchState(items.size, done, failed, m.title, errors.toList())
                    val p = FilenameParser.parse(m.title + ".x")
                    val artist = p.artist.ifBlank { m.channel }
                    try { download(m, format, toNas, nasFolder, p.title.ifBlank { m.title }, artist); done++ }
                    catch (t: Throwable) {
                        if (t is kotlinx.coroutines.CancellationException) throw t
                        failed++; errors += m.title to friendly(t)
                        android.util.Log.w("LumenImport", "Import failed: ${m.url}", t)
                    }
                }
                _state.value = ImportState.Idle
            } finally {
                _batch.value = BatchState(items.size, done, failed, null, errors.toList())
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

    fun clearBatch() { if (!_batch.value.active) _batch.value = BatchState() }

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
            m.lines().any { it.contains(": ") && (it.startsWith("standard") || it.startsWith("signed in") || it.startsWith("TV")) } -> m.take(900)
            m.contains("STATUS_ACCESS_DENIED") -> "Your NAS account can’t write to that folder"
            m.contains("STATUS_OBJECT_PATH_NOT_FOUND") -> "That NAS folder doesn’t exist anymore"
            m.contains("Private video", true) -> "This video is private."
            m.contains("Video unavailable", true) -> "This video is unavailable."
            m.contains("NAS", true) || m.contains("connect", true) -> "Couldn’t reach your NAS: $m"
            else -> m.lineSequence().lastOrNull { it.isNotBlank() }?.take(200) ?: m
        }
    }
}
