@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.lumen.player.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.lumen.player.AppContainer
import com.lumen.player.ui.icons.LIcons

enum class Tab(val label: String, val icon: ImageVector, val route: String) {
    Home("Home", LIcons.home, Routes.HOME),
    Library("Library", LIcons.library, Routes.LIBRARY),
    Search("Search", LIcons.search, Routes.SEARCH),
    Playlists("Playlists", LIcons.playlist, Routes.PLAYLISTS),
    Downloads("Downloads", LIcons.download, Routes.DOWNLOADS),
    Import("Import", LIcons.youtube, Routes.IMPORT),
    Settings("Settings", LIcons.settings, Routes.SETTINGS);

    companion object {
        /** Cover screen bar: one-handed, five slots. Playlists live inside Library; Settings via avatar. */
        val cover = listOf(Home, Library, Search, Import, Downloads)
        val rail = listOf(Home, Library, Search, Playlists, Downloads, Import, Settings)
        fun forRoute(route: String?): Tab? = when {
            route == null -> null
            route.startsWith("folder") || route.startsWith("artist") || route.startsWith("album") || route.startsWith("favorites") || route.startsWith("history") -> Library
            route.startsWith("playlist") -> Playlists
            route.startsWith("nas") || route.startsWith("eq") -> Settings
            else -> entries.firstOrNull { route.startsWith(it.route) }
        }
    }
}

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val PLAYLISTS = "playlists"
    const val DOWNLOADS = "downloads"
    const val IMPORT = "import"
    const val YT_LOGIN = "yt_login"
    const val YT_LIKES = "yt_likes"
    const val YT_SWITCH = "yt_switch"
    const val SETTINGS = "settings"
    const val NAS = "nas"
    const val EQ = "eq"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val ALL_SONGS = "all_songs"
    const val RECENT = "recent"
    fun folder(path: String) = "folder?path=" + Uri.encode(path)
    fun playlist(id: Long) = "playlist/$id"
    fun edit(trackId: Long) = "edit/$trackId"
    fun artist(name: String) = "artist?name=" + Uri.encode(name)
    fun album(name: String, artist: String) = "album?name=" + Uri.encode(name) + "&artist=" + Uri.encode(artist)
}

/** Everything a screen needs: data, navigation, playback, overlays. */
class Env(
    val c: AppContainer,
    val nav: NavController,
    val wide: Boolean,
    val openPlayer: () -> Unit,
    val openQueue: () -> Unit,
)

val LocalEnv = staticCompositionLocalOf<Env> { error("no env") }

class SharedArt(val scope: SharedTransitionScope, val vis: AnimatedVisibilityScope)
val LocalSharedArt = compositionLocalOf<SharedArt?> { null }

/** Morphs the album art between the mini-player and the full player. */
@Composable
fun Modifier.sharedArt(): Modifier {
    val s = LocalSharedArt.current ?: return this
    return with(s.scope) { this@sharedArt.sharedElement(rememberSharedContentState("now-playing-art"), s.vis) }
}
