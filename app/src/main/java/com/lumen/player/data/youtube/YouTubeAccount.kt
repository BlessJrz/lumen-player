package com.lumen.player.data.youtube

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The person's own YouTube sign-in. They sign in themselves inside an in-app web page; Lumen keeps the
 * resulting YouTube session cookies in app-private storage and hands them to yt-dlp so it can read their
 * liked videos. Nothing is sent anywhere except YouTube.
 */
class YouTubeAccount(private val context: Context) {
    private val file = File(context.noBackupFilesDir, "yt-cookies.txt")
    private val prefs = context.getSharedPreferences("yt_account", Context.MODE_PRIVATE)
    private val _signedIn = MutableStateFlow(file.exists() && file.length() > 0)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    val cookieFile: File? get() = file.takeIf { _signedIn.value && it.exists() }

    private val domains = listOf("https://www.youtube.com", "https://music.youtube.com", "https://m.youtube.com")

    /** True once the web session has the cookies YouTube uses for a signed-in user. */
    fun webSessionLooksSignedIn(): Boolean {
        val all = domains.mapNotNull { CookieManager.getInstance().getCookie(it) }.joinToString("; ")
        return "LOGIN_INFO=" in all || "SAPISID=" in all || "__Secure-3PAPISID=" in all
    }

    /** Copies the WebView's YouTube cookies into a Netscape cookies file for yt-dlp. */
    fun captureFromWebView(): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val pairs = LinkedHashMap<String, String>()
        domains.forEach { d ->
            cm.getCookie(d)?.split(';')?.forEach { kv ->
                val i = kv.indexOf('=')
                if (i > 0) pairs[kv.substring(0, i).trim()] = kv.substring(i + 1).trim()
            }
        }
        if (pairs.isEmpty()) return false
        val expiry = System.currentTimeMillis() / 1000 + 180L * 24 * 3600
        val text = buildString {
            append("# Netscape HTTP Cookie File\n")
            pairs.forEach { (k, v) -> append(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$k\t$v\n") }
        }
        file.writeText(text)
        _signedIn.value = true
        return true
    }

    var label: String
        get() = prefs.getString("label", "") ?: ""
        set(v) { prefs.edit().putString("label", v).apply() }

    fun signOut() {
        file.delete()
        prefs.edit().clear().apply()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        _signedIn.value = false
    }

    // ---------- Which liked videos have already been imported ----------
    private val imported = context.getSharedPreferences("yt_imported", Context.MODE_PRIVATE)
    private val _importedIds = MutableStateFlow(imported.all.keys.toSet())
    val importedIds: StateFlow<Set<String>> = _importedIds.asStateFlow()
    fun markImported(id: String) {
        if (id.isBlank()) return
        imported.edit().putLong(id, System.currentTimeMillis()).apply()
        _importedIds.value = _importedIds.value + id
    }
}
