@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lumen.player.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.playback.OutputDevice
import com.lumen.player.playback.PlayerState
import com.lumen.player.ui.components.AmbientBackground
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.IconKind
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LSlider
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LToggle
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.MenuItem
import com.lumen.player.ui.components.Overlays
import com.lumen.player.ui.components.TrackRow
import com.lumen.player.ui.components.Waveform
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.rememberArtColors
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.Env
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.nav.sharedArt
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- Lyrics ----------------

data class LyricLine(val ms: Long, val text: String)

fun parseLyrics(raw: String?, synced: Boolean): List<LyricLine> {
    if (raw.isNullOrBlank()) return emptyList()
    val re = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    val out = mutableListOf<LyricLine>()
    for (line in raw.lines()) {
        val tags = re.findAll(line).toList()
        val text = line.replace(re, "").trim()
        if (tags.isEmpty()) { if (!synced || out.isEmpty()) out += LyricLine(-1, text); continue }
        for (m in tags) {
            val frac = m.groupValues[3].let { if (it.isEmpty()) 0 else it.padEnd(3, '0').take(3).toInt() }
            out += LyricLine(m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 + frac, text)
        }
    }
    return if (out.any { it.ms >= 0 }) out.filter { it.ms >= 0 }.sortedBy { it.ms } else out
}

@Composable
fun LyricsView(t: TrackEntity, ps: PlayerState, modifier: Modifier = Modifier, big: Boolean = false, onSeek: (Long) -> Unit = {}) {
    val c = Lumen.colors
    val lines = remember(t.lyrics) { parseLyrics(t.lyrics, t.lyricsSynced) }
    val synced = lines.any { it.ms >= 0 }
    val cur = if (synced) lines.indexOfLast { it.ms <= ps.positionMs + 300 } else -1
    val state = rememberLazyListState()
    LaunchedEffect(cur) { if (cur > 1) state.animateScrollToItem((cur - 1).coerceAtLeast(0)) }
    Glass(modifier, radius = Lumen.shapes.card, shadow = false) {
        if (lines.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            LIcon(LIcons.lyrics, 32.dp, c.textTertiary)
            Spacer(Modifier.height(10.dp))
            LText("No lyrics yet", Lumen.type.titleM)
            LText("Lumen checks the file’s tags and LRCLIB automatically. You can paste lyrics in Edit info.", Lumen.type.bodyS, c.textSecondary, align = TextAlign.Center)
        } else LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(if (big) 14.dp else 10.dp)) {
            item { LText(if (synced) "Lyrics · synced" else "Lyrics", Lumen.type.overline, c.accentText, upper = true) }
            itemsIndexed(lines) { i, l ->
                val active = i == cur
                val past = synced && i < cur
                val scale by animateFloatAsState(if (active) 1f else .96f, label = "ly")
                LText(
                    l.text.ifBlank { "♪" },
                    if (active) (if (big) Lumen.type.display else Lumen.type.titleL) else (if (big) Lumen.type.titleM else Lumen.type.body),
                    if (active) c.textPrimary else if (past) c.textTertiary else c.textSecondary,
                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f) }
                        .then(if (synced) Modifier.pressable(scaleTo = .98f) { onSeek(l.ms) } else Modifier),
                )
            }
        }
    }
}

// ---------------- Player ----------------

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val ps by c.player.state.collectAsState()
    val settings by c.settings.settings.collectAsState(initial = com.lumen.player.data.settings.AppSettings())
    val t = ps.current
    val artColors = rememberArtColors(t)
    var drag by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    val dragAnim by animateFloatAsState(drag, label = "pd")

    Box(
        Modifier.fillMaxSize()
            .offset { IntOffset(0, dragAnim.roundToInt().coerceAtLeast(0)) }
            .graphicsLayer { val k = 1f - (dragAnim / 3000f).coerceIn(0f, .08f); scaleX = k; scaleY = k }
            .clip(shape(if (dragAnim > 1f) Lumen.shapes.panel else 0.dp))
            .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (drag > 220f) onClose(); drag = 0f }, onDragCancel = { drag = 0f }) { _, d -> drag = (drag + d).coerceAtLeast(0f) } }
    ) {
        AmbientBackground(if (settings.artworkTint) artColors else null, intensity = 1.35f)
        Box(Modifier.fillMaxSize().background(Lumen.colors.bgBase.copy(alpha = if (Lumen.colors.isDark) .18f else .1f)))
        if (t == null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                LText("Nothing playing", Lumen.type.titleL); Spacer(Modifier.height(12.dp)); LButton("Close", onClose, kind = BtnKind.Glass)
            }
            return@Box
        }
        val actions = PlayerActions(env, o, ps, t, { showLyrics = !showLyrics })
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = if (env.wide) 24.dp else 16.dp)) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBtn(LIcons.chevronDown, onClose, size = 44.dp)
                Column(Modifier.weight(1f).pressable { onClose(); env.nav.navigate(Routes.folder(t.folder)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    LText(if (Lumen.design.name == "OBSIDIAN") "Now playing" else "Playing from folder", Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
                    LText(t.folder.replace("/", " › ").ifBlank { "Music" }, Lumen.type.titleM, maxLines = 1)
                }
                if (env.wide) IconBtn(LIcons.queue, actions.queue, size = 44.dp)
                IconBtn(LIcons.more, { env.trackMenu(o, t) }, size = 44.dp)
            }
            if (env.wide) WidePlayer(ps, t, actions, showLyrics) else when (Lumen.design.name) {
                "OBSIDIAN" -> ObsidianPlayer(ps, t, actions, showLyrics)
                "FLUID" -> FluidPlayer(ps, t, actions, showLyrics)
                else -> AuroraPlayer(ps, t, actions, showLyrics)
            }
        }
    }
}

class PlayerActions(val env: Env, val o: Overlays, val ps: PlayerState, val t: TrackEntity, val toggleLyrics: () -> Unit) {
    val p = env.c.player
    val toggle = { p.toggle() }
    val next: () -> Unit = { p.next() }
    val prev: () -> Unit = { p.previous() }
    val shuffle = { p.setShuffle(!ps.shuffle) }
    val repeat = { p.cycleRepeat() }
    val seek: (Float) -> Unit = { p.seekTo(it) }
    val queue: () -> Unit = { env.openQueue() }
    val favorite: () -> Unit = { env.c.appScope.launch { env.c.library.toggleFavorite(t); p.reloadCurrent() } }
    val output: () -> Unit = { outputSheet(env, o) }
    val sleep: () -> Unit = { sleepSheet(env, o) }
    val speed: () -> Unit = {
        o.menu("Playback speed", null, null, listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { s ->
            MenuItem(LIcons.speed, "${s}×".replace(".0×", ".0×"), checked = kotlin.math.abs(ps.speed - s) < .01f) {
                p.setSpeed(s); env.c.appScope.launch { env.c.settings.setSpeed(s) }
            }
        })
    }
    val eq: () -> Unit = { env.openPlayerClose?.invoke(); env.nav.navigate(Routes.EQ) }
}

/** Closes the player overlay before navigating somewhere (set by the root). */
var Env.openPlayerClose: (() -> Unit)?
    get() = closePlayerRef
    set(v) { closePlayerRef = v }
private var closePlayerRef: (() -> Unit)? = null

@Composable
private fun TitleRow(t: TrackEntity, a: PlayerActions, style: androidx.compose.ui.text.TextStyle = Lumen.type.titleL, center: Boolean = false) {
    val c = Lumen.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
            LText(t.title, style, maxLines = 1)
            LText(t.displayArtist() + if (t.album.isNotBlank()) " · ${t.album}" else "", Lumen.type.body, c.textSecondary, maxLines = 1,
                modifier = Modifier.pressable { if (t.artist.isNotBlank()) { a.env.openPlayerClose?.invoke(); a.env.nav.navigate(Routes.artist(t.artist)) } })
        }
        if (!center) IconBtn(if (t.favorite) LIcons.heartFilled else LIcons.heart, a.favorite, size = 44.dp, tint = c.accentText)
    }
}

@Composable
private fun Transport(ps: PlayerState, a: PlayerActions, big: Dp = 72.dp) {
    val c = Lumen.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(44.dp).pressable(onClick = a.shuffle), contentAlignment = Alignment.Center) { LIcon(LIcons.shuffle, 22.dp, if (ps.shuffle) c.accentText else c.textSecondary) }
        Box(Modifier.size(52.dp).pressable(scaleTo = .85f, onClick = a.prev), contentAlignment = Alignment.Center) { LIcon(LIcons.prev, 32.dp) }
        IconBtn(if (ps.isPlaying) LIcons.pause else LIcons.play, a.toggle, size = big, kind = IconKind.Accent, iconSize = big * .42f)
        Box(Modifier.size(52.dp).pressable(scaleTo = .85f, onClick = a.next), contentAlignment = Alignment.Center) { LIcon(LIcons.next, 32.dp) }
        Box(Modifier.size(44.dp).pressable(onClick = a.repeat), contentAlignment = Alignment.Center) { LIcon(if (ps.repeat == 1) LIcons.repeatOne else LIcons.repeat, 22.dp, if (ps.repeat != 0) c.accentText else c.textSecondary) }
    }
}

@Composable
private fun Timeline(ps: PlayerState, a: PlayerActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LSlider(ps.progress, {}, onDone = a.seek)
        Row(Modifier.fillMaxWidth()) {
            LText(fmtTime(ps.positionMs), Lumen.type.caption, Lumen.colors.textSecondary, Modifier.weight(1f))
            LText("-" + fmtTime((ps.durationMs - ps.positionMs).coerceAtLeast(0)), Lumen.type.caption, Lumen.colors.textSecondary)
        }
    }
}

@Composable
private fun ExtraItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Lumen.colors
    Column(Modifier.width(62.dp).pressable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LIcon(icon, 22.dp, if (active) c.accentText else c.textPrimary)
        LText(label, Lumen.type.caption, if (active) c.accentText else c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun ArtOrLyrics(t: TrackEntity, ps: PlayerState, showLyrics: Boolean, a: PlayerActions, modifier: Modifier, radius: Dp, artShadow: Color) {
    AnimatedContent(showLyrics, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "artlyr", modifier = modifier) { ly ->
        if (ly) LyricsView(t, ps, Modifier.fillMaxSize()) { a.p.seekToMs(it) }
        else Box(Modifier.fillMaxSize().pressable(scaleTo = .98f, onClick = a.toggleLyrics)) {
            Artwork(t, Modifier.fillMaxSize().shadow(36.dp, shape(radius), ambientColor = artShadow, spotColor = artShadow).sharedArt(), radius = radius)
        }
    }
}

@Composable
private fun ColumnScope.AuroraPlayer(ps: PlayerState, t: TrackEntity, a: PlayerActions, showLyrics: Boolean) {
    val c = Lumen.colors
    val sleep by a.env.c.player.sleep.collectAsState()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, (LocalConfiguration.current.screenHeightDp * 0.42f).dp)
            ArtOrLyrics(t, ps, showLyrics, a, Modifier.size(side), Lumen.shapes.panel, c.accent.copy(alpha = .5f))
        }
        TitleRow(t, a)
        Timeline(ps, a)
        Transport(ps, a)
        Glass(Modifier.fillMaxWidth(), strong = true) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ExtraItem(LIcons.lyrics, "Lyrics", showLyrics, a.toggleLyrics)
                    ExtraItem(LIcons.speaker, "Output", false, a.output)
                    ExtraItem(LIcons.moon, if (sleep.active) "On" else "Sleep", sleep.active, a.sleep)
                    ExtraItem(LIcons.speed, "${ps.speed}×", ps.speed != 1f, a.speed)
                    ExtraItem(LIcons.queue, "Queue", false, a.queue)
                }
                VolumeRow(a)
            }
        }
    }
}

@Composable
private fun VolumeRow(a: PlayerActions) {
    val c = Lumen.colors
    var vol by remember { mutableFloatStateOf(a.p.volume) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LIcon(LIcons.volume, 18.dp, c.textSecondary)
        Box(Modifier.weight(1f)) { LSlider(vol, { vol = it; a.p.setVolume(it) }) }
        Box(Modifier.pressable(onClick = a.eq)) { LIcon(LIcons.eq, 18.dp, c.textSecondary) }
    }
}

@Composable
private fun ColumnScope.ObsidianPlayer(ps: PlayerState, t: TrackEntity, a: PlayerActions, showLyrics: Boolean) {
    val c = Lumen.colors
    val sleep by a.env.c.player.sleep.collectAsState()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
            ArtOrLyrics(t, ps, showLyrics, a, Modifier.size(side), 18.dp, Color.Black.copy(alpha = .6f))
        }
        LText(listOfNotNull(t.trackNo?.let { "Track %02d".format(it) }, t.fileName.substringAfterLast('.').uppercase(), t.year?.toString(), fmtTime(t.durationMs)).joinToString(" · "), Lumen.type.overline, c.accentText, upper = true)
        TitleRow(t, a, Lumen.type.display)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Waveform(ps.progress, t.id.toInt(), a.seek, height = 40.dp)
            Row(Modifier.fillMaxWidth()) { LText(fmtTime(ps.positionMs), Lumen.type.caption, c.textSecondary, Modifier.weight(1f)); LText(fmtTime(ps.durationMs), Lumen.type.caption, c.textSecondary) }
        }
        Glass(Modifier.fillMaxWidth().height(84.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.pressable(onClick = a.shuffle)) { LIcon(LIcons.shuffle, 20.dp, if (ps.shuffle) c.accentText else c.textSecondary) }
                Box(Modifier.pressable(onClick = a.prev)) { LIcon(LIcons.prev, 28.dp) }
                IconBtn(if (ps.isPlaying) LIcons.pause else LIcons.play, a.toggle, size = 62.dp, kind = IconKind.Accent)
                Box(Modifier.pressable(onClick = a.next)) { LIcon(LIcons.next, 28.dp) }
                Box(Modifier.pressable(onClick = a.repeat)) { LIcon(if (ps.repeat == 1) LIcons.repeatOne else LIcons.repeat, 20.dp, if (ps.repeat != 0) c.accentText else c.textSecondary) }
            }
        }
        val chips = listOf(
            Triple("Lyrics", showLyrics, a.toggleLyrics), Triple("Output", false, a.output), Triple(if (sleep.active) "Sleep on" else "Sleep", sleep.active, a.sleep),
            Triple("${ps.speed}x", ps.speed != 1f, a.speed), Triple("EQ", false, a.eq), Triple("Queue ${(ps.queue.size - ps.index - 1).coerceAtLeast(0)}", false, a.queue),
        )
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (l, on, f) ->
                Box(Modifier.height(34.dp).clip(shape(Lumen.shapes.button)).border(1.dp, if (on) c.accent else c.glassStroke, shape(Lumen.shapes.button)).pressable(onClick = f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    LText(l, Lumen.type.caption, if (on) c.accentText else c.textSecondary, upper = true)
                }
            }
        }
    }
}

/** Fluid: the art is a spinning vinyl disc wrapped by a draggable progress ring. */
@Composable
fun Disc(t: TrackEntity, ps: PlayerState, size: Dp, onSeek: (Float) -> Unit, onTap: () -> Unit) {
    val c = Lumen.colors
    val density = LocalDensity.current
    var spin by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ps.isPlaying) { while (ps.isPlaying) { spin = (spin + 0.6f) % 360f; delay(16) } }
    var drag by remember { mutableStateOf<Float?>(null) }
    val prog = drag ?: ps.progress
    Box(
        Modifier.size(size).pointerInput(Unit) {
            fun frac(o: Offset): Float {
                val cx = this.size.width / 2f; val cy = this.size.height / 2f
                var a = Math.toDegrees(kotlin.math.atan2((o.y - cy).toDouble(), (o.x - cx).toDouble())).toFloat() + 90f
                if (a < 0) a += 360f
                return a / 360f
            }
            detectDragGestures(
                onDragStart = { o -> drag = frac(o) }, onDragEnd = { drag?.let(onSeek); drag = null }, onDragCancel = { drag = null },
            ) { ch, _ -> drag = frac(ch.position) }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = with(density) { 5.dp.toPx() }
            drawArc(c.track, 0f, 360f, false, Offset(sw, sw), Size(this.size.width - 2 * sw, this.size.height - 2 * sw), style = Stroke(sw))
            drawArc(c.accent, -90f, 360f * prog, false, Offset(sw, sw), Size(this.size.width - 2 * sw, this.size.height - 2 * sw), style = Stroke(sw, cap = StrokeCap.Round))
            val ang = Math.toRadians((-90f + 360f * prog).toDouble())
            val r = this.size.width / 2 - sw
            drawCircle(Color.White, with(density) { 9.dp.toPx() }, Offset(center.x + (r * cos(ang)).toFloat(), center.y + (r * sin(ang)).toFloat()))
        }
        Box(Modifier.size(size - 44.dp).graphicsLayer { rotationZ = spin }.pressable(scaleTo = .97f, onClick = onTap)) {
            Artwork(t, Modifier.fillMaxSize().shadow(40.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent).sharedArt(), radius = size, outline = false)
        }
    }
}

@Composable
private fun ColumnScope.FluidPlayer(ps: PlayerState, t: TrackEntity, a: PlayerActions, showLyrics: Boolean) {
    val c = Lumen.colors
    val sleep by a.env.c.player.sleep.collectAsState()
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val s = minOf(maxWidth, (LocalConfiguration.current.screenHeightDp * 0.42f).dp)
            if (showLyrics) LyricsView(t, ps, Modifier.size(s)) { a.p.seekToMs(it) } else Disc(t, ps, s, a.seek, a.toggleLyrics)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LText(t.title, Lumen.type.titleL, maxLines = 1, align = TextAlign.Center)
            LText("${t.displayArtist()} · ${fmtTime(ps.positionMs)} / ${fmtTime(ps.durationMs)}", Lumen.type.body, c.textSecondary, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBtn(LIcons.shuffle, a.shuffle, size = 54.dp, tint = if (ps.shuffle) c.accentText else c.textSecondary, blur = true)
            Glass(Modifier.height(86.dp), radius = 43.dp, strong = true, blur = true) {
                Row(Modifier.fillMaxHeight().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.pressable(onClick = a.prev)) { LIcon(LIcons.prev, 30.dp) }
                    IconBtn(if (ps.isPlaying) LIcons.pause else LIcons.play, a.toggle, size = 64.dp, kind = IconKind.Accent)
                    Box(Modifier.pressable(onClick = a.next)) { LIcon(LIcons.next, 30.dp) }
                }
            }
            IconBtn(if (ps.repeat == 1) LIcons.repeatOne else LIcons.repeat, a.repeat, size = 54.dp, tint = if (ps.repeat != 0) c.accentText else c.textSecondary, blur = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconBtn(LIcons.lyrics, a.toggleLyrics, size = 52.dp, tint = if (showLyrics) c.accentText else null)
            IconBtn(LIcons.speaker, a.output, size = 52.dp)
            IconBtn(LIcons.moon, a.sleep, size = 52.dp, tint = if (sleep.active) c.accentText else null)
            IconBtn(LIcons.speed, a.speed, size = 52.dp, tint = if (ps.speed != 1f) c.accentText else null)
            IconBtn(if (t.favorite) LIcons.heartFilled else LIcons.heart, a.favorite, size = 52.dp, tint = c.accentText)
            IconBtn(LIcons.queue, a.queue, size = 52.dp)
        }
    }
}

/** Unfolded inner screen: art left, controls + live lyrics right. */
@Composable
private fun ColumnScope.WidePlayer(ps: PlayerState, t: TrackEntity, a: PlayerActions, showLyrics: Boolean) {
    val c = Lumen.colors
    val design = Lumen.design.name
    val sleep by a.env.c.player.sleep.collectAsState()
    Row(Modifier.weight(1f).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                val s = minOf(maxWidth, 440.dp)
                if (design == "FLUID") Disc(t, ps, s, a.seek, {}) else Artwork(t, Modifier.size(s).shadow(40.dp, shape(Lumen.shapes.panel)).sharedArt(), radius = if (design == "OBSIDIAN") 18.dp else Lumen.shapes.panel)
            }
            if (design == "OBSIDIAN") {
                Waveform(ps.progress, t.id.toInt(), a.seek, height = 44.dp)
                Row(Modifier.fillMaxWidth()) { LText(fmtTime(ps.positionMs), Lumen.type.caption, c.textSecondary, Modifier.weight(1f)); LText(fmtTime(ps.durationMs), Lumen.type.caption, c.textSecondary) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TitleRow(t, a, Lumen.type.display)
            if (design != "OBSIDIAN") Timeline(ps, a)
            Transport(ps, a, 68.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ExtraItem(LIcons.speaker, "Output", false, a.output)
                ExtraItem(LIcons.moon, if (sleep.active) "On" else "Sleep", sleep.active, a.sleep)
                ExtraItem(LIcons.speed, "${ps.speed}×", ps.speed != 1f, a.speed)
                ExtraItem(LIcons.eq, "EQ", false, a.eq)
                ExtraItem(LIcons.queue, "Queue", false, a.queue)
            }
            VolumeRow(a)
            LyricsView(t, ps, Modifier.fillMaxWidth().weight(1f), big = design == "OBSIDIAN") { a.p.seekToMs(it) }
        }
    }
}

// ---------------- Sheets ----------------

fun outputSheet(env: Env, o: Overlays) {
    o.sheet("Play on", "Choose where audio plays") { close ->
        val ctx = LocalContext.current
        val devices = remember { env.c.output.devices() }
        var current by remember { mutableIntStateOf(env.c.output.currentId() ?: -1) }
        devices.forEach { d: OutputDevice ->
            val on = d.id == current
            val icon = when (d.kind) { "bluetooth" -> LIcons.bluetooth; "headphones" -> LIcons.headphones; "cast" -> LIcons.cast; else -> LIcons.phone }
            Glass(Modifier.fillMaxWidth().height(62.dp).pressable { env.c.output.select(d); current = d.id }, radius = Lumen.shapes.tile, shadow = false, fill = if (on) Lumen.colors.accentSoft else null) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LIcon(icon, 22.dp, if (on) Lumen.colors.accentText else Lumen.colors.textSecondary)
                    Column(Modifier.weight(1f)) {
                        LText(d.name, Lumen.type.body, maxLines = 1)
                        LText(when (d.kind) { "bluetooth" -> "Bluetooth"; "headphones" -> "Wired / USB"; "cast" -> "HDMI"; else -> "Speaker" }, Lumen.type.bodyS, Lumen.colors.textSecondary)
                    }
                    if (on) LIcon(LIcons.check, 20.dp, Lumen.colors.accentText)
                }
            }
        }
        LButton("Cast & more devices", {
            close()
            runCatching {
                ctx.startActivity(Intent("com.android.settings.panel.action.MEDIA_OUTPUT").putExtra("com.android.settings.panel.extra.PACKAGE_NAME", ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, Modifier.fillMaxWidth(), icon = LIcons.cast, kind = BtnKind.Glass)
        var vol by remember { mutableFloatStateOf(env.c.player.volume) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LIcon(LIcons.volume, 20.dp, Lumen.colors.textSecondary)
            Box(Modifier.weight(1f)) { LSlider(vol, { vol = it; env.c.player.setVolume(it) }) }
        }
    }
}

fun sleepSheet(env: Env, o: Overlays) {
    o.sheet("Sleep timer", "Music fades out, then pauses") { close ->
        val st by env.c.player.sleep.collectAsState()
        var minutes by remember { mutableFloatStateOf(30f) }
        var fade by remember { mutableStateOf(true) }
        if (st.active) {
            val left = if (st.endOfTrack) "At the end of this song" else "${((st.endsAt - System.currentTimeMillis()) / 60000).coerceAtLeast(0) + 1} min left"
            Glass(Modifier.fillMaxWidth(), shadow = false, fill = Lumen.colors.accentSoft) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LIcon(LIcons.moon, 22.dp, Lumen.colors.accentText)
                    LText("Timer on · $left", Lumen.type.label, Modifier.weight(1f), upper = true)
                    LText("Cancel", Lumen.type.label, Lumen.colors.danger, Modifier.pressable { env.c.player.cancelSleep() }, upper = true)
                }
            }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { m -> LChip(if (m == 60) "1 hour" else "$m min", minutes.toInt() == m, { minutes = m.toFloat() }) }
            LChip("End of song", false, { env.c.player.sleepAtEndOfTrack(); close(); o.toast("Pauses at the end of this song") })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LText("Custom", Lumen.type.label, Lumen.colors.textSecondary, upper = true)
            Box(Modifier.weight(1f)) { LSlider((minutes - 5f) / 115f, { minutes = (5f + it * 115f).roundToInt().toFloat() }) }
            LText("${minutes.toInt()} min", Lumen.type.label, Lumen.colors.accentText)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { LText("Fade out gently", Lumen.type.body); LText("Last 30 seconds", Lumen.type.bodyS, Lumen.colors.textSecondary) }
            LToggle(fade, { fade = it })
        }
        LButton("Start ${minutes.toInt()}-minute timer", { env.c.player.sleepIn(minutes.toInt(), fade); close(); o.toast("Sleep timer set for ${minutes.toInt()} min") }, Modifier.fillMaxWidth())
    }
}

/** Up-next list with long-press-drag reordering, swipe-free remove buttons. */
@Composable
fun QueueList(onClose: () -> Unit) {
    val env = LocalEnv.current
    val ps by env.c.player.state.collectAsState()
    val c = Lumen.colors
    val density = LocalDensity.current
    val rowPx = with(density) { 66.dp.toPx() }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val upcoming = ps.queue.withIndex().drop(ps.index + 1)
    val now = ps.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (now != null) Glass(Modifier.fillMaxWidth(), strong = true, shadow = false) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(now, Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    LText("Now playing", Lumen.type.overline, c.accentText, upper = true)
                    LText(now.title, Lumen.type.label, maxLines = 1)
                    LText(now.displayArtist(), Lumen.type.caption, c.textSecondary, maxLines = 1)
                }
                LIcon(LIcons.bars, 18.dp, c.accentText)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LText("Up next · ${upcoming.size}", Lumen.type.overline, c.textTertiary, Modifier.weight(1f), upper = true)
            if (upcoming.isNotEmpty()) LText("Clear", Lumen.type.label, c.danger, Modifier.pressable { env.c.player.clearUpcoming() }, upper = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            upcoming.forEachIndexed { pos, (qi, t) ->
                val dragging = dragIndex == pos
                Row(
                    Modifier.fillMaxWidth()
                        .graphicsLayer { if (dragging) { translationY = dragOffset; scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 16f } }
                        .then(if (dragging) Modifier.clip(shape(Lumen.shapes.tile)).background(c.glassFillStrong) else Modifier),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).pointerInput(qi) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragIndex = pos; dragOffset = 0f },
                                onDragEnd = {
                                    val to = (pos + (dragOffset / rowPx).roundToInt()).coerceIn(0, upcoming.lastIndex)
                                    if (to != pos) env.c.player.move(ps.index + 1 + pos, ps.index + 1 + to)
                                    dragIndex = null; dragOffset = 0f
                                },
                                onDragCancel = { dragIndex = null; dragOffset = 0f },
                            ) { ch, d -> ch.consume(); dragOffset += d.y }
                        },
                        contentAlignment = Alignment.Center,
                    ) { LIcon(LIcons.drag, 18.dp, c.textTertiary) }
                    Box(Modifier.weight(1f)) { TrackRow(t, { env.c.player.skipTo(qi) }) }
                    Box(Modifier.size(28.dp).clip(CircleShape).background(c.track).pressable { env.c.player.remove(qi) }, contentAlignment = Alignment.Center) { LIcon(LIcons.close, 14.dp, c.danger) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LButton("Save as playlist", {
                val o = env.c
                env.c.appScope.launch { o.library.createPlaylist("Queue · " + java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date()), ps.queue.map { it.id }) }
                onClose()
            }, Modifier.weight(1f), icon = LIcons.playlist)
        }
    }
}
