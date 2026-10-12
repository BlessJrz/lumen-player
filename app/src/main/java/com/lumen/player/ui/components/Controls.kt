package com.lumen.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.theme.Lumen
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class BtnKind { Primary, Glass, Danger, Ghost }

@Composable
fun LButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: BtnKind = BtnKind.Primary,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val c = Lumen.colors
    val fg = when (kind) { BtnKind.Primary -> c.onAccent; BtnKind.Danger -> Color.White; BtnKind.Ghost -> c.accentText; BtnKind.Glass -> c.textPrimary }
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) LIcon(icon, 18.dp, fg)
        LText(text, Lumen.type.label, fg, maxLines = 1, upper = true)
    }
    val m = modifier.height(height).pressable(enabled = enabled, haptic = true, onClick = onClick)
    val alpha = if (enabled) 1f else 0.45f
    when (kind) {
        BtnKind.Glass -> Glass(m, radius = Lumen.shapes.button, strong = true, shadow = false) {
            Row(Modifier.padding(horizontal = 22.dp).fillMaxHeight().align(Alignment.Center).alpha(alpha), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content)
        }
        else -> {
            val bg = when (kind) { BtnKind.Primary -> c.accent; BtnKind.Danger -> c.danger; else -> Color.Transparent }
            Row(
                m.then(if (kind == BtnKind.Primary && Lumen.theme.glass.neon) Modifier.shadow(14.dp, shape(Lumen.shapes.button), ambientColor = c.accent, spotColor = c.accent) else Modifier)
                    .clip(shape(Lumen.shapes.button)).background(bg.copy(alpha = bg.alpha * alpha)).padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content,
            )
        }
    }
}


enum class IconKind { Glass, Accent, Plain, Soft }

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    kind: IconKind = IconKind.Glass,
    tint: Color? = null,
    iconSize: Dp = size * 0.46f,
    blur: Boolean = false,
) {
    val c = Lumen.colors
    val fg = tint ?: when (kind) { IconKind.Accent -> c.onAccent; IconKind.Soft -> c.accentText; else -> c.textPrimary }
    val m = modifier.size(size).pressable(haptic = true, scaleTo = .9f, onClick = onClick)
    when (kind) {
        IconKind.Glass -> Glass(m, radius = Lumen.shapes.control, strong = true, shadow = blur, blur = blur) { LIcon(icon, iconSize, fg, Modifier.align(Alignment.Center)) }
        IconKind.Accent -> Box(
            m.then(if (Lumen.theme.glass.neon) Modifier.shadow(18.dp, shape(Lumen.shapes.control), ambientColor = c.accent, spotColor = c.accent)
            else Modifier.shadow(20.dp, shape(Lumen.shapes.control), ambientColor = c.accent.copy(alpha = .6f), spotColor = c.accent.copy(alpha = .6f)))
                .clip(shape(Lumen.shapes.control)).background(c.accent),
            contentAlignment = Alignment.Center,
        ) { LIcon(icon, iconSize, fg) }
        IconKind.Soft -> Box(m.clip(shape(Lumen.shapes.control)).background(c.accentSoft), contentAlignment = Alignment.Center) { LIcon(icon, iconSize, fg) }
        IconKind.Plain -> Box(m, contentAlignment = Alignment.Center) { LIcon(icon, iconSize, fg) }
    }
}

/** Custom toggle switch (Obsidian uses a squarer thumb). */
@Composable
fun LToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    val obs = Lumen.design.name == "OBSIDIAN"
    val x by animateDpAsState(if (checked) 23.dp else 3.dp, spring(dampingRatio = .6f, stiffness = 600f), label = "tg")
    val bg by animateColorAsState(if (checked) c.accent else c.track, label = "tgc")
    val r = if (obs) 10.dp else 16.dp
    Box(
        modifier.size(52.dp, 32.dp).clip(RoundedCornerShape(r + 3.dp)).background(bg)
            .then(if (!checked) Modifier.border(1.dp, c.glassStrokeSoft, RoundedCornerShape(r + 3.dp)) else Modifier)
            .pressable(scaleTo = .94f, haptic = true) { onChange(!checked) }
    ) {
        Box(
            Modifier.offset(x = x, y = 3.dp).size(26.dp).shadow(4.dp, RoundedCornerShape(r))
                .clip(RoundedCornerShape(r)).background(if (obs && checked) c.onAccent else Color.White)
        )
    }
}

/** Custom slider used for the timeline, volume, EQ and timers. */
@Composable
fun LSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onDone: (Float) -> Unit = {}, fillColor: Color? = null) {
    val c = Lumen.colors
    val obs = Lumen.design.name == "OBSIDIAN"
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val v = if (dragging) local else value
    val track by animateDpAsState(if (dragging) 8.dp else 5.dp, label = "trk")
    BoxWithConstraints(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { o -> val f = (o.x / size.width).coerceIn(0f, 1f); onChange(f); onDone(f) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; local = (o.x / size.width).coerceIn(0f, 1f); onChange(local) },
                    onDragEnd = { dragging = false; onDone(local) },
                    onDragCancel = { dragging = false },
                ) { ch, _ -> local = (ch.position.x / size.width).coerceIn(0f, 1f); onChange(local) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(track).clip(CircleShape).background(c.track))
        Box(Modifier.width(w * v).height(track).clip(CircleShape).background(fillColor ?: c.accent))
        if (obs) {
            Box(Modifier.offset(x = (w * v) - 2.dp).size(4.dp, if (dragging) 24.dp else 18.dp).shadow(if (dragging) 10.dp else 0.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent).clip(CircleShape).background(fillColor ?: c.accent))
        } else {
            val d = if (dragging) 26.dp else 18.dp
            Box(Modifier.offset(x = (w * v) - d / 2).size(d).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).border(if (dragging) 3.dp else 2.dp, fillColor ?: c.accent, CircleShape))
        }
    }
}

/** Obsidian's waveform scrubber — played part lit in the accent color. */
@Composable
fun Waveform(progress: Float, seed: Int, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, bars: Int = 64, height: Dp = 44.dp) {
    val c = Lumen.colors
    var drag by remember { mutableStateOf<Float?>(null) }
    val p = drag ?: progress
    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(Unit) { detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { drag?.let(onSeek); drag = null }, onDragCancel = { drag = null }) { ch, _ ->
                    drag = (ch.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        val gap = 2.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val k = abs(sin((i + seed % 17) * 1.7) * cos((i + seed % 11) * 0.55)).toFloat()
            val h = 6.dp.toPx() + k * (size.height - 8.dp.toPx())
            val on = i.toFloat() / bars < p
            drawRoundRect(if (on) c.accent else c.track, Offset(i * (bw + gap), (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

@Composable
fun LChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Lumen.colors
    val obs = Lumen.design.name == "OBSIDIAN"
    val fg = if (selected) c.onAccent else c.textSecondary
    val shp = shape(Lumen.shapes.control)
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) LIcon(icon, 16.dp, if (selected) fg else c.accentText)
            LText(label, Lumen.type.label, fg, maxLines = 1, upper = true)
        }
    }
    val m = modifier.pressable(scaleTo = .94f, onClick = onClick)
    when {
        selected -> Box(m.clip(shp).background(c.accent)) { body() }
        obs -> Box(m.clip(shp).border(1.dp, c.glassStroke, shp)) { body() }
        else -> Glass(m, radius = Lumen.shapes.control, shadow = false) { body() }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Lumen.colors
    Glass(modifier, radius = Lumen.shapes.control, shadow = false) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Box(
                    Modifier.height(32.dp).clip(shape(Lumen.shapes.control))
                        .then(if (on) Modifier.background(c.glassFillStrong).border(1.dp, c.glassStroke, shape(Lumen.shapes.control)) else Modifier)
                        .pressable(scaleTo = .95f) { onSelect(i) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { LText(o, Lumen.type.label, if (on) c.textPrimary else c.textSecondary, maxLines = 1, upper = true) }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Songs, artists, folders, lyrics", onSubmit: () -> Unit = {}, autoFocusRequester: androidx.compose.ui.focus.FocusRequester? = null) {
    val c = Lumen.colors
    val typing = value.isNotEmpty()
    Glass(modifier.fillMaxWidth().height(52.dp), radius = Lumen.shapes.control, strong = typing, shadow = typing) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LIcon(LIcons.search, 20.dp, if (typing) c.accentText else c.textSecondary)
            Box(Modifier.weight(1f)) {
                if (!typing) LText(placeholder, Lumen.type.body, c.textTertiary, maxLines = 1)
                BasicTextField(
                    value = value, onValueChange = onChange, singleLine = true,
                    textStyle = Lumen.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().then(if (autoFocusRequester != null) Modifier.focusRequester(autoFocusRequester) else Modifier),
                )
            }
            if (typing) Box(Modifier.size(24.dp).clip(CircleShape).background(c.track).pressable { onChange("") }, contentAlignment = Alignment.Center) {
                LIcon(LIcons.close, 14.dp, c.textSecondary)
            }
        }
    }
}


/** Labelled glass text input for forms. */
@Composable
fun LField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    placeholder: String = "",
    password: Boolean = false,
    badge: String? = null,
    badgeColor: Color? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: Dp = 50.dp,
) {
    val c = Lumen.colors
    var focused by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LText(label, Lumen.type.overline, c.textTertiary, upper = true)
        Glass(
            Modifier.fillMaxWidth().then(Modifier.height(if (singleLine) minHeight else minHeight * 2.4f)), radius = Lumen.shapes.tile, shadow = false,
            stroke = if (focused) c.accent else null, strokeWidth = if (focused) 1.5.dp else 1.dp,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp).then(if (singleLine) Modifier.fillMaxHeight() else Modifier), verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) LIcon(icon, 18.dp, c.textSecondary)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) LText(placeholder, Lumen.type.body, c.textTertiary, maxLines = 1)
                    BasicTextField(
                        value = value, onValueChange = onChange, singleLine = singleLine,
                        textStyle = Lumen.type.body.copy(color = c.textPrimary), cursorBrush = SolidColor(c.accent),
                        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    )
                }
                if (badge != null) Badge(badge, badgeColor ?: c.accentText)
            }
        }
    }
}


@Composable
fun Badge(text: String, color: Color = Lumen.colors.accentText, fill: Color? = null) {
    Box(Modifier.height(22.dp).clip(shape(Lumen.shapes.control)).background(fill ?: color.copy(alpha = .16f)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        LText(text, Lumen.type.caption, color, maxLines = 1, upper = true)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Lumen.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LText(title, Lumen.type.titleM, c.textPrimary, Modifier.weight(1f), maxLines = 1)
        if (action != null && onAction != null) Row(Modifier.pressable(onClick = onAction), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            LText(action, Lumen.type.label, c.accentText, upper = true)
            LIcon(LIcons.chevronRight, 16.dp, c.accentText)
        }
    }
}

@Composable
fun StatusPill(label: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Glass(modifier.height(28.dp).then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier), radius = Lumen.shapes.control, shadow = false) {
        Row(Modifier.padding(horizontal = 10.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            LText(label, Lumen.type.caption, Lumen.colors.textSecondary, maxLines = 1, upper = true)
        }
    }
}

enum class RingState { Queued, Downloading, Paused, Done, Failed }

@Composable
fun ProgressRing(state: RingState, fraction: Float, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val c = Lumen.colors
    val col = when (state) { RingState.Done -> c.success; RingState.Failed -> c.danger; else -> c.accent }
    val anim by animateFloatAsState(fraction, tween(400), label = "ring")
    Box(modifier.size(40.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            val sw = 3.dp.toPx()
            drawArc(c.track, 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
            val sweep = if (state == RingState.Done || state == RingState.Failed) 360f else 360f * anim
            if (sweep > 0) drawArc(col, -90f, sweep, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
        }
        val (ic, tint) = when (state) {
            RingState.Queued -> LIcons.clock to c.textTertiary
            RingState.Downloading -> LIcons.pause to c.textPrimary
            RingState.Paused -> LIcons.play to c.textPrimary
            RingState.Done -> LIcons.check to c.success
            RingState.Failed -> LIcons.refresh to c.danger
        }
        LIcon(ic, 14.dp, tint)
    }
}

@Composable
fun LinearBar(fraction: Float, modifier: Modifier = Modifier, color: Color? = null, height: Dp = 5.dp) {
    val c = Lumen.colors
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(350), label = "bar")
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.track)) {
        Box(Modifier.width(maxWidth * anim).fillMaxHeight().clip(CircleShape).background(color ?: c.accent))
    }
}

/** Glass orb spinner (rotating accent arc). */
@Composable
fun OrbLoader(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val c = Lumen.colors
    val inf = rememberInfiniteTransition(label = "orb")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "r")
    Glass(modifier.size(size), radius = size / 2, strong = true) {
        Canvas(Modifier.size(size).padding(6.dp).rotate(rot)) {
            drawArc(c.accent, -90f, 250f, false, style = Stroke(size.toPx() * .06f, cap = StrokeCap.Round))
        }
        LIcon(LIcons.music, size * .34f, c.accentText, Modifier.align(Alignment.Center))
    }
}

@Composable
fun PulseDots(modifier: Modifier = Modifier) {
    val c = Lumen.colors
    val inf = rememberInfiniteTransition(label = "dots")
    val t by inf.animateFloat(0f, 3f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "t")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val a = 1f - ((t - i + 3) % 3) / 3f
            Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent.copy(alpha = .3f + .7f * a)))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Lumen.colors
    androidx.compose.foundation.layout.Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Glass(Modifier.size(112.dp), radius = 56.dp, strong = true) { LIcon(icon, 44.dp, c.accentText, Modifier.align(Alignment.Center)) }
        LText(title, Lumen.type.titleL, c.textPrimary, align = TextAlign.Center)
        LText(body, Lumen.type.body, c.textSecondary, align = TextAlign.Center)
        if (action != null && onAction != null) LButton(action, onAction, Modifier.wrapContentWidth())
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
