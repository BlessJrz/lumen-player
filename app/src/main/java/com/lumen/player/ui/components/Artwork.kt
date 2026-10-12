package com.lumen.player.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Same 12 palettes as the Figma placeholder artwork — used when a song has no cover yet. */
private val PALETTES = listOf(
    listOf(0xFFFF5FA2, 0xFF7B3FF2, 0xFF140726), listOf(0xFFFFB547, 0xFFFF5E3A, 0xFF2A0E05), listOf(0xFF3AE0FF, 0xFF2563EB, 0xFF04122B),
    listOf(0xFFC4B5FD, 0xFFEC4899, 0xFF120A24), listOf(0xFF22D3EE, 0xFFA3E635, 0xFF07111F), listOf(0xFFFDE047, 0xFFF59E0B, 0xFF1A1202),
    listOf(0xFF34D399, 0xFF0EA5E9, 0xFF02140F), listOf(0xFF818CF8, 0xFFDB2777, 0xFF0A0A1F), listOf(0xFFFB923C, 0xFFE11D48, 0xFF2B0716),
    listOf(0xFF93C5FD, 0xFFF0ABFC, 0xFF1E1B4B), listOf(0xFFE5E7EB, 0xFF6B7280, 0xFF0B0B0E), listOf(0xFFF472B6, 0xFFFACC15, 0xFF1F0A14),
).map { p -> p.map { Color(it) } }

fun seedOf(s: String): Int = abs(s.hashCode())

fun placeholderColors(seed: Int): List<Color> = PALETTES[seed % PALETTES.size]

/** Generative cover: gradient, two soft orbs and a motif (ring / sun / bars / arcs). */
@Composable
fun GeneratedArt(seed: Int, modifier: Modifier = Modifier) {
    val p = placeholderColors(seed)
    val motif = (seed / 7) % 4
    Box(
        modifier
            .background(Brush.linearGradient(listOf(p[2], p[1], p[0]), Offset.Zero, Offset.Infinite))
            .drawBehind {
                val w = size.width; val h = size.height
                drawCircle(Brush.radialGradient(listOf(p[0].copy(alpha = .9f), Color.Transparent), Offset(w * .8f, h * .1f), w * .55f), w * .55f, Offset(w * .8f, h * .1f))
                drawCircle(Brush.radialGradient(listOf(p[1].copy(alpha = .8f), Color.Transparent), Offset(w * .1f, h * .85f), w * .5f), w * .5f, Offset(w * .1f, h * .85f))
                val white = Color.White
                when (motif) {
                    0 -> { drawCircle(white.copy(alpha = .6f), w * .26f, center, style = Stroke(w * .008f)); drawCircle(white.copy(alpha = .22f), w * .36f, center, style = Stroke(w * .004f)); drawCircle(white.copy(alpha = .9f), w * .03f, center) }
                    1 -> { drawCircle(white.copy(alpha = .92f), w * .19f, Offset(w * .5f, h * .38f)); drawRect(p[2].copy(alpha = .55f), Offset(0f, h * .7f), Size(w, h * .3f)); drawRect(white.copy(alpha = .45f), Offset(0f, h * .7f), Size(w, h * .006f)) }
                    2 -> listOf(.35f, .7f, .5f, .9f, .6f, .42f, .78f).forEachIndexed { i, k ->
                        val bw = w * .058f; val bh = h * .5f * k
                        drawRoundRect(white.copy(alpha = .75f), Offset(w * .17f + i * w * .1f, h * .82f - bh), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                    }
                    else -> {
                        drawArc(white.copy(alpha = .7f), 180f, 180f, false, Offset(w * .08f, h * .3f), Size(w * .84f, w * .84f), style = Stroke(w * .07f))
                        drawArc(white.copy(alpha = .4f), 180f, 180f, false, Offset(w * .23f, h * .45f), Size(w * .54f, w * .54f), style = Stroke(w * .06f))
                    }
                }
            }
    )
}

/** Album art for a track (cached file) with a generated fallback. */
@Composable
fun Artwork(track: TrackEntity?, modifier: Modifier = Modifier, radius: Dp = Lumen.shapes.tile, outline: Boolean = Lumen.design.name == "OBSIDIAN") {
    ArtworkFile(track?.artPath, seedOf(track?.album?.ifBlank { null } ?: track?.title ?: ""), modifier, radius, outline)
}

@Composable
fun ArtworkFile(path: String?, seed: Int, modifier: Modifier = Modifier, radius: Dp = Lumen.shapes.tile, outline: Boolean = false) {
    val shp = shape(radius)
    Box(
        modifier
            .clip(shp)
            .then(if (outline) Modifier.border(1.dp, Lumen.colors.glassStroke, shp) else Modifier)
    ) {
        GeneratedArt(seed, Modifier.fillMaxSize())
        if (path != null && File(path).exists()) {
            AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun ArtworkUrl(url: String, modifier: Modifier = Modifier, radius: Dp = Lumen.shapes.tile) {
    Box(modifier.clip(shape(radius)).background(Lumen.colors.track)) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Extracts three glow colors from the playing song's art — drives the "album art tints the room" effect. */
@Composable
fun rememberArtColors(track: TrackEntity?): List<Color>? {
    val context = LocalContext.current
    var colors by remember { mutableStateOf<List<Color>?>(null) }
    val key = track?.artPath ?: track?.let { "gen:${seedOf(it.album.ifBlank { it.title })}" }
    LaunchedEffect(key) {
        colors = when {
            track == null -> null
            track.artPath == null -> placeholderColors(seedOf(track.album.ifBlank { track.title }))
            else -> withContext(Dispatchers.IO) {
                val res = context.imageLoader.execute(ImageRequest.Builder(context).data(File(track.artPath)).allowHardware(false).size(160).build())
                val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
                val pal = Palette.from(bmp).maximumColorCount(16).generate()
                val fallback = 0xFF7C5CFF.toInt()
                listOf(
                    pal.getVibrantColor(pal.getDominantColor(fallback)),
                    pal.getLightVibrantColor(pal.getMutedColor(fallback)),
                    pal.getDarkVibrantColor(pal.getDarkMutedColor(fallback)),
                ).map { Color(it) }
            }
        }
    }
    return colors
}
