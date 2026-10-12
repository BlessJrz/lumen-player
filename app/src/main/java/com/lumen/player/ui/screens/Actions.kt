package com.lumen.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.ui.components.DashedAddRow
import com.lumen.player.ui.components.DialogSpec
import com.lumen.player.ui.components.MenuItem
import com.lumen.player.ui.components.Overlays
import com.lumen.player.ui.components.PromptSpec
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.collectionName
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.Env
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The custom long-press / ⋮ menu for a song. */
fun Env.trackMenu(o: Overlays, t: TrackEntity, playlistContext: Pair<Long, List<TrackEntity>>? = null) {
    val scope = c.appScope
    val items = buildList {
        add(MenuItem(LIcons.play, "Play next") { c.player.playNext(listOf(t)); o.toast("Plays next: ${t.title}") })
        add(MenuItem(LIcons.queue, "Add to queue") { c.player.addToQueue(listOf(t)); o.toast("Added to queue") })
        add(MenuItem(LIcons.playlist, "Add to playlist…") { addToPlaylistSheet(o, listOf(t)) })
        add(MenuItem(if (t.favorite) LIcons.heartFilled else LIcons.heart, if (t.favorite) "Remove from favorites" else "Add to favorites") {
            scope.launch { c.library.toggleFavorite(t); c.player.reloadCurrent() }
        })
        if (t.localPath == null) add(MenuItem(LIcons.download, "Download") { scope.launch { c.downloads.enqueue(listOf(t.id)) }; o.toast("Downloading ${t.title}", ToastKind.Info) })
        add(MenuItem(LIcons.edit, "Edit info & artwork") { nav.navigate(Routes.edit(t.id)) })
        add(MenuItem(LIcons.folder, "Go to folder") { nav.navigate(Routes.folder(t.folder)) })
        if (t.artist.isNotBlank()) add(MenuItem(LIcons.user, "Go to artist") { nav.navigate(Routes.artist(t.artist)) })
        if (t.album.isNotBlank()) add(MenuItem(LIcons.disc, "Go to album") { nav.navigate(Routes.album(t.album, t.albumArtist.ifBlank { t.artist })) })
        if (playlistContext != null) add(MenuItem(LIcons.close, "Remove from this playlist", danger = true) {
            scope.launch { c.library.setPlaylistTracks(playlistContext.first, playlistContext.second.filter { it.id != t.id }.map { it.id }) }
        })
        if (t.localPath != null) {
            add(MenuItem(null, "—") {})
            add(MenuItem(LIcons.trash, "Remove download", danger = true) {
                o.dialog(DialogSpec(LIcons.trash, "Remove download?", "“${t.title}” stays on your NAS and can be downloaded again any time.", "Remove", true) {
                    scope.launch { c.downloads.remove(listOf(t.id)); o.toast("Download removed") }
                })
            })
        }
    }
    o.menu(t.title, "${t.displayArtist()} · ${t.collectionName()}", t, items)
}

fun Env.addToPlaylistSheet(o: Overlays, tracks: List<TrackEntity>) {
    o.sheet("Add to playlist", "${tracks.size} song${if (tracks.size == 1) "" else "s"}") { close ->
        PlaylistPickerList(tracks, o, close)
    }
}

@Composable
private fun PlaylistPickerList(tracks: List<TrackEntity>, o: Overlays, close: () -> Unit) {
    val env = LocalEnv.current
    val lists by env.c.library.playlists.collectAsState(initial = emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DashedAddRow("New playlist") {
            close()
            o.prompt(PromptSpec("New playlist", "", "Late night feels", "Create") { name ->
                env.c.appScope.launch { env.c.library.createPlaylist(name, tracks.map { it.id }); o.toast("Created “$name”") }
            })
        }
        lists.forEach { p ->
            com.lumen.player.ui.components.FolderRow(p.name, "${p.n} songs", onClick = {
                close()
                env.c.appScope.launch { env.c.library.addToPlaylist(p.id, tracks.map { it.id }); o.toast("Added to ${p.name}") }
            })
        }
    }
}

/** Menu for a whole folder / collection. */
fun Env.folderMenu(o: Overlays, path: String, name: String) {
    val scope = c.appScope
    o.menu(name, "Collection", null, listOf(
        MenuItem(LIcons.play, "Play") { scope.launch { val l = c.library.tracksInTree(path).first(); c.player.play(l) } },
        MenuItem(LIcons.shuffle, "Shuffle") { scope.launch { val l = c.library.tracksInTree(path).first(); c.player.play(l, shuffle = true) } },
        MenuItem(LIcons.queue, "Add to queue") { scope.launch { c.player.addToQueue(c.library.tracksInTree(path).first()) } },
        MenuItem(LIcons.download, "Download folder") { scope.launch { c.downloads.enqueueFolder(path) }; o.toast("Downloading $name for offline", ToastKind.Info) },
        MenuItem(LIcons.playlist, "Save as playlist") { scope.launch { c.library.createPlaylist(name, c.library.tracksInTree(path).first().map { it.id }); o.toast("Playlist “$name” created") } },
    ))
}

@Suppress("unused")
@Composable
private fun unusedTheme() = Lumen.colors
