package com.lumen.player.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lumen.player.playback.PlayerState
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LinearBar
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.Tab
import com.lumen.player.ui.nav.sharedArt
import com.lumen.player.ui.theme.Lumen

/** Floating tab bar for the cover screen — three personalities. */
@Composable
fun TabBar(tabs: List<Tab>, current: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    when (Lumen.design.name) {
        "FLUID" -> Glass(modifier.height(64.dp), radius = 32.dp, strong = true, blur = true) {
            Row(Modifier.padding(6.dp).animateContentSize(spring(dampingRatio = .7f, stiffness = 500f)), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t == current
                    if (on) Row(
                        Modifier.height(52.dp).clip(CircleShape).background(c.accent).pressable { onSelect(t) }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { LIcon(t.icon, 22.dp, c.onAccent); LText(t.label, Lumen.type.label, c.onAccent) }
                    else Box(Modifier.size(52.dp).clip(CircleShape).pressable { onSelect(t) }, contentAlignment = Alignment.Center) { LIcon(t.icon, 22.dp, c.textSecondary) }
                }
            }
        }
        "OBSIDIAN" -> Glass(modifier.fillMaxWidth().height(62.dp), radius = Lumen.shapes.card, blur = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t == current
                    Column(Modifier.weight(1f).fillMaxHeight().pressable { onSelect(t) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                        Box(Modifier.size(18.dp, 2.dp).then(if (on) Modifier.shadow(8.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent) else Modifier).clip(CircleShape).background(if (on) c.accent else c.accent.copy(alpha = 0f)))
                        LIcon(t.icon, 22.dp, if (on) c.textPrimary else c.textTertiary)
                        LText(t.label, Lumen.type.caption, if (on) c.accentText else c.textTertiary, upper = true, maxLines = 1)
                    }
                }
            }
        }
        else -> Glass(modifier.fillMaxWidth().height(68.dp), radius = 34.dp, strong = true, blur = true) {
            Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                tabs.forEach { t ->
                    val on = t == current
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape).then(if (on) Modifier.background(c.accentSoft) else Modifier).pressable { onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        LIcon(t.icon, 22.dp, if (on) c.accentText else c.textSecondary)
                        LText(t.label, Lumen.type.caption, if (on) c.accentText else c.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Navigation rail for the unfolded inner screen. */
@Composable
fun NavRail(tabs: List<Tab>, current: Tab?, onSelect: (Tab) -> Unit, avatar: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    val obs = Lumen.design.name == "OBSIDIAN"
    Glass(modifier.width(92.dp).fillMaxHeight(), radius = Lumen.shapes.panel, strong = !obs, blur = true) {
        Column(Modifier.fillMaxSize().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(48.dp).clip(shape(Lumen.shapes.control)).background(c.accent), contentAlignment = Alignment.Center) { LIcon(LIcons.music, 24.dp, c.onAccent) }
            Spacer(Modifier.height(18.dp))
            val main = tabs.filter { it != Tab.Settings }
            main.forEach { t -> RailItem(t, t == current, obs) { onSelect(t) } }
            Spacer(Modifier.weight(1f))
            RailItem(Tab.Settings, current == Tab.Settings, obs) { onSelect(Tab.Settings) }
            avatar()
        }
    }
}

@Composable
private fun RailItem(t: Tab, on: Boolean, obs: Boolean, onClick: () -> Unit) {
    val c = Lumen.colors
    Column(
        Modifier.size(76.dp, 66.dp).clip(shape(Lumen.shapes.tile))
            .then(if (on) { if (obs) Modifier.background(c.glassFill).border(1.dp, c.glassStroke, shape(Lumen.shapes.tile)) else Modifier.background(c.accentSoft) } else Modifier)
            .pressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        LIcon(t.icon, 22.dp, if (on) c.accentText else c.textSecondary)
        LText(t.label, Lumen.type.caption, if (on) c.accentText else c.textSecondary, upper = true, maxLines = 1)
    }
}

/** Persistent mini-player. Tap to expand, swipe sideways to skip. */
@Composable
fun MiniPlayer(s: PlayerState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onFavorite: () -> Unit, modifier: Modifier = Modifier) {
    val t = s.current ?: return
    val c = Lumen.colors
    var dx by remember { mutableFloatStateOf(0f) }
    val swipe = Modifier.pointerInput(t.id) {
        detectHorizontalDragGestures(onDragEnd = { if (dx < -120) onNext() else if (dx > 120) onPrev(); dx = 0f }) { _, d -> dx += d }
    }
    val off by animateFloatAsState(dx / 3f, label = "mini")
    when (Lumen.design.name) {
        "FLUID" -> Box(modifier.size(76.dp).then(swipe).graphicsLayer { translationX = off }.pressable(onClick = onOpen)) {
            Glass(Modifier.fillMaxSize(), radius = 38.dp, strong = true, blur = true) {}
            Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                val sw = 3.5.dp.toPx()
                drawArc(c.track, 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                drawArc(c.accent, -90f, 360f * s.progress, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Artwork(t, Modifier.align(Alignment.Center).size(58.dp).sharedArt(), radius = 29.dp, outline = false)
            Box(Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(c.accent).pressable(onClick = onToggle), contentAlignment = Alignment.Center) {
                LIcon(if (s.isPlaying) LIcons.pause else LIcons.play, 13.dp, c.onAccent)
            }
        }
        "OBSIDIAN" -> Glass(modifier.fillMaxWidth().height(62.dp).then(swipe).graphicsLayer { translationX = off }.pressable(scaleTo = .98f, onClick = onOpen), radius = Lumen.shapes.card, blur = true) {
            Box(Modifier.fillMaxWidth(s.progress).height(1.5.dp).shadow(6.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent).background(c.accent))
            Row(Modifier.fillMaxSize().padding(start = 7.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(t, Modifier.size(48.dp).sharedArt(), radius = 10.dp)
                Column(Modifier.weight(1f)) {
                    LText(t.title, Lumen.type.bodyS, c.textPrimary, maxLines = 1)
                    LText(t.displayArtist(), Lumen.type.caption, c.textSecondary, maxLines = 1, upper = true)
                }
                Box(Modifier.size(40.dp).clip(shape(Lumen.shapes.button)).border(1.dp, c.glassStroke, shape(Lumen.shapes.button)).pressable(onClick = onToggle), contentAlignment = Alignment.Center) {
                    LIcon(if (s.isPlaying) LIcons.pause else LIcons.play, 18.dp)
                }
                Box(Modifier.size(32.dp, 40.dp).pressable(onClick = onNext), contentAlignment = Alignment.Center) { LIcon(LIcons.next, 18.dp, c.textSecondary) }
            }
        }
        else -> Glass(modifier.fillMaxWidth().height(66.dp).then(swipe).graphicsLayer { translationX = off }.pressable(scaleTo = .98f, onClick = onOpen), radius = Lumen.shapes.tile, strong = true, blur = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(t, Modifier.size(48.dp).sharedArt(), radius = 15.dp)
                Column(Modifier.weight(1f)) {
                    LText(t.title, Lumen.type.label, c.textPrimary, maxLines = 1)
                    LText(t.displayArtist(), Lumen.type.caption, c.textSecondary, maxLines = 1)
                }
                Box(Modifier.size(36.dp).pressable(onClick = onFavorite), contentAlignment = Alignment.Center) {
                    LIcon(if (t.favorite) LIcons.heartFilled else LIcons.heart, 20.dp, c.accentText)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.accent).pressable(onClick = onToggle), contentAlignment = Alignment.Center) {
                    LIcon(if (s.isPlaying) LIcons.pause else LIcons.play, 18.dp, c.onAccent)
                }
            }
            LinearBar(s.progress, Modifier.align(Alignment.BottomCenter).padding(horizontal = 25.dp).offset(y = (-3).dp), height = 2.5.dp)
        }
    }
}

/** Docked player bar for the unfolded screen. */
@Composable
fun PlayerBar(s: PlayerState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit, onSeek: (Float) -> Unit, onQueue: () -> Unit, onLyrics: () -> Unit, onOutput: () -> Unit, modifier: Modifier = Modifier) {
    val t = s.current ?: return
    val c = Lumen.colors
    Glass(modifier.fillMaxWidth().height(80.dp), radius = Lumen.shapes.card, strong = Lumen.design.name != "OBSIDIAN", blur = true) {
        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.width(240.dp).pressable(scaleTo = .98f, onClick = onOpen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(t, Modifier.size(60.dp).sharedArt(), radius = minOf(Lumen.shapes.tile, 16.dp))
                Column(Modifier.weight(1f)) {
                    LText(t.title, Lumen.type.label, maxLines = 1)
                    LText(t.displayArtist() + if (t.album.isNotBlank()) " · ${t.album}" else "", Lumen.type.caption, c.textSecondary, maxLines = 1)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.pressable(onClick = onShuffle)) { LIcon(LIcons.shuffle, 18.dp, if (s.shuffle) c.accentText else c.textSecondary) }
                    Box(Modifier.pressable(onClick = onPrev)) { LIcon(LIcons.prev, 20.dp) }
                    Box(Modifier.size(40.dp).clip(shape(Lumen.shapes.control)).background(c.accent).pressable(onClick = onToggle), contentAlignment = Alignment.Center) {
                        LIcon(if (s.isPlaying) LIcons.pause else LIcons.play, 18.dp, c.onAccent)
                    }
                    Box(Modifier.pressable(onClick = onNext)) { LIcon(LIcons.next, 20.dp) }
                    Box(Modifier.pressable(onClick = onRepeat)) { LIcon(if (s.repeat == 1) LIcons.repeatOne else LIcons.repeat, 18.dp, if (s.repeat != 0) c.accentText else c.textSecondary) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LText(fmtTime(s.positionMs), Lumen.type.caption, c.textTertiary)
                    Box(Modifier.weight(1f)) { com.lumen.player.ui.components.LSlider(s.progress, onChange = {}, onDone = onSeek) }
                    LText(fmtTime(s.durationMs), Lumen.type.caption, c.textTertiary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.pressable(onClick = onLyrics)) { LIcon(LIcons.lyrics, 20.dp, c.textSecondary) }
                Box(Modifier.pressable(onClick = onQueue)) { LIcon(LIcons.queue, 20.dp, c.textSecondary) }
                Box(Modifier.pressable(onClick = onOutput)) { LIcon(LIcons.speaker, 20.dp, c.textSecondary) }
            }
        }
    }
}

@Suppress("unused")
private fun iv(i: ImageVector) = i
