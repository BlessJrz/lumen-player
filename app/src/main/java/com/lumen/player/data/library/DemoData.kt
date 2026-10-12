package com.lumen.player.data.library

import android.content.Context
import com.lumen.player.data.db.LumenDatabase
import com.lumen.player.data.db.MetaSource
import com.lumen.player.data.db.PlayEntity
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.scan.LibraryScanner

/**
 * Sample library used only for automated UI screenshots in CI (launched with the "lumen.demo" extra).
 * Never runs during normal use.
 */
object DemoData {
    fun isDemo(context: Context) = context.getSharedPreferences("demo", Context.MODE_PRIVATE).getBoolean("on", false)

    suspend fun seed(context: Context, db: LumenDatabase, scanner: LibraryScanner) {
        context.getSharedPreferences("demo", Context.MODE_PRIVATE).edit().putBoolean("on", true).apply()
        if (db.tracks().countNow() > 0) return
        val rows = listOf(
            "Playlists/Emotional" to listOf("Weightless Nights|Lyra Vale|Glass Hearts", "Velvet Static|Nova Reign|Velvet Rain", "Ocean Hours|Saint Halo|Ocean Hours", "Letters I Never Sent|Nova Reign|Velvet Rain", "Wildflower Season|Mosaic Youth|Wildflower", "Midnight Bloom|Lyra Vale|Midnight Bloom"),
            "Playlists/Hip Hop" to listOf("Neon Psalms|Kairo|Neon Avenue", "Gold Teeth, Silver Tongue|J. Ember|Gold Teeth", "Amber Static|Velour|Amber Static", "Smoke & Mirrors|Kairo|Smoke and Mirrors", "Crown Heavy|J. Ember|Gold Teeth"),
            "Playlists/Party" to listOf("Afterglow Protocol|The Glass Wolves|After Hours", "Sunset Drive|DJ Prism|Sunset Drive", "Bassline Theory|Oceanic|Bassline", "Rooftop Frequencies|DJ Prism|Neon Avenue", "Dancefloor Prayer|The Glass Wolves|After Hours"),
            "Playlists/Low Roar" to listOf("Half Asleep|Low Roar|Once in a Long", "Bones|Low Roar|Ross"),
            "Playlists/Made In Abyss" to listOf("Hanezeve Caradhina|Kevin Penkin|Made in Abyss OST", "The First Layer|Kevin Penkin|Made in Abyss OST"),
            "" to listOf("Agni Kai|Unknown|", "True Faith|New Order|Substance"),
        )
        val now = System.currentTimeMillis()
        var i = 0
        val tracks = rows.flatMap { (folder, list) ->
            list.map { s ->
                val (t, a, al) = s.split("|")
                i++
                TrackEntity(
                    path = (if (folder.isEmpty()) "" else "$folder/") + "$a - $t.mp3", folder = folder, fileName = "$a - $t.mp3",
                    size = 6_000_000L + i * 1000, mtime = now - i * 3_600_000L, title = t, artist = a, album = al, albumArtist = a,
                    durationMs = (150 + (i * 37) % 160) * 1000L, titleSource = MetaSource.TAG, tagsRead = true, enriched = true,
                    favorite = i % 3 == 0, playCount = (i * 7) % 23, lastPlayed = now - i * 900_000L, addedAt = now - i * 86_400_000L,
                    lyrics = if (i == 1) "[00:05.00]City lights are humming low\n[00:10.00]every street a quiet glow\n[00:15.00]Hold on to the weightless nights,\n[00:20.00]we were never meant to land\n[00:25.00]Falling slow through neon skies\n[00:30.00]with your heartbeat in my hand" else null,
                    lyricsSynced = i == 1,
                )
            }
        }
        val ids = db.tracks().insertAll(tracks)
        ids.take(8).forEachIndexed { k, id -> db.plays().insert(PlayEntity(trackId = id, playedAt = now - k * 600_000L, positionMs = 60_000, completed = k > 2)) }
        scanner.rebuildFolders()
    }
}
