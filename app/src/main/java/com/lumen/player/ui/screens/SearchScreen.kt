package com.lumen.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.lumen.player.data.library.SearchResults
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.Badge
import com.lumen.player.ui.components.EmptyState
import com.lumen.player.ui.components.FolderRow
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.IconKind
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.SearchField
import com.lumen.player.ui.components.SectionHeader
import com.lumen.player.ui.components.TrackRow
import com.lumen.player.ui.components.collectionName
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.components.metaLine
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.delay

@Composable
fun SearchScreen() {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    var res by remember { mutableStateOf(SearchResults()) }
    val ps by c.player.state.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(q) { delay(180); res = c.library.search(q) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val filters = listOf("All", "Songs", "Artists", "Albums", "Folders", "Lyrics", "Offline")

    ScreenList(gap = 18.dp) {
        item("title") { TitleBar("Search") }
        item("field") { SearchField(q, { q = it }, autoFocusRequester = focus) }
        item("chips") { HRow(8.dp) { items(filters.size) { i -> LChip(filters[i], i == filter, { filter = i }) } } }
        if (q.length < 2) {
            item("hint") { EmptyState(LIcons.search, "Search everything", "Titles, artists, albums, folder names — and words inside lyrics.") }
            return@ScreenList
        }
        val tracks = if (filter == 6) res.tracks.filter { it.localPath != null } else res.tracks
        if (tracks.isEmpty() && res.folders.isEmpty() && res.lyrics.isEmpty()) {
            item("none") { EmptyState(LIcons.search, "No results for “$q”", "Try fewer words, or check the spelling.") }
            return@ScreenList
        }
        val top = tracks.firstOrNull()
        if (top != null && filter in listOf(0, 1, 6)) item("top") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LText("Top result", Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
                Glass(Modifier.fillMaxWidth().pressable(scaleTo = .98f) { c.player.play(tracks, 0) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Artwork(top, Modifier.size(84.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LText(top.title, Lumen.type.titleM, maxLines = 1)
                            LText("Song · ${top.displayArtist()}", Lumen.type.bodyS, Lumen.colors.textSecondary, maxLines = 1)
                            Badge(top.folder.replace("/", " › ").ifBlank { "Music" })
                        }
                        IconBtn(LIcons.play, { c.player.play(tracks, 0) }, size = 48.dp, kind = IconKind.Accent)
                    }
                }
            }
        }
        if (filter in listOf(0, 1, 6) && tracks.size > 1) {
            item("songhead") { SectionHeader("Songs · ${tracks.size}") }
            items(tracks.drop(1).take(if (filter == 0) 8 else 200), key = { "s${it.id}" }) { t ->
                TrackRow(t, { c.player.play(tracks, tracks.indexOf(t)) }, playing = ps.current?.id == t.id, onMore = { env.trackMenu(o, t) }, subtitle = "${t.displayArtist()} · ${t.collectionName()}")
            }
        }
        if (filter in listOf(0, 2) && res.artists.isNotEmpty()) item("artists") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Artists")
                HRow {
                    items(res.artists, key = { "a${it.first}" }) { (name, t) ->
                        Column(Modifier.pressable { env.nav.navigate(Routes.artist(name)) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Artwork(t, Modifier.size(76.dp), radius = if (Lumen.design.name == "OBSIDIAN") 14.dp else 38.dp)
                            LText(name, Lumen.type.caption, Lumen.colors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (filter in listOf(0, 3) && res.albums.isNotEmpty()) item("albums") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Albums")
                HRow { items(res.albums, key = { "al${it.first}" }) { (name, t) -> com.lumen.player.ui.components.AlbumCard(name, t.displayArtist(), t, { env.nav.navigate(Routes.album(name, t.albumArtist.ifBlank { t.artist })) }) } }
            }
        }
        if (filter in listOf(0, 5) && res.lyrics.isNotEmpty()) item("lyrics") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("In lyrics")
                res.lyrics.take(5).forEach { t ->
                    val line = t.lyrics!!.lines().firstOrNull { it.contains(q, true) }?.replace(Regex("""^\[[\d:.]+]\s*"""), "")?.trim() ?: ""
                    Glass(Modifier.fillMaxWidth().pressable(scaleTo = .98f) { c.player.play(listOf(t)) }, shadow = false) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LText("“…$line…”", Lumen.type.body, maxLines = 3)
                            LText("${t.title} · ${t.displayArtist()}", Lumen.type.caption, Lumen.colors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        if (filter in listOf(0, 4) && res.folders.isNotEmpty()) {
            item("fhead") { SectionHeader("Folders") }
            items(res.folders, key = { "f${it.path}" }) { f -> FolderRow(f.name, f.path.replace("/", " › ").ifBlank { "Music" } + " · " + f.metaLine(), { env.nav.navigate(Routes.folder(f.path)) }) }
        }
    }
}

@Suppress("unused")
@Composable
private fun unusedBox() = Box(Modifier) {}
