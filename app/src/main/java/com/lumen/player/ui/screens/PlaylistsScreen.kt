package com.lumen.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.PlaylistSummary
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.DashedAddRow
import com.lumen.player.ui.components.DialogSpec
import com.lumen.player.ui.components.EmptyState
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.IconKind
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.MenuItem
import com.lumen.player.ui.components.Mosaic
import com.lumen.player.ui.components.PromptSpec
import com.lumen.player.ui.components.SearchField
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.TrackRow
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlaylistsScreen(selected: Long? = null) {
    val env = LocalEnv.current
    if (env.wide) {
        val lists by env.c.library.playlists.collectAsState(initial = emptyList())
        var sel by remember { mutableStateOf(selected) }
        LaunchedEffect(lists) { if (sel == null) sel = lists.firstOrNull()?.id }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.width(290.dp)) { PlaylistList(sel) { sel = it } }
            Box(Modifier.weight(1f)) { val s = sel; if (s != null) PlaylistDetail(s, showBack = false) else EmptyState(LIcons.playlist, "No playlist selected", "Create one from songs, a folder or your queue.") }
        }
    } else if (selected != null) PlaylistDetail(selected) else PlaylistList(null) { env.nav.navigate(Routes.playlist(it)) }
}

@Composable
private fun PlaylistList(selected: Long?, onOpen: (Long) -> Unit) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val lists by c.library.playlists.collectAsState(initial = emptyList())
    val favs by c.library.favorites.collectAsState(initial = emptyList())
    ScreenList(gap = 14.dp) {
        item("t") {
            TitleBar(if (env.wide) "Playlists" else "Playlists") {
                IconBtn(LIcons.plus, { newPlaylist(env, o, emptyList()) }, size = 40.dp, kind = IconKind.Accent)
            }
        }
        item("smart") { LText("Smart · update automatically", Lumen.type.overline, Lumen.colors.textTertiary, upper = true) }
        item("fav") { SmartRow("Favorites", "${favs.size} songs", favs) { env.nav.navigate(Routes.FAVORITES) } }
        item("hist") { SmartRow("Recently played", "Listening history", emptyList(), LIcons.history) { env.nav.navigate(Routes.HISTORY) } }
        item("yours") { LText("Yours · ${lists.size}", Lumen.type.overline, Lumen.colors.textTertiary, upper = true) }
        if (lists.isEmpty()) item("none") { LText("No playlists yet. Create one, or long-press a folder → Save as playlist.", Lumen.type.bodyS, Lumen.colors.textSecondary) }
        items(lists, key = { it.id }) { p -> PlaylistRow(p, p.id == selected) { onOpen(p.id) } }
        item("add") { DashedAddRow("New playlist") { newPlaylist(env, o, emptyList()) } }
    }
}

fun newPlaylist(env: com.lumen.player.ui.nav.Env, o: com.lumen.player.ui.components.Overlays, ids: List<Long>) {
    o.prompt(PromptSpec("New playlist", "", "Late night feels", "Create") { name ->
        env.c.appScope.launch { val id = env.c.library.createPlaylist(name, ids); o.toast("Created “$name”"); if (!env.wide) env.nav.navigate(Routes.playlist(id)) }
    })
}

@Composable
private fun SmartRow(name: String, meta: String, tracks: List<TrackEntity>, icon: androidx.compose.ui.graphics.vector.ImageVector = LIcons.heartFilled, onClick: () -> Unit) {
    val c = Lumen.colors
    Row(Modifier.fillMaxWidth().height(72.dp).pressable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (tracks.size >= 4) Mosaic(tracks.take(4), 60.dp, radius = minOf(16.dp, Lumen.shapes.tile))
        else Box(Modifier.size(60.dp).clip(shape(minOf(16.dp, Lumen.shapes.tile))).background(c.accentSoft), contentAlignment = Alignment.Center) { LIcon(icon, 24.dp, c.accentText) }
        Column(Modifier.weight(1f)) { LText(name, Lumen.type.body, maxLines = 1); LText(meta, Lumen.type.bodyS, c.textSecondary, maxLines = 1) }
        LIcon(LIcons.chevronRight, 20.dp, c.textTertiary)
    }
}

@Composable
private fun PlaylistRow(p: PlaylistSummary, selected: Boolean, onClick: () -> Unit) {
    val env = LocalEnv.current
    val c = Lumen.colors
    var cover by remember(p.id, p.n) { mutableStateOf<List<TrackEntity>>(emptyList()) }
    LaunchedEffect(p.id, p.n) { cover = env.c.library.playlistCover(p.id) }
    Row(
        Modifier.fillMaxWidth().height(72.dp).clip(shape(Lumen.shapes.tile)).then(if (selected) Modifier.background(c.accentSoft) else Modifier).pressable(onClick = onClick).padding(horizontal = if (selected) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Mosaic(cover, 60.dp, radius = minOf(16.dp, Lumen.shapes.tile), seed = p.name)
        Column(Modifier.weight(1f)) {
            LText(p.name, Lumen.type.body, maxLines = 1)
            LText("${p.n} songs · edited ${android.text.format.DateUtils.getRelativeTimeSpanString(p.updatedAt)}", Lumen.type.bodyS, c.textSecondary, maxLines = 1)
        }
        LIcon(LIcons.chevronRight, 20.dp, c.textTertiary)
    }
}

@Composable
fun PlaylistDetail(id: Long, showBack: Boolean = true) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val pl by c.library.playlist(id).collectAsState(initial = null)
    val tracks by c.library.playlistTracks(id).collectAsState(initial = emptyList())
    val ps by c.player.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val rowPx = with(density) { 66.dp.toPx() }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val name = pl?.name ?: ""

    ScreenList(gap = 14.dp) {
        if (showBack) item("bar") {
            BackBar(name, "Playlist") {
                IconBtn(LIcons.more, {
                    o.menu(name, "${tracks.size} songs", null, listOf(
                        MenuItem(LIcons.edit, "Rename") { o.prompt(PromptSpec("Rename playlist", name, "Name", "Save") { n -> c.appScope.launch { c.library.renamePlaylist(id, n) } }) },
                        MenuItem(LIcons.download, "Download playlist") { c.appScope.launch { c.downloads.enqueuePlaylist(id, name) }; o.toast("Downloading “$name”", ToastKind.Info) },
                        MenuItem(LIcons.queue, "Add to queue") { c.player.addToQueue(tracks) },
                        MenuItem(null, "—") {},
                        MenuItem(LIcons.trash, "Delete playlist", danger = true) {
                            o.dialog(DialogSpec(LIcons.trash, "Delete “$name”?", "The songs stay in your library.", "Delete", true) { c.appScope.launch { c.library.deletePlaylist(id) }; env.nav.popBackStack() })
                        },
                    ))
                }, size = 40.dp)
            }
        }
        item("hero") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Mosaic(tracks.take(4), if (env.wide) 150.dp else 120.dp, radius = Lumen.shapes.card, seed = name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LText(if (editing) "Playlist · Editing" else "Playlist", Lumen.type.overline, Lumen.colors.accentText, upper = true)
                    LText(name, Lumen.type.titleL, maxLines = 2, modifier = Modifier.pressable { o.prompt(PromptSpec("Rename playlist", name, "Name", "Save") { n -> c.appScope.launch { c.library.renamePlaylist(id, n) } }) })
                    LText("${tracks.size} songs · ${fmtTime(tracks.sumOf { it.durationMs })}", Lumen.type.bodyS, Lumen.colors.textSecondary)
                }
            }
        }
        item("act") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!editing) {
                    LButton("Play", { c.player.play(tracks) }, Modifier.weight(1f), icon = LIcons.play, enabled = tracks.isNotEmpty())
                    LButton("Shuffle", { c.player.play(tracks, shuffle = true) }, Modifier.weight(1f), icon = LIcons.shuffle, kind = BtnKind.Glass, enabled = tracks.isNotEmpty())
                    IconBtn(LIcons.edit, { editing = true }, size = 48.dp)
                } else LButton("Done editing", { editing = false }, Modifier.weight(1f), icon = LIcons.check)
                IconBtn(LIcons.download, { c.appScope.launch { c.downloads.enqueuePlaylist(id, name) }; o.toast("Downloading “$name”", ToastKind.Info) }, size = 48.dp)
            }
        }
        if (tracks.isEmpty()) item("empty") { EmptyState(LIcons.playlist, "Empty playlist", "Add songs from search, folders or the ⋮ menu on any song.") }
        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
            if (editing) {
                val dragging = dragIndex == i
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { if (dragging) { translationY = dragOffset; scaleX = 1.02f; scaleY = 1.02f } }
                        .then(if (dragging) Modifier.clip(shape(Lumen.shapes.tile)).background(Lumen.colors.glassFillStrong) else Modifier),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(32.dp).pointerInput(i, tracks.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragIndex = i; dragOffset = 0f },
                            onDragEnd = {
                                val to = (i + (dragOffset / rowPx).roundToInt()).coerceIn(0, tracks.lastIndex)
                                if (to != i) { val l = tracks.map { it.id }.toMutableList(); val x = l.removeAt(i); l.add(to, x); c.appScope.launch { c.library.setPlaylistTracks(id, l) } }
                                dragIndex = null; dragOffset = 0f
                            },
                            onDragCancel = { dragIndex = null; dragOffset = 0f },
                        ) { ch, d -> ch.consume(); dragOffset += d.y }
                    }, contentAlignment = Alignment.Center) { LIcon(LIcons.drag, 18.dp, Lumen.colors.textTertiary) }
                    Box(Modifier.weight(1f)) { TrackRow(t, {}) }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Lumen.colors.track).pressable {
                        c.appScope.launch { c.library.setPlaylistTracks(id, tracks.filterIndexed { j, _ -> j != i }.map { it.id }) }
                    }, contentAlignment = Alignment.Center) { LIcon(LIcons.close, 14.dp, Lumen.colors.danger) }
                }
            } else TrackRow(t, { c.player.play(tracks, i) }, playing = ps.current?.id == t.id, onMore = { env.trackMenu(o, t, id to tracks) }, index = if (env.wide) i + 1 else null)
        }
        item("addsongs") { DashedAddRow("Add songs") { addSongsSheet(env, o, id, tracks.map { it.id }) } }
    }
}

private fun addSongsSheet(env: com.lumen.player.ui.nav.Env, o: com.lumen.player.ui.components.Overlays, id: Long, existing: List<Long>) {
    o.sheet("Add songs", "Search your library") { _ ->
        var q by remember { mutableStateOf("") }
        var res by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
        var added by remember { mutableStateOf(existing.toSet()) }
        LaunchedEffect(q) { kotlinx.coroutines.delay(180); res = env.c.library.search(q).tracks.take(40) }
        SearchField(q, { q = it })
        res.forEach { t ->
            val inList = t.id in added
            TrackRow(t, {
                if (!inList) { added = added + t.id; env.c.appScope.launch { env.c.library.addToPlaylist(id, listOf(t.id)) } }
            }, trailing = { LIcon(if (inList) LIcons.check else LIcons.plus, 20.dp, Lumen.colors.accentText) })
        }
    }
}
