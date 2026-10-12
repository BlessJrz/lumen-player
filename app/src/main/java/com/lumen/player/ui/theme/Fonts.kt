@file:OptIn(ExperimentalTextApi::class)

package com.lumen.player.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lumen.player.R

/** Variable OFL fonts bundled in res/font (fetched by scripts/fetch_fonts.sh). */
object LumenFonts {
    private fun family(res: Int, vararg weights: Int): FontFamily = FontFamily(
        weights.map { w ->
            Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        }
    )

    val jakarta = family(R.font.plus_jakarta_sans, 400, 500, 600, 700, 800)
    val grotesk = family(R.font.space_grotesk, 300, 400, 500, 700)
    val mono = family(R.font.jetbrains_mono, 400, 500, 700)
    val bricolage = family(R.font.bricolage_grotesque, 500, 700, 800)
    val outfit = family(R.font.outfit, 400, 500, 600, 700)
}
