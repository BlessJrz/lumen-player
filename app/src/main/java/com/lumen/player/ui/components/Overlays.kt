package com.lumen.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class MenuItem(val icon: ImageVector?, val label: String, val danger: Boolean = false, val checked: Boolean = false, val trailing: String? = null, val onClick: () -> Unit)
data class MenuSpec(val title: String?, val subtitle: String?, val track: TrackEntity?, val items: List<MenuItem>, val anchor: Offset)
data class DialogSpec(val icon: ImageVector, val title: String, val body: String, val confirm: String, val danger: Boolean, val cancel: String = "Cancel", val onConfirm: () -> Unit)
data class PromptSpec(val title: String, val initial: String, val placeholder: String, val confirm: String, val onConfirm: (String) -> Unit)
enum class ToastKind { Success, Error, Info }
data class ToastSpec(val id: Long, val message: String, val kind: ToastKind, val action: String?, val onAction: (() -> Unit)?)
data class SheetSpec(val title: String, val subtitle: String?, val content: @Composable ColumnScope.(close: () -> Unit) -> Unit)

/** App-wide custom overlays: sheets, dialogs, menus, prompts and toasts — no platform dialogs. */
@Stable
class Overlays {
    var menu by mutableStateOf<MenuSpec?>(null); private set
    var dialog by mutableStateOf<DialogSpec?>(null); private set
    var prompt by mutableStateOf<PromptSpec?>(null); private set
    var sheet by mutableStateOf<SheetSpec?>(null); private set
    val toasts = mutableStateListOf<ToastSpec>()
    var lastTap: Offset = Offset.Zero

    fun menu(title: String?, subtitle: String?, track: TrackEntity?, items: List<MenuItem>) { menu = MenuSpec(title, subtitle, track, items, lastTap) }
    fun dialog(d: DialogSpec) { dialog = d }
    fun prompt(p: PromptSpec) { prompt = p }
    fun sheet(title: String, subtitle: String? = null, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) { sheet = SheetSpec(title, subtitle, content) }
    fun toast(message: String, kind: ToastKind = ToastKind.Success, action: String? = null, onAction: (() -> Unit)? = null) {
        toasts.add(ToastSpec(System.nanoTime(), message, kind, action, onAction))
        while (toasts.size > 2) toasts.removeAt(0)
    }
    fun closeMenu() { menu = null }
    fun closeDialog() { dialog = null }
    fun closePrompt() { prompt = null }
    fun closeSheet() { sheet = null }
    val anyOpen get() = menu != null || dialog != null || prompt != null || sheet != null
    fun closeTop() { when { prompt != null -> prompt = null; dialog != null -> dialog = null; menu != null -> menu = null; sheet != null -> sheet = null } }
}

val LocalOverlays = staticCompositionLocalOf { Overlays() }

/** Records where the user last touched so context menus open next to their finger. */
fun Modifier.trackTaps(o: Overlays): Modifier = pointerInput(o) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull()?.let { if (it.pressed) o.lastTap = it.position }
        }
    }
}

@Composable
private fun Scrim(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(Lumen.colors.scrim).pressable(scaleTo = 1f, onClick = onClick))
    }
}

@Composable
fun OverlayHost(o: Overlays, wide: Boolean) {
    BackHandler(enabled = o.anyOpen) { o.closeTop() }
    Box(Modifier.fillMaxSize()) {
        // ---- Sheet ----
        Scrim(o.sheet != null) { o.closeSheet() }
        AnimatedVisibility(
            o.sheet != null, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .82f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(),
        ) {
            val spec = remember(o.sheet) { o.sheet } ?: return@AnimatedVisibility
            var drag by remember { mutableFloatStateOf(0f) }
            Glass(
                Modifier.widthIn(max = if (wide) 520.dp else 2000.dp).fillMaxWidth()
                    .offset { IntOffset(0, drag.roundToInt().coerceAtLeast(0)) }
                    .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { if (drag > 160f) o.closeSheet(); drag = 0f }) { _, d -> drag += d } },
                radius = Lumen.shapes.panel, strong = true, blur = true,
            ) {
                Column(
                    Modifier.navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp)
                        .heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(CircleShape).background(Lumen.colors.textTertiary))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            LText(spec.title, Lumen.type.titleL)
                            if (spec.subtitle != null) LText(spec.subtitle, Lumen.type.bodyS, Lumen.colors.textSecondary)
                        }
                        IconBtn(LIcons.close, { o.closeSheet() }, size = 36.dp, kind = IconKind.Soft, tint = Lumen.colors.textSecondary)
                    }
                    spec.content(this) { o.closeSheet() }
                }
            }
        }

        // ---- Context menu ----
        Scrim(o.menu != null) { o.closeMenu() }
        val menu = o.menu
        if (menu != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val menuW = 256.dp
                val estH = (64 + menu.items.size * 44 + 24).dp
                val ax = with(density) { menu.anchor.x.toDp() }
                val ay = with(density) { menu.anchor.y.toDp() }
                val x = (ax - menuW).coerceIn(12.dp, maxWidth - menuW - 12.dp)
                val y = (ay - 20.dp).coerceIn(56.dp, (maxHeight - estH - 24.dp).coerceAtLeast(56.dp))
                var shown by remember(menu) { mutableStateOf(false) }
                LaunchedEffect(menu) { shown = true }
                AnimatedVisibility(shown, modifier = Modifier.offset(x, y), enter = scaleIn(spring(dampingRatio = .7f, stiffness = 500f), initialScale = .85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)) + fadeIn()) {
                    MenuCard(menu, menuW) { o.closeMenu() }
                }
            }
        }

        // ---- Dialog ----
        Scrim(o.dialog != null || o.prompt != null) { o.closeDialog(); o.closePrompt() }
        AnimatedVisibility(o.dialog != null, modifier = Modifier.align(Alignment.Center), enter = scaleIn(spring(dampingRatio = .72f), initialScale = .9f) + fadeIn(), exit = scaleOut(targetScale = .94f) + fadeOut()) {
            val d = remember(o.dialog) { o.dialog } ?: return@AnimatedVisibility
            DialogCard(d) { o.closeDialog() }
        }
        AnimatedVisibility(o.prompt != null, modifier = Modifier.align(Alignment.Center).imePadding(), enter = scaleIn(spring(dampingRatio = .72f), initialScale = .9f) + fadeIn(), exit = fadeOut()) {
            val p = remember(o.prompt) { o.prompt } ?: return@AnimatedVisibility
            PromptCard(p) { o.closePrompt() }
        }

        // ---- Toasts ----
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in o.toasts.toList()) key(t.id) {
                ToastCard(t) { o.toasts.remove(t) }
            }
        }
    }
}

@Composable
private fun key(id: Long, content: @Composable () -> Unit) = androidx.compose.runtime.key(id) { content() }

@Composable
fun MenuCard(m: MenuSpec, width: Dp, close: () -> Unit) {
    val c = Lumen.colors
    Glass(Modifier.width(width), radius = Lumen.shapes.card.coerceAtMost(24.dp), strong = true, blur = true) {
        Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (m.title != null) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (m.track != null) Artwork(m.track, Modifier.size(36.dp), radius = 8.dp)
                    Column(Modifier.weight(1f)) {
                        LText(m.title, Lumen.type.label, c.textPrimary, maxLines = 1)
                        if (m.subtitle != null) LText(m.subtitle, Lumen.type.caption, c.textSecondary, maxLines = 1)
                    }
                }
                Divider()
            }
            m.items.forEach { it ->
                if (it.label == "—") { Divider(Modifier.padding(vertical = 2.dp)); return@forEach }
                Row(
                    Modifier.fillMaxWidth().height(42.dp).clip(shape(minOf(Lumen.shapes.tile, 14.dp)))
                        .then(if (it.checked) Modifier.background(c.accentSoft) else Modifier)
                        .pressable(scaleTo = .97f) { close(); it.onClick() }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (it.icon != null) LIcon(it.icon, 18.dp, if (it.danger) c.danger else c.textSecondary)
                    LText(it.label, Lumen.type.body, if (it.danger) c.danger else c.textPrimary, Modifier.weight(1f), maxLines = 1)
                    if (it.trailing != null) LText(it.trailing, Lumen.type.caption, c.textTertiary, upper = true)
                    if (it.checked) LIcon(LIcons.check, 16.dp, c.accentText)
                }
            }
        }
    }
}

@Composable
private fun DialogCard(d: DialogSpec, close: () -> Unit) {
    val c = Lumen.colors
    Glass(Modifier.width(340.dp), radius = Lumen.shapes.panel, strong = true, blur = true) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(shape(Lumen.shapes.control)).background(if (d.danger) c.danger.copy(alpha = .14f) else c.accentSoft), contentAlignment = Alignment.Center) {
                LIcon(d.icon, 24.dp, if (d.danger) c.danger else c.accentText)
            }
            LText(d.title, Lumen.type.titleM, align = TextAlign.Center)
            LText(d.body, Lumen.type.bodyS, c.textSecondary, align = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton(d.cancel, close, Modifier.weight(1f), kind = BtnKind.Glass)
                LButton(d.confirm, { close(); d.onConfirm() }, Modifier.weight(1f), kind = if (d.danger) BtnKind.Danger else BtnKind.Primary)
            }
        }
    }
}

@Composable
private fun PromptCard(p: PromptSpec, close: () -> Unit) {
    var text by remember(p) { mutableStateOf(p.initial) }
    Glass(Modifier.width(340.dp), radius = Lumen.shapes.panel, strong = true, blur = true) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LText(p.title, Lumen.type.titleM)
            LField("Name", text, { text = it }, placeholder = p.placeholder)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Cancel", close, Modifier.weight(1f), kind = BtnKind.Glass)
                LButton(p.confirm, { close(); p.onConfirm(text.trim()) }, Modifier.weight(1f), enabled = text.isNotBlank())
            }
        }
    }
}

@Composable
private fun ToastCard(t: ToastSpec, dismiss: () -> Unit) {
    val c = Lumen.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(t.id) { shown = true; delay(3800); shown = false; delay(300); dismiss() }
    AnimatedVisibility(shown, enter = slideInVertically(spring(dampingRatio = .7f)) { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
        Glass(Modifier.width(360.dp).height(56.dp), radius = Lumen.shapes.control, strong = true, blur = true) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val (ic, col) = when (t.kind) { ToastKind.Success -> LIcons.check to c.success; ToastKind.Error -> LIcons.alert to c.danger; ToastKind.Info -> LIcons.download to c.accentText }
                Box(Modifier.size(32.dp).clip(shape(Lumen.shapes.control)).background(c.track), contentAlignment = Alignment.Center) { LIcon(ic, 16.dp, col) }
                LText(t.message, Lumen.type.label, c.textPrimary, Modifier.weight(1f), maxLines = 2)
                if (t.action != null && t.onAction != null) LText(t.action, Lumen.type.label, c.accentText, Modifier.pressable { t.onAction.invoke(); shown = false }, upper = true)
            }
        }
    }
}

@Suppress("unused")
private val keepImports = listOf(RoundedCornerShape(1.dp)).size + Modifier.graphicsLayer().hashCode()
