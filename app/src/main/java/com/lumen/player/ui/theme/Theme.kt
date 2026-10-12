package com.lumen.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumen.player.data.settings.Design
import com.lumen.player.data.settings.ThemeMode

/** Semantic tokens — mirrors the Figma variable collections (Aurora/Obsidian/Fluid × Dark/Light). */
@Immutable
data class LumenColors(
    val bgBase: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassStroke: Color,
    val glassStrokeSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val scrim: Color,
    val divider: Color,
    val track: Color,
    val isDark: Boolean,
)

@Immutable
data class LumenShapes(val card: Dp, val tile: Dp, val control: Dp, val panel: Dp, val button: Dp)

@Immutable
data class LumenType(
    val display: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
    val upperLabels: Boolean,
)

@Immutable
data class GlassSpec(
    val blur: Dp,
    val glowAlpha: Float,
    val shadowAlpha: Float,
    val highlight: Float, // strength of the top specular edge
    val neon: Boolean,     // Obsidian: illuminated accent glow
)

@Immutable
data class LumenTheme(
    val design: Design,
    val colors: LumenColors,
    val shapes: LumenShapes,
    val type: LumenType,
    val glass: GlassSpec,
    val reduceTransparency: Boolean,
    val ambientMotion: Boolean,
)

private fun c(hex: String): Color {
    val parts = hex.split("@")
    val v = parts[0].toLong(16)
    val base = Color(0xFF000000 or v)
    return if (parts.size > 1) base.copy(alpha = parts[1].toFloat()) else base
}

private fun palette(dark: Boolean, v: List<String>) = LumenColors(
    c(v[0]), c(v[1]), c(v[2]), c(v[3]), c(v[4]), c(v[5]), c(v[6]), c(v[7]), c(v[8]), c(v[9]), c(v[10]),
    c(v[11]), c(v[12]), c(v[13]), c(v[14]), c(v[15]), c(v[16]), c(v[17]), c(v[18]), c(v[19]), c(v[20]), c(v[21]), dark,
)

private val TOK = mapOf(
    Design.AURORA to (listOf("0A0B18", "7C5CFF", "1FD2F0", "FF5FA2", "FFFFFF@.08", "FFFFFF@.16", "FFFFFF@.26", "FFFFFF@.10", "F5F6FF", "F5F6FF@.68", "F5F6FF@.42", "8B7BFF", "34D8F5", "0A0B18", "8B7BFF@.24", "B8AEFF", "3EE0A0", "FF6B81", "FFC861", "05060F@.6", "FFFFFF@.08", "FFFFFF@.14") to
        listOf("EDEFFA", "B3A2FF", "8FE7F7", "FFB5D3", "FFFFFF@.55", "FFFFFF@.8", "FFFFFF@.9", "0E1030@.08", "0E1030", "0E1030@.66", "0E1030@.42", "6A55F5", "0BA6C9", "FFFFFF", "6A55F5@.14", "5B44EE", "12A874", "E5384F", "C98A0A", "0E1030@.3", "0E1030@.07", "0E1030@.10")),
    Design.OBSIDIAN to (listOf("050506", "2A2F38", "D4FF3F", "1C1F24", "FFFFFF@.045", "FFFFFF@.09", "FFFFFF@.16", "FFFFFF@.07", "F2F3F5", "F2F3F5@.6", "F2F3F5@.36", "D4FF3F", "F2F3F5", "050506", "D4FF3F@.14", "D4FF3F", "D4FF3F", "FF4D4D", "FFB020", "000000@.7", "FFFFFF@.06", "FFFFFF@.12") to
        listOf("F3F3F1", "DADBD7", "D4FF3F", "FFFFFF", "FFFFFF@.6", "FFFFFF@.88", "0A0A0B@.14", "0A0A0B@.07", "0A0A0B", "0A0A0B@.6", "0A0A0B@.38", "0A0A0B", "0A0A0B", "D4FF3F", "0A0A0B@.06", "0A0A0B", "3F7A00", "D92D2D", "B26B00", "0A0A0B@.35", "0A0A0B@.07", "0A0A0B@.10")),
    Design.FLUID to (listOf("0B0612", "FF2E93", "FF8A1F", "12D6B0", "FFFFFF@.10", "FFFFFF@.18", "FFFFFF@.30", "FFFFFF@.10", "FFF5FA", "FFF5FA@.7", "FFF5FA@.44", "FF4D8D", "FF9A3D", "FFFFFF", "FF4D8D@.22", "FF8AB5", "2EE6B8", "FF5A5A", "FFC23D", "0B0612@.55", "FFFFFF@.08", "FFFFFF@.16") to
        listOf("FFF3EA", "FF9CC6", "FFC18A", "8EEBD6", "FFFFFF@.5", "FFFFFF@.78", "FFFFFF@.95", "24101F@.08", "24101F", "24101F@.68", "24101F@.44", "F0337A", "F27A12", "FFFFFF", "F0337A@.14", "D41F64", "0E9E7E", "D93636", "B9780A", "24101F@.3", "24101F@.07", "24101F@.10")),
)

private fun ts(f: FontFamily, w: FontWeight, size: Int, lh: Int, ls: Float) =
    TextStyle(fontFamily = f, fontWeight = w, fontSize = size.sp, lineHeight = lh.sp, letterSpacing = (ls / size).em)

private fun typeFor(d: Design): LumenType = when (d) {
    Design.AURORA -> {
        val f = LumenFonts.jakarta
        LumenType(
            ts(f, FontWeight.ExtraBold, 34, 40, -1f), ts(f, FontWeight.ExtraBold, 24, 30, -.5f), ts(f, FontWeight.Bold, 18, 24, -.2f),
            ts(f, FontWeight.Medium, 15, 21, 0f), ts(f, FontWeight.Normal, 13, 18, 0f), ts(f, FontWeight.SemiBold, 13, 16, .1f),
            ts(f, FontWeight.Normal, 11, 14, .2f), ts(f, FontWeight.SemiBold, 11, 14, 1.2f), false,
        )
    }
    Design.OBSIDIAN -> {
        val g = LumenFonts.grotesk; val m = LumenFonts.mono
        LumenType(
            ts(g, FontWeight.Bold, 32, 36, -1.2f), ts(g, FontWeight.Bold, 22, 28, -.6f), ts(g, FontWeight.Medium, 17, 22, -.2f),
            ts(g, FontWeight.Normal, 15, 21, 0f), ts(g, FontWeight.Normal, 13, 18, 0f), ts(m, FontWeight.Medium, 11, 14, .6f),
            ts(m, FontWeight.Medium, 10, 13, .4f), ts(m, FontWeight.Medium, 10, 13, 2f), true,
        )
    }
    Design.FLUID -> {
        val h = LumenFonts.bricolage; val b = LumenFonts.outfit
        LumenType(
            ts(h, FontWeight.ExtraBold, 40, 42, -1.4f), ts(h, FontWeight.ExtraBold, 27, 30, -.7f), ts(h, FontWeight.Bold, 19, 24, -.2f),
            ts(b, FontWeight.Medium, 15, 21, 0f), ts(b, FontWeight.Normal, 13, 18, 0f), ts(b, FontWeight.SemiBold, 13, 16, 0f),
            ts(b, FontWeight.Normal, 11, 14, .2f), ts(b, FontWeight.SemiBold, 11, 14, 1f), false,
        )
    }
}

private fun shapesFor(d: Design) = when (d) {
    Design.AURORA -> LumenShapes(28.dp, 22.dp, 999.dp, 32.dp, 999.dp)
    Design.OBSIDIAN -> LumenShapes(18.dp, 12.dp, 14.dp, 22.dp, 12.dp)
    Design.FLUID -> LumenShapes(34.dp, 26.dp, 999.dp, 40.dp, 999.dp)
}

private fun glassFor(d: Design) = when (d) {
    Design.AURORA -> GlassSpec(28.dp, .55f, .20f, .55f, false)
    Design.OBSIDIAN -> GlassSpec(22.dp, .40f, .40f, .85f, true)
    Design.FLUID -> GlassSpec(34.dp, .70f, .18f, .45f, false)
}

fun buildTheme(design: Design, dark: Boolean, reduceTransparency: Boolean, ambientMotion: Boolean): LumenTheme {
    val pair = TOK.getValue(design)
    var colors = palette(dark, if (dark) pair.first else pair.second)
    if (reduceTransparency) {
        // Solid surfaces for readability: lift glass alpha considerably.
        colors = colors.copy(
            glassFill = colors.glassFill.copy(alpha = if (dark) .14f else .85f),
            glassFillStrong = colors.glassFillStrong.copy(alpha = if (dark) .22f else .95f),
        )
    }
    return LumenTheme(design, colors, shapesFor(design), typeFor(design), glassFor(design), reduceTransparency, ambientMotion)
}

val LocalLumen = staticCompositionLocalOf { buildTheme(Design.AURORA, true, false, true) }

object Lumen {
    val theme: LumenTheme @Composable get() = LocalLumen.current
    val colors: LumenColors @Composable get() = LocalLumen.current.colors
    val type: LumenType @Composable get() = LocalLumen.current.type
    val shapes: LumenShapes @Composable get() = LocalLumen.current.shapes
    val design: Design @Composable get() = LocalLumen.current.design
}

@Composable
fun LumenThemeProvider(
    design: Design,
    mode: ThemeMode,
    reduceTransparency: Boolean,
    ambientMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val theme = buildTheme(design, dark, reduceTransparency, ambientMotion)
    CompositionLocalProvider(LocalLumen provides theme, content = content)
}

@Suppress("unused")
val NoOverflow = TextOverflow.Ellipsis
@Suppress("unused")
val Unspecified: TextUnit = TextUnit.Unspecified
