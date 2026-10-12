package com.lumen.player.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Custom line icon set (same geometry as the Figma icon components). */
object LIcons {
    private fun icon(name: String, d: String, filled: Boolean): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        val nodes = PathParser().parsePathString(d).toNodes()
        if (filled) addPath(nodes, fill = SolidColor(Color.Black))
        else addPath(nodes, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

    val home: ImageVector by lazy { icon("home", "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z", false) }
    val library: ImageVector by lazy { icon("library", "M4.5 4v16M9 4v16M13.2 4.6l4.6 15.2", false) }
    val search: ImageVector by lazy { icon("search", "M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14zM20 20l-4-4", false) }
    val download: ImageVector by lazy { icon("download", "M12 3v12M7 10l5 5 5-5M4 20h16", false) }
    val upload: ImageVector by lazy { icon("upload", "M12 20V8M7 13l5-5 5 5M4 4h16", false) }
    val settings: ImageVector by lazy { icon("settings", "M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM12 2.5v3M12 18.5v3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M2.5 12h3M18.5 12h3M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1", false) }
    val play: ImageVector by lazy { icon("play", "M7 4.8v14.4a1 1 0 0 0 1.53.85l11.4-7.2a1 1 0 0 0 0-1.7L8.53 3.95A1 1 0 0 0 7 4.8z", true) }
    val pause: ImageVector by lazy { icon("pause", "M7.1 4h1.4a1.6 1.6 0 0 1 1.6 1.6v12.8a1.6 1.6 0 0 1-1.6 1.6H7.1a1.6 1.6 0 0 1-1.6-1.6V5.6A1.6 1.6 0 0 1 7.1 4zM15.5 4h1.4a1.6 1.6 0 0 1 1.6 1.6v12.8a1.6 1.6 0 0 1-1.6 1.6h-1.4a1.6 1.6 0 0 1-1.6-1.6V5.6A1.6 1.6 0 0 1 15.5 4z", true) }
    val next: ImageVector by lazy { icon("next", "M4.5 5.2v13.6a1 1 0 0 0 1.55.83l9.45-6.43a1.4 1.4 0 0 0 0-2.4L6.05 4.37A1 1 0 0 0 4.5 5.2zM17.5 5h2v14h-2z", true) }
    val prev: ImageVector by lazy { icon("prev", "M19.5 5.2v13.6a1 1 0 0 1-1.55.83L8.5 13.2a1.4 1.4 0 0 1 0-2.4l9.45-6.43a1 1 0 0 1 1.55.83zM4.5 5h2v14h-2z", true) }
    val shuffle: ImageVector by lazy { icon("shuffle", "M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5", false) }
    val repeat: ImageVector by lazy { icon("repeat", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3", false) }
    val repeatOne: ImageVector by lazy { icon("repeatOne", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3M11.5 10.5l1.2-1v5", false) }
    val heart: ImageVector by lazy { icon("heart", "M12 20.5s-7.5-4.6-9.3-9.2C1.4 7.9 3.7 4.5 7.2 4.5c2 0 3.6 1 4.8 2.7 1.2-1.7 2.8-2.7 4.8-2.7 3.5 0 5.8 3.4 4.5 6.8-1.8 4.6-9.3 9.2-9.3 9.2z", false) }
    val heartFilled: ImageVector by lazy { icon("heartFilled", "M12 20.5s-7.5-4.6-9.3-9.2C1.4 7.9 3.7 4.5 7.2 4.5c2 0 3.6 1 4.8 2.7 1.2-1.7 2.8-2.7 4.8-2.7 3.5 0 5.8 3.4 4.5 6.8-1.8 4.6-9.3 9.2-9.3 9.2z", true) }
    val more: ImageVector by lazy { icon("more", "M3.2 12a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 1 0-3.6 0zM10.2 12a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 1 0-3.6 0zM17.2 12a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 1 0-3.6 0z", true) }
    val moreVert: ImageVector by lazy { icon("moreVert", "M10.2 5a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 1 0-3.6 0zM10.2 12a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 1 0-3.6 0zM10.2 19a1.8 1.8 0 1 0 3.6 0 1.8 1.8 0 1 0-3.6 0z", true) }
    val chevronLeft: ImageVector by lazy { icon("chevronLeft", "M15 18 9 12l6-6", false) }
    val chevronRight: ImageVector by lazy { icon("chevronRight", "M9 18l6-6-6-6", false) }
    val chevronDown: ImageVector by lazy { icon("chevronDown", "M6 9l6 6 6-6", false) }
    val chevronUp: ImageVector by lazy { icon("chevronUp", "M6 15l6-6 6 6", false) }
    val plus: ImageVector by lazy { icon("plus", "M12 5v14M5 12h14", false) }
    val folder: ImageVector by lazy { icon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", false) }
    val queue: ImageVector by lazy { icon("queue", "M3 6h12M3 12h12M3 18h7M18 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0zM18 18V8l3-1", false) }
    val lyrics: ImageVector by lazy { icon("lyrics", "M4 5h16v11H9l-5 4zM8 9h8M8 12h5", false) }
    val mic: ImageVector by lazy { icon("mic", "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5 11a7 7 0 0 0 14 0M12 18v3", false) }
    val speaker: ImageVector by lazy { icon("speaker", "M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM12 11a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7zM11.5 6.5h1", false) }
    val volume: ImageVector by lazy { icon("volume", "M11 5 6 9H3v6h3l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13", false) }
    val moon: ImageVector by lazy { icon("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z", false) }
    val eq: ImageVector by lazy { icon("eq", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M2 14h4M10 8h4M18 16h4", false) }
    val speed: ImageVector by lazy { icon("speed", "M12 14l4-4M3.5 18a9.5 9.5 0 1 1 17 0", false) }
    val link: ImageVector by lazy { icon("link", "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7", false) }
    val close: ImageVector by lazy { icon("close", "M18 6 6 18M6 6l12 12", false) }
    val check: ImageVector by lazy { icon("check", "M20 6 9 17l-5-5", false) }
    val server: ImageVector by lazy { icon("server", "M4 4h16v6H4zM4 14h16v6H4zM7 7h2M7 17h2", false) }
    val cloud: ImageVector by lazy { icon("cloud", "M7 18a5 5 0 1 1 1-9.9A6 6 0 0 1 19 10a4 4 0 0 1-1 8z", false) }
    val trash: ImageVector by lazy { icon("trash", "M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14", false) }
    val edit: ImageVector by lazy { icon("edit", "M4 20h4L19 9l-4-4L4 16zM14 6l4 4", false) }
    val image: ImageVector by lazy { icon("image", "M3 5h18v14H3zM21 15l-5-5-11 9M8.5 8.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z", false) }
    val refresh: ImageVector by lazy { icon("refresh", "M20 11a8 8 0 0 0-14.9-4M4 3v4h4M4 13a8 8 0 0 0 14.9 4M20 21v-4h-4", false) }
    val sun: ImageVector by lazy { icon("sun", "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4", false) }
    val music: ImageVector by lazy { icon("music", "M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM21 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", false) }
    val user: ImageVector by lazy { icon("user", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0", false) }
    val clock: ImageVector by lazy { icon("clock", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM12 7v5l3 2", false) }
    val sparkle: ImageVector by lazy { icon("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z", false) }
    val filter: ImageVector by lazy { icon("filter", "M3 5h18l-7 8v6l-4 2v-8z", false) }
    val sort: ImageVector by lazy { icon("sort", "M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4", false) }
    val lock: ImageVector by lazy { icon("lock", "M5 11h14v10H5zM8 11V7a4 4 0 0 1 8 0v4", false) }
    val youtube: ImageVector by lazy { icon("youtube", "M3 7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4zM10 8.8v6.4l5.5-3.2z", false) }
    val drive: ImageVector by lazy { icon("drive", "M3 13h18M3 13l3-8h12l3 8v6H3zM7 16h1M11 16h1", false) }
    val globe: ImageVector by lazy { icon("globe", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18", false) }
    val bluetooth: ImageVector by lazy { icon("bluetooth", "M7 7l10 10-5 5V2l5 5L7 17", false) }
    val wifi: ImageVector by lazy { icon("wifi", "M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M2 9a15 15 0 0 1 20 0M11 19.5h2", false) }
    val offline: ImageVector by lazy { icon("offline", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM8 12l3 3 5-6", false) }
    val fold: ImageVector by lazy { icon("fold", "M3 4h8v16H3zM13 4h8v16h-8z", false) }
    val alert: ImageVector by lazy { icon("alert", "M12 3 2 20h20zM12 10v4M12 17v.6", false) }
    val palette: ImageVector by lazy { icon("palette", "M12 3a9 9 0 0 0 0 18c1.7 0 2-1.3 1.3-2.3-.7-1 0-2.7 1.7-2.7H18a3 3 0 0 0 3-3c0-5.5-4-10-9-10zM7.5 11h1M10 7h1M15 7h1", false) }
    val tag: ImageVector by lazy { icon("tag", "M3 3h8l10 10-8 8L3 11zM7 7.5h1", false) }
    val headphones: ImageVector by lazy { icon("headphones", "M3 18v-6a9 9 0 0 1 18 0v6M3 18a2 2 0 0 0 2 2h1v-6H5a2 2 0 0 0-2 2zM21 18a2 2 0 0 1-2 2h-1v-6h1a2 2 0 0 1 2 2z", false) }
    val phone: ImageVector by lazy { icon("phone", "M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM11 18h2", false) }
    val bars: ImageVector by lazy { icon("bars", "M4.4 9h.8a1.4 1.4 0 0 1 1.4 1.4v9.2a1.4 1.4 0 0 1-1.4 1.4h-.8A1.4 1.4 0 0 1 3 19.6v-9.2A1.4 1.4 0 0 1 4.4 9zM11.6 3h.8a1.4 1.4 0 0 1 1.4 1.4v15.2a1.4 1.4 0 0 1-1.4 1.4h-.8a1.4 1.4 0 0 1-1.4-1.4V4.4A1.4 1.4 0 0 1 11.6 3zM18.8 12h.8a1.4 1.4 0 0 1 1.4 1.4v6.2a1.4 1.4 0 0 1-1.4 1.4h-.8a1.4 1.4 0 0 1-1.4-1.4v-6.2a1.4 1.4 0 0 1 1.4-1.4z", true) }
    val grid: ImageVector by lazy { icon("grid", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z", false) }
    val list: ImageVector by lazy { icon("list", "M8 6h12M8 12h12M8 18h12M4 6h1M4 12h1M4 18h1", false) }
    val drag: ImageVector by lazy { icon("drag", "M5 9h14M5 15h14", false) }
    val history: ImageVector by lazy { icon("history", "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2", false) }
    val playlist: ImageVector by lazy { icon("playlist", "M3 6h13M3 11h13M3 16h8M18 13v8M14 17h8", false) }
    val shield: ImageVector by lazy { icon("shield", "M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z", false) }
    val cast: ImageVector by lazy { icon("cast", "M3 8V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6M3 12a8 8 0 0 1 8 8M3 16a4 4 0 0 1 4 4M3 20h.5", false) }
    val clipboard: ImageVector by lazy { icon("clipboard", "M9 4h6v3H9zM7 5.5H5v15h14v-15h-2", false) }
    val disc: ImageVector by lazy { icon("disc", "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6z", false) }
    val stop: ImageVector by lazy { icon("stop", "M7 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z", true) }
}
