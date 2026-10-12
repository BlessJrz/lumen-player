package com.lumen.player.data.nas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

sealed interface NasStatus {
    data object NotConfigured : NasStatus
    data object Connecting : NasStatus
    data class Connected(val host: String, val remote: Boolean) : NasStatus
    data class Offline(val reason: String) : NasStatus
}

/**
 * Owns the live NAS connection. Tries the LAN address first, then the remote (Tailscale) address, and
 * transparently reconnects when a call fails.
 */
class NasManager(private val store: NasConfigStore, private val http: OkHttpClient) {
    private val _status = MutableStateFlow<NasStatus>(NasStatus.NotConfigured)
    val status: StateFlow<NasStatus> = _status.asStateFlow()

    @Volatile var config: NasConfig = store.load(); private set
    @Volatile private var client: NasClient? = null
    private val lock = Any()

    init { if (config.isConfigured) _status.value = NasStatus.Offline("Not connected yet") }

    fun save(c: NasConfig) {
        store.save(c)
        synchronized(lock) { runCatching { client?.close() }; client = null; config = store.load() }
        _status.value = if (config.isConfigured) NasStatus.Offline("Not connected yet") else NasStatus.NotConfigured
    }

    private fun create(host: String, c: NasConfig): NasClient = when (c.protocol) {
        NasProtocol.SMB -> SmbNasClient.connect(host, c)
        NasProtocol.WEBDAV -> WebDavNasClient(host, c, http).also { it.ping() }
    }

    /** Blocking — call from IO threads only. */
    fun client(): NasClient = synchronized(lock) {
        client?.let { return it }
        val c = config
        if (!c.isConfigured) { _status.value = NasStatus.NotConfigured; throw IllegalStateException("NAS not set up") }
        _status.value = NasStatus.Connecting
        var last: Throwable? = null
        for (h in c.hosts) {
            try {
                val cl = create(h, c)
                client = cl
                _status.value = NasStatus.Connected(h, remote = h == c.remoteHost && h != c.lanHost)
                return cl
            } catch (t: Throwable) { last = t }
        }
        _status.value = NasStatus.Offline(last?.message ?: "Unreachable")
        throw last ?: IllegalStateException("Unreachable")
    }

    fun invalidate() = synchronized(lock) { runCatching { client?.close() }; client = null }

    /** Runs [block] with a connected client; reconnects once on failure. */
    fun <T> call(block: (NasClient) -> T): T = try {
        block(client())
    } catch (t: Throwable) {
        if (t is IllegalStateException && !config.isConfigured) throw t
        invalidate()
        block(client())
    }

    suspend fun <T> io(block: (NasClient) -> T): T = withContext(Dispatchers.IO) { call(block) }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) { runCatching { client() }.isSuccess }

    /** Tests a config without saving it. Returns the host that worked. */
    suspend fun test(c: NasConfig): Result<String> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (h in c.hosts) {
            try {
                create(h, c).use { cl -> cl.list("") }
                return@withContext Result.success(h)
            } catch (t: Throwable) { last = t }
        }
        Result.failure(last ?: IllegalStateException("No host"))
    }

    suspend fun listShares(c: NasConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (h in c.hosts) {
            try { return@withContext Result.success(SmbNasClient.listShares(h, c)) } catch (t: Throwable) { last = t }
        }
        Result.failure(last ?: IllegalStateException("No host"))
    }

    /** Lists folders for the setup picker, relative to the share (ignores rootPath). */
    suspend fun browse(c: NasConfig, path: String): Result<List<NasEntry>> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (h in c.hosts) {
            try {
                val list = when (c.protocol) {
                    NasProtocol.SMB -> SmbNasClient.browse(h, c, c.share, path)
                    NasProtocol.WEBDAV -> WebDavNasClient(h, c.copy(rootPath = ""), http).list(path)
                }
                return@withContext Result.success(list.filter { it.isDir }.sortedBy { it.name.lowercase() })
            } catch (t: Throwable) { last = t }
        }
        Result.failure(last ?: IllegalStateException("No host"))
    }
}
