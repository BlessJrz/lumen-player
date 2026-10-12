package com.lumen.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import com.lumen.player.BuildConfig
import com.lumen.player.data.download.StorageInfo
import com.lumen.player.data.nas.NasStatus
import com.lumen.player.data.settings.AppSettings
import com.lumen.player.data.settings.Design
import com.lumen.player.data.settings.ThemeMode
import com.lumen.player.ui.components.AmbientBackground
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.Divider
import com.lumen.player.ui.components.GeneratedArt
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.Group
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LField
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LSlider
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LToggle
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.MenuItem
import com.lumen.player.ui.components.RowControl
import com.lumen.player.ui.components.Segmented
import com.lumen.player.ui.components.SettingRow
import com.lumen.player.ui.components.StatusPill
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.components.shape
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.LocalLumen
import com.lumen.player.ui.theme.Lumen
import com.lumen.player.ui.theme.buildTheme
import kotlinx.coroutines.launch

private enum class SetCat(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Appearance("Appearance", LIcons.palette), Library("Library & NAS", LIcons.server), Playback("Playback", LIcons.headphones),
    Downloads("Downloads", LIcons.download), Metadata("Metadata & artwork", LIcons.tag), Import("YouTube import", LIcons.youtube), About("About Lumen", LIcons.music)
}

@Composable
fun SettingsScreen() {
    val env = LocalEnv.current
    val s by env.c.settings.settings.collectAsState(initial = AppSettings())
    if (env.wide) {
        var cat by rememberSaveable { mutableStateOf(SetCat.Appearance) }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.width(240.dp)) {
                ScreenList(gap = 6.dp) {
                    item { LText("Settings", Lumen.type.titleL, Modifier.padding(bottom = 12.dp)) }
                    item { ProfileCard() }
                    item { Spacer(Modifier.height(8.dp)) }
                    SetCat.entries.forEach { ct ->
                        item(ct.name) {
                            val on = ct == cat
                            Row(Modifier.fillMaxWidth().height(46.dp).clip(shape(Lumen.shapes.tile)).then(if (on) Modifier.background(Lumen.colors.accentSoft) else Modifier).pressable { cat = ct }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LIcon(ct.icon, 18.dp, if (on) Lumen.colors.accentText else Lumen.colors.textSecondary)
                                LText(ct.label, Lumen.type.label, if (on) Lumen.colors.accentText else Lumen.colors.textPrimary, maxLines = 1, upper = true)
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                ScreenList(gap = 20.dp) {
                    item { Column { LText(cat.label, Lumen.type.overline, Lumen.colors.accentText, upper = true); LText(if (cat == SetCat.Appearance) "Look & feel" else cat.label, Lumen.type.titleL) } }
                    item { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Section(cat, s) } }
                }
            }
        }
    } else ScreenList(gap = 22.dp) {
        item { TitleBar("Settings") }
        item { ProfileCard() }
        SetCat.entries.forEach { ct -> item(ct.name) { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { Section(ct, s) } } }
    }
}

@Composable
private fun ProfileCard() {
    val env = LocalEnv.current
    val st by env.c.nas.status.collectAsState()
    val (pill, col) = nasPill()
    val count by env.c.library.trackCount.collectAsState(initial = 0)
    Glass(Modifier.fillMaxWidth().pressable(scaleTo = .98f) { env.nav.navigate(Routes.NAS) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Lumen.colors.accent), contentAlignment = Alignment.Center) { LText("K", Lumen.type.titleL, Lumen.colors.onAccent) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LText("Khalil", Lumen.type.titleM)
                LText("$count songs${if (st is NasStatus.Connected) " · connected" else ""}", Lumen.type.caption, Lumen.colors.textSecondary)
                StatusPill(pill, col)
            }
        }
    }
}

@Composable
private fun Section(cat: SetCat, s: AppSettings) {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val scope = c.appScope
    when (cat) {
        SetCat.Appearance -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LText("Design", Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Design.entries.forEach { d -> DesignPreview(d, s, d == s.design, Modifier.weight(1f)) { scope.launch { c.settings.setDesign(d) } } }
                }
                LText(s.design.blurb, Lumen.type.bodyS, Lumen.colors.textSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LText("Theme", Lumen.type.overline, Lumen.colors.textTertiary, upper = true)
                Segmented(listOf("System", "Dark", "Light"), ThemeMode.entries.indexOf(s.themeMode), { scope.launch { c.settings.setThemeMode(ThemeMode.entries[it]) } })
            }
            Group("Surfaces") {
                SettingRow(LIcons.sparkle, "Artwork colors", "Tint backgrounds from the playing album", RowControl.Toggle, s.artworkTint, onToggle = { scope.launch { c.settings.setArtworkTint(it) } })
                Divider()
                SettingRow(LIcons.image, "Reduce transparency", "Solid surfaces, higher contrast", RowControl.Toggle, s.reduceTransparency, onToggle = { scope.launch { c.settings.setReduceTransparency(it) } })
                Divider()
                SettingRow(LIcons.sun, "Ambient motion", "Slowly drifting light behind the glass", RowControl.Toggle, s.ambientMotion, onToggle = { scope.launch { c.settings.setAmbientMotion(it) } })
            }
        }
        SetCat.Library -> Group("Library & NAS") {
            val cfg = c.nas.config
            SettingRow(LIcons.server, "NAS connection", if (cfg.isConfigured) "${cfg.name.ifBlank { cfg.lanHost }} · ${cfg.protocol.title} · /${cfg.share}/${cfg.rootPath}" else "Not set up", onClick = { env.nav.navigate(Routes.NAS) })
            Divider()
            SettingRow(LIcons.refresh, "Scan now", "Find added and removed songs", RowControl.Chevron, onClick = { c.refreshLibrary(); o.toast("Scanning your NAS…", ToastKind.Info) })
            Divider()
            SettingRow(LIcons.clock, "Auto-scan", "How often to check for changes", RowControl.Value, value = if (s.scanIntervalMin >= 60) "${s.scanIntervalMin / 60} h" else "${s.scanIntervalMin} min", onClick = {
                o.menu("Auto-scan every", null, null, listOf(15, 30, 60, 180, 720).map { m ->
                    MenuItem(null, if (m >= 60) "${m / 60} hour${if (m > 60) "s" else ""}" else "$m minutes", checked = m == s.scanIntervalMin) { scope.launch { c.settings.setScanInterval(m); c.schedulePeriodicScan(m) } }
                })
            })
            Divider()
            SettingRow(LIcons.globe, "Remote access", if (cfg.remoteHost.isNotBlank()) "Uses ${cfg.remoteHost} away from home" else "Add your NAS's Tailscale address", RowControl.Chevron, onClick = { env.nav.navigate(Routes.NAS) })
        }
        SetCat.Playback -> Group("Playback") {
            SettingRow(LIcons.eq, "Equalizer", if (s.eqEnabled) "On · custom" else "Off", RowControl.Chevron, onClick = { env.nav.navigate(Routes.EQ) })
            Divider()
            SettingRow(LIcons.speed, "Playback speed", null, RowControl.Value, value = "${s.playbackSpeed}×", onClick = {
                o.menu("Playback speed", null, null, listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { v -> MenuItem(null, "$v×", checked = v == s.playbackSpeed) { scope.launch { c.settings.setSpeed(v) }; c.player.setSpeed(v) } })
            })
            Divider()
            SettingRow(LIcons.repeat, "Gapless playback", "For live albums and mixes", RowControl.Toggle, s.gapless, onToggle = { scope.launch { c.settings.setGapless(it) } })
        }
        SetCat.Downloads -> {
            var st by remember { mutableStateOf<StorageInfo?>(null) }
            LaunchedEffect(Unit) { st = c.downloads.storage() }
            Group("Downloads") {
                SettingRow(LIcons.wifi, "Wi-Fi only", "Never use mobile data for downloads", RowControl.Toggle, s.wifiOnlyDownloads, onToggle = { scope.launch { c.settings.setWifiOnly(it) } })
                Divider()
                SettingRow(LIcons.drive, "Storage limit", st?.let { "${fmtBytes(it.offlineBytes)} used · ${fmtBytes(it.freeBytes)} free" }, RowControl.Value, value = if (s.storageLimitMb >= 1024) "${s.storageLimitMb / 1024} GB" else "${s.storageLimitMb} MB", onClick = {
                    o.menu("Storage limit", null, null, listOf(1024, 2048, 4096, 8192, 16384, 32768).map { mb -> MenuItem(null, "${mb / 1024} GB", checked = mb == s.storageLimitMb) { scope.launch { c.settings.setStorageLimit(mb) } } })
                })
                Divider()
                SettingRow(LIcons.download, "Manage downloads", null, onClick = { env.nav.navigate(Routes.DOWNLOADS) })
            }
        }
        SetCat.Metadata -> {
            Group("Metadata & artwork") {
                SettingRow(LIcons.tag, "Fix missing info automatically", "Tags first, then MusicBrainz (only confident matches)", RowControl.Toggle, s.autoMetadata, onToggle = { scope.launch { c.settings.setAutoMetadata(it) } })
                Divider()
                SettingRow(LIcons.image, "Find missing artwork", "Cover Art Archive, then exact Apple Music matches", RowControl.Toggle, s.autoArtwork, onToggle = { scope.launch { c.settings.setAutoArtwork(it) } })
                Divider()
                SettingRow(LIcons.lyrics, "Fetch lyrics", "Synced lyrics from LRCLIB when available", RowControl.Toggle, s.autoLyrics, onToggle = { scope.launch { c.settings.setAutoLyrics(it) } })
                Divider()
                SettingRow(LIcons.edit, "Write edits into MP3 files", "Saves your changes to the file on the NAS", RowControl.Toggle, s.writeTagsToFile, onToggle = { scope.launch { c.settings.setWriteTags(it) } })
                Divider()
                SettingRow(LIcons.sparkle, "Match remaining songs now", null, RowControl.Chevron, onClick = { scope.launch { c.metadata.enrichPending(500) }; o.toast("Matching metadata in the background", ToastKind.Info) })
            }
            var key by remember { mutableStateOf(s.googleApiKey) }
            var cx by remember { mutableStateOf(s.googleCx) }
            Group("Google image search (optional)") {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LText("Adds Google Images as a source in the artwork picker. Needs a free Programmable Search Engine key.", Lumen.type.bodyS, Lumen.colors.textSecondary)
                    LField("API key", key, { key = it }, password = true)
                    LField("Search engine ID (cx)", cx, { cx = it })
                    LButton("Save", { scope.launch { c.settings.setGoogle(key, cx); o.toast("Saved") } }, Modifier.fillMaxWidth(), kind = BtnKind.Glass)
                }
            }
        }
        SetCat.Import -> Group("YouTube import") {
            SettingRow(LIcons.music, "Default format", null, RowControl.Value, value = s.importFormat.name, onClick = {
                o.menu("Default format", null, null, com.lumen.player.data.settings.AudioFormat.entries.map { f -> MenuItem(null, f.title, checked = f == s.importFormat) { scope.launch { c.settings.setImportFormat(f) } } })
            })
            Divider()
            SettingRow(LIcons.folder, "Save imports to", if (s.importToNas) "NAS › ${s.importFolder}" else "This phone", RowControl.Chevron, onClick = { env.nav.navigate(Routes.IMPORT) })
            Divider()
            run {
                val yt by c.ytAccount.signedIn.collectAsState()
                SettingRow(LIcons.youtube, "YouTube account", if (yt) "Signed in · tap to see your likes" else "Sign in to import from your likes", RowControl.Chevron,
                    onClick = { env.nav.navigate(if (yt) Routes.YT_LIKES else Routes.YT_LOGIN) })
            }
            Divider()
            SettingRow(LIcons.refresh, "Update import engine", "Get the latest yt-dlp — fixes most failed imports", RowControl.Chevron, onClick = { c.importer.updateEngine { o.toast(it, ToastKind.Info) } })
        }
        SetCat.About -> Group(null) {
            SettingRow(LIcons.music, "Lumen ${BuildConfig.VERSION_NAME}", "Personal NAS player · Liquid Glass", RowControl.None)
            Divider()
            SettingRow(LIcons.shield, "Privacy", "Credentials stay encrypted on this phone. Music streams directly from your NAS.", RowControl.None)
        }
    }
}

/** Live mini-preview of a design rendered with that design's real tokens. */
@Composable
private fun DesignPreview(d: Design, s: AppSettings, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dark = Lumen.colors.isDark
    val t = remember(d, dark) { buildTheme(d, dark, false, false) }
    Column(modifier.pressable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalLumen provides t) {
            Box(Modifier.fillMaxWidth().height(150.dp).clip(shape(16.dp)).border(if (selected) 3.dp else 1.dp, if (selected) t.colors.accent else t.colors.glassStroke, shape(16.dp))) {
                AmbientBackground(null)
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(shape(t.shapes.tile * .6f))) { GeneratedArt(listOf(9, 10, 0)[d.ordinal] * 7, Modifier.fillMaxSize()) }
                    Glass(Modifier.fillMaxWidth().height(22.dp), radius = t.shapes.control, strong = true, shadow = false) {}
                    Spacer(Modifier.weight(1f))
                    Glass(Modifier.fillMaxWidth().height(28.dp), radius = t.shapes.control, strong = true, shadow = false) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            repeat(4) { i -> Box(Modifier.size(if (i == 0) 14.dp else 8.dp).clip(CircleShape).background(if (i == 0) t.colors.accent else t.colors.textTertiary)) }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (selected) LIcon(LIcons.check, 14.dp, Lumen.colors.accentText)
            LText(d.title.removeSuffix(" Glass"), Lumen.type.label, if (selected) Lumen.colors.accentText else Lumen.colors.textSecondary, maxLines = 1, upper = true)
        }
    }
}

/** Five-band equalizer with presets. */
@Composable
fun EqualizerScreen() {
    val env = LocalEnv.current
    val c = env.c
    val s by c.settings.settings.collectAsState(initial = AppSettings())
    val freqs = remember { c.playerHolder.eqBandFrequencies() }
    val presets = listOf("Flat" to listOf(0, 0, 0, 0, 0), "Bass boost" to listOf(6, 4, 0, 0, 1), "Warm" to listOf(3, 2, 0, -1, -2), "Vocal" to listOf(-2, 0, 3, 3, 1), "Bright" to listOf(-1, 0, 1, 3, 5), "Party" to listOf(5, 3, -1, 2, 4))
    ScreenList(gap = 18.dp) {
        item { BackBar("Equalizer", "Playback") { LToggle(s.eqEnabled, { v -> c.appScope.launch { c.settings.setEqEnabled(v) } }) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.take(3).forEach { (n, b) -> LChip(n, s.eqBands == b, { c.appScope.launch { c.settings.setEqBands(b); c.settings.setEqEnabled(true) } }) }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { presets.drop(3).forEach { (n, b) -> LChip(n, s.eqBands == b, { c.appScope.launch { c.settings.setEqBands(b); c.settings.setEqEnabled(true) } }) } } }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    s.eqBands.forEachIndexed { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val f = freqs.getOrElse(i) { 0 }
                            LText(if (f >= 1000) "${f / 1000}k" else "$f", Lumen.type.caption, Lumen.colors.textSecondary, Modifier.width(36.dp), upper = true)
                            Box(Modifier.weight(1f)) {
                                LSlider((g + 12) / 24f, {}, onDone = { v ->
                                    val nb = s.eqBands.toMutableList(); nb[i] = (v * 24 - 12).toInt(); c.appScope.launch { c.settings.setEqBands(nb); c.settings.setEqEnabled(true) }
                                })
                            }
                            LText("${if (g > 0) "+" else ""}$g dB", Lumen.type.caption, Lumen.colors.accentText, Modifier.width(48.dp))
                        }
                    }
                }
            }
        }
        item { LText("Uses the phone's audio effects on Lumen's playback only. Turn off for bit-perfect output.", Lumen.type.caption, Lumen.colors.textTertiary) }
    }
}

@Suppress("unused")
private fun ColumnScope.unused() = Unit
