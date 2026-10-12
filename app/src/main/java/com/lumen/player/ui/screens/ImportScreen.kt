@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lumen.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lumen.player.data.settings.AppSettings
import com.lumen.player.data.settings.AudioFormat
import com.lumen.player.data.youtube.ImportState
import com.lumen.player.data.youtube.ImportStep
import com.lumen.player.data.youtube.VideoMeta
import com.lumen.player.ui.components.Badge
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LField
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LToggle
import com.lumen.player.ui.components.LinearBar
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.OrbLoader
import com.lumen.player.ui.components.PulseDots
import com.lumen.player.ui.components.SectionHeader
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.launch

@Composable
fun ImportScreen() {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val st by c.importer.state.collectAsState()
    val history by c.importer.history.collectAsState()
    val settings by c.settings.settings.collectAsState(initial = AppSettings())
    val clipboard = LocalClipboardManager.current
    var link by rememberSaveable { mutableStateOf("") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) c.importer.importFile(uri, settings.importToNas && c.nas.config.isConfigured, settings.importFolder)
    }
    LaunchedEffect(st) { if (st is ImportState.Fetching) link = (st as ImportState.Fetching).url }

    ScreenList(gap = 18.dp) {
        item("title") {
            TitleBar("Import", overline = "YouTube → MP3") { IconBtn(LIcons.refresh, { c.importer.updateEngine { o.toast(it, ToastKind.Info) } }, size = 40.dp) }
        }
        item("lead") { LText("Paste a link to save audio you own or are authorized to download — straight into a NAS folder or onto this phone.", Lumen.type.bodyS, Lumen.colors.textSecondary) }
        item("steps") { StepBar(st) }
        item("link") {
            val cc = Lumen.colors
            Glass(Modifier.fillMaxWidth().height(56.dp), radius = Lumen.shapes.control, strong = true, stroke = if (link.isNotBlank()) cc.accent else null, strokeWidth = if (link.isNotBlank()) 1.5.dp else 1.dp) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LIcon(LIcons.link, 20.dp, cc.accentText)
                    Box(Modifier.weight(1f)) {
                        if (link.isEmpty()) LText("youtube.com/watch?v=…", Lumen.type.body, cc.textTertiary, maxLines = 1)
                        BasicTextField(link, { link = it }, singleLine = true, textStyle = Lumen.type.body.copy(color = cc.textPrimary), cursorBrush = SolidColor(cc.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { if (link.isNotBlank()) c.importer.fetch(link) }), modifier = Modifier.fillMaxWidth())
                    }
                    Box(Modifier.pressable {
                        if (link.isEmpty()) { val t = clipboard.getText()?.text.orEmpty(); link = c.importer.extractUrl(t) ?: t.trim(); if (link.isNotBlank()) c.importer.fetch(link) }
                        else { link = ""; c.importer.reset() }
                    }) { Badge(if (link.isEmpty()) "Paste" else "Clear") }
                }
            }
        }
        if (link.isNotBlank() && (st is ImportState.Idle || st is ImportState.Failed)) item("fetch") {
            LButton("Get video info", { c.importer.fetch(link) }, Modifier.fillMaxWidth(), icon = LIcons.search)
        }
        when (val s = st) {
            ImportState.Idle -> {
                item("alt") {
                    Glass(Modifier.fillMaxWidth(), shadow = false) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LText("Other ways to add music", Lumen.type.titleM)
                            LText("Share a YouTube link to Lumen from the YouTube app, or add an audio file you already have.", Lumen.type.bodyS, Lumen.colors.textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LButton("Pick an audio file", { pickFile.launch(arrayOf("audio/*")) }, Modifier.weight(1f), icon = LIcons.folder, kind = BtnKind.Glass)
                            }
                        }
                    }
                }
            }
            ImportState.Preparing, is ImportState.Fetching -> item("loading") {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OrbLoader(size = 72.dp)
                    LText(if (s is ImportState.Preparing) "Starting the import engine…" else "Getting video info…", Lumen.type.label, Lumen.colors.textSecondary, upper = true)
                }
            }
            is ImportState.Ready -> item("ready") { ReadyCard(s.meta, settings) }
            is ImportState.Running -> item("run") { RunningCard(s) }
            is ImportState.Done -> item("done") {
                Glass(Modifier.fillMaxWidth(), strong = true) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Lumen.colors.success.copy(alpha = .16f)), contentAlignment = Alignment.Center) { LIcon(LIcons.check, 20.dp, Lumen.colors.success) }
                            Column(Modifier.weight(1f)) { LText("Imported", Lumen.type.titleM); LText("Saved to ${s.savedTo} · tagged", Lumen.type.bodyS, Lumen.colors.textSecondary) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LButton("Play now", { c.appScope.launch { c.library.trackNow(s.trackId)?.let { c.player.play(listOf(it)) } } }, Modifier.weight(1f), icon = LIcons.play)
                            LButton("Edit info", { env.nav.navigate(Routes.edit(s.trackId)) }, Modifier.weight(1f), icon = LIcons.edit, kind = BtnKind.Glass)
                        }
                        LButton("Import another", { link = ""; c.importer.reset() }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost)
                    }
                }
            }
            is ImportState.Failed -> item("fail") {
                Glass(Modifier.fillMaxWidth(), strong = true, stroke = Lumen.colors.danger, strokeWidth = 1.5.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LIcon(LIcons.alert, 22.dp, Lumen.colors.danger)
                            LText(s.message, Lumen.type.body, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LButton("Try again", { if (s.meta != null) c.importer.fetch(s.meta.url) else c.importer.fetch(link) }, Modifier.weight(1f), icon = LIcons.refresh)
                            LButton("Update engine", { c.importer.updateEngine { o.toast(it, ToastKind.Info) } }, Modifier.weight(1f), kind = BtnKind.Glass)
                        }
                        LButton("Pick an audio file instead", { pickFile.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth(), kind = BtnKind.Ghost)
                    }
                }
            }
        }
        if (history.isNotEmpty()) {
            item("hh") { SectionHeader("Imported this session") }
            history.forEach { h ->
                item("h${h.at}") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LIcon(LIcons.check, 18.dp, Lumen.colors.success)
                        Column(Modifier.weight(1f)) { LText(h.title, Lumen.type.body, maxLines = 1); LText(h.savedTo, Lumen.type.caption, Lumen.colors.textSecondary) }
                    }
                }
            }
        }
        item("legal") {
            LText("Only import audio you own or have permission to download. If a video can’t be imported directly, use “Pick an audio file” or open it in YouTube Music.", Lumen.type.caption, Lumen.colors.textTertiary)
        }
    }
}

@Composable
private fun StepBar(st: ImportState) {
    val idx = when (st) {
        ImportState.Idle -> 0; ImportState.Preparing, is ImportState.Fetching -> 1; is ImportState.Ready -> 2
        is ImportState.Running -> 3 + st.step.ordinal.coerceAtMost(1); is ImportState.Done -> 5; is ImportState.Failed -> 0
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(5) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < idx) Lumen.colors.accent else Lumen.colors.track)) }
        LText("${idx.coerceAtMost(5)}/5", Lumen.type.caption, Lumen.colors.textSecondary)
    }
}

@Composable
private fun ReadyCard(m: VideoMeta, s: AppSettings) {
    val env = LocalEnv.current
    val c = env.c
    val cc = Lumen.colors
    val collections by c.library.collections.collectAsState(initial = emptyList())
    val guess = remember(m) { com.lumen.player.data.scan.FilenameParser.parse(m.title + ".mp3") }
    var title by remember(m) { mutableStateOf(guess.title) }
    var artist by remember(m) { mutableStateOf(guess.artist.ifBlank { m.channel.removeSuffix(" - Topic").removeSuffix("VEVO") }) }
    var owned by remember(m) { mutableStateOf(false) }
    var format by remember { mutableStateOf(s.importFormat) }
    var toNas by remember { mutableStateOf(s.importToNas && c.nas.config.isConfigured) }
    var folder by remember { mutableStateOf(s.importFolder) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Glass(Modifier.fillMaxWidth()) {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(cc.track), contentAlignment = Alignment.Center) {
                    if (m.thumbnail != null) AsyncImage(m.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) { Badge(fmtTime(m.durationSec * 1000L), cc.textPrimary, cc.glassFillStrong) }
                }
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LText(m.title, Lumen.type.titleM, maxLines = 2)
                    LText(listOf(m.channel, fmtTime(m.durationSec * 1000L)).filter { it.isNotBlank() }.joinToString(" · "), Lumen.type.bodyS, cc.textSecondary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LField("Title", title, { title = it }, Modifier.weight(1f))
            LField("Artist", artist, { artist = it }, Modifier.weight(1f))
        }
        Glass(Modifier.fillMaxWidth(), radius = Lumen.shapes.tile, shadow = false, stroke = if (!owned) cc.warning.copy(alpha = .6f) else null) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LIcon(LIcons.shield, 22.dp, cc.accentText)
                Column(Modifier.weight(1f)) {
                    LText("I own this audio or have permission", Lumen.type.label)
                    LText("Required before anything is downloaded.", Lumen.type.caption, cc.textSecondary)
                }
                LToggle(owned, { owned = it })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LText("Audio format", Lumen.type.overline, cc.textTertiary, upper = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioFormat.entries.forEach { f -> LChip(f.title, f == format, { format = f; c.appScope.launch { c.settings.setImportFormat(f) } }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LText("Save to", Lumen.type.overline, cc.textTertiary, upper = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LChip("This phone", !toNas, { toNas = false }, icon = LIcons.phone)
                if (c.nas.config.isConfigured) {
                    (listOf("Imported") + collections.map { it.path }.filter { it.isNotEmpty() }).distinct().take(10).forEach { p ->
                        LChip(p.substringAfterLast('/'), toNas && folder == p, { toNas = true; folder = p }, icon = LIcons.folder)
                    }
                }
            }
            if (toNas) LText("NAS › ${env.c.nas.config.rootPath.ifBlank { "" }} › ${folder.replace("/", " › ")}", Lumen.type.caption, cc.textSecondary)
        }
        LButton(
            if (toNas) "Import to NAS" else "Save to this phone",
            {
                c.appScope.launch { c.settings.setImportFolder(folder); c.settings.setImportToNas(toNas) }
                c.importer.start(m, format, toNas, folder, title, artist)
            },
            Modifier.fillMaxWidth(), icon = LIcons.upload, enabled = owned && title.isNotBlank(),
        )
    }
}

@Composable
private fun RunningCard(s: ImportState.Running) {
    val env = LocalEnv.current
    val cc = Lumen.colors
    Glass(Modifier.fillMaxWidth(), strong = true) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.meta.thumbnail != null) AsyncImage(s.meta.thumbnail, null, Modifier.size(56.dp).clip(shape(12.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    LText(when (s.step) { ImportStep.DOWNLOAD -> "Downloading audio…"; ImportStep.CONVERT -> "Converting…"; ImportStep.SAVE -> "Saving…"; ImportStep.TAG -> "Tagging & matching…"; else -> "Working…" }, Lumen.type.label, upper = true)
                    LText(s.meta.title, Lumen.type.caption, cc.textSecondary, maxLines = 1)
                }
                LText("${(s.progress * 100).toInt()}%", Lumen.type.titleM, cc.accentText)
            }
            LinearBar(s.progress)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ImportStep.entries.forEach { step ->
                    val state = when { step.ordinal < s.step.ordinal -> 2; step == s.step -> 1; else -> 0 }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LIcon(if (state == 2) LIcons.check else if (state == 1) LIcons.refresh else LIcons.clock, 12.dp, if (state > 0) cc.accentText else cc.textTertiary)
                        LText(step.label, Lumen.type.caption, if (state > 0) cc.textPrimary else cc.textTertiary)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDots(Modifier.weight(1f))
                LText("Cancel", Lumen.type.label, cc.danger, Modifier.pressable { env.c.importer.cancel() }, upper = true)
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun unusedCtx() = LocalContext.current.let { Intent() }
@Suppress("unused")
private val unusedMods = listOf(Modifier.fillMaxHeight(), Modifier.width(1.dp))
