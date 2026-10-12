package com.lumen.player.data.meta

import android.content.Context
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.db.MetaSource
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.nas.NasFileInputStream
import com.lumen.player.data.nas.NasManager
import com.lumen.player.data.scan.FilenameParser
import com.lumen.player.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** A possible identity for a track, from MusicBrainz or iTunes. [confidence] is 0..1. */
data class MetaCandidate(
    val source: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val year: Int?,
    val trackNo: Int?,
    val genre: String,
    val durationMs: Long,
    val artUrl: String?,
    val mbRecordingId: String?,
    val mbReleaseId: String?,
    val confidence: Double,
)

data class ArtCandidate(val url: String, val thumb: String, val source: String, val label: String)

class MetadataRepository(
    private val context: Context,
    private val db: LumenDatabase,
    private val http: OkHttpClient,
    private val art: ArtworkStore,
    private val nas: NasManager,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ua = "LumenPlayer/1.0 ( https://github.com/BlessJrz/lumen-player )"
    private val mbLock = Mutex()
    private var lastMb = 0L
    val running = MutableStateFlow(false)
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress

    private fun get(url: String): JsonElement? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", ua).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) null else json.parseToJsonElement(r.body!!.string())
        }
    }.getOrNull()

    private fun bytes(url: String): ByteArray? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body!!.bytes()
        }
    }.getOrNull()

    private suspend fun mb(url: String): JsonElement? = mbLock.withLock {
        val wait = 1100 - (System.currentTimeMillis() - lastMb) // MusicBrainz asks for ≤1 request/second
        if (wait > 0) delay(wait)
        lastMb = System.currentTimeMillis()
        withContext(Dispatchers.IO) { get(url) }
    }

    private fun JsonElement?.str(key: String): String? = (this as? JsonObject)?.get(key)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
    private fun JsonElement?.arr(key: String): JsonArray = ((this as? JsonObject)?.get(key) as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

    private fun q(s: String) = s.replace("\"", "").replace("\\", "")

    // ---------- MusicBrainz ----------

    suspend fun musicBrainz(title: String, artist: String, durationMs: Long): List<MetaCandidate> {
        if (title.isBlank()) return emptyList()
        val query = buildString {
            append("recording:\"").append(q(FilenameParser.norm(title).ifBlank { title })).append('"')
            if (artist.isNotBlank()) append(" AND artist:\"").append(q(artist)).append('"')
        }
        val url = "https://musicbrainz.org/ws/2/recording".toHttpUrl().newBuilder()
            .addQueryParameter("query", query).addQueryParameter("fmt", "json").addQueryParameter("limit", "8").build().toString()
        val root = mb(url) ?: return emptyList()
        return root.arr("recordings").mapNotNull { rec ->
            val o = rec as? JsonObject ?: return@mapNotNull null
            val score = (o["score"]?.jsonPrimitive?.intOrNull ?: 0) / 100.0
            val recTitle = o.str("title") ?: return@mapNotNull null
            val credit = o.arr("artist-credit").joinToString("") { c -> (c.str("name") ?: "") + (c.str("joinphrase") ?: "") }
            val length = o["length"]?.jsonPrimitive?.longOrNull ?: 0L
            val releases = o.arr("releases").mapNotNull { it as? JsonObject }
            val rel = releases.sortedWith(
                compareBy<JsonObject>(
                    { if (it.str("status") == "Official") 0 else 1 },
                    { if (it.obj("release-group").str("primary-type") == "Album") 0 else 1 },
                    { it.obj("release-group")?.arr("secondary-types")?.size ?: 0 },
                    { it.str("date") ?: "9999" },
                )
            ).firstOrNull()
            val media = rel.arr("media").firstOrNull()
            val trackNo = media.arr("track").firstOrNull().str("number")?.toIntOrNull()
            val relArtist = rel.arr("artist-credit").joinToString("") { c -> (c.str("name") ?: "") + (c.str("joinphrase") ?: "") }

            // Confidence = MB score × title similarity × artist similarity × duration agreement
            val ts = FilenameParser.similarity(title, recTitle)
            val asim = if (artist.isBlank()) 0.55 else FilenameParser.similarity(artist, credit).coerceAtLeast(
                if (FilenameParser.norm(title).contains(FilenameParser.norm(credit))) 0.8 else 0.0)
            val dur = if (durationMs > 0 && length > 0) { if (kotlin.math.abs(durationMs - length) <= 6000) 1.0 else 0.6 } else 0.9
            MetaCandidate(
                source = "MusicBrainz", title = recTitle, artist = credit, album = rel.str("title") ?: "",
                albumArtist = relArtist.ifBlank { credit },
                year = rel.str("date")?.take(4)?.toIntOrNull(), trackNo = trackNo, genre = "",
                durationMs = length,
                artUrl = rel.str("id")?.let { "https://coverartarchive.org/release/$it/front-500" },
                mbRecordingId = o.str("id"), mbReleaseId = rel.str("id"),
                confidence = score * ts * asim * dur,
            )
        }.sortedByDescending { it.confidence }
    }

    // ---------- iTunes (metadata + artwork, no key) ----------

    suspend fun iTunes(term: String, limit: Int = 10): List<MetaCandidate> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term).addQueryParameter("entity", "song").addQueryParameter("limit", limit.toString()).build().toString()
        get(url).arr("results").mapNotNull { el ->
            val r = el as? JsonObject ?: return@mapNotNull null
            val t = r.str("trackName") ?: return@mapNotNull null
            MetaCandidate(
                source = "Apple Music", title = t, artist = r.str("artistName") ?: "", album = r.str("collectionName") ?: "",
                albumArtist = r.str("collectionArtistName") ?: r.str("artistName") ?: "",
                year = r.str("releaseDate")?.take(4)?.toIntOrNull(),
                trackNo = r["trackNumber"]?.jsonPrimitive?.intOrNull,
                genre = r.str("primaryGenreName") ?: "",
                durationMs = r["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
                artUrl = r.str("artworkUrl100")?.replace("100x100bb", "1000x1000bb"),
                mbRecordingId = null, mbReleaseId = null, confidence = 0.0,
            )
        }
    }

    /** All plausible identities for a track (used by the editor's "Suggested match" list). */
    suspend fun candidates(t: TrackEntity): List<MetaCandidate> {
        val parsed = FilenameParser.parse(t.fileName)
        val title = t.title.ifBlank { parsed.title }
        val artist = t.artist.ifBlank { parsed.artist }
        val mb = runCatching { musicBrainz(title, artist, t.durationMs) }.getOrDefault(emptyList())
        val it = runCatching { iTunes(listOf(artist, FilenameParser.norm(title)).filter { s -> s.isNotBlank() }.joinToString(" ")) }.getOrDefault(emptyList())
            .map { c ->
                val ts = FilenameParser.similarity(title, c.title)
                val asim = if (artist.isBlank()) 0.5 else FilenameParser.similarity(artist, c.artist)
                val dur = if (t.durationMs > 0 && c.durationMs > 0) { if (kotlin.math.abs(t.durationMs - c.durationMs) <= 6000) 1.0 else 0.6 } else 0.9
                c.copy(confidence = ts * asim * dur * 0.97)
            }
        return (mb + it).sortedByDescending { c -> c.confidence }.distinctBy { c -> FilenameParser.norm(c.title + c.artist + c.album) }.take(8)
    }

    // ---------- Lyrics (LRCLIB) ----------

    suspend fun lyrics(t: TrackEntity): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        if (t.artist.isBlank() || t.title.isBlank()) return@withContext null
        val b = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", t.artist).addQueryParameter("track_name", t.title)
        if (t.album.isNotBlank()) b.addQueryParameter("album_name", t.album)
        if (t.durationMs > 0) b.addQueryParameter("duration", (t.durationMs / 1000).toString())
        val r = get(b.build().toString()) ?: return@withContext null
        r.str("syncedLyrics")?.takeIf { it.isNotBlank() }?.let { return@withContext it to true }
        r.str("plainLyrics")?.takeIf { it.isNotBlank() }?.let { return@withContext it to false }
        null
    }

    // ---------- Artwork search ----------

    suspend fun artworkCandidates(t: TrackEntity, source: String): List<ArtCandidate> = withContext(Dispatchers.IO) {
        val term = listOf(t.artist, t.album.ifBlank { t.title }).filter { it.isNotBlank() }.joinToString(" ")
        when (source) {
            "Cover Art Archive" -> buildList {
                t.mbReleaseId?.let { add(ArtCandidate("https://coverartarchive.org/release/$it/front-1200", "https://coverartarchive.org/release/$it/front-250", source, "Release cover")) }
                runCatching { musicBrainz(t.title, t.artist, t.durationMs) }.getOrDefault(emptyList()).take(6).forEach { c ->
                    c.mbReleaseId?.let { id -> add(ArtCandidate("https://coverartarchive.org/release/$id/front-1200", "https://coverartarchive.org/release/$id/front-250", source, c.album)) }
                }
            }.distinctBy { it.url }
            "Apple Music" -> iTunes(term, 12).mapNotNull { c -> c.artUrl?.let { ArtCandidate(it, it.replace("1000x1000bb", "300x300bb"), source, c.album) } }.distinctBy { it.url }
            "Deezer" -> {
                val url = "https://api.deezer.com/search".toHttpUrl().newBuilder().addQueryParameter("q", term).build().toString()
                get(url).arr("data").mapNotNull { d ->
                    val al = d.obj("album") ?: return@mapNotNull null
                    val xl = al.str("cover_xl") ?: return@mapNotNull null
                    ArtCandidate(xl, al.str("cover_medium") ?: xl, source, al.str("title") ?: "")
                }.distinctBy { it.url }
            }
            "Google Images" -> {
                val s = settings.settings.first()
                if (s.googleApiKey.isBlank() || s.googleCx.isBlank()) emptyList() else {
                    val url = "https://www.googleapis.com/customsearch/v1".toHttpUrl().newBuilder()
                        .addQueryParameter("key", s.googleApiKey).addQueryParameter("cx", s.googleCx)
                        .addQueryParameter("searchType", "image").addQueryParameter("num", "10")
                        .addQueryParameter("q", "$term album cover").build().toString()
                    get(url).arr("items").mapNotNull { i ->
                        val link = i.str("link") ?: return@mapNotNull null
                        ArtCandidate(link, i.obj("image").str("thumbnailLink") ?: link, source, i.str("displayLink") ?: "")
                    }
                }
            }
            else -> emptyList()
        }
    }

    /** Downloads [url] and sets it as artwork for [t] (and every track in the same album if [wholeAlbum]). */
    suspend fun applyArtwork(t: TrackEntity, url: String, wholeAlbum: Boolean): Boolean = withContext(Dispatchers.IO) {
        val data = bytes(url) ?: return@withContext false
        applyArtworkBytes(t, data, wholeAlbum)
    }

    suspend fun applyArtworkBytes(t: TrackEntity, data: ByteArray, wholeAlbum: Boolean): Boolean = withContext(Dispatchers.IO) {
        val f = art.save("t${t.id}-${System.currentTimeMillis()}", data) ?: return@withContext false
        val targets = if (wholeAlbum && t.album.isNotBlank()) db.tracks().byAlbum(t.album, t.albumArtist.ifBlank { t.artist }).first() else listOf(t)
        db.tracks().updateAll(targets.map { it.copy(artPath = f.absolutePath) })
        true
    }

    fun applyCandidate(t: TrackEntity, c: MetaCandidate, source: Int = MetaSource.MUSICBRAINZ) = t.copy(
        title = c.title, artist = c.artist, album = c.album.ifBlank { t.album }, albumArtist = c.albumArtist.ifBlank { t.albumArtist },
        year = c.year ?: t.year, trackNo = c.trackNo ?: t.trackNo, genre = c.genre.ifBlank { t.genre },
        mbRecordingId = c.mbRecordingId ?: t.mbRecordingId, mbReleaseId = c.mbReleaseId ?: t.mbReleaseId,
        titleSource = source, albumSource = source, enriched = true,
    )

    // ---------- Automatic enrichment ----------

    /**
     * Fills in missing info for tracks that haven't been checked yet. Only applies a match when it is
     * very confident (≥ 0.8); anything weaker is left for the user to confirm in the editor.
     */
    suspend fun enrichPending(max: Int = 200) {
        if (running.value) return
        running.value = true
        try {
            val s = settings.settings.first()
            if (!s.autoMetadata && !s.autoArtwork && !s.autoLyrics) return
            var done = 0
            while (done < max) {
                val batch = db.tracks().needingEnrichment(10)
                if (batch.isEmpty()) break
                for (t in batch) {
                    var u = t
                    val complete = t.titleSource == MetaSource.TAG && t.artist.isNotBlank() && t.album.isNotBlank()
                    if (s.autoMetadata && !complete && t.artist.isNotBlank()) {
                        val best = runCatching { musicBrainz(t.title, t.artist, t.durationMs) }.getOrDefault(emptyList()).firstOrNull()
                        if (best != null && best.confidence >= 0.8) u = applyCandidate(u, best)
                    }
                    if (s.autoArtwork && u.artPath == null) {
                        val artUrl = u.mbReleaseId?.let { "https://coverartarchive.org/release/$it/front-500" }
                        val got = artUrl?.let { withContext(Dispatchers.IO) { bytes(it) } }
                            ?: run {
                                // Fall back to an exact iTunes match (same artist & title) for cover art only.
                                if (u.artist.isBlank()) null else runCatching { iTunes("${u.artist} ${FilenameParser.norm(u.title)}", 5) }.getOrDefault(emptyList())
                                    .firstOrNull { FilenameParser.similarity(it.title, u.title) >= 0.9 && FilenameParser.similarity(it.artist, u.artist) >= 0.8 }
                                    ?.artUrl?.let { withContext(Dispatchers.IO) { bytes(it) } }
                            }
                        got?.let { b -> art.save("t${u.id}", b)?.let { u = u.copy(artPath = it.absolutePath) } }
                    }
                    if (s.autoLyrics && u.lyrics == null) {
                        runCatching { lyrics(u) }.getOrNull()?.let { (text, synced) -> u = u.copy(lyrics = text, lyricsSynced = synced) }
                    }
                    db.tracks().update(u.copy(enriched = true))
                    done++
                    _progress.value = done
                }
            }
        } finally { running.value = false }
    }

    // ---------- Manual edits ----------

    /** Saves user edits; optionally writes ID3 tags back into the MP3 on the NAS. */
    suspend fun saveEdits(t: TrackEntity, writeToFile: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val edited = t.copy(userEdited = true, titleSource = MetaSource.USER, enriched = true)
        db.tracks().update(edited)
        if (!writeToFile || !t.fileName.endsWith(".mp3", true)) return@withContext Result.success(Unit)
        runCatching {
            val tmpIn = File(context.cacheDir, "tag-in.mp3"); val tmpOut = File(context.cacheDir, "tag-out.mp3")
            nas.call { c -> c.open(t.path).use { f -> NasFileInputStream(f).use { inp -> tmpIn.outputStream().use { inp.copyTo(it, 256 * 1024) } } } }
            Id3.rewrite(tmpIn, tmpOut, Id3.Fields(edited.title, edited.artist, edited.album, edited.albumArtist, edited.genre, edited.year, edited.trackNo, edited.lyrics, art.jpegBytes(edited.artPath)))
            nas.call { it.upload(tmpOut, t.path) }
            db.tracks().update(edited.copy(size = tmpOut.length(), mtime = System.currentTimeMillis()))
            tmpIn.delete(); tmpOut.delete()
            Unit
        }
    }
}
