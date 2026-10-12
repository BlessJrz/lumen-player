package com.lumen.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.lumen.player.data.settings.AppSettings
import com.lumen.player.data.settings.ThemeMode
import com.lumen.player.ui.nav.LumenRoot
import com.lumen.player.ui.nav.PendingIntentAction
import com.lumen.player.ui.theme.LumenThemeProvider

class MainActivity : ComponentActivity() {
    private val pending = PendingIntentAction()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
        handle(intent)
        val c = (application as LumenApp).container
        setContent {
            val s by c.settings.settings.collectAsState(initial = remember { AppSettings() })
            LumenThemeProvider(s.design, s.themeMode, s.reduceTransparency, s.ambientMotion) {
                val dark = when (s.themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme() }
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(style, style)
                }
                LumenRoot(c, pending)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url = (application as LumenApp).container.importer.extractUrl(text)
            if (url != null) pending.openImport = url
        }
        if (i.getStringExtra("open") == "player") pending.openPlayer = true
        i.getStringExtra("lumen.route")?.let { pending.route = it }
        val c = (application as LumenApp).container
        val design = i.getStringExtra("lumen.design")
        val theme = i.getStringExtra("lumen.theme")
        val demo = i.getBooleanExtra("lumen.demo", false)
        if (design != null || theme != null || demo) c.appScope.launch {
            design?.let { d -> runCatching { c.settings.setDesign(com.lumen.player.data.settings.Design.valueOf(d)) } }
            theme?.let { t -> runCatching { c.settings.setThemeMode(ThemeMode.valueOf(t)) } }
            if (demo) {
                com.lumen.player.data.library.DemoData.seed(this@MainActivity, c.db, c.scanner)
                c.settings.setOnboarded(true)
                kotlinx.coroutines.delay(1500)
                val t = c.library.tracks(listOf(1L, 2L, 3L, 4L, 5L))
                c.player.load(t)
            }
        }
    }
}
