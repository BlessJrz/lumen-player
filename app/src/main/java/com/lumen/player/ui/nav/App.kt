@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.lumen.player.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumen.player.AppContainer
import com.lumen.player.data.settings.AppSettings
import com.lumen.player.ui.components.AmbientBackground
import com.lumen.player.ui.components.Avatar
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.OverlayHost
import com.lumen.player.ui.components.Overlays
import com.lumen.player.ui.components.ProvideHaze
import com.lumen.player.ui.components.rememberArtColors
import com.lumen.player.ui.components.trackTaps
import com.lumen.player.ui.screens.AlbumScreen
import com.lumen.player.ui.screens.ArtistScreen
import com.lumen.player.ui.screens.DownloadsScreen
import com.lumen.player.ui.screens.EqualizerScreen
import com.lumen.player.ui.screens.FavoritesScreen
import com.lumen.player.ui.screens.FolderScreen
import com.lumen.player.ui.screens.HistoryScreen
import com.lumen.player.ui.screens.HomeScreen
import com.lumen.player.ui.screens.ImportScreen
import com.lumen.player.ui.screens.LibraryScreen
import com.lumen.player.ui.screens.MetadataEditorScreen
import com.lumen.player.ui.screens.MiniPlayer
import com.lumen.player.ui.screens.NasSetupScreen
import com.lumen.player.ui.screens.NavRail
import com.lumen.player.ui.screens.PlayerBar
import com.lumen.player.ui.screens.PlayerScreen
import com.lumen.player.ui.screens.PlaylistsScreen
import com.lumen.player.ui.screens.QueueList
import com.lumen.player.ui.screens.SearchScreen
import com.lumen.player.ui.screens.SettingsScreen
import com.lumen.player.ui.screens.TabBar
import com.lumen.player.ui.screens.openPlayerClose
import com.lumen.player.ui.screens.outputSheet
import com.lumen.player.ui.theme.Lumen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

/** Hands the root a pending deep link (e.g. a YouTube URL shared to Lumen). */
class PendingIntentAction { var openImport by mutableStateOf<String?>(null); var openPlayer by mutableStateOf(false); var route by mutableStateOf<String?>(null) }

@Composable
fun LumenRoot(c: AppContainer, pending: PendingIntentAction) {
    val nav = rememberNavController()
    val overlays = remember { Overlays() }
    val haze = remember { HazeState() }
    val cfg = LocalConfiguration.current
    // Fold7: cover screen ≈ 412dp wide; inner screen ≈ 880dp. Anything ≥ 600dp gets the expanded layout.
    // The activity handles configuration changes itself, so folding/unfolding keeps playback and the back stack.
    val wide = cfg.screenWidthDp >= 600
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val ps by c.player.state.collectAsState()
    val settings by c.settings.settings.collectAsState(initial = AppSettings())
    val artColors = rememberArtColors(ps.current)
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val tab = Tab.forRoute(route?.substringBefore('?')?.substringBefore('/'))

    val env = remember(nav, wide) {
        Env(c, nav, wide, openPlayer = { playerOpen = true }, openQueue = {
            overlays.sheet("Queue", "Long-press ≡ to reorder") { close -> QueueList(close) }
        }).also { it.openPlayerClose = { playerOpen = false } }
    }

    LaunchedEffect(Unit) { c.player.connect(); if (c.nas.config.isConfigured) c.refreshLibrary() }
    LaunchedEffect(pending.openImport) {
        pending.openImport?.let { url -> nav.navigate(Routes.IMPORT) { launchSingleTop = true }; c.importer.fetch(url); pending.openImport = null }
    }
    LaunchedEffect(pending.openPlayer) { if (pending.openPlayer) { playerOpen = true; pending.openPlayer = false } }
    LaunchedEffect(pending.route) { pending.route?.let { r -> playerOpen = false; runCatching { nav.navigate(r) { launchSingleTop = true } }; pending.route = null } }
    BackHandler(enabled = playerOpen && !overlays.anyOpen) { playerOpen = false }

    fun go(t: Tab) {
        nav.navigate(t.route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalEnv provides env, LocalOverlays provides overlays) {
        SharedTransitionLayout(Modifier.fillMaxSize().trackTaps(overlays)) {
            ProvideHaze(haze) { Box(Modifier.fillMaxSize()) {
                // Everything the floating glass blurs.
                Box(Modifier.fillMaxSize().haze(haze)) {
                    AmbientBackground(if (settings.artworkTint) artColors else null)
                    Row(Modifier.fillMaxSize()) {
                        if (wide) Spacer(Modifier.width(120.dp))
                        Box(Modifier.weight(1f).padding(end = if (wide) 16.dp else 0.dp)) {
                            NavHost(nav, startDestination = if (c.nas.config.isConfigured || com.lumen.player.data.library.DemoData.isDemo(c.context)) Routes.HOME else Routes.NAS,
                                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(260), initialScale = .985f) },
                                exitTransition = { fadeOut(tween(160)) },
                                popEnterTransition = { fadeIn(tween(220)) },
                                popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(200), targetScale = .985f) },
                            ) {
                                composable(Routes.HOME) { HomeScreen() }
                                composable(Routes.LIBRARY) { LibraryScreen() }
                                composable(Routes.SEARCH) { SearchScreen() }
                                composable(Routes.PLAYLISTS) { PlaylistsScreen() }
                                composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType })) { PlaylistsScreen(it.arguments?.getLong("id")) }
                                composable(Routes.DOWNLOADS) { DownloadsScreen() }
                                composable(Routes.IMPORT) { ImportScreen() }
                                composable(Routes.SETTINGS) { SettingsScreen() }
                                composable(Routes.NAS) { NasSetupScreen() }
                                composable(Routes.EQ) { EqualizerScreen() }
                                composable(Routes.FAVORITES) { FavoritesScreen() }
                                composable(Routes.HISTORY) { HistoryScreen() }
                                composable("folder?path={path}", listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })) { FolderScreen(it.arguments?.getString("path") ?: "") }
                                composable("edit/{id}", listOf(navArgument("id") { type = NavType.LongType })) { MetadataEditorScreen(it.arguments?.getLong("id") ?: 0) }
                                composable("artist?name={name}", listOf(navArgument("name") { type = NavType.StringType; defaultValue = "" })) { ArtistScreen(it.arguments?.getString("name") ?: "") }
                                composable("album?name={name}&artist={artist}", listOf(navArgument("name") { type = NavType.StringType; defaultValue = "" }, navArgument("artist") { type = NavType.StringType; defaultValue = "" })) {
                                    AlbumScreen(it.arguments?.getString("name") ?: "", it.arguments?.getString("artist") ?: "")
                                }
                            }
                        }
                    }
                }

                // ---------- Floating chrome ----------
                if (wide) {
                    Box(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding().padding(start = 14.dp, top = 8.dp, bottom = 14.dp)) {
                        NavRail(Tab.rail, tab, ::go, avatar = { Avatar(ps.current, 40.dp) { go(Tab.Settings) } })
                    }
                    AnimatedVisibility(ps.hasMedia && !playerOpen, Modifier.align(Alignment.BottomEnd), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                        CompositionLocalProvider(LocalSharedArt provides SharedArt(this@SharedTransitionLayout, this)) {
                            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 120.dp, end = 16.dp, bottom = 16.dp)) {
                                PlayerBar(ps, { playerOpen = true }, { c.player.toggle() }, { c.player.next() }, { c.player.previous() }, { c.player.setShuffle(!ps.shuffle) }, { c.player.cycleRepeat() }, { c.player.seekTo(it) },
                                    onQueue = { env.openQueue() }, onLyrics = { playerOpen = true }, onOutput = { outputSheet(env, overlays) })
                            }
                        }
                    }
                } else {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val fluid = Lumen.design.name == "FLUID"
                        if (!fluid) AnimatedVisibility(ps.hasMedia && !playerOpen, enter = slideInVertically(spring(dampingRatio = .8f)) { it } + fadeIn(), exit = fadeOut()) {
                            CompositionLocalProvider(LocalSharedArt provides SharedArt(this@SharedTransitionLayout, this)) {
                                MiniPlayer(ps, { playerOpen = true }, { c.player.toggle() }, { c.player.next() }, { c.player.previous() }, {
                                    ps.current?.let { t -> c.appScope.launch { c.library.toggleFavorite(t); c.player.reloadCurrent() } }
                                })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (fluid) {
                                TabBar(Tab.cover, tab, ::go)
                                Spacer(Modifier.weight(1f))
                                AnimatedVisibility(ps.hasMedia && !playerOpen, enter = scaleIn(spring(dampingRatio = .6f)) + fadeIn(), exit = fadeOut()) {
                                    CompositionLocalProvider(LocalSharedArt provides SharedArt(this@SharedTransitionLayout, this)) {
                                        MiniPlayer(ps, { playerOpen = true }, { c.player.toggle() }, { c.player.next() }, { c.player.previous() }, {}, Modifier.size(76.dp))
                                    }
                                }
                            } else TabBar(Tab.cover, tab, ::go)
                        }
                    }
                }

                // ---------- Full player (art morphs from the mini-player) ----------
                AnimatedVisibility(
                    playerOpen,
                    enter = slideInVertically(spring(dampingRatio = .86f, stiffness = 320f)) { it / 3 } + fadeIn(tween(220)),
                    exit = slideOutVertically(tween(260)) { it / 3 } + fadeOut(tween(200)),
                ) {
                    CompositionLocalProvider(LocalSharedArt provides SharedArt(this@SharedTransitionLayout, this)) {
                        PlayerScreen { playerOpen = false }
                    }
                }

                OverlayHost(overlays, wide)
            } }
        }
    }
}
