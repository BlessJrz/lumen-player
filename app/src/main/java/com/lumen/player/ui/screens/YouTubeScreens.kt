package com.lumen.player.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.lumen.player.data.settings.AppSettings
import com.lumen.player.data.youtube.VideoMeta
import com.lumen.player.data.youtube.YouTubeImporter.LikeSource
import com.lumen.player.ui.components.BtnKind
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.LButton
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LinearBar
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.OrbLoader
import com.lumen.player.ui.components.PulseDots
import com.lumen.player.ui.components.Segmented
import com.lumen.player.ui.components.ToastKind
import com.lumen.player.ui.components.ArtworkUrl
import com.lumen.player.ui.components.fmtTime
import com.lumen.player.ui.components.pressable
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fm.youtube.com%2F"
private const val SWITCHER_URL = "https://www.youtube.com/channel_switcher?next=%2Ffeed%2Fyou"
private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

/**
 * Two steps: the person signs in to their own Google account, then picks which YouTube channel (brand account)
 * Lumen should use on YouTube's own channel switcher. Lumen only keeps the resulting YouTube cookies.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(pickOnly: Boolean = false) {
    val env = LocalEnv.current
    val o = LocalOverlays.current
    val account = env.c.ytAccount
    var loading by remember { mutableStateOf(true) }
    var picking by remember { mutableStateOf(pickOnly) }
    var done by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun finish() {
        if (done) return
        if (account.captureFromWebView()) {
            done = true
            o.toast("YouTube channel saved", ToastKind.Success)
            env.nav.popBackStack()
            if (!pickOnly) env.nav.navigate(Routes.YT_LIKES)
        } else o.toast("Not signed in yet", ToastKind.Error)
    }
    fun openSwitcher(v: WebView) {
        picking = true
        v.settings.userAgentString = DESKTOP_UA
        v.loadUrl(SWITCHER_URL)
    }

    Column(Modifier.fillMaxSize().padding(top = top + 8.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            BackBar(if (picking) "Choose your channel" else "Sign in to YouTube", overline = if (picking) "Step 2 of 2" else "Step 1 of 2") {
                if (loading) PulseDots()
                if (picking) LButton("Done", { finish() }, height = 40.dp)
            }
        }
        LText(
            if (picking) "Tap the channel whose likes you want to import. You can switch later in Settings → YouTube account."
            else "Sign in with your Google account — your password goes straight to Google. Next you’ll pick which channel to use.",
            Lumen.type.bodyS, Lumen.colors.textSecondary, Modifier.padding(horizontal = 16.dp),
        )
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(Lumen.shapes.card))) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    web = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // Present as a normal mobile browser: Google refuses sign-in from pages flagged as embedded.
                    settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("""Version/[\d.]+ """), "")
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            val u = android.net.Uri.parse(url ?: "")
                            val onYouTube = u.host.orEmpty().endsWith("youtube.com")
                            if (done || view == null || !onYouTube || !account.webSessionLooksSignedIn()) return
                            when {
                                !picking -> openSwitcher(view)                       // signed in → choose channel
                                !u.path.orEmpty().contains("channel_switcher") -> finish() // a channel was picked
                            }
                        }
                    }
                    if (pickOnly) openSwitcher(this) else loadUrl(SIGN_IN_URL)
                }
            }, modifier = Modifier.fillMaxSize(), onRelease = { it.stopLoading(); it.destroy() })
        }
    }
}

/** The signed-in person's liked videos, ready to import as MP3s. */
@Composable
fun YouTubeLikesScreen() {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val signedIn by c.ytAccount.signedIn.collectAsState()
    val imported by c.ytAccount.importedIds.collectAsState()
    val batch by c.importer.batch.collectAsState()
    val settings by c.settings.settings.collectAsState(initial = AppSettings())
    var source by remember { mutableStateOf(0) }
    val cookieStamp = remember(signedIn) { c.ytAccount.cookieFile?.lastModified() ?: 0L }
    val items = remember(source, cookieStamp) { mutableStateListOf<VideoMeta>() }
    val selected = remember { mutableStateListOf<String>() }
    var loading by remember(source) { mutableStateOf(false) }
    var end by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    val pageSize = 50
    val toNas = settings.importToNas && c.nas.config.isConfigured

    fun loadMore() {
        if (loading || end) return
        loading = true
        val src = LikeSource.entries[source]
        scope.launch {
            runCatching { c.importer.likes(src, items.size, pageSize) }
                .onSuccess { page -> val known = items.map { it.id }.toSet(); items += page.filter { it.id !in known }; if (page.size < pageSize) end = true }
                .onFailure { error = likesError(it) }
            loading = false
        }
    }
    LaunchedEffect(source, signedIn) { if (signedIn && items.isEmpty()) loadMore() }

    ScreenList(gap = 12.dp) {
        item("bar") {
            BackBar("Your likes", overline = "YouTube") {
                if (signedIn) com.lumen.player.ui.components.IconBtn(LIcons.user, { env.nav.navigate(Routes.YT_SWITCH) }, size = 40.dp)
                if (signedIn) LButton("Sign out", {
                    c.ytAccount.signOut(); o.toast("Signed out of YouTube", ToastKind.Info); env.nav.popBackStack()
                }, kind = BtnKind.Ghost, height = 40.dp)
            }
        }
        if (!signedIn) {
            item("signin") { YouTubeSignInCard { env.nav.navigate(Routes.YT_LOGIN) } }
            return@ScreenList
        }
        item("src") { Segmented(LikeSource.entries.map { it.label }, source, { source = it; selected.clear() }) }
        item("dest") {
            Row(Modifier.fillMaxWidth().pressable(scaleTo = .98f) { pickDestination(env, o, toNas, settings.importFolder) { n, f -> scope.launch { c.settings.setImportToNas(n); c.settings.setImportFolder(f) } } },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LIcon(if (toNas) LIcons.folder else LIcons.phone, 16.dp, Lumen.colors.accentText)
                LText("Default: ${destLabel(toNas, settings.importFolder)} · ${settings.importFormat.name}", Lumen.type.caption, Lumen.colors.textSecondary, Modifier.weight(1f), maxLines = 1)
                LText("Change", Lumen.type.label, Lumen.colors.accentText, upper = true)
            }
        }
        if (batch.active || batch.total > 0) item("batch") {
            Glass(Modifier.fillMaxWidth(), strong = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LText(if (batch.active) "Importing ${batch.done + batch.failed + 1} of ${batch.total}" else "Imported ${batch.done} of ${batch.total}${if (batch.failed > 0) " · ${batch.failed} failed" else ""}",
                            Lumen.type.label, Modifier.weight(1f), upper = true)
                        if (batch.active) LButton("Stop", { c.importer.cancel() }, kind = BtnKind.Ghost, height = 36.dp)
                        else LButton("Dismiss", { c.importer.clearBatch() }, kind = BtnKind.Ghost, height = 36.dp)
                    }
                    if (batch.active) {
                        LinearBar((batch.done + batch.failed).toFloat() / batch.total.coerceAtLeast(1))
                        LText(batch.current.orEmpty(), Lumen.type.bodyS, Lumen.colors.textSecondary, maxLines = 1)
                    }
                    batch.errors.takeLast(3).forEach { (title, why) ->
                        Column {
                            LText(title, Lumen.type.bodyS, Lumen.colors.textPrimary, maxLines = 1)
                            LText(why, Lumen.type.caption, Lumen.colors.danger, maxLines = 3)
                        }
                    }
                }
            }
        }
        item("actions") {
            val notImported = items.filter { it.id !in imported }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LText(if (selected.isEmpty()) "${items.size}${if (end) "" else "+"} liked" else "${selected.size} selected", Lumen.type.titleM, Modifier.weight(1f))
                LButton(if (selected.size == notImported.size && selected.isNotEmpty()) "Clear" else "Select new", {
                    if (selected.size == notImported.size && selected.isNotEmpty()) selected.clear()
                    else { selected.clear(); selected += notImported.map { it.id } }
                }, kind = BtnKind.Glass, height = 40.dp, enabled = notImported.isNotEmpty())
                LButton("Import${if (selected.isNotEmpty()) " ${selected.size}" else ""}", {
                    val pick = items.filter { it.id in selected }
                    pickDestination(env, o, toNas, settings.importFolder, title = "Save ${pick.size} song${if (pick.size == 1) "" else "s"} to") { n, f ->
                        c.importer.importMany(pick, settings.importFormat, n, f)
                        selected.clear()
                        o.toast("Importing ${pick.size} song${if (pick.size == 1) "" else "s"} to ${destLabel(n, f)}", ToastKind.Info)
                    }
                }, icon = LIcons.download, height = 40.dp, enabled = selected.isNotEmpty() && !batch.active)
            }
        }
        items.forEach { m ->
            item("v${m.id}") {
                LikeRow(m, m.id in imported, m.id in selected,
                    onToggle = { if (m.id in imported) Unit else if (m.id in selected) selected.remove(m.id) else selected.add(m.id) },
                    onImport = {
                        if (batch.active) o.toast("Wait for the current import to finish", ToastKind.Info)
                        else pickDestination(env, o, toNas, settings.importFolder, title = "Save “${m.title}” to") { n, f ->
                            c.importer.importMany(listOf(m), settings.importFormat, n, f); o.toast("Importing to ${destLabel(n, f)}", ToastKind.Info)
                        }
                    })
            }
        }
        if (error != null) item("err") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LText(error!!, Lumen.type.bodyS, Lumen.colors.danger)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LButton("Try again", { error = null; loadMore() }, kind = BtnKind.Glass)
                    LButton("Sign in again", { env.nav.navigate(Routes.YT_LOGIN) }, kind = BtnKind.Ghost)
                }
            }
        } else if (!end) item("more") {
            LaunchedEffect(items.size) { loadMore() }
            Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                if (items.isEmpty()) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OrbLoader(size = 64.dp)
                    LText("Loading your likes…", Lumen.type.label, Lumen.colors.textSecondary, upper = true)
                } else PulseDots()
            }
        } else if (items.isEmpty()) item("empty") {
            LText("No liked videos here yet.", Lumen.type.body, Lumen.colors.textSecondary)
        }
    }
}

@Composable
private fun LikeRow(m: VideoMeta, imported: Boolean, selected: Boolean, onToggle: () -> Unit, onImport: () -> Unit) {
    val cc = Lumen.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).pressable(scaleTo = .98f, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selected) cc.accent else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(2.dp, cc.textSecondary, CircleShape)), contentAlignment = Alignment.Center) {
            if (selected) LIcon(LIcons.check, 16.dp, cc.onAccent)
        }
        ArtworkUrl(m.thumbnail.orEmpty(), Modifier.width(88.dp).size(88.dp, 50.dp), radius = 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LText(m.title, Lumen.type.body, cc.textPrimary, maxLines = 2)
            LText(listOf(m.channel, if (m.durationSec > 0) fmtTime(m.durationSec * 1000L) else "").filter { it.isNotBlank() }.joinToString(" · "),
                Lumen.type.bodyS, cc.textSecondary, maxLines = 1)
        }
        if (imported) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LIcon(LIcons.check, 16.dp, cc.success); LText("Saved", Lumen.type.caption, cc.success)
        } else Box(Modifier.size(40.dp).pressable(onClick = onImport), contentAlignment = Alignment.Center) { LIcon(LIcons.download, 20.dp, cc.accentText) }
    }
}

/** Shown on the Import tab and the likes screen when not signed in. */
@Composable
fun YouTubeSignInCard(onSignIn: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), strong = true) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Lumen.colors.accentSoft), contentAlignment = Alignment.Center) { LIcon(LIcons.heart, 22.dp, Lumen.colors.accentText) }
                Column(Modifier.weight(1f)) {
                    LText("Import from your likes", Lumen.type.titleM)
                    LText("Sign in to YouTube to see your liked videos and YouTube Music likes, then import them in one tap.", Lumen.type.bodyS, Lumen.colors.textSecondary)
                }
            }
            LButton("Sign in to YouTube", onSignIn, Modifier.fillMaxWidth(), icon = LIcons.youtube)
        }
    }
}

private fun likesError(t: Throwable): String {
    val m = t.message ?: t.javaClass.simpleName
    return when {
        m.contains("Sign in", true) || m.contains("cookies", true) || m.contains("401") || m.contains("private", true) ->
            "YouTube didn’t accept the saved sign-in. Sign in again."
        m.contains("does not exist", true) -> "That likes list isn’t available for this account."
        else -> "Couldn’t load your likes: " + (m.lineSequence().lastOrNull { it.isNotBlank() }?.take(160) ?: m)
    }
}

fun destLabel(toNas: Boolean, folder: String) = if (!toNas) "This phone" else "NAS › " + folder.ifBlank { "Music" }.replace("/", " › ")

/** Lets the person choose where an import goes: this phone, or any folder in their NAS music library. */
fun pickDestination(env: com.lumen.player.ui.nav.Env, o: com.lumen.player.ui.components.Overlays, toNas: Boolean, folder: String,
                    title: String = "Save imports to", onPick: (toNas: Boolean, folder: String) -> Unit) {
    val c = env.c
    c.appScope.launch {
        val folders = runCatching { c.db.folders().all().first() }.getOrDefault(emptyList())
            .map { it.path }.filter { it.isNotBlank() }
        val nasOk = c.nas.config.isConfigured
        val options = (listOf("Imported") + folders).distinct()
        val items = buildList {
            add(com.lumen.player.ui.components.MenuItem(LIcons.phone, "This phone (offline)", checked = !toNas) { onPick(false, folder) })
            if (nasOk) options.forEach { p ->
                val depth = p.count { it == '/' }
                add(com.lumen.player.ui.components.MenuItem(LIcons.folder, "  ".repeat(depth) + p.substringAfterLast('/') + if (p == "Imported" && p !in folders) " (new)" else "",
                    checked = toNas && folder == p, trailing = p.substringBeforeLast('/', "").replace("/", " › ").ifBlank { null }) { onPick(true, p) })
            }
            if (nasOk) add(com.lumen.player.ui.components.MenuItem(LIcons.plus, "New folder…") {
                o.prompt(com.lumen.player.ui.components.PromptSpec("New NAS folder", "", "e.g. Playlists/Road Trip", "Use folder") { name ->
                    val clean = name.trim().trim('/'); if (clean.isNotEmpty()) onPick(true, clean)
                })
            })
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { o.menu(title, "Imported songs are saved here and added to your library", null, items) }
    }
}
