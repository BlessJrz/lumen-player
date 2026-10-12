package com.lumen.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumen.player.data.db.DlState
import com.lumen.player.data.db.DownloadEntity
import com.lumen.player.data.db.TrackEntity
import com.lumen.player.data.download.StorageInfo
import com.lumen.player.ui.components.Artwork
import com.lumen.player.ui.components.DialogSpec
import com.lumen.player.ui.components.EmptyState
import com.lumen.player.ui.components.Glass
import com.lumen.player.ui.components.IconBtn
import com.lumen.player.ui.components.LIcon
import com.lumen.player.ui.components.LText
import com.lumen.player.ui.components.LinearBar
import com.lumen.player.ui.components.LocalOverlays
import com.lumen.player.ui.components.MenuItem
import com.lumen.player.ui.components.ProgressRing
import com.lumen.player.ui.components.RingState
import com.lumen.player.ui.components.SectionHeader
import com.lumen.player.ui.components.TrackRow
import com.lumen.player.ui.components.collectionName
import com.lumen.player.ui.components.displayArtist
import com.lumen.player.ui.icons.LIcons
import com.lumen.player.ui.nav.LocalEnv
import com.lumen.player.ui.nav.Routes
import com.lumen.player.ui.theme.Lumen
import kotlinx.coroutines.launch

fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "%.0f KB".format(b / 1024.0)
}

@Composable
fun DownloadsScreen() {
    val env = LocalEnv.current
    val c = env.c
    val o = LocalOverlays.current
    val queue by c.downloads.queue.collectAsState(initial = emptyList())
    val done by c.library.downloaded.collectAsState(initial = emptyList())
    val ps by c.player.state.collectAsState()
    var storage by remember { mutableStateOf<StorageInfo?>(null) }
    var trackMap by remember { mutableStateOf<Map<Long, TrackEntity>>(emptyMap()) }
    LaunchedEffect(queue.size, done.size) { storage = c.downloads.storage() }
    LaunchedEffect(queue.map { it.trackId }) { trackMap = c.library.tracks(queue.map { it.trackId }).associateBy { it.id } }

    ScreenList(gap = 20.dp) {
        item("title") {
            TitleBar("Downloads") {
                IconBtn(LIcons.more, {
                    o.menu("Downloads", null, null, listOf(
                        MenuItem(LIcons.pause, "Pause all") { c.appScope.launch { c.downloads.pauseAll() } },
                        MenuItem(LIcons.play, "Resume all") { c.appScope.launch { c.downloads.resumeAll() } },
                        MenuItem(LIcons.settings, "Download settings") { env.nav.navigate(Routes.SETTINGS) },
                        MenuItem(null, "—") {},
                        MenuItem(LIcons.trash, "Remove all downloads", danger = true) {
                            o.dialog(DialogSpec(LIcons.trash, "Remove ${done.size} downloads?", "They stay on your NAS and can be downloaded again any time. Frees ${fmtBytes(storage?.offlineBytes ?: 0)} on this phone.", "Remove", true) {
                                c.appScope.launch { c.downloads.remove(done.map { it.id }); o.toast("Downloads removed") }
                            })
                        },
                    ))
                }, size = 40.dp)
            }
        }
        item("storage") { StorageCard(storage, done) }
        if (queue.isNotEmpty()) {
            item("qh") { SectionHeader("Downloading · ${queue.count { it.state == DlState.RUNNING || it.state == DlState.QUEUED }} of ${queue.size}", action = if (queue.any { it.state == DlState.RUNNING || it.state == DlState.QUEUED }) "Pause all" else "Resume all") {
                c.appScope.launch { if (queue.any { it.state == DlState.RUNNING || it.state == DlState.QUEUED }) c.downloads.pauseAll() else c.downloads.resumeAll() }
            } }
            items(queue, key = { "q${it.trackId}" }) { d -> DownloadRow(d, trackMap[d.trackId]) }
        }
        item("dh") { SectionHeader("On this phone · ${done.size}", action = if (done.isNotEmpty()) "Shuffle" else null) { c.player.play(done, shuffle = true) } }
        if (done.isEmpty() && queue.isEmpty()) item("empty") {
            EmptyState(LIcons.download, "Nothing offline yet", "Download songs, folders or playlists to keep listening when your NAS is out of reach.", action = "Browse collections") { env.nav.navigate(Routes.LIBRARY) }
        }
        val groups = done.groupBy { it.folder }
        groups.forEach { (folder, list) ->
            item("g$folder") {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LIcon(LIcons.folder, 16.dp, Lumen.colors.accentText)
                    LText(folder.replace("/", " › ").ifBlank { "Music" }, Lumen.type.overline, Lumen.colors.textTertiary, Modifier.weight(1f), upper = true)
                    LText("${list.size} · ${fmtBytes(list.sumOf { it.size })}", Lumen.type.caption, Lumen.colors.textTertiary)
                }
            }
            items(list, key = { "d${it.id}" }) { t ->
                TrackRow(t, { c.player.play(done, done.indexOf(t)) }, playing = ps.current?.id == t.id, onMore = {
                    o.menu(t.title, t.displayArtist(), t, listOf(
                        MenuItem(LIcons.play, "Play next") { c.player.playNext(listOf(t)) },
                        MenuItem(LIcons.folder, "Go to folder") { env.nav.navigate(Routes.folder(t.folder)) },
                        MenuItem(LIcons.trash, "Remove download", danger = true) {
                            o.dialog(DialogSpec(LIcons.trash, "Remove download?", "“${t.title}” stays on your NAS.", "Remove", true) { c.appScope.launch { c.downloads.remove(listOf(t.id)) } })
                        },
                    ))
                }, subtitle = "${t.displayArtist()} · ${fmtBytes(t.size)}")
            }
        }
    }
}

@Composable
private fun StorageCard(s: StorageInfo?, done: List<TrackEntity>) {
    val c = Lumen.colors
    val used = s?.offlineBytes ?: 0L
    val limit = s?.limitBytes ?: 1L
    val byFolder = done.groupBy { it.folder.substringAfterLast('/').ifBlank { "Music" } }.mapValues { (_, l) -> l.sumOf { it.size } }.entries.sortedByDescending { it.value }.take(3)
    val colors = listOf(c.accent, c.accentSecondary, c.warning)
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    LText("Offline library", Lumen.type.overline, c.textTertiary, upper = true)
                    LText(fmtBytes(used), Lumen.type.display)
                }
                LText("of ${fmtBytes(limit)} · ${done.size} songs", Lumen.type.caption, c.textSecondary)
            }
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.track), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                byFolder.forEachIndexed { i, (_, b) ->
                    val f = (b.toFloat() / limit).coerceIn(0.01f, 1f)
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(colors[i]))
                }
            }
            if (byFolder.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                byFolder.forEachIndexed { i, (n, b) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors[i]))
                        LText("$n ${fmtBytes(b)}", Lumen.type.caption, c.textSecondary, maxLines = 1)
                    }
                }
            }
            if (s != null) LText("${fmtBytes(s.freeBytes)} free on this phone · artwork cache ${fmtBytes(s.artBytes)}", Lumen.type.caption, c.textTertiary)
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadEntity, t: TrackEntity?) {
    val env = LocalEnv.current
    val c = Lumen.colors
    val frac = if (d.total > 0) d.bytes.toFloat() / d.total else 0f
    val state = when (d.state) { DlState.RUNNING -> RingState.Downloading; DlState.PAUSED -> RingState.Paused; DlState.DONE -> RingState.Done; DlState.FAILED -> RingState.Failed; else -> RingState.Queued }
    val caption = when (d.state) {
        DlState.RUNNING -> "${(frac * 100).toInt()}% · ${fmtBytes(d.bytes)} of ${fmtBytes(d.total)}"
        DlState.PAUSED -> "Paused · ${(frac * 100).toInt()}%"
        DlState.FAILED -> d.error ?: "Failed — tap to retry"
        DlState.DONE -> "Done"
        else -> "Queued"
    }
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Artwork(t, Modifier.size(48.dp), radius = 12.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LText(t?.title ?: "…", Lumen.type.body, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { LinearBar(frac, color = if (d.state == DlState.FAILED) c.danger else null) }
                LText(caption, Lumen.type.caption, if (d.state == DlState.FAILED) c.danger else c.textTertiary, maxLines = 1)
            }
        }
        ProgressRing(state, frac) {
            env.c.appScope.launch {
                when (d.state) {
                    DlState.RUNNING, DlState.QUEUED -> env.c.downloads.pause(d.trackId)
                    else -> env.c.downloads.resume(d.trackId)
                }
            }
        }
        Box(Modifier.size(28.dp).clip(CircleShape).background(c.track).padding(0.dp).let { it }, contentAlignment = Alignment.Center) {
            IconBtn(LIcons.close, { env.c.appScope.launch { env.c.downloads.cancel(d.trackId) } }, size = 28.dp, kind = com.lumen.player.ui.components.IconKind.Plain, tint = c.textSecondary, iconSize = 14.dp)
        }
    }
}

@Suppress("unused")
private fun unused(t: TrackEntity) = t.collectionName()
