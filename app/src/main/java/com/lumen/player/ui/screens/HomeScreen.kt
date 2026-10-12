package com.lumen.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.library.HomeData
import com.lumen.player.data.nas.NasStatus
import com.lumen.player.data.scan.ScanState
import com.lumen.player.playback.PlayerState
import com.lumen.player.ui.components.AlbumCard
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.Avatar
import com.lumen.player.ui.components.CategoryCard
import com.lumen.player.ui.components.EmptyState
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.IconKind
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LinearBar
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.OrbLoader
import com.lumen.player.ui.components.SectionHeader
import com.lumen.player.ui.components.StatusPill
import com.lumen.player.ui.components.TrackRow
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.metaLine
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.Env
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import java.util.Calendar

@Composable
fun nasPill(): Pair<String, Color> {
    val env = LocalEnv.current
    val st by env.c.nas.status.collectAsState()
    val c = Lumen.colors
    val off by env.c.library.downloaded.collectAsState(initial = emptyList())
    return when (val s = st) {
        is NasStatus.Connected -> (if (s.remote) "Remote · Tailscale" else "${env.c.nas.config.name.ifBlank { "NAS" }} · LAN") to (if (s.remote) c.warning else c.success)
        NasStatus.Connecting -> "Connecting…" to c.warning
        NasStatus.NotConfigured -> "NAS not set up" to c.danger
        is NasStatus.Offline -> "Offline · ${off.size} on device" to c.danger
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Late night"
}

@Composable
fun HomeScreen() {
    val env = LocalEnv.current
    val c = env.c
    val count by c.library.trackCount.collectAsState(initial = -1)
    val ps by c.player.state.collectAsState()
    val scan by c.scanner.state.collectAsState()
    var home by remember { mutableStateOf<HomeData?>(null) }
    var mood by remember { mutableStateOf(0) }
    LaunchedEffect(count, ps.current?.id) { if (count > 0) home = c.library.home() }

    // Unfolded: one full-width column. The persistent player bar already covers "now playing", so no side panel.
    HomeBody(env, count, home, ps, scan, mood) { mood = it }
}

@Composable
private fun HomeBody(env: Env, count: Int, home: HomeData?, ps: PlayerState, scan: ScanState, mood: Int, setMood: (Int) -> Unit) {
    val c = env.c
    val o = LocalOverlays.current
    val design = Lumen.design.name
    val (pill, pillColor) = nasPill()
    val avatarTrack = home?.favorites?.firstOrNull() ?: home?.recentlyPlayed?.firstOrNull()
    ScreenList {
        // ---------- Header (different per design) ----------
        item("header") {
            when (design) {
                "OBSIDIAN" -> Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(pill, pillColor, onClick = { env.nav.navigate(Routes.NAS) })
                        LText("Tonight.", Lumen.type.display)
                    }
                    if (!env.wide) Avatar(avatarTrack) { env.nav.navigate(Routes.SETTINGS) }
                }
                "FLUID" -> Column {
                    LText("Hey Khalil,", Lumen.type.display)
                    LText("what’s the mood?", Lumen.type.display, Lumen.colors.accentText)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        LText(greeting().let { if (it == "Late night") "Late night" else java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(java.util.Date()) + " " + it.substringAfter("Good ").lowercase() }, Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
                        LText("${greeting()}, Khalil", Lumen.type.titleL, maxLines = 2)
                    }
                    if (env.wide) StatusPill(pill, pillColor, onClick = { env.nav.navigate(Routes.NAS) })
                    else { IconBtn(LIcons.search, { env.nav.navigate(Routes.SEARCH) }, size = 40.dp); Avatar(avatarTrack) { env.nav.navigate(Routes.SETTINGS) } }
                }
            }
        }
        if (design == "FLUID") item("moods") {
            HRow(8.dp) {
                val moods = listOf("All") + (home?.categoryRows?.map { it.first.name } ?: emptyList()) + "Discover"
                items(moods.size) { i -> LChip(moods[i], i == mood, { setMood(i) }) }
            }
        }

        // ---------- Empty / first-run states ----------
        if (count == 0 || (count < 0)) {
            item("empty") {
                when {
                    !c.nas.config.isConfigured -> EmptyState(LIcons.server, "Connect your NAS", "Lumen streams the music already on your NAS — Emotional, Hip Hop, Party and every other folder.", action = "Set up connection") { env.nav.navigate(Routes.NAS) }
                    scan is ScanState.Listing || scan is ScanState.ReadingTags -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OrbLoader(size = 84.dp)
                        LText(scanLabel(scan), Lumen.type.titleM)
                    }
                    else -> EmptyState(LIcons.refresh, "No music found yet", "Check that the music folder in NAS settings points at your songs, then scan again.", action = "Scan now") { c.refreshLibrary() }
                }
            }
            return@ScreenList
        }
        val h = home ?: return@ScreenList

        // Mood filter (Fluid): show just one collection's rows.
        val filter = if (design == "FLUID" && mood in 1..h.categoryRows.size) h.categoryRows[mood - 1].first.path else null

        item("hero") {
            val t = ps.current ?: h.continueListening.firstOrNull() ?: h.recentlyPlayed.firstOrNull()
            if (t != null) Hero(t, ps, env)
        }
        if (scan is ScanState.ReadingTags) item("scan") { ScanBanner(scan) }

        if (filter == null) section("collections") {
            SectionHeader("Your collections", action = "All folders") { env.nav.navigate(Routes.LIBRARY) }
            HRow(12.dp) {
                items(h.collections, key = { it.path }) { f ->
                    val cover = remember(f.coverTrackId) { mutableStateOf<TrackEntity?>(null) }
                    LaunchedEffect(f.coverTrackId) { cover.value = f.coverTrackId?.let { c.library.trackNow(it) } }
                    CategoryCard(f.name, f.metaLine(), cover.value, { env.nav.navigate(Routes.folder(f.path)) }, width = if (env.wide) 220.dp else 176.dp, height = if (env.wide) 260.dp else 212.dp, onLongClick = { env.folderMenu(o, f.path, f.name) })
                }
            }
        }
        if (h.continueListening.isNotEmpty() && filter == null) section("continue") {
            SectionHeader("Continue listening", action = "History") { env.nav.navigate(Routes.HISTORY) }
            Column { h.continueListening.take(3).forEach { t -> TrackRow(t, { c.player.play(listOf(t)) }, onMore = { env.trackMenu(o, t) }, subtitle = "${t.displayArtist()} · ${t.collectionName2()}") } }
        }
        if (h.recentlyPlayed.isNotEmpty() && filter == null) section("recent") {
            SectionHeader("Recently played", action = "History") { env.nav.navigate(Routes.HISTORY) }
            HRow { items(h.recentlyPlayed, key = { "r${it.id}" }) { t -> AlbumCard(t.title, t.displayArtist(), t, { c.player.play(h.recentlyPlayed, h.recentlyPlayed.indexOf(t)) }, onLongClick = { env.trackMenu(o, t) }, onPlay = { c.player.play(listOf(t)) }) } }
        }
        if (h.recommended.isNotEmpty() && filter == null) section("recommended") {
            SectionHeader("Recommended for you")
            HRow { items(h.recommended, key = { it.key }) { m -> AlbumCard(m.title, m.subtitle, m.cover, { c.player.play(m.tracks) }, size = 172.dp, seed = m.key, onPlay = { c.player.play(m.tracks) }) } }
        }
        if (h.mostPlayed.isNotEmpty() && filter == null) section("most") {
            SectionHeader("Your most played")
            Column { h.mostPlayed.take(5).forEachIndexed { i, t -> TrackRow(t, { c.player.play(h.mostPlayed, i) }, playing = ps.current?.id == t.id, subtitle = "${t.displayArtist()} · ${t.playCount} plays", onMore = { env.trackMenu(o, t) }) } }
        }
        if (filter == null) section("added") {
            SectionHeader("Recently added", action = "See all") { env.nav.navigate(Routes.LIBRARY) }
            HRow { items(h.recentlyAdded, key = { "a${it.id}" }) { t -> AlbumCard(t.title, t.displayArtist(), t, { c.player.play(h.recentlyAdded, h.recentlyAdded.indexOf(t)) }, onLongClick = { env.trackMenu(o, t) }, onPlay = { c.player.play(listOf(t)) }) } }
        }
        if (h.favorites.isNotEmpty() && filter == null) section("favs") {
            SectionHeader("Favorites", action = "See all") { env.nav.navigate(Routes.FAVORITES) }
            HRow { items(h.favorites, key = { "f${it.id}" }) { t -> AlbumCard(t.title, t.displayArtist(), t, { c.player.play(h.favorites, h.favorites.indexOf(t)) }, onLongClick = { env.trackMenu(o, t) }, onPlay = { c.player.play(listOf(t)) }) } }
        }
        h.categoryRows.filter { filter == null || it.first.path == filter }.forEach { (f, tracks) ->
            if (tracks.isNotEmpty()) section("cat-${f.path}") {
                SectionHeader(f.name, action = "Open folder") { env.nav.navigate(Routes.folder(f.path)) }
                HRow { items(tracks, key = { "c${f.path}${it.id}" }) { t -> AlbumCard(t.title, t.displayArtist(), t, { c.player.play(tracks, tracks.indexOf(t)) }, onLongClick = { env.trackMenu(o, t) }, onPlay = { c.player.play(listOf(t)) }) } }
            }
        }
        if (h.discoveries.isNotEmpty() && filter == null) section("discover") {
            SectionHeader("Random discoveries", action = "Shuffle") { c.player.play(h.discoveries, shuffle = true) }
            Discover(h.discoveries) { c.player.play(h.discoveries, shuffle = true) }
        }
        if (h.suggestedPlaylists.isNotEmpty() && filter == null) section("suggested") {
            SectionHeader("Suggested playlists")
            HRow {
                items(h.suggestedPlaylists, key = { "s${it.key}" }) { m ->
                    AlbumCard(m.title, m.subtitle, m.cover, { c.player.play(m.tracks) }, seed = m.key, onLongClick = {
                        o.menu(m.title, m.subtitle, m.cover, listOf(
                            com.lumen.player.ui.components.MenuItem(LIcons.play, "Play") { c.player.play(m.tracks) },
                            com.lumen.player.ui.components.MenuItem(LIcons.shuffle, "Shuffle") { c.player.play(m.tracks, shuffle = true) },
                            com.lumen.player.ui.components.MenuItem(LIcons.playlist, "Save as playlist") {
                                c.appScope.launchSafe { c.library.createPlaylist(m.title, m.tracks.map { it.id }); o.toast("Saved “${m.title}”") }
                            },
                        ))
                    }, onPlay = { c.player.play(m.tracks) })
                }
            }
        }
    }
}

fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) { launch { runCatching { block() } } }

private fun TrackEntity.collectionName2() = folder.substringAfterLast('/').ifBlank { "Music" }

fun scanLabel(s: ScanState) = when (s) {
    is ScanState.Listing -> "Discovering music… ${s.files} songs in ${s.folders} folders"
    is ScanState.ReadingTags -> "Reading tags · ${s.done} of ${s.total}"
    is ScanState.Finished -> "${s.total} songs · ${if (s.added > 0) "+${s.added} new" else "up to date"}"
    is ScanState.Failed -> "Scan failed: ${s.message}"
    ScanState.Idle -> "Ready"
}

@Composable
private fun ScanBanner(s: ScanState.ReadingTags) {
    Glass(Modifier.fillMaxWidth(), shadow = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LIcon(LIcons.tag, 18.dp, Lumen.colors.accentText)
                LText("Organizing your library", Lumen.type.label, Modifier.weight(1f), upper = true)
                LText("${s.done}/${s.total}", Lumen.type.caption, Lumen.colors.textSecondary)
            }
            LinearBar(if (s.total > 0) s.done.toFloat() / s.total else 0f)
        }
    }
}

/** "Continue listening" hero — per-design layout. */
@Composable
private fun Hero(t: TrackEntity, ps: PlayerState, env: Env) {
    val c = Lumen.colors
    val isCurrent = ps.current?.id == t.id
    val progress = if (isCurrent) ps.progress else 0f
    val left = if (isCurrent && ps.durationMs > 0) "${fmtTime(ps.durationMs - ps.positionMs)} left" else fmtTime(t.durationMs)
    val play = { if (isCurrent) env.c.player.toggle() else env.c.player.play(listOf(t)) }
    when (Lumen.design.name) {
        "OBSIDIAN" -> Glass(Modifier.fillMaxWidth().height(if (env.wide) 196.dp else 156.dp).pressable(scaleTo = .98f) { env.openPlayer() }) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(t, Modifier.size(if (env.wide) 112.dp else 132.dp), radius = 10.dp)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LText(if (isCurrent) "Now · ${fmtTime(ps.positionMs)} / ${fmtTime(ps.durationMs)}" else "Resume", Lumen.type.overline, c.accentText, upper = true)
                    LText(t.title, Lumen.type.titleL, maxLines = 2)
                    LText(t.displayArtist(), Lumen.type.caption, c.textSecondary, maxLines = 1, upper = true)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearBar(progress, Modifier.weight(1f))
                        IconBtn(if (isCurrent && ps.isPlaying) LIcons.pause else LIcons.play, play, size = 40.dp, kind = IconKind.Accent)
                    }
                }
            }
        }
        "FLUID" -> Box(Modifier.fillMaxWidth().height(if (env.wide) 290.dp else 256.dp).clip(shape(Lumen.shapes.panel)).pressable(scaleTo = .98f) { env.openPlayer() }) {
            Artwork(t, Modifier.fillMaxSize(), radius = 0.dp, outline = false)
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent, Color.Black.copy(alpha = .25f)))))
            Column(Modifier.padding(20.dp)) {
                LText("Pick up where you left off", Lumen.type.overline, Color.White.copy(alpha = .85f), upper = true)
                LText(t.title, Lumen.type.display, Color.White, maxLines = 2)
            }
            Glass(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().height(66.dp), radius = 33.dp, strong = true) {
                Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Artwork(t, Modifier.size(50.dp), radius = 25.dp, outline = false)
                    Column(Modifier.weight(1f)) { LText(t.displayArtist(), Lumen.type.label, maxLines = 1); LText("$left · ${t.collectionName2()}", Lumen.type.caption, c.textSecondary, maxLines = 1) }
                    IconBtn(if (isCurrent && ps.isPlaying) LIcons.pause else LIcons.play, play, size = 48.dp, kind = IconKind.Accent)
                }
            }
        }
        else -> Box(Modifier.fillMaxWidth().height(if (env.wide) 260.dp else 224.dp).clip(shape(Lumen.shapes.card)).pressable(scaleTo = .98f) { env.openPlayer() }) {
            Artwork(t, Modifier.fillMaxSize(), radius = 0.dp, outline = false)
            Glass(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth(), radius = Lumen.shapes.tile, strong = true, shadow = false, fill = if (c.isDark) c.bgBase.copy(alpha = .5f) else c.glassFillStrong) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            LText("Continue listening", Lumen.type.overline, c.textSecondary, upper = true)
                            LText(t.title, Lumen.type.titleM, maxLines = 1)
                            LText("${t.displayArtist()} · $left", Lumen.type.caption, c.textSecondary, maxLines = 1)
                        }
                        IconBtn(if (isCurrent && ps.isPlaying) LIcons.pause else LIcons.play, play, size = 46.dp, kind = IconKind.Accent)
                    }
                    LinearBar(progress)
                }
            }
        }
    }
}

private fun Modifier.aspect1() = this.aspectRatio(1f)

@Composable
private fun Discover(tracks: List<TrackEntity>, onShuffle: () -> Unit) {
    Glass(Modifier.fillMaxWidth().pressable(scaleTo = .98f, onClick = onShuffle)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                tracks.take(3).forEachIndexed { i, t -> Artwork(t, Modifier.size(54.dp).rotate(listOf(-8f, 0f, 8f)[i]).shadow(6.dp, shape(12.dp)), radius = 12.dp) }
            }
            Column(Modifier.weight(1f)) {
                LText("Surprise me", Lumen.type.titleM)
                LText("${tracks.size} songs you haven’t played in a while", Lumen.type.bodyS, Lumen.colors.textSecondary)
            }
            IconBtn(LIcons.shuffle, onShuffle, size = 46.dp, kind = IconKind.Accent)
        }
    }
}

/** Side panel on the unfolded Home screen. */
@Composable
fun NowPlayingPanel(ps: PlayerState, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val c = Lumen.colors
    val o = LocalOverlays.current
    val t = ps.current
    Glass(modifier, radius = Lumen.shapes.panel, strong = Lumen.design.name != "OBSIDIAN") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LText("Now playing", Lumen.type.overline, c.textTertiary, Modifier.weight(1f), upper = true)
                if (t != null) Box(Modifier.pressable { env.trackMenu(o, t) }) { LIcon(LIcons.moreVert, 18.dp, c.textSecondary) }
            }
            if (t == null) {
                LText("Nothing playing yet. Pick a collection to start.", Lumen.type.bodyS, c.textSecondary)
                return@Column
            }
            val fluid = Lumen.design.name == "FLUID"
            Box(Modifier.fillMaxWidth().pressable(scaleTo = .98f) { env.openPlayer() }) {
                Artwork(t, Modifier.fillMaxWidth().aspectSquare(), radius = if (fluid) 200.dp else (Lumen.shapes.card - 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { LText(t.title, Lumen.type.titleM, maxLines = 1); LText(t.displayArtist(), Lumen.type.bodyS, c.textSecondary, maxLines = 1) }
                Box(Modifier.pressable { env.c.appScope.launchSafe { env.c.library.toggleFavorite(t); env.c.player.reloadCurrent() } }) {
                    LIcon(if (t.favorite) LIcons.heartFilled else LIcons.heart, 22.dp, c.accentText)
                }
            }
            if (Lumen.design.name == "OBSIDIAN") com.lumen.player.ui.components.Waveform(ps.progress, t.id.toInt(), { env.c.player.seekTo(it) }, bars = 40, height = 28.dp)
            else com.lumen.player.ui.components.LSlider(ps.progress, {}, onDone = { env.c.player.seekTo(it) })
            Row(Modifier.fillMaxWidth()) { LText(fmtTime(ps.positionMs), Lumen.type.caption, c.textSecondary, Modifier.weight(1f)); LText(fmtTime(ps.durationMs), Lumen.type.caption, c.textSecondary) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.pressable { env.c.player.setShuffle(!ps.shuffle) }) { LIcon(LIcons.shuffle, 20.dp, if (ps.shuffle) c.accentText else c.textSecondary) }
                Box(Modifier.pressable { env.c.player.previous() }) { LIcon(LIcons.prev, 26.dp) }
                IconBtn(if (ps.isPlaying) LIcons.pause else LIcons.play, { env.c.player.toggle() }, size = 56.dp, kind = IconKind.Accent)
                Box(Modifier.pressable { env.c.player.next() }) { LIcon(LIcons.next, 26.dp) }
                Box(Modifier.pressable { env.c.player.cycleRepeat() }) { LIcon(if (ps.repeat == 1) LIcons.repeatOne else LIcons.repeat, 20.dp, if (ps.repeat != 0) c.accentText else c.textSecondary) }
            }
            com.lumen.player.ui.components.Divider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                LText("Up next", Lumen.type.overline, c.textTertiary, Modifier.weight(1f), upper = true)
                LText("Queue · ${(ps.queue.size - ps.index - 1).coerceAtLeast(0)}", Lumen.type.caption, c.accentText, Modifier.pressable { env.openQueue() }, upper = true)
            }
            ps.queue.drop(ps.index + 1).take(3).forEachIndexed { i, q -> TrackRow(q, { env.c.player.skipTo(ps.index + 1 + i) }) }
        }
    }
}

private fun Modifier.aspectSquare() = this.aspectRatio(1f)

@Suppress("unused")
private val unusedCircle = CircleShape
@Suppress("unused")
private val unusedOffset = Modifier.offset(0.dp)
@Suppress("unused")
private fun dp(x: Dp) = x
