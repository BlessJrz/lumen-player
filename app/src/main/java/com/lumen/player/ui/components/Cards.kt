package com.lumen.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.FolderEntity
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.theme.Lumen

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "–:––"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun TrackEntity.displayArtist() = artist.ifBlank { "Unknown artist" }
fun TrackEntity.collectionName() = folder.substringAfterLast('/').ifBlank { "Music" }

@Composable
fun TrackRow(
    t: TrackEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    index: Int? = null,
    showAlbum: Boolean = false,
    highlight: Boolean = false,
) {
    val c = Lumen.colors
    Row(
        modifier.fillMaxWidth().height(64.dp)
            .then(if (highlight || playing && index != null) Modifier.clip(shape(Lumen.shapes.tile)).background(c.accentSoft).padding(horizontal = 10.dp) else Modifier)
            .pressable(scaleTo = .98f, onLongClick = onLongClick ?: onMore, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        if (index != null) Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            if (playing) LIcon(LIcons.bars, 16.dp, c.accentText) else LText("$index", Lumen.type.caption, c.textTertiary)
        }
        Artwork(t, Modifier.size(if (index != null) 42.dp else 48.dp), radius = minOf(Lumen.shapes.tile * 0.55f, 14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LText(t.title, Lumen.type.body, if (playing) c.accentText else c.textPrimary, maxLines = 1)
            LText(subtitle ?: t.displayArtist(), Lumen.type.bodyS, c.textSecondary, maxLines = 1)
        }
        if (showAlbum) LText(t.album.ifBlank { t.collectionName() }, Lumen.type.bodyS, c.textSecondary, Modifier.width(140.dp), maxLines = 1)
        if (playing && index == null) LIcon(LIcons.bars, 16.dp, c.accentText)
        if (t.localPath != null) LIcon(LIcons.offline, 15.dp, c.accentText)
        if (t.favorite) LIcon(LIcons.heartFilled, 14.dp, c.accentText)
        LText(fmtTime(t.durationMs), Lumen.type.caption, c.textTertiary)
        trailing?.invoke()
        if (onMore != null) Box(Modifier.size(32.dp).pressable(onClick = onMore), contentAlignment = Alignment.Center) { LIcon(LIcons.moreVert, 20.dp, c.textSecondary) }
    }
}

@Composable
fun FolderRow(name: String, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val c = Lumen.colors
    Row(modifier.fillMaxWidth().height(64.dp).pressable(scaleTo = .98f, onLongClick = onLongClick, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(shape(minOf(16.dp, Lumen.shapes.tile * .55f))).background(if (selected) c.accent else c.accentSoft), contentAlignment = Alignment.Center) {
            LIcon(LIcons.folder, 22.dp, if (selected) c.onAccent else c.accentText)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LText(name, Lumen.type.body, c.textPrimary, maxLines = 1)
            LText(meta, Lumen.type.bodyS, c.textSecondary, maxLines = 1)
        }
        LIcon(LIcons.chevronRight, 20.dp, c.textTertiary)
    }
}

/** A NAS folder presented as a music category — big art, glass label panel. */
@Composable
fun CategoryCard(name: String, meta: String, cover: TrackEntity?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 176.dp, height: Dp = 212.dp, icon: ImageVector = LIcons.folder, onLongClick: (() -> Unit)? = null) {
    val c = Lumen.colors
    Box(modifier.size(width, height).clip(shape(Lumen.shapes.card)).pressable(onLongClick = onLongClick, onClick = onClick)) {
        if (cover?.artPath != null) Artwork(cover, Modifier.fillMaxSize(), radius = 0.dp, outline = false)
        else GeneratedArt(seedOf(name), Modifier.fillMaxSize())
        Glass(Modifier.padding(12.dp).size(36.dp), radius = Lumen.shapes.control, strong = true, shadow = false) {
            LIcon(icon, 18.dp, c.textPrimary, Modifier.align(Alignment.Center))
        }
        Glass(
            Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(), radius = Lumen.shapes.tile, strong = true, shadow = false,
            fill = if (c.isDark) c.bgBase.copy(alpha = .38f) else c.glassFillStrong,
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LText(name, Lumen.type.titleM, c.textPrimary, maxLines = 1)
                LText(meta, Lumen.type.caption, c.textSecondary, maxLines = 1, upper = true)
            }
        }
    }
}

fun FolderEntity.metaLine(): String = buildString {
    append("$totalCount songs")
    if (directCount != totalCount && directCount >= 0) { val sub = totalCount - directCount; if (sub > 0) append(" · incl. subfolders") }
}

@Composable
fun AlbumCard(title: String, subtitle: String, art: TrackEntity?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 140.dp, onLongClick: (() -> Unit)? = null, seed: String = title, onPlay: (() -> Unit)? = null) {
    val c = Lumen.colors
    Column(modifier.width(size).pressable(onLongClick = onLongClick, onClick = onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            if (art?.artPath != null) Artwork(art, Modifier.size(size), radius = Lumen.shapes.tile)
            else ArtworkFile(null, seedOf(seed), Modifier.size(size), Lumen.shapes.tile, Lumen.design.name == "OBSIDIAN")
            if (onPlay != null && Lumen.design.name == "FLUID") Glass(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(38.dp).pressable(onClick = onPlay), radius = 19.dp, strong = true, shadow = false) {
                LIcon(LIcons.play, 16.dp, c.textPrimary, Modifier.align(Alignment.Center))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LText(title, Lumen.type.label, c.textPrimary, maxLines = 1, upper = true)
            LText(subtitle, Lumen.type.caption, c.textSecondary, maxLines = 1)
        }
    }
}

/** 2×2 cover mosaic for playlists. */
@Composable
fun Mosaic(tracks: List<TrackEntity>, size: Dp, radius: Dp = Lumen.shapes.tile, seed: String = "") {
    Box(Modifier.size(size).clip(shape(radius))) {
        if (tracks.size < 4) {
            if (tracks.isEmpty()) GeneratedArt(seedOf(seed), Modifier.fillMaxSize()) else Artwork(tracks.first(), Modifier.fillMaxSize(), radius = 0.dp, outline = false)
        } else {
            Column {
                Row { Artwork(tracks[0], Modifier.size(size / 2), 0.dp, false); Artwork(tracks[1], Modifier.size(size / 2), 0.dp, false) }
                Row { Artwork(tracks[2], Modifier.size(size / 2), 0.dp, false); Artwork(tracks[3], Modifier.size(size / 2), 0.dp, false) }
            }
        }
    }
}

enum class RowControl { Toggle, Chevron, Value, None }

@Composable
fun SettingRow(
    icon: ImageVector,
    label: String,
    hint: String? = null,
    control: RowControl = RowControl.Chevron,
    checked: Boolean = false,
    value: String? = null,
    onToggle: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val c = Lumen.colors
    Row(
        Modifier.fillMaxWidth().height(if (hint != null) 64.dp else 54.dp).clip(shape(Lumen.shapes.tile))
            .pressable(scaleTo = .985f) { if (control == RowControl.Toggle) onToggle(!checked) else onClick?.invoke() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(shape(minOf(12.dp, Lumen.shapes.control))).background(if (danger) c.danger.copy(alpha = .14f) else c.accentSoft), contentAlignment = Alignment.Center) {
            LIcon(icon, 18.dp, if (danger) c.danger else c.accentText)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            LText(label, Lumen.type.body, if (danger) c.danger else c.textPrimary, maxLines = 1)
            if (hint != null) LText(hint, Lumen.type.bodyS, c.textSecondary, maxLines = 1)
        }
        when (control) {
            RowControl.Toggle -> LToggle(checked, onToggle)
            RowControl.Value -> { if (value != null) LText(value, Lumen.type.label, c.textSecondary, maxLines = 1, upper = true); LIcon(LIcons.chevronRight, 18.dp, c.textTertiary) }
            RowControl.Chevron -> LIcon(LIcons.chevronRight, 18.dp, c.textTertiary)
            RowControl.None -> {}
        }
    }
}

/** Glass card grouping rows with hairline dividers. */
@Composable
fun Group(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) LText(title, Lumen.type.overline, Lumen.colors.textTertiary, Modifier.padding(start = 4.dp), upper = true)
        Glass(Modifier.fillMaxWidth(), shadow = false) { Column(Modifier.padding(4.dp), content = content) }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(Lumen.colors.divider))

@Composable
fun Avatar(track: TrackEntity?, size: Dp = 40.dp, onClick: () -> Unit = {}) {
    Box(Modifier.size(size).pressable(onClick = onClick)) {
        Artwork(track, Modifier.size(size), radius = if (Lumen.design.name == "OBSIDIAN") 10.dp else size / 2)
    }
}

@Composable
fun DotIndicator(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun DashedAddRow(label: String, onClick: () -> Unit) {
    val c = Lumen.colors
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(shape(Lumen.shapes.tile)).border(1.5.dp, c.glassStroke, shape(Lumen.shapes.tile)).pressable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { LIcon(LIcons.plus, 20.dp, c.accentText); LText(label, Lumen.type.label, c.accentText, upper = true) }
}

@Suppress("unused")
private val unusedOffset = Modifier.offset(0.dp)
@Suppress("unused")
private val unusedAspect = Modifier.aspectRatio(1f)
