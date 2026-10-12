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

    @Volatile private var lastGoodHost: String? = null

    private fun portOf(c: NasConfig) = when {
        c.port > 0 -> c.port
        c.protocol == NasProtocol.SMB -> 445
        c.useHttps -> 5006
        else -> 5005
    }

    /** Quick TCP check (no SMB handshake) so an unreachable address costs ~2 s instead of a 20 s timeout. */
    private fun reachable(host: String, port: Int, timeoutMs: Int = 2500): Boolean = try {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), timeoutMs); true }
    } catch (_: Throwable) { false }

    /**
     * Hosts worth trying, best first: probes LAN and Tailscale addresses in parallel and returns the reachable ones
     * (home address preferred). Away from home this skips the LAN address immediately.
     */
    private fun orderedHosts(c: NasConfig): List<String> {
        val hosts = c.hosts
        if (hosts.size <= 1) return hosts
        val port = portOf(c)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(hosts.size)
        return try {
            val futures = hosts.associateWith { h -> pool.submit<Boolean> { reachable(h, port) } }
            val ok = hosts.filter { h -> runCatching { futures.getValue(h).get(3, java.util.concurrent.TimeUnit.SECONDS) }.getOrDefault(false) }
            if (ok.isEmpty()) hosts else ok
        } finally { pool.shutdownNow() }
    }

    /** Blocking — call from IO threads only. */
    fun client(): NasClient = synchronized(lock) {
        client?.let { return it }
        val c = config
        if (!c.isConfigured) { _status.value = NasStatus.NotConfigured; throw IllegalStateException("NAS not set up") }
        _status.value = NasStatus.Connecting
        var last: Throwable? = null
        for (h in orderedHosts(c)) {
            try {
                val cl = create(h, c)
                client = cl
                lastGoodHost = h
                _status.value = NasStatus.Connected(h, remote = h == c.remoteHost && h != c.lanHost)
                return cl
            } catch (t: Throwable) { last = t }
        }
        _status.value = NasStatus.Offline(last?.message ?: "Unreachable")
        throw last ?: IllegalStateException("Unreachable")
    }

    /** Call when the phone switches networks (Wi-Fi ↔ mobile data / Tailscale) so the next call picks the right address. */
    fun onNetworkChanged() {
        val cur = (status.value as? NasStatus.Connected) ?: return
        val c = config
        // Re-check in the background; only drop the connection if the current host is no longer reachable.
        Thread {
            if (!reachable(cur.host, portOf(c), 2000) || (cur.remote && c.lanHost.isNotBlank() && reachable(c.lanHost, portOf(c), 1500))) invalidate()
        }.start()
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
        for (h in orderedHosts(c)) {
            try {
                create(h, c).use { cl -> cl.list("") }
                return@withContext Result.success(h)
            } catch (t: Throwable) { last = t }
        }
        Result.failure(last ?: IllegalStateException("No host"))
    }

    /**
     * When no shared folder is given, finds the share that contains the music folder (UGOS names the personal
     * folder share differently across versions, e.g. "personal_folder" or the username).
     */
    suspend fun findShare(c: NasConfig): Result<String> = withContext(Dispatchers.IO) {
        val shares = listShares(c).getOrElse { return@withContext Result.failure(it) }
        val ordered = shares.sortedBy { s ->
            val l = s.lowercase()
            when { "personal" in l || l == c.username.lowercase() || "home" in l -> 0; "music" in l -> 1; else -> 2 }
        }
        for (h in c.hosts) for (s in ordered) {
            val ok = runCatching { SmbNasClient.connect(h, c.copy(share = s)).use { it.list("") } }.isSuccess
            if (ok) return@withContext Result.success(s)
        }
        Result.failure(IllegalStateException(
            if (shares.isEmpty()) "No shared folders visible — check SMB is on in UGOS Control Panel → File Service"
            else "Couldn’t find a “${c.rootPath}” folder in: ${shares.joinToString()}. Tap List to pick the shared folder."))
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

/** Turns low-level SMB/network exceptions into something a person can act on. */
fun friendlyNasError(t: Throwable?): String {
    val m = (t?.message ?: t?.javaClass?.simpleName ?: "Unknown error")
    val all = generateSequence(t) { it.cause }.joinToString(" | ") { (it.message ?: "") + " " + it.javaClass.simpleName }
    return when {
        "STATUS_LOGON_FAILURE" in all -> "Wrong username or password"
        "STATUS_ACCESS_DENIED" in all -> "Access denied — this account can’t open that folder"
        "STATUS_BAD_NETWORK_NAME" in all -> "That shared folder doesn’t exist — tap List to pick one"
        "STATUS_OBJECT_NAME_NOT_FOUND" in all || "STATUS_OBJECT_PATH_NOT_FOUND" in all -> "Shared folder found, but no music folder at that path — tap Browse"
        "UnknownHost" in all -> "Can’t find that address — check it’s typed correctly"
        "ConnectException" in all || "NoRouteToHost" in all || "Connection refused" in all -> "NAS not reachable at that address (is SMB on, and are you on home Wi-Fi or Tailscale?)"
        "SocketTimeout" in all || "TimeoutException" in all -> "Timed out reaching the NAS — if you’re away, make sure Tailscale is connected on the phone"
        else -> m
    }
}
