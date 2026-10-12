package com.lumen.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.player.ui.theme.Lumen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Backdrop the floating glass chrome blurs (the scrolling content + ambient background). */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

fun shape(r: Dp): Shape = RoundedCornerShape(r)

@Composable
fun LText(
    text: String,
    style: TextStyle,
    color: Color = Lumen.colors.textPrimary,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    upper: Boolean = false,
) {
    BasicText(
        text = if (upper && Lumen.type.upperLabels) text.uppercase() else text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun LIcon(icon: ImageVector, size: Dp = 22.dp, tint: Color = Lumen.colors.textPrimary, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Press-to-shrink physics instead of Material ripples; optional long-press and haptics. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    haptic: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scaleTo else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    val h = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .combinedClickable(
            interactionSource = src, indication = null, enabled = enabled,
            onLongClick = onLongClick?.let { lc -> { h.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
            onClick = { if (haptic) h.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        )
}

/**
 * The Liquid Glass surface: translucent fill, specular top edge, soft shadow and — when [blur] is on and a
 * backdrop is available — a real background blur of whatever scrolls underneath.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = Lumen.shapes.card,
    strong: Boolean = false,
    blur: Boolean = false,
    shadow: Boolean = true,
    stroke: Color? = null,
    strokeWidth: Dp = 1.dp,
    fill: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = Lumen.theme
    val c = t.colors
    val shp = shape(radius)
    val haze = LocalHaze.current
    val baseFill = fill ?: if (strong) c.glassFillStrong else c.glassFill
    val edge = stroke ?: c.glassStroke
    val useBlur = blur && haze != null && !t.reduceTransparency
    val highlight = t.glass.highlight
    Box(
        modifier
            .then(if (shadow) Modifier.shadow((if (c.isDark) 18 else 12).dp, shp, ambientColor = Color.Black.copy(alpha = t.glass.shadowAlpha), spotColor = Color.Black.copy(alpha = t.glass.shadowAlpha)) else Modifier)
            .clip(shp)
            .then(
                if (useBlur) Modifier.hazeEffect(
                    state = haze!!,
                    style = HazeStyle(
                        backgroundColor = c.bgBase,
                        tints = listOf(HazeTint(baseFill)),
                        blurRadius = t.glass.blur,
                        noiseFactor = if (t.design.name == "OBSIDIAN") 0.04f else 0.08f,
                    ),
                ) else Modifier.background(if (blur && t.reduceTransparency) baseFill.copy(alpha = maxOf(baseFill.alpha, if (c.isDark) .2f else .9f)) else baseFill)
            )
            .drawBehind {
                // Specular sheen: brighter along the top edge, like light catching curved glass.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = (if (c.isDark) 0.10f else 0.35f) * highlight),
                        0.35f to Color.Transparent,
                        1f to Color.Transparent,
                    )
                )
            }
            .border(
                strokeWidth,
                Brush.linearGradient(
                    listOf(edge, edge.copy(alpha = edge.alpha * 0.35f), edge.copy(alpha = edge.alpha * 0.8f)),
                    start = Offset.Zero, end = Offset.Infinite,
                ),
                shp,
            ),
        content = content,
    )
}

/** Ambient light behind the glass. Colors follow the album art when [artColors] is given. */
@Composable
fun AmbientBackground(artColors: List<Color>?, modifier: Modifier = Modifier, intensity: Float = 1f) {
    val t = Lumen.theme
    val c = t.colors
    val a by animateColorAsState(artColors?.getOrNull(0) ?: c.glowA, tween(900), label = "ga")
    val b by animateColorAsState(artColors?.getOrNull(1) ?: c.glowB, tween(900), label = "gb")
    val d by animateColorAsState(artColors?.getOrNull(2) ?: c.glowC, tween(900), label = "gc")
    val drift = if (t.ambientMotion) {
        val inf = rememberInfiniteTransition(label = "amb")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "d").value
    } else 0.5f
    val obs = t.design.name == "OBSIDIAN"
    Box(
        modifier
            .fillMaxSize()
            .background(c.bgBase)
            .drawBehind {
                val w = size.width; val h = size.height
                val alpha = t.glass.glowAlpha * intensity
                fun glow(color: Color, cx: Float, cy: Float, r: Float, k: Float) = drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = alpha * k), color.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy),
                )
                glow(a, w * (0.1f + 0.15f * drift), h * 0.12f, w * 1.0f, 1f)
                glow(b, w * (0.95f - 0.2f * drift), h * (0.42f + 0.08f * drift), w * 0.85f, if (obs && artColors == null) 0.35f else 0.9f)
                glow(d, w * (0.2f + 0.1f * drift), h * 0.92f, w * 0.95f, 0.85f)
            }
    )
}

@Composable
fun ProvideHaze(state: HazeState?, content: @Composable () -> Unit) = CompositionLocalProvider(LocalHaze provides state, content = content)
