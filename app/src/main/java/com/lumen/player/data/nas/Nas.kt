package com.lumen.player.data.nas

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.Closeable
import java.io.File
import java.io.InputStream

enum class NasProtocol(val title: String) { SMB("SMB"), WEBDAV("WebDAV") }

/**
 * Connection settings. [lanHost] is tried first (home Wi-Fi), [remoteHost] (e.g. the NAS's Tailscale
 * IP or MagicDNS name) is used when away from home.
 */
data class NasConfig(
    val name: String = "",
    val protocol: NasProtocol = NasProtocol.SMB,
    val lanHost: String = "",
    val remoteHost: String = "",
    val port: Int = 0,
    val useHttps: Boolean = false,
    val share: String = "",
    val rootPath: String = "Music",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
) {
    val isConfigured get() = lanHost.isNotBlank() || remoteHost.isNotBlank()
    val hosts get() = listOf(lanHost, remoteHost).filter { it.isNotBlank() }.distinct()
}

data class NasEntry(val name: String, val path: String, val isDir: Boolean, val size: Long, val mtime: Long)

/** Random-access handle to a remote file. Used for streaming, tag reading and downloads. */
interface NasFile : Closeable {
    val size: Long
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

interface NasClient : Closeable {
    val host: String
    /** [path] is relative to the configured music root ("" = root). */
    fun list(path: String): List<NasEntry>
    fun open(path: String): NasFile
    fun upload(local: File, path: String)
    fun ensureDir(path: String)
    fun exists(path: String): Boolean
}

fun joinPath(a: String, b: String): String = listOf(a.trim('/'), b.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
fun parentOf(path: String): String = path.trim('/').substringBeforeLast('/', "")
fun nameOf(path: String): String = path.trim('/').substringAfterLast('/')

class NasFileInputStream(private val file: NasFile, private var pos: Long = 0) : InputStream() {
    private val one = ByteArray(1)
    override fun read(): Int = if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xFF
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= file.size) return -1
        val n = file.read(pos, b, off, minOf(len.toLong(), file.size - pos).toInt())
        if (n <= 0) return -1
        pos += n
        return n
    }
    override fun skip(n: Long): Long { val s = minOf(n, file.size - pos); pos += s; return s }
    override fun available(): Int = minOf(Int.MAX_VALUE.toLong(), file.size - pos).toInt()
}

/** Stores the NAS settings; the password lives in Android Keystore-backed encrypted preferences. */
class NasConfigStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "nas_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): NasConfig = NasConfig(
        name = prefs.getString("name", "") ?: "",
        protocol = runCatching { NasProtocol.valueOf(prefs.getString("protocol", "SMB")!!) }.getOrDefault(NasProtocol.SMB),
        lanHost = prefs.getString("lan", "") ?: "",
        remoteHost = prefs.getString("remote", "") ?: "",
        port = prefs.getInt("port", 0),
        useHttps = prefs.getBoolean("https", false),
        share = prefs.getString("share", "") ?: "",
        rootPath = prefs.getString("root", "Music") ?: "Music",
        username = prefs.getString("user", "") ?: "",
        password = prefs.getString("pass", "") ?: "",
        domain = prefs.getString("domain", "") ?: "",
    )

    fun save(c: NasConfig) {
        prefs.edit()
            .putString("name", c.name).putString("protocol", c.protocol.name)
            .putString("lan", c.lanHost.trim()).putString("remote", c.remoteHost.trim())
            .putInt("port", c.port).putBoolean("https", c.useHttps)
            .putString("share", c.share.trim('/', '\\', ' ')).putString("root", c.rootPath.trim('/', ' '))
            .putString("user", c.username).putString("pass", c.password).putString("domain", c.domain)
            .apply()
    }
}
