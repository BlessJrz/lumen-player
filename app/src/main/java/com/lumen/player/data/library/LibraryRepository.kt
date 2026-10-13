package com.lumen.player.data.library

import com.lumen.player.data.db.FolderEntity
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.db.PlaylistEntity
import com.lumen.player.data.db.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** A generated mix shown under "Recommended" / "Suggested playlists". */
data class Mix(val key: String, val title: String, val subtitle: String, val tracks: List<TrackEntity>) {
    val cover: TrackEntity? get() = tracks.firstOrNull { it.artPath != null } ?: tracks.firstOrNull()
}

data class HomeData(
    val continueListening: List<TrackEntity> = emptyList(),
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val recommended: List<Mix> = emptyList(),
    val mostPlayed: List<TrackEntity> = emptyList(),
    val recentlyAdded: List<TrackEntity> = emptyList(),
    val favorites: List<TrackEntity> = emptyList(),
    val collections: List<FolderEntity> = emptyList(),
    val categoryRows: List<Pair<FolderEntity, List<TrackEntity>>> = emptyList(),
    val discoveries: List<TrackEntity> = emptyList(),
    val suggestedPlaylists: List<Mix> = emptyList(),
    val trackCount: Int = 0,
)

data class SearchResults(
    val query: String = "",
    val tracks: List<TrackEntity> = emptyList(),
    val artists: List<Pair<String, TrackEntity>> = emptyList(),
    val albums: List<Pair<String, TrackEntity>> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val lyrics: List<TrackEntity> = emptyList(),
)

class LibraryRepository(private val db: LumenDatabase) {
    private val day = 24 * 3600 * 1000L

    val collections: Flow<List<FolderEntity>> = db.folders().collections()
    val allFolders: Flow<List<FolderEntity>> = db.folders().all()
    val favorites = db.tracks().favorites()
    val downloaded = db.tracks().downloaded()
    val history = db.plays().history(200)
    val allSongs = db.tracks().all()
    val recentlyAddedAll = db.tracks().recentlyAdded(300)
    suspend fun allSongsNow() = db.tracks().all().first()
    val playlists = db.playlists().summaries()
    val artists = db.tracks().artists()
    val albums = db.tracks().albums()
    val trackCount = db.tracks().count()

    fun folder(path: String) = db.folders().observe(path)
    fun subfolders(path: String) = db.folders().children(path)
    fun tracksIn(path: String) = db.tracks().inFolder(path)
    fun tracksInTree(path: String) = db.tracks().inFolderTree(path, if (path.isEmpty()) "%" else "$path/%")
    fun track(id: Long) = db.tracks().observe(id)
    suspend fun trackNow(id: Long) = db.tracks().get(id)
    suspend fun tracks(ids: List<Long>): List<TrackEntity> { val m = db.tracks().getMany(ids).associateBy { it.id }; return ids.mapNotNull { m[it] } }
    fun byArtist(a: String) = db.tracks().byArtist(a)
    fun byAlbum(album: String, artist: String) = db.tracks().byAlbum(album, artist)
    fun playlist(id: Long) = db.playlists().observe(id)
    fun playlistTracks(id: Long) = db.playlists().tracks(id)
    suspend fun playlistCover(id: Long) = tracks(db.playlists().coverIds(id))

    suspend fun toggleFavorite(t: TrackEntity) = db.tracks().setFavorite(t.id, !t.favorite)
    suspend fun setFavorite(id: Long, fav: Boolean) = db.tracks().setFavorite(id, fav)
    suspend fun updateTrack(t: TrackEntity) = db.tracks().update(t)

    suspend fun createPlaylist(name: String, ids: List<Long> = emptyList()): Long {
        val id = db.playlists().create(PlaylistEntity(name = name.ifBlank { "New playlist" }))
        if (ids.isNotEmpty()) db.playlists().setTracks(id, ids)
        return id
    }
    suspend fun renamePlaylist(id: Long, name: String) = db.playlists().rename(id, name)
    suspend fun deletePlaylist(id: Long) { db.playlists().clearTracks(id); db.playlists().delete(id) }
    suspend fun setPlaylistTracks(id: Long, ids: List<Long>) = db.playlists().setTracks(id, ids)
    suspend fun addToPlaylist(id: Long, ids: List<Long>) = db.playlists().setTracks(id, (db.playlists().trackIds(id) + ids).distinct())

    suspend fun search(q: String): SearchResults = withContext(Dispatchers.IO) {
        val s = q.trim()
        if (s.length < 2) return@withContext SearchResults(s)
        val like = "%$s%"
        val tracks = db.tracks().search(like, "$s%")
        SearchResults(
            query = s,
            tracks = tracks,
            artists = tracks.filter { it.artist.contains(s, true) }.groupBy { it.artist }.map { (a, l) -> a to (l.firstOrNull { it.artPath != null } ?: l.first()) }.take(10),
            albums = tracks.filter { it.album.isNotBlank() && it.album.contains(s, true) }.groupBy { it.album }.map { (a, l) -> a to (l.firstOrNull { it.artPath != null } ?: l.first()) }.take(10),
            folders = db.folders().search(like),
            lyrics = db.tracks().searchLyrics(like).filter { t -> tracks.none { it.id == t.id } },
        )
    }

    /** Builds every home-screen section from listening history, favorites and folders. */
    suspend fun home(): HomeData = withContext(Dispatchers.IO) {
        val t = db.tracks()
        val now = System.currentTimeMillis()
        val collections = db.folders().collections().first()
        val favs = t.favorites().first()
        val recent = t.recent(30).first()
        val most = t.mostPlayed(20).first()

        val affinity = db.plays().folderAffinity(now - 30 * day)
        val topFolders = affinity.map { it.folder }.take(4)
        val recommended = mutableListOf<Mix>()
        for (f in topFolders) {
            val pool = t.randomIn(f, "$f/%", 40).filter { it.lastPlayed < now - 2 * day }.take(25)
            if (pool.size >= 4) recommended += Mix("folder:$f", "More from ${f.substringAfterLast('/').ifBlank { "your library" }}", "Because you've been playing it", pool)
        }
        for (a in db.plays().artistAffinity(now - 60 * day).take(3)) {
            val pool = t.byArtist(a.artist).first().shuffled().take(25)
            if (pool.size >= 3) recommended += Mix("artist:${a.artist}", "${a.artist} Radio", "${pool.size} songs", pool)
        }
        if (recommended.isEmpty()) {
            collections.filter { it.totalCount >= 5 }.take(4).forEach { c ->
                val pool = t.randomIn(c.path, if (c.path.isEmpty()) "%" else "${c.path}/%", 25)
                recommended += Mix("folder:${c.path}", "${c.name} mix", "${c.totalCount} songs to explore", pool)
            }
        }

        val rows = collections.filter { it.sortKey <= 2 }.map { c -> c to t.randomIn(c.path, if (c.path.isEmpty()) "%" else "${c.path}/%", 12) }

        val suggested = buildList {
            if (favs.size >= 4) add(Mix("favorites", "Your favorites", "${favs.size} songs", favs.shuffled()))
            val late = rows.firstOrNull { it.first.name.equals("Emotional", true) }
            if (late != null) add(Mix("late", "Late night feels", "Emotional · slow down", t.randomIn(late.first.path, "${late.first.path}/%", 30)))
            val party = rows.firstOrNull { it.first.name.equals("Party", true) }
            if (party != null) add(Mix("pregame", "Pre-game energy", "Party · turn it up", t.randomIn(party.first.path, "${party.first.path}/%", 30)))
            val hip = rows.firstOrNull { it.first.name.replace("-", " ").equals("Hip Hop", true) }
            if (hip != null) add(Mix("bars", "Heavy rotation", "Hip Hop · on repeat", t.randomIn(hip.first.path, "${hip.first.path}/%", 30)))
            val forgot = t.forgotten(now - 120 * day, 30)
            if (forgot.size >= 5) add(Mix("rediscover", "Rediscover", "Songs you haven't played in a while", forgot))
        }

        HomeData(
            continueListening = db.plays().unfinished(6).first(),
            recentlyPlayed = recent.distinctBy { it.id }.take(14),
            recommended = recommended,
            mostPlayed = most,
            recentlyAdded = t.recentlyAdded(14).first(),
            favorites = favs.take(14),
            collections = collections,
            categoryRows = rows,
            discoveries = t.forgotten(now - 60 * day, 12),
            suggestedPlaylists = suggested,
            trackCount = t.countNow(),
        )
    }
}
