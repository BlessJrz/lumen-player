package com.lumen.player.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Where a field's value came from — shown as small source badges in the metadata editor. */
object MetaSource {
    const val FILENAME = 0
    const val TAG = 1
    const val MUSICBRAINZ = 2
    const val USER = 3
}

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["path"], unique = true), Index("folder"), Index("artist"), Index("album")],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Path relative to the music root, e.g. "Playlists/Emotional/song.mp3". */
    val path: String,
    /** Relative folder, e.g. "Playlists/Emotional" ("" = root). */
    val folder: String,
    val fileName: String,
    val size: Long,
    val mtime: Long,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: Int? = null,
    val trackNo: Int? = null,
    val durationMs: Long = 0,
    /** Local cached artwork file (absolute path) or null. */
    val artPath: String? = null,
    val lyrics: String? = null,
    val lyricsSynced: Boolean = false,
    val mbRecordingId: String? = null,
    val mbReleaseId: String? = null,
    val titleSource: Int = MetaSource.FILENAME,
    val albumSource: Int = MetaSource.FILENAME,
    /** True once tags were read from the file. */
    val tagsRead: Boolean = false,
    /** True once online enrichment was attempted (successfully or not). */
    val enriched: Boolean = false,
    val userEdited: Boolean = false,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    /** Absolute path of an offline copy on the phone, if downloaded. */
    val localPath: String? = null,
    /** Mark-and-sweep flag used by the scanner to detect removed files. */
    val seenScan: Long = 0,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val parent: String?,
    val name: String,
    val directCount: Int,
    val totalCount: Int,
    val coverTrackId: Long?,
    val isCollection: Boolean,
    val sortKey: Int,
)

@Entity(tableName = "plays", indices = [Index("trackId"), Index("playedAt")])
data class PlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long,
    val positionMs: Long = 0,
    val completed: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "position"], indices = [Index("trackId")])
data class PlaylistTrackEntity(val playlistId: Long, val position: Int, val trackId: Long)

object DlState {
    const val QUEUED = 0
    const val RUNNING = 1
    const val PAUSED = 2
    const val DONE = 3
    const val FAILED = 4
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: Long,
    val state: Int,
    val bytes: Long = 0,
    val total: Long = 0,
    val error: String? = null,
    val groupName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

data class FolderStat(val folder: String, val n: Int)
data class ArtistStat(val artist: String, val n: Int, val artTrackId: Long)
data class AlbumStat(val album: String, val artist: String, val n: Int, val artTrackId: Long, val year: Int?)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun get(id: Long): TrackEntity?
    @Query("SELECT * FROM tracks WHERE id = :id") fun observe(id: Long): Flow<TrackEntity?>
    @Query("SELECT * FROM tracks WHERE id IN (:ids)") suspend fun getMany(ids: List<Long>): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE path = :path") suspend fun byPath(path: String): TrackEntity?
    @Query("SELECT path, size, mtime, id FROM tracks") suspend fun index(): List<PathIndex>
    @Query("SELECT COUNT(*) FROM tracks") fun count(): Flow<Int>
    @Query("SELECT COUNT(*) FROM tracks") suspend fun countNow(): Int

    @Query("SELECT * FROM tracks WHERE folder = :folder ORDER BY title COLLATE NOCASE") fun inFolder(folder: String): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE folder = :folder OR folder LIKE :prefix ORDER BY folder, title COLLATE NOCASE") fun inFolderTree(folder: String, prefix: String): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE folder = :folder OR folder LIKE :prefix ORDER BY RANDOM() LIMIT :n") suspend fun randomIn(folder: String, prefix: String, n: Int): List<TrackEntity>
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE") fun all(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT :n") fun recent(n: Int): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE playCount > 0 ORDER BY playCount DESC, lastPlayed DESC LIMIT :n") fun mostPlayed(n: Int): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC LIMIT :n") fun recentlyAdded(n: Int): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE favorite = 1 ORDER BY title COLLATE NOCASE") fun favorites(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL ORDER BY title COLLATE NOCASE") fun downloaded(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL") suspend fun downloadedNow(): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE playCount = 0 OR lastPlayed < :before ORDER BY RANDOM() LIMIT :n") suspend fun forgotten(before: Long, n: Int): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album, trackNo, title") fun byArtist(artist: String): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks WHERE album = :album AND (albumArtist = :artist OR artist = :artist) ORDER BY trackNo, title") fun byAlbum(album: String, artist: String): Flow<List<TrackEntity>>

    @Query(
        """SELECT * FROM tracks WHERE title LIKE :q OR artist LIKE :q OR album LIKE :q OR fileName LIKE :q OR genre LIKE :q
           ORDER BY CASE WHEN title LIKE :qs THEN 0 WHEN artist LIKE :qs THEN 1 ELSE 2 END, playCount DESC LIMIT 200"""
    )
    suspend fun search(q: String, qs: String): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE lyrics LIKE :q LIMIT 20") suspend fun searchLyrics(q: String): List<TrackEntity>

    @Query("SELECT artist, COUNT(*) AS n, MAX(id) AS artTrackId FROM tracks WHERE artist != '' GROUP BY artist ORDER BY n DESC")
    fun artists(): Flow<List<ArtistStat>>
    @Query("SELECT album, CASE WHEN albumArtist != '' THEN albumArtist ELSE artist END AS artist, COUNT(*) AS n, MAX(id) AS artTrackId, MAX(year) AS year FROM tracks WHERE album != '' GROUP BY album, CASE WHEN albumArtist != '' THEN albumArtist ELSE artist END HAVING n >= 2 ORDER BY album COLLATE NOCASE")
    fun albums(): Flow<List<AlbumStat>>

    @Query("SELECT * FROM tracks WHERE tagsRead = 0 LIMIT :n") suspend fun needingTags(n: Int): List<TrackEntity>
    @Query("SELECT COUNT(*) FROM tracks WHERE tagsRead = 0") suspend fun countNeedingTags(): Int
    @Query("SELECT * FROM tracks WHERE enriched = 0 AND userEdited = 0 ORDER BY lastPlayed DESC, playCount DESC LIMIT :n") suspend fun needingEnrichment(n: Int): List<TrackEntity>
    @Query("SELECT COUNT(*) FROM tracks WHERE enriched = 0") fun pendingEnrichment(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(t: List<TrackEntity>): List<Long>
    @Update suspend fun update(t: TrackEntity)
    @Update suspend fun updateAll(t: List<TrackEntity>)
    @Query("UPDATE tracks SET size = :size, mtime = :mtime, tagsRead = 0, seenScan = :scan WHERE id = :id") suspend fun markChanged(id: Long, size: Long, mtime: Long, scan: Long)
    @Query("UPDATE tracks SET seenScan = :scan WHERE id IN (:ids)") suspend fun markSeen(ids: List<Long>, scan: Long)
    @Query("DELETE FROM tracks WHERE seenScan != :scan AND localPath IS NULL") suspend fun sweep(scan: Long): Int
    @Query("SELECT COUNT(*) FROM tracks WHERE seenScan != :scan") suspend fun countUnseen(scan: Long): Int
    @Query("UPDATE tracks SET favorite = :fav WHERE id = :id") suspend fun setFavorite(id: Long, fav: Boolean)
    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayed = :at WHERE id = :id") suspend fun bumpPlay(id: Long, at: Long)
    @Query("UPDATE tracks SET localPath = :p WHERE id = :id") suspend fun setLocal(id: Long, p: String?)
    @Query("SELECT folder, COUNT(*) AS n FROM tracks GROUP BY folder") suspend fun folderCounts(): List<FolderStat>
    @Query("SELECT id FROM tracks WHERE folder = :folder ORDER BY playCount DESC, (artPath IS NULL), id LIMIT 1") suspend fun coverFor(folder: String): Long?
    @Query("SELECT id FROM tracks WHERE folder = :folder OR folder LIKE :prefix ORDER BY (artPath IS NULL), playCount DESC LIMIT 1") suspend fun coverForTree(folder: String, prefix: String): Long?
}

data class PathIndex(val path: String, val size: Long, val mtime: Long, val id: Long)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE isCollection = 1 ORDER BY sortKey, name COLLATE NOCASE") fun collections(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders WHERE parent = :parent ORDER BY name COLLATE NOCASE") fun children(parent: String): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders WHERE path = :path") fun observe(path: String): Flow<FolderEntity?>
    @Query("SELECT * FROM folders ORDER BY path COLLATE NOCASE") fun all(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders WHERE name LIKE :q LIMIT 20") suspend fun search(q: String): List<FolderEntity>
    @Query("DELETE FROM folders") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(f: List<FolderEntity>)
    @Transaction suspend fun replaceAll(f: List<FolderEntity>) { clear(); insertAll(f) }
}

@Dao
interface PlayDao {
    @Insert suspend fun insert(p: PlayEntity): Long
    @Query("UPDATE plays SET positionMs = :pos, completed = :done WHERE id = :id") suspend fun progress(id: Long, pos: Long, done: Boolean)
    @Query(
        """SELECT t.* FROM plays p JOIN tracks t ON t.id = p.trackId
           WHERE p.completed = 0 AND p.positionMs > 20000 AND p.id IN (SELECT MAX(id) FROM plays GROUP BY trackId)
           ORDER BY p.playedAt DESC LIMIT :n"""
    )
    fun unfinished(n: Int): Flow<List<TrackEntity>>
    @Query("SELECT positionMs FROM plays WHERE trackId = :trackId ORDER BY id DESC LIMIT 1") suspend fun lastPosition(trackId: Long): Long?
    @Query("SELECT t.* FROM plays p JOIN tracks t ON t.id = p.trackId ORDER BY p.playedAt DESC LIMIT :n") fun history(n: Int): Flow<List<TrackEntity>>
    @Query("SELECT t.folder AS folder, COUNT(*) AS n FROM plays p JOIN tracks t ON t.id = p.trackId WHERE p.playedAt > :since GROUP BY t.folder ORDER BY n DESC")
    suspend fun folderAffinity(since: Long): List<FolderStat>
    @Query("SELECT t.artist AS artist, COUNT(*) AS n, MAX(t.id) AS artTrackId FROM plays p JOIN tracks t ON t.id = p.trackId WHERE p.playedAt > :since AND t.artist != '' GROUP BY t.artist ORDER BY n DESC LIMIT 10")
    suspend fun artistAffinity(since: Long): List<ArtistStat>
}

data class PlaylistSummary(val id: Long, val name: String, val updatedAt: Long, val n: Int)

@Dao
interface PlaylistDao {
    @Query("SELECT p.id, p.name, p.updatedAt, (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS n FROM playlists p ORDER BY p.updatedAt DESC")
    fun summaries(): Flow<List<PlaylistSummary>>
    @Query("SELECT * FROM playlists WHERE id = :id") fun observe(id: Long): Flow<PlaylistEntity?>
    @Query("SELECT t.* FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId WHERE pt.playlistId = :id ORDER BY pt.position")
    fun tracks(id: Long): Flow<List<TrackEntity>>
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :id ORDER BY position") suspend fun trackIds(id: Long): List<Long>
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :id ORDER BY position LIMIT 4") suspend fun coverIds(id: Long): List<Long>
    @Insert suspend fun create(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name, updatedAt = :at WHERE id = :id") suspend fun rename(id: Long, name: String, at: Long = System.currentTimeMillis())
    @Query("UPDATE playlists SET updatedAt = :at WHERE id = :id") suspend fun touch(id: Long, at: Long = System.currentTimeMillis())
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id") suspend fun clearTracks(id: Long)
    @Insert suspend fun insertTracks(t: List<PlaylistTrackEntity>)

    @Transaction
    suspend fun setTracks(id: Long, ids: List<Long>) {
        clearTracks(id)
        insertTracks(ids.mapIndexed { i, t -> PlaylistTrackEntity(id, i, t) })
        touch(id)
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt") fun all(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE state IN (0,1,2,4) ORDER BY createdAt") fun active(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE state = 0 ORDER BY createdAt LIMIT 1") suspend fun nextQueued(): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE trackId = :id") suspend fun get(id: Long): DownloadEntity?
    @Upsert suspend fun upsert(d: DownloadEntity)
    @Query("UPDATE downloads SET state = :state, bytes = :bytes, total = :total, error = :err WHERE trackId = :id")
    suspend fun update(id: Long, state: Int, bytes: Long, total: Long, err: String?)
    @Query("UPDATE downloads SET state = 2 WHERE state IN (0,1)") suspend fun pauseAll()
    @Query("UPDATE downloads SET state = 0 WHERE state IN (2,4)") suspend fun resumeAll()
    @Query("UPDATE downloads SET state = 0 WHERE state = 1") suspend fun requeueRunning()
    @Query("DELETE FROM downloads WHERE trackId = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM downloads WHERE state = 3") suspend fun clearDone()
}

@Database(
    entities = [TrackEntity::class, FolderEntity::class, PlayEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, DownloadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LumenDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun folders(): FolderDao
    abstract fun plays(): PlayDao
    abstract fun playlists(): PlaylistDao
    abstract fun downloads(): DownloadDao

    companion object {
        fun build(context: Context): LumenDatabase =
            Room.databaseBuilder(context, LumenDatabase::class.java, "lumen.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

