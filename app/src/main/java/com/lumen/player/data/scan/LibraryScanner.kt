package com.lumen.player.data.scan

import com.lumen.player.data.db.FolderEntity
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.db.MetaSource
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.meta.ArtworkStore
import com.lumen.player.data.nas.NasEntry
import com.lumen.player.data.nas.NasManager
import com.lumen.player.data.nas.nameOf
import com.lumen.player.data.nas.parentOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Listing(val folders: Int, val files: Int) : ScanState
    data class ReadingTags(val done: Int, val total: Int) : ScanState
    data class Finished(val added: Int, val removed: Int, val total: Int, val at: Long) : ScanState
    data class Failed(val message: String) : ScanState
}

class LibraryScanner(
    private val nas: NasManager,
    private val db: LumenDatabase,
    private val art: ArtworkStore,
) {
    companion object {
        val AUDIO = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "aif", "aiff", "mp4", "3gp", "mka", "amr")
        /** Containers whose children should be shown as collections instead of the container itself. */
        private val CONTAINERS = setOf("playlists", "music", "collections", "library", "categories")
        private val PINNED = listOf("emotional", "hip hop", "hip-hop", "hiphop", "party")
    }

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()
    private val mutex = Mutex()

    suspend fun scan(readTags: Boolean = true): ScanState = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val scanId = System.currentTimeMillis()
                val files = mutableListOf<NasEntry>()
                val dirs = ArrayDeque(listOf(""))
                var folderCount = 0
                while (dirs.isNotEmpty()) {
                    val d = dirs.removeFirst()
                    val entries = nas.call { it.list(d) }
                    folderCount++
                    for (e in entries) {
                        if (e.name.startsWith(".") || e.name.startsWith("@") || e.name == "#recycle") continue
                        if (e.isDir) dirs.addLast(e.path)
                        else if (e.name.substringAfterLast('.', "").lowercase() in AUDIO) files += e
                    }
                    _state.value = ScanState.Listing(folderCount, files.size)
                }

                val known = db.tracks().index().associateBy { it.path }
                val fresh = mutableListOf<TrackEntity>()
                val seen = mutableListOf<Long>()
                for (f in files) {
                    val k = known[f.path]
                    when {
                        k == null -> {
                            val p = FilenameParser.parse(f.name)
                            fresh += TrackEntity(
                                path = f.path, folder = parentOf(f.path), fileName = f.name, size = f.size, mtime = f.mtime,
                                title = p.title, artist = p.artist, titleSource = MetaSource.FILENAME,
                                addedAt = if (f.mtime > 0) f.mtime else scanId, seenScan = scanId,
                            )
                        }
                        k.size != f.size || (k.mtime != f.mtime && f.mtime > 0) -> db.tracks().markChanged(k.id, f.size, f.mtime, scanId)
                        else -> seen += k.id
                    }
                }
                seen.chunked(900).forEach { db.tracks().markSeen(it, scanId) }
                fresh.chunked(500).forEach { db.tracks().insertAll(it) }
                val removed = db.tracks().sweep(scanId)
                rebuildFolders()

                if (readTags) readTags()
                rebuildFolders()
                val total = db.tracks().countNow()
                ScanState.Finished(fresh.size, removed, total, System.currentTimeMillis()).also { _state.value = it }
            } catch (t: Throwable) {
                ScanState.Failed(t.message ?: t.javaClass.simpleName).also { _state.value = it }
            }
        }
    }

    /** Reads embedded tags for every track that hasn't had them read yet. Runs three NAS reads in parallel. */
    suspend fun readTags() = coroutineScope {
        var done = 0
        val total = db.tracks().countNeedingTags()
        while (true) {
            val batch = db.tracks().needingTags(24)
            if (batch.isEmpty()) break
            _state.value = ScanState.ReadingTags(done, total)
            val updated = batch.chunked(8).flatMap { group ->
                group.map { t -> async(Dispatchers.IO) { readOne(t) } }.awaitAll()
            }
            db.tracks().updateAll(updated)
            done += batch.size
            _state.value = ScanState.ReadingTags(minOf(done, total), total)
        }
    }

    private fun readOne(t: TrackEntity): TrackEntity = try {
        nas.call { it.open(t.path) }.use { f ->
            val tags = TagReader.read(f, t.fileName.endsWith(".mp3", true))
            val parsed = FilenameParser.parse(t.fileName)
            val artPath = tags.picture?.let { art.save("t${t.id}", it)?.absolutePath } ?: t.artPath
            val hasTitle = !tags.title.isNullOrBlank()
            t.copy(
                title = if (t.userEdited) t.title else tags.title ?: parsed.title,
                artist = if (t.userEdited) t.artist else tags.artist ?: tags.albumArtist ?: parsed.artist,
                album = if (t.userEdited) t.album else tags.album ?: t.album,
                albumArtist = if (t.userEdited) t.albumArtist else tags.albumArtist ?: t.albumArtist,
                genre = if (t.userEdited) t.genre else tags.genre ?: t.genre,
                year = if (t.userEdited) t.year else tags.year ?: t.year,
                trackNo = if (t.userEdited) t.trackNo else tags.track ?: t.trackNo,
                durationMs = if (tags.durationMs > 0) tags.durationMs else t.durationMs,
                lyrics = t.lyrics ?: tags.lyrics,
                artPath = artPath,
                titleSource = if (t.userEdited) MetaSource.USER else if (hasTitle) MetaSource.TAG else MetaSource.FILENAME,
                albumSource = if (!tags.album.isNullOrBlank()) MetaSource.TAG else t.albumSource,
                tagsRead = true,
            )
        }
    } catch (e: Throwable) {
        t.copy(tagsRead = true)
    }

    /** Recomputes the folder tree, counts, covers and which folders are presented as collections. */
    suspend fun rebuildFolders() {
        val counts = db.tracks().folderCounts().associate { it.folder to it.n }
        val all = mutableSetOf<String>()
        for (f in counts.keys) {
            var p = f
            while (true) { all += p; if (p.isEmpty()) break; p = parentOf(p) }
        }
        val total = mutableMapOf<String, Int>()
        for ((f, n) in counts) {
            var p = f
            while (true) { total[p] = (total[p] ?: 0) + n; if (p.isEmpty()) break; p = parentOf(p) }
        }
        val childrenOf = all.filter { it.isNotEmpty() }.groupBy { parentOf(it) }
        fun isContainer(path: String): Boolean {
            val kids = childrenOf[path].orEmpty()
            return kids.size >= 2 && (nameOf(path).lowercase() in CONTAINERS || (counts[path] ?: 0) < kids.size * 3)
        }
        val collections = mutableSetOf<String>()
        for (top in childrenOf[""].orEmpty()) {
            if (isContainer(top)) {
                childrenOf[top].orEmpty().forEach { collections += it }
                if ((counts[top] ?: 0) > 0) collections += top
            } else collections += top
        }
        if ((counts[""] ?: 0) > 0) collections += ""

        val entities = all.map { path ->
            val name = if (path.isEmpty()) "Loose tracks" else nameOf(path)
            val pin = PINNED.indexOfFirst { it == name.lowercase() }
            FolderEntity(
                path = path,
                parent = if (path.isEmpty()) null else parentOf(path),
                name = name,
                directCount = counts[path] ?: 0,
                totalCount = total[path] ?: 0,
                coverTrackId = db.tracks().coverForTree(path, if (path.isEmpty()) "%" else "$path/%"),
                isCollection = path in collections,
                sortKey = when {
                    pin >= 0 -> minOf(pin, 2)
                    path.isEmpty() -> 99
                    else -> 10
                },
            )
        }
        db.folders().replaceAll(entities)
    }
}
