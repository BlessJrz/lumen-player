package com.lumen.player.ui.screens

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumen.player.data.nas.friendlyNasError
import com.lumen.player.data.nas.NasConfig
import com.lumen.player.data.nas.NasEntry
import com.lumen.player.data.nas.NasProtocol
import com.lumen.player.data.nas.joinPath
import com.lumen.player.data.nas.parentOf
import com.lumen.player.data.scan.ScanState
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.FolderRow
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LChip
import com.lumen.player.ui.components.LField
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LinearBar
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.OrbLoader
import com.lumen.player.ui.components.Overlays
import com.lumen.player.ui.components.PulseDots
import com.lumen.player.ui.components.Segmented
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.Env
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.launch

private data class Found(val name: String, val host: String)

@Composable
fun NasSetupScreen() {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(c.nas.config.let { if (it.isConfigured) it else it.copy(rootPath = "Music") }) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    val found = remember { mutableStateListOf<Found>() }
    val scan by c.scanner.state.collectAsState()
    val status by c.nas.status.collectAsState()

    // Look for NAS devices advertising SMB on the local network.
    DisposableEffect(Unit) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, e: Int) {}
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = i.host?.hostAddress ?: return
                        if (found.none { it.host == host }) found.add(Found(i.serviceName, host))
                    }
                })
            }
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        runCatching { nsd.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
        onDispose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    ScreenList(gap = 18.dp) {
        item("bar") { BackBar("Connect your NAS", "NAS connection") }
        item("orb") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Glass(Modifier.size(104.dp), radius = 52.dp, strong = true) { LIcon(LIcons.server, 44.dp, Lumen.colors.accentText, Modifier.align(Alignment.Center)) }
                LText(if (found.isEmpty()) "Looking for servers on your Wi-Fi…" else "Found ${found.size} server${if (found.size == 1) "" else "s"} on your Wi-Fi", Lumen.type.titleM, align = TextAlign.Center)
                LText("Lumen streams straight from your NAS over SMB or WebDAV. Your UGREEN NAS supports both.", Lumen.type.bodyS, Lumen.colors.textSecondary, align = TextAlign.Center)
            }
        }
        if (found.isNotEmpty()) item("found") {
            Glass(Modifier.fillMaxWidth(), shadow = false) {
                Column(Modifier.padding(4.dp)) {
                    found.forEach { f ->
                        val sel = cfg.lanHost == f.host
                        Row(Modifier.fillMaxWidth().height(64.dp).pressable { cfg = cfg.copy(lanHost = f.host, name = cfg.name.ifBlank { f.name }, protocol = NasProtocol.SMB) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LIcon(LIcons.drive, 22.dp, if (sel) Lumen.colors.accentText else Lumen.colors.textSecondary)
                            Column(Modifier.weight(1f)) { LText(f.name, Lumen.type.body, maxLines = 1); LText("${f.host} · SMB", Lumen.type.caption, Lumen.colors.textSecondary) }
                            if (sel) LIcon(LIcons.check, 20.dp, Lumen.colors.accentText)
                        }
                    }
                }
            }
        }
        item("proto") { Segmented(listOf("SMB", "WebDAV"), cfg.protocol.ordinal, { cfg = cfg.copy(protocol = NasProtocol.entries[it]) }) }
        item("name") { LField("Name", cfg.name, { cfg = cfg.copy(name = it) }, icon = LIcons.tag, placeholder = "e.g. Home NAS") }
        item("lan") { LField("Home network address", cfg.lanHost, { cfg = cfg.copy(lanHost = it.trim()) }, icon = LIcons.wifi, placeholder = "e.g. 192.168.1.x (optional)", keyboard = KeyboardType.Uri) }
        item("remote") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LField("Away-from-home address (Tailscale)", cfg.remoteHost, { cfg = cfg.copy(remoteHost = it.trim()) }, icon = LIcons.globe, placeholder = "e.g. 100.x.y.z (optional)", keyboard = KeyboardType.Uri)
                LText("Install Tailscale on your phone and sign in with the same account as the NAS’s Tailscale app. Lumen switches to this address automatically when home Wi-Fi isn’t reachable.", Lumen.type.caption, Lumen.colors.textTertiary)
            }
        }
        item("cred") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LField("Username", cfg.username, { cfg = cfg.copy(username = it) }, Modifier.weight(1f), icon = LIcons.user, placeholder = "your UGOS login")
                LField("Password", cfg.password, { cfg = cfg.copy(password = it) }, Modifier.weight(1f), icon = LIcons.lock, password = true)
            }
        }
        if (cfg.protocol == NasProtocol.WEBDAV) item("port") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                LField("Port", if (cfg.port > 0) cfg.port.toString() else "", { cfg = cfg.copy(port = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) }, Modifier.weight(1f), placeholder = if (cfg.useHttps) "5006" else "5005", keyboard = KeyboardType.Number)
                LChip("HTTPS", cfg.useHttps, { cfg = cfg.copy(useHttps = !cfg.useHttps) }, icon = LIcons.lock)
            }
        }
        item("share") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                LField(if (cfg.protocol == NasProtocol.SMB) "Shared folder" else "Base path", cfg.share, { cfg = cfg.copy(share = it) }, Modifier.weight(1f), icon = LIcons.drive, placeholder = if (cfg.protocol == NasProtocol.SMB) "auto-detect" else "optional")
                if (cfg.protocol == NasProtocol.SMB) LButton("List", {
                    c.appScope.launch {
                        c.nas.listShares(cfg).onSuccess { shares ->
                            o.menu("Shared folders", cfg.lanHost, null, shares.map { s -> com.lumen.player.ui.components.MenuItem(LIcons.drive, s, checked = s == cfg.share) { cfg = cfg.copy(share = s) } })
                        }.onFailure { o.toast("Couldn’t list shares: ${friendlyNasError(it)}", ToastKind.Error) }
                    }
                }, kind = BtnKind.Glass)
            }
        }
        item("root") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                LField("Music folder", cfg.rootPath, { cfg = cfg.copy(rootPath = it.trim('/')) }, Modifier.weight(1f), icon = LIcons.folder, placeholder = "e.g. Music")
                LButton("Browse", { browseSheet(env, o, cfg) { cfg = cfg.copy(rootPath = it) } }, kind = BtnKind.Glass)
            }
        }
        item("hint") {
            Glass(Modifier.fillMaxWidth(), shadow = false, fill = Lumen.colors.accentSoft) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LIcon(LIcons.sparkle, 18.dp, Lumen.colors.accentText)
                    LText("On your UGREEN NAS the music lives in your personal folder → Music (with Emotional, Hip Hop and Party inside Music/Playlists). Turn on SMB in Control Panel → File Service if it isn’t already.", Lumen.type.bodyS, Lumen.colors.textPrimary)
                }
            }
        }
        if (result != null) item("result") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LIcon(if (ok == true) LIcons.check else LIcons.alert, 18.dp, if (ok == true) Lumen.colors.success else Lumen.colors.danger)
                LText(result!!, Lumen.type.bodyS, if (ok == true) Lumen.colors.success else Lumen.colors.danger)
            }
        }
        item("actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LButton("Test", {
                    testing = true; result = null
                    c.appScope.launch {
                        val r = resolveAndTest(c.nas, cfg) { cfg = it }; testing = false; ok = r.isSuccess
                        result = r.fold({ "Connected via $it — music folder found in “${cfg.share}”" }, { "Couldn’t connect: ${friendlyNasError(it)}" })
                    }
                }, Modifier.weight(1f), icon = LIcons.wifi, kind = BtnKind.Glass, enabled = cfg.isConfigured && !testing)
                LButton("Connect & scan", {
                    testing = true; result = null
                    c.appScope.launch {
                        val r = resolveAndTest(c.nas, cfg) { cfg = it }
                        testing = false; ok = r.isSuccess
                        if (r.isSuccess) {
                            c.nas.save(cfg)
                            c.settings.setOnboarded(true)
                            c.refreshLibrary()
                            c.schedulePeriodicScan(30)
                            o.toast("Connected — building your library", ToastKind.Success)
                        } else result = "Couldn’t connect: ${friendlyNasError(r.exceptionOrNull())}"
                    }
                }, Modifier.weight(1.4f), icon = LIcons.refresh, enabled = cfg.isConfigured && !testing)
            }
        }
        if (testing) item("testing") { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PulseDots() } }
        if (scan is ScanState.Listing || scan is ScanState.ReadingTags) item("scan") {
            Glass(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OrbLoader(size = 52.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LText(scanLabel(scan), Lumen.type.label, upper = true)
                        val s = scan
                        if (s is ScanState.ReadingTags) LinearBar(if (s.total > 0) s.done.toFloat() / s.total else 0f)
                    }
                }
            }
        }
        if (scan is ScanState.Finished && c.nas.config.isConfigured) item("done") {
            LButton("Open your library", { env.nav.navigate(Routes.HOME) { popUpTo(0) } }, Modifier.fillMaxWidth(), icon = LIcons.music)
        }
        item("privacy") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LIcon(LIcons.lock, 14.dp, Lumen.colors.textTertiary)
                LText("Credentials are encrypted with Android Keystore. Music streams directly between your phone and your NAS. Status: ${status.javaClass.simpleName}", Lumen.type.caption, Lumen.colors.textTertiary)
            }
        }
    }
}

/** Folder browser sheet for choosing the music root. */
private fun browseSheet(env: Env, o: Overlays, cfg: NasConfig, onPick: (String) -> Unit) {
    o.sheet("Choose music folder", cfg.share.ifBlank { cfg.lanHost }) { close ->
        var path by remember { mutableStateOf(cfg.rootPath.substringBeforeLast('/', "")) }
        var entries by remember { mutableStateOf<List<NasEntry>?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        androidx.compose.runtime.LaunchedEffect(path) {
            entries = null; err = null
            env.c.nas.browse(cfg, path).onSuccess { entries = it }.onFailure { err = it.message }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (path.isNotEmpty()) LChip("Up", false, { path = parentOf(path) }, icon = LIcons.chevronUp)
            LText("/" + path, Lumen.type.label, Lumen.colors.textSecondary, Modifier.weight(1f), maxLines = 1)
        }
        when {
            err != null -> LText(err!!, Lumen.type.bodyS, Lumen.colors.danger)
            entries == null -> PulseDots()
            entries!!.isEmpty() -> LText("No subfolders here.", Lumen.type.bodyS, Lumen.colors.textSecondary)
            else -> entries!!.forEach { e -> FolderRow(e.name, "Open", { path = joinPath(path, e.name) }) }
        }
        LButton("Use “/${path.ifBlank { "(share root)" }}”", { onPick(path); close() }, Modifier.fillMaxWidth(), icon = LIcons.check)
    }
}

/** Validates the form, auto-detects the shared folder if blank, then tests the connection. */
private suspend fun resolveAndTest(nas: com.lumen.player.data.nas.NasManager, cfg0: NasConfig, update: (NasConfig) -> Unit): Result<String> {
    var cfg = cfg0
    if (!cfg.isConfigured) return Result.failure(IllegalArgumentException("Enter the NAS address (home or Tailscale)"))
    if (cfg.protocol == NasProtocol.SMB) {
        if (cfg.username.isBlank()) return Result.failure(IllegalArgumentException("Enter your NAS username (the one you sign in to UGOS with)"))
        if (cfg.password.isEmpty()) return Result.failure(IllegalArgumentException("Enter your NAS password"))
        if (cfg.share.isBlank()) {
            val s = nas.findShare(cfg).getOrElse { return Result.failure(it) }
            cfg = cfg.copy(share = s); update(cfg)
        }
    }
    return nas.test(cfg)
}
