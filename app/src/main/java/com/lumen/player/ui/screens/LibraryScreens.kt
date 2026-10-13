package com.lumen.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.FolderEntity
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.ui.components.AlbumCard
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.CategoryCard
import com.lumen.player.ui.components.EmptyState
import com.lumen.player.ui.components.FolderRow
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.MenuItem
import com.lumen.player.ui.components.SearchField
import com.lumen.player.ui.components.SectionHeader
import com.lumen.player.ui.components.Segmented
import com.lumen.player.ui.components.StatusPill
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.TrackRow
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.metaLine
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SortMode(val label: String) { TITLE("Title A–Z"), ARTIST("Artist"), ADDED("Recently added"), PLAYS("Most played"), DURATION("Duration"), FILE("Folder order") }

fun List<TrackEntity>.sortedByMode(m: SortMode): List<TrackEntity> = when (m) {
    SortMode.TITLE -> sortedBy { it.title.lowercase() }
    SortMode.ARTIST -> sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNo ?: 0 }))
    SortMode.ADDED -> sortedByDescending { it.addedAt }
    SortMode.PLAYS -> sortedByDescending { it.playCount }
    SortMode.DURATION -> sortedByDescending { it.durationMs }
    SortMode.FILE -> sortedBy { it.fileName.lowercase() }
}

@Composable
private fun coverOf(id: Long?): TrackEntity? {
    val env = LocalEnv.current
    var t by remember(id) { mutableStateOf<TrackEntity?>(null) }
    LaunchedEffect(id) { t = id?.let { env.c.library.trackNow(it) } }
    return t
}

@Composable
fun LibraryScreen() {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val collections by c.library.collections.collectAsState(initial = emptyList())
    val albums by c.library.albums.collectAsState(initial = emptyList())
    val artists by c.library.artists.collectAsState(initial = emptyList())
    val favs by c.library.favorites.collectAsState(initial = emptyList())
    val dl by c.library.downloaded.collectAsState(initial = emptyList())
    val count by c.library.trackCount.collectAsState(initial = 0)
    val scan by c.scanner.state.collectAsState()
    val (pill, pillColor) = nasPill()
    val cols = if (env.wide) 4 else 2

    ScreenList(gap = 20.dp) {
        item("title") {
            TitleBar("Library", overline = if (Lumen.design.name == "OBSIDIAN") "$count tracks / NAS" else null) {
                IconBtn(LIcons.refresh, { c.refreshLibrary(); o.toast("Scanning your NAS for changes…", ToastKind.Info) }, size = 40.dp)
                IconBtn(LIcons.plus, {
                    o.menu("Create", null, null, listOf(
                        MenuItem(LIcons.playlist, "New playlist") { env.nav.navigate(Routes.PLAYLISTS) },
                        MenuItem(LIcons.youtube, "Import from YouTube") { env.nav.navigate(Routes.IMPORT) },
                    ))
                }, size = 40.dp)
            }
        }
        item("status") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill(pill, pillColor, onClick = { env.nav.navigate(Routes.NAS) })
                LText(scanLabel(scan).takeIf { scan !is com.lumen.player.data.scan.ScanState.Idle } ?: "$count songs", Lumen.type.caption, Lumen.colors.textTertiary, maxLines = 1)
            }
        }
        item("playall") { PlayAllRow(count) }
        item("seg") { Segmented(listOf("Collections", "Albums", "Artists") + if (!env.wide) listOf("Playlists") else emptyList(), tab, { if (it == 3) env.nav.navigate(Routes.PLAYLISTS) else tab = it }) }
        if (tab == 0) {
            item("quick") {
                val tiles = listOf(
                    Triple(LIcons.music, "All songs", "$count songs") to { env.nav.navigate(Routes.ALL_SONGS) },
                    Triple(LIcons.sparkle, "Recently added", "Newest first") to { env.nav.navigate(Routes.RECENT) },
                    Triple(LIcons.heartFilled, "Favorites", "${favs.size} songs") to { env.nav.navigate(Routes.FAVORITES) },
                    Triple(LIcons.download, "Downloaded", "${dl.size} songs") to { env.nav.navigate(Routes.DOWNLOADS) },
                    Triple(LIcons.history, "History", "Recently played") to { env.nav.navigate(Routes.HISTORY) },
                    Triple(LIcons.youtube, "Import", "YouTube → MP3") to { env.nav.navigate(Routes.IMPORT) },
                )
                GridOf(tiles, if (env.wide) 4 else 2, 12.dp) { (tr, go), w ->
                    Glass(Modifier.width(w).height(64.dp).pressable(onClick = go), radius = Lumen.shapes.tile, shadow = false) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(38.dp).clip(shape(Lumen.shapes.control)).background(Lumen.colors.accentSoft), contentAlignment = Alignment.Center) { LIcon(tr.first, 18.dp, Lumen.colors.accentText) }
                            Column(Modifier.weight(1f)) { LText(tr.second, Lumen.type.label, maxLines = 1, upper = true); LText(tr.third, Lumen.type.caption, Lumen.colors.textSecondary, maxLines = 1) }
                        }
                    }
                }
            }
            item("colhead") { SectionHeader("Collections · ${collections.size}") }
            item("grid") {
                GridOf(collections, cols, 12.dp) { f, w ->
                    CategoryCard(f.name, f.metaLine(), coverOf(f.coverTrackId), { env.nav.navigate(Routes.folder(f.path)) }, width = w, height = w * 1.18f, onLongClick = { env.folderMenu(o, f.path, f.name) })
                }
            }
        }
        if (tab == 1) {
            if (albums.isEmpty()) item("noalb") { EmptyState(LIcons.disc, "No albums yet", "Albums appear as tags are read and metadata is matched online.") }
            item("albums") {
                GridOf(albums, if (env.wide) 5 else 2, 14.dp) { a, w ->
                    AlbumCard(a.album, "${a.artist}${a.year?.let { " · $it" } ?: ""}", coverOf(a.artTrackId), { env.nav.navigate(Routes.album(a.album, a.artist)) }, size = w, seed = a.album)
                }
            }
        }
        if (tab == 2) items(artists, key = { "ar${it.artist}" }) { a ->
            Row(Modifier.fillMaxWidth().height(64.dp).pressable { env.nav.navigate(Routes.artist(a.artist)) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(coverOf(a.artTrackId), Modifier.size(52.dp), radius = if (Lumen.design.name == "OBSIDIAN") 12.dp else 26.dp)
                Column(Modifier.weight(1f)) { LText(a.artist, Lumen.type.body, maxLines = 1); LText("${a.n} songs", Lumen.type.bodyS, Lumen.colors.textSecondary) }
                LIcon(LIcons.chevronRight, 18.dp, Lumen.colors.textTertiary)
            }
        }
    }
}

/** Simple fixed-column grid usable inside a LazyColumn item. */
@Composable
fun <T> GridOf(list: List<T>, cols: Int, gap: Dp, cell: @Composable (T, Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - gap * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            list.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) { row.forEach { cell(it, w) } }
            }
        }
    }
}

// ---------------- Folder browser ----------------

@Composable
fun FolderScreen(path: String) {
    val env = LocalEnv.current
    if (env.wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(240.dp).fillMaxHeight().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp, bottom = 120.dp)) {
            FolderTree(path) { env.nav.navigate(Routes.folder(it)) { launchSingleTop = true } }
        }
        Box(Modifier.weight(1f)) { FolderContent(path) }
    } else FolderContent(path)
}

@Composable
private fun FolderTree(selected: String, onOpen: (String) -> Unit) {
    val env = LocalEnv.current
    val all by env.c.library.allFolders.collectAsState(initial = emptyList())
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val c = Lumen.colors
    Glass(Modifier.fillMaxSize(), radius = Lumen.shapes.panel, shadow = false) {
        LazyColumn(Modifier.padding(10.dp)) {
            item {
                Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIcon(LIcons.drive, 16.dp, c.textTertiary)
                    LText((env.c.nas.config.name.ifBlank { "NAS" }) + " / " + env.c.nas.config.rootPath, Lumen.type.overline, c.textTertiary, maxLines = 1, upper = true)
                }
            }
            val byParent = all.groupBy { it.parent }
            fun visible(parent: String, depth: Int, out: MutableList<Pair<FolderEntity, Int>>) {
                byParent[parent].orEmpty().sortedBy { it.name.lowercase() }.forEach { f ->
                    out += f to depth
                    val open = expanded[f.path] ?: (selected.startsWith(f.path + "/") || selected == f.path)
                    if (open) visible(f.path, depth + 1, out)
                }
            }
            val rows = mutableListOf<Pair<FolderEntity, Int>>().also { visible("", 0, it) }
            items(rows, key = { it.first.path }) { (f, depth) ->
                val sel = f.path == selected
                val hasKids = byParent[f.path].orEmpty().isNotEmpty()
                val open = expanded[f.path] ?: (selected.startsWith(f.path + "/") || sel)
                Row(
                    Modifier.fillMaxWidth().height(40.dp).clip(shape(Lumen.shapes.tile)).then(if (sel) Modifier.background(c.accentSoft) else Modifier)
                        .pressable(scaleTo = .98f) { onOpen(f.path) }.padding(start = (8 + depth * 18).dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasKids) Box(Modifier.size(18.dp).pressable { expanded[f.path] = !open }, contentAlignment = Alignment.Center) { LIcon(if (open) LIcons.chevronDown else LIcons.chevronRight, 14.dp, c.textTertiary) }
                    else Spacer(Modifier.width(18.dp))
                    LIcon(LIcons.folder, 18.dp, if (sel) c.accentText else c.textSecondary)
                    LText(f.name, Lumen.type.label, if (sel) c.accentText else c.textPrimary, Modifier.weight(1f), maxLines = 1)
                    LText("${f.totalCount}", Lumen.type.caption, c.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun FolderContent(path: String) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val folder by c.library.folder(path).collectAsState(initial = null)
    val subs by c.library.subfolders(path).collectAsState(initial = emptyList())
    val direct by c.library.tracksIn(path).collectAsState(initial = emptyList())
    val ps by c.player.state.collectAsState()
    var sort by rememberSaveable { mutableStateOf(SortMode.TITLE) }
    var filter by rememberSaveable { mutableStateOf("") }
    val name = folder?.name ?: path.substringAfterLast('/').ifBlank { "Music" }
    val cover = coverOf(folder?.coverTrackId)
    val tracks = remember(direct, sort, filter) {
        direct.sortedByMode(sort).filter { filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) }
    }
    val totalMs = direct.sumOf { it.durationMs }
    suspend fun all(): List<TrackEntity> = c.library.tracksInTree(path).first()

    ScreenList(gap = 18.dp) {
        item("bar") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBtn(LIcons.chevronLeft, { env.nav.popBackStack() }, size = 40.dp)
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val crumbs = listOf("Music") + path.split('/').filter { it.isNotEmpty() }
                    crumbs.forEachIndexed { i, cr ->
                        if (i > 0) LIcon(LIcons.chevronRight, 14.dp, Lumen.colors.textTertiary)
                        LText(cr, Lumen.type.label, if (i == crumbs.lastIndex) Lumen.colors.textPrimary else Lumen.colors.textTertiary, maxLines = 1,
                            modifier = Modifier.pressable { if (i < crumbs.lastIndex) env.nav.navigate(Routes.folder(path.split('/').take(i).joinToString("/"))) })
                    }
                }
                IconBtn(LIcons.sort, {
                    o.menu("Sort by", null, null, SortMode.entries.map { m -> MenuItem(null, m.label, checked = m == sort) { sort = m } })
                }, size = 40.dp)
                IconBtn(LIcons.more, { env.folderMenu(o, path, name) }, size = 40.dp)
            }
        }
        item("hero") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(cover, Modifier.size(if (env.wide) 150.dp else 136.dp).shadow(16.dp, shape(Lumen.shapes.card)), radius = Lumen.shapes.card)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LText("Collection", Lumen.type.overline, Lumen.colors.accentText, upper = true)
                    LText(name, Lumen.type.display, maxLines = 2)
                    LText(buildString {
                        append("${folder?.totalCount ?: direct.size} songs")
                        if (subs.isNotEmpty()) append(" · ${subs.size} folders")
                        if (totalMs > 0) append(" · ${fmtTime(totalMs)}")
                    }, Lumen.type.bodyS, Lumen.colors.textSecondary)
                }
            }
        }
        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LButton("Play", { c.appScope.launch { c.player.play(all()) } }, Modifier.weight(1f), icon = LIcons.play)
                LButton("Shuffle", { c.appScope.launch { c.player.play(all(), shuffle = true) } }, Modifier.weight(1f), icon = LIcons.shuffle, kind = BtnKind.Glass)
                IconBtn(LIcons.download, { c.appScope.launch { c.downloads.enqueueFolder(path) }; o.toast("Downloading “$name” for offline", ToastKind.Info) }, size = 48.dp)
            }
        }
        if (direct.size > 12) item("filter") { SearchField(filter, { filter = it }, placeholder = "Filter $name") }
        if (subs.isNotEmpty()) {
            if (env.wide) item("subchips") {
                HRow(8.dp) { items(subs, key = { "chip${it.path}" }) { s -> LChip(s.name, false, { env.nav.navigate(Routes.folder(s.path)) }, icon = LIcons.folder) } }
            } else {
                item("subhead") { SectionHeader("Folders · ${subs.size}") }
                items(subs, key = { "sub${it.path}" }) { s -> FolderRow(s.name, s.metaLine(), { env.nav.navigate(Routes.folder(s.path)) }, onLongClick = { env.folderMenu(o, s.path, s.name) }) }
            }
        }
        if (tracks.isNotEmpty()) {
            item("songhead") { SectionHeader("Songs · ${tracks.size}", action = sort.label) { o.menu("Sort by", null, null, SortMode.entries.map { m -> MenuItem(null, m.label, checked = m == sort) { sort = m } }) } }
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
                TrackRow(
                    t, { c.player.play(tracks, i) }, playing = ps.current?.id == t.id, onMore = { env.trackMenu(o, t) },
                    index = if (env.wide) i + 1 else null, showAlbum = env.wide,
                )
            }
        } else if (subs.isEmpty()) item("emptyf") { EmptyState(LIcons.folder, "Empty folder", "No supported audio files here yet.") }
    }
}

// ---------------- Artist / Album / Favorites / History ----------------

@Composable
fun TrackListScreen(title: String, overline: String, tracksFlow: kotlinx.coroutines.flow.Flow<List<TrackEntity>>, emptyTitle: String, emptyBody: String, header: (@Composable (List<TrackEntity>) -> Unit)? = null) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val tracks by tracksFlow.collectAsState(initial = emptyList())
    val ps by c.player.state.collectAsState()
    ScreenList(gap = 16.dp) {
        item("bar") { BackBar(title, overline) }
        if (header != null) item("hdr") { header(tracks) }
        if (tracks.isNotEmpty()) item("act") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Play", { c.player.play(tracks) }, Modifier.weight(1f), icon = LIcons.play)
                LButton("Shuffle", { c.player.play(tracks, shuffle = true) }, Modifier.weight(1f), icon = LIcons.shuffle, kind = BtnKind.Glass)
            }
        }
        if (tracks.isEmpty()) item("empty") { EmptyState(LIcons.music, emptyTitle, emptyBody) }
        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(t, { c.player.play(tracks, i) }, playing = ps.current?.id == t.id, onMore = { env.trackMenu(o, t) }, index = if (env.wide) i + 1 else null, showAlbum = env.wide)
        }
    }
}

@Composable
fun ArtistScreen(name: String) {
    val env = LocalEnv.current
    TrackListScreen(name, "Artist", env.c.library.byArtist(name), "No songs", "") { tracks ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Artwork(tracks.firstOrNull { it.artPath != null } ?: tracks.firstOrNull(), Modifier.size(120.dp), radius = if (Lumen.design.name == "OBSIDIAN") 18.dp else 60.dp)
            Column { LText(name, Lumen.type.display, maxLines = 2); LText("${tracks.size} songs · ${tracks.map { it.album }.filter { it.isNotBlank() }.distinct().size} albums", Lumen.type.bodyS, Lumen.colors.textSecondary) }
        }
    }
}

@Composable
fun AlbumScreen(name: String, artist: String) {
    val env = LocalEnv.current
    TrackListScreen(name, "Album", env.c.library.byAlbum(name, artist), "No songs", "") { tracks ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Artwork(tracks.firstOrNull { it.artPath != null } ?: tracks.firstOrNull(), Modifier.size(220.dp).shadow(20.dp, shape(Lumen.shapes.card)), radius = Lumen.shapes.card)
            LText(name, Lumen.type.titleL, maxLines = 2)
            LText("$artist${tracks.firstOrNull()?.year?.let { " · $it" } ?: ""} · ${tracks.size} songs", Lumen.type.bodyS, Lumen.colors.textSecondary, Modifier.pressable { env.nav.navigate(Routes.artist(artist)) })
        }
    }
}

@Composable
fun FavoritesScreen() {
    val env = LocalEnv.current
    TrackListScreen("Favorites", "Collection", env.c.library.favorites, "No favorites yet", "Tap the heart on any song to keep it here.")
}

@Composable
fun AllSongsScreen() {
    val env = LocalEnv.current
    TrackListScreen("All songs", "Library", env.c.library.allSongs, "No songs yet", "Connect your NAS and scan to see every song here.") { tracks ->
        LText("${tracks.size} songs · A–Z", Lumen.type.bodyS, Lumen.colors.textSecondary)
    }
}

@Composable
fun RecentlyAddedScreen() {
    val env = LocalEnv.current
    TrackListScreen("Recently added", "Newest first", env.c.library.recentlyAddedAll, "Nothing new yet", "Songs you add to the NAS or import show up here.")
}

@Composable
fun HistoryScreen() {
    val env = LocalEnv.current
    TrackListScreen("Listening history", "Last 200 plays", env.c.library.history, "Nothing played yet", "Songs you play show up here.")
}

@Suppress("unused")
private val unusedFlow = flowOf(1)
@Suppress("unused")
private val unusedCircle = CircleShape
@Suppress("unused")
private fun unusedDisplay(t: TrackEntity) = t.displayArtist()

/** "Play all / Shuffle all" for the whole library, plus a way into the full song list. */
@Composable
fun PlayAllRow(count: Int) {
    val env = LocalEnv.current
    val c = env.c
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        LButton("Play all", { c.appScope.launch { val all = c.library.allSongsNow(); c.player.play(all) } }, Modifier.weight(1f), icon = LIcons.play, enabled = count > 0)
        LButton("Shuffle all", { c.appScope.launch { val all = c.library.allSongsNow(); c.player.play(all, shuffle = true) } }, Modifier.weight(1f), icon = LIcons.shuffle, kind = BtnKind.Glass, enabled = count > 0)
        com.lumen.player.ui.components.IconBtn(LIcons.list, { env.nav.navigate(Routes.ALL_SONGS) }, size = 48.dp)
    }
}
