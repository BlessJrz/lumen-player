package com.lumen.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.theme.Lumen

/** Bottom space reserved for the floating tab bar + mini-player (cover) or the player bar (inner). */
@Composable
fun bottomInset(): Dp = if (LocalEnv.current.wide) 120.dp else 200.dp

@Composable
fun hPad(): Dp = if (LocalEnv.current.wide) 0.dp else 16.dp

/** Standard vertically-scrolling screen body. */
@Composable
fun ScreenList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    gap: Dp = 26.dp,
    horizontal: Dp = hPad(),
    content: LazyListScope.() -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = horizontal, end = horizontal, top = top + 12.dp, bottom = bottomInset()),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

/** Big screen title row (display style) with optional action buttons. */
@Composable
fun TitleBar(title: String, modifier: Modifier = Modifier, overline: String? = null, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            if (overline != null) LText(overline, Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
            LText(title, Lumen.type.display, maxLines = 1)
        }
        actions()
    }
}

/** Back + centered title bar for pushed screens. */
@Composable
fun BackBar(title: String, overline: String? = null, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val env = LocalEnv.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBtn(LIcons.chevronLeft, onBack ?: { env.nav.popBackStack(); Unit }, size = 40.dp)
        Column(Modifier.weight(1f)) {
            if (overline != null) LText(overline, Lumen.type.overline, Lumen.colors.textTertiary, maxLines = 1, upper = true)
            LText(title, Lumen.type.titleM, maxLines = 1)
        }
        actions()
    }
}

@Composable
fun HRow(gap: Dp = 14.dp, content: LazyListScope.() -> Unit) {
    val pad = hPad()
    Bleed {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

/** Section with a header; horizontal rows bleed to the screen edge on the cover screen. */
fun LazyListScope.section(key: String, content: @Composable () -> Unit) {
    item(key) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } }
}

@Composable
fun Bleed(content: @Composable () -> Unit) {
    // Undo the list's horizontal padding so carousels scroll edge to edge.
    val pad = hPad()
    Box(Modifier.padding(start = 0.dp).fillMaxWidth().then(if (pad > 0.dp) Modifier.layoutBleed(pad) else Modifier)) { content() }
}

private fun Modifier.layoutBleed(pad: Dp): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val extra = (pad * 2).roundToPx()
        val p = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra, minWidth = constraints.minWidth + extra))
        layout(constraints.maxWidth, p.height) { p.place(-pad.roundToPx(), 0) }
    }
)

