@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lumen.player.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.MetaSource
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.meta.ArtCandidate
import com.lumen.player.data.meta.MetaCandidate
import com.lumen.player.data.settings.AppSettings
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.ArtworkUrl
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
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.PulseDots
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.launch

@Composable
fun MetadataEditorScreen(trackId: Long) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val ctx = LocalContext.current
    val original by c.library.track(trackId).collectAsState(initial = null)
    val settings by c.settings.settings.collectAsState(initial = AppSettings())
    val t0 = original ?: return
    var t by remember(t0.id) { mutableStateOf(t0) }
    LaunchedEffect(t0.artPath) { t = t.copy(artPath = t0.artPath) }
    var candidates by remember { mutableStateOf<List<MetaCandidate>?>(null) }
    var artSource by remember { mutableStateOf("Cover Art Archive") }
    var arts by remember { mutableStateOf<List<ArtCandidate>?>(null) }
    var pickedArt by remember { mutableStateOf<ArtCandidate?>(null) }
    var wholeAlbum by remember { mutableStateOf(true) }
    var writeFile by remember(settings.writeTagsToFile) { mutableStateOf(settings.writeTagsToFile) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(t0.id) { candidates = runCatching { c.metadata.candidates(t0) }.getOrDefault(emptyList()) }
    LaunchedEffect(artSource, t.album, t.artist) { arts = null; pickedArt = null; arts = runCatching { c.metadata.artworkCandidates(t, artSource) }.getOrDefault(emptyList()) }
    val pickLocal = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) c.appScope.launch {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            if (c.metadata.applyArtworkBytes(t0, bytes, wholeAlbum)) o.toast("Artwork updated") else o.toast("That file isn’t an image", ToastKind.Error)
        }
    }
    val badge: @Composable (Int) -> Pair<String, androidx.compose.ui.graphics.Color> = { src ->
        when (src) {
            MetaSource.TAG -> "Tag" to Lumen.colors.textSecondary
            MetaSource.MUSICBRAINZ -> "MusicBrainz" to Lumen.colors.accentText
            MetaSource.USER -> "Edited" to Lumen.colors.warning
            else -> "Filename" to Lumen.colors.textTertiary
        }
    }
    val save = {
        saving = true
        c.appScope.launch {
            val r = c.metadata.saveEdits(t, writeFile)
            saving = false
            c.player.reloadCurrent()
            if (r.isSuccess) { o.toast(if (writeFile) "Saved & written to the file" else "Saved"); env.nav.popBackStack() }
            else o.toast("Saved in Lumen, but writing the file failed: ${r.exceptionOrNull()?.message}", ToastKind.Error)
        }
    }

    val form: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val (tb, tc) = badge(t.titleSource)
            LField("Title", t.title, { t = t.copy(title = it, titleSource = MetaSource.USER) }, badge = tb, badgeColor = tc)
            LField("Artist", t.artist, { t = t.copy(artist = it, titleSource = MetaSource.USER) }, badge = tb, badgeColor = tc)
            val (ab, ac) = badge(t.albumSource)
            LField("Album", t.album, { t = t.copy(album = it, albumSource = MetaSource.USER) }, badge = ab, badgeColor = ac)
            LField("Album artist", t.albumArtist, { t = t.copy(albumArtist = it) })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LField("Year", t.year?.toString() ?: "", { t = t.copy(year = it.filter(Char::isDigit).take(4).toIntOrNull()) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
                LField("Track #", t.trackNo?.toString() ?: "", { t = t.copy(trackNo = it.filter(Char::isDigit).take(3).toIntOrNull()) }, Modifier.weight(1f), keyboard = KeyboardType.Number)
            }
            LField("Genre", t.genre, { t = t.copy(genre = it) }, placeholder = "Hip-Hop, R&B, Soundtrack…")
            LField("Lyrics", t.lyrics ?: "", { t = t.copy(lyrics = it.ifBlank { null }, lyricsSynced = it.contains(Regex("""\[\d{1,2}:\d{2}"""))) }, singleLine = false, placeholder = "Paste plain or [mm:ss] synced lyrics")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Find lyrics", { c.appScope.launch { val l = c.metadata.lyrics(t); if (l != null) { t = t.copy(lyrics = l.first, lyricsSynced = l.second); o.toast(if (l.second) "Synced lyrics found" else "Lyrics found") } else o.toast("No lyrics found", ToastKind.Error) } }, Modifier.weight(1f), icon = LIcons.lyrics, kind = BtnKind.Glass)
            }
            Glass(Modifier.fillMaxWidth(), shadow = false) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LIcon(LIcons.edit, 18.dp, Lumen.colors.accentText)
                    Column(Modifier.weight(1f)) {
                        LText("Write tags to file", Lumen.type.body)
                        LText(if (t.fileName.endsWith(".mp3", true)) "Saves into ${t.fileName} on your NAS" else "Only MP3 files can be rewritten — edits stay in Lumen", Lumen.type.bodyS, Lumen.colors.textSecondary)
                    }
                    LToggle(writeFile && t.fileName.endsWith(".mp3", true), { writeFile = it })
                }
            }
            LText("File: ${t.path}", Lumen.type.caption, Lumen.colors.textTertiary)
        }
    }

    val side: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(t0, Modifier.size(188.dp).shadow(20.dp, shape(Lumen.shapes.card)), radius = Lumen.shapes.card)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LButton("From files", { pickLocal.launch("image/*") }, icon = LIcons.image, kind = BtnKind.Glass)
                }
            }
            // Suggested matches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LText("Suggested matches", Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
                when {
                    candidates == null -> PulseDots()
                    candidates!!.isEmpty() -> LText("No confident online matches. Edit the fields by hand, or fix the title/artist and reopen.", Lumen.type.bodyS, Lumen.colors.textSecondary)
                    else -> candidates!!.take(4).forEachIndexed { i, m ->
                        val pct = (m.confidence * 100).toInt()
                        Glass(Modifier.fillMaxWidth().pressable(scaleTo = .98f) { t = c.metadata.applyCandidate(t, m); o.toast("Applied — review and Save") }, shadow = false, strong = i == 0, stroke = if (i == 0 && pct >= 80) Lumen.colors.accent else null, strokeWidth = if (i == 0) 1.5.dp else 1.dp) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (m.artUrl != null) ArtworkUrl(m.artUrl.replace("1000x1000bb", "200x200bb").replace("front-500", "front-250"), Modifier.size(48.dp), 10.dp)
                                Column(Modifier.weight(1f)) {
                                    LText("${m.title} — ${m.artist}", Lumen.type.label, maxLines = 1)
                                    LText(listOfNotNull(m.album.ifBlank { null }, m.year?.toString(), m.trackNo?.let { "Track $it" }, m.source).joinToString(" · "), Lumen.type.caption, Lumen.colors.textSecondary, maxLines = 1)
                                }
                                Badge("$pct%", if (pct >= 80) Lumen.colors.success else Lumen.colors.textSecondary)
                            }
                        }
                    }
                }
            }
            // Artwork picker
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { LText("Artwork", Lumen.type.titleM, Modifier.weight(1f)); LText("Pick, then confirm", Lumen.type.caption, Lumen.colors.textTertiary) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Cover Art Archive", "Apple Music", "Deezer", "Google Images").forEach { s -> LChip(s, s == artSource, { artSource = s }) }
                    }
                    if (artSource == "Google Images" && settings.googleApiKey.isBlank()) LText("Add a Google search key in Settings → Metadata to enable Google Images.", Lumen.type.bodyS, Lumen.colors.textSecondary)
                    when {
                        arts == null -> PulseDots()
                        arts!!.isEmpty() -> LText("Nothing found here — try another source.", Lumen.type.bodyS, Lumen.colors.textSecondary)
                        else -> com.lumen.player.ui.screens.GridOf(arts!!.take(9), 3, 10.dp) { a, w ->
                            val sel = a == pickedArt
                            Column(Modifier.width(w).pressable { pickedArt = a }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(w).then(if (sel) Modifier.border(3.dp, Lumen.colors.accent, shape(minOf(Lumen.shapes.tile, 16.dp))) else Modifier)) {
                                    ArtworkUrl(a.thumb, Modifier.fillMaxSize(), minOf(Lumen.shapes.tile, 16.dp))
                                    if (sel) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { Badge("✓", Lumen.colors.onAccent, Lumen.colors.accent) }
                                }
                                LText(a.label, Lumen.type.caption, if (sel) Lumen.colors.accentText else Lumen.colors.textTertiary, maxLines = 1)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LText("Apply to the whole album", Lumen.type.body, Modifier.weight(1f))
                        LToggle(wholeAlbum, { wholeAlbum = it })
                    }
                    LButton("Use selected artwork", {
                        val p = pickedArt ?: return@LButton
                        c.appScope.launch { if (c.metadata.applyArtwork(t0, p.url, wholeAlbum)) o.toast("Artwork updated") else o.toast("Couldn’t download that image", ToastKind.Error) }
                    }, Modifier.fillMaxWidth(), icon = LIcons.check, enabled = pickedArt != null)
                    LText("Nothing changes until you confirm. Low-confidence matches are never applied automatically.", Lumen.type.caption, Lumen.colors.textTertiary)
                }
            }
        }
    }

    ScreenList(gap = 18.dp) {
        item("bar") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBtn(LIcons.close, { env.nav.popBackStack() }, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    LText(t0.folder.replace("/", " › ").ifBlank { "Music" }, Lumen.type.overline, Lumen.colors.textTertiary, maxLines = 1, upper = true)
                    LText("Edit info & artwork", Lumen.type.titleM)
                }
                LButton(if (saving) "Saving…" else "Save", { save() }, icon = LIcons.check, enabled = !saving, height = 40.dp)
            }
        }
        if (env.wide) item("wide") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1f)) { form() }
                Box(Modifier.weight(1f)) { side() }
            }
        } else {
            item("side") { side() }
            item("form") { form() }
        }
    }
}

@Suppress("unused")
private fun unused(t: TrackEntity) = t
