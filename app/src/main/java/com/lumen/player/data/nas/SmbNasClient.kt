package com.lumen.player.data.nas

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import java.io.File
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbNasClient private constructor(
    override val host: String,
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    private val share: DiskShare?,
    private val root: String,
) : NasClient {

    companion object {
        private const val MAX_READ = 512 * 1024

        private fun config() = SmbConfig.builder()
            .withTimeout(12, TimeUnit.SECONDS)
            .withSoTimeout(40, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true)
            .withSecurityProvider(BCSecurityProvider())
            .withSigningRequired(false)
            .build()

        fun connect(host: String, cfg: NasConfig, shareOverride: String? = null): SmbNasClient {
            require(cfg.username.isNotBlank()) { "Enter your NAS username (the one you sign in to UGOS with)" }
            val client = SMBClient(config())
            val conn = if (cfg.port > 0) client.connect(host, cfg.port) else client.connect(host)
            val auth = AuthenticationContext(cfg.username.trim(), cfg.password.toCharArray(), cfg.domain.ifBlank { null })
            val session = try { conn.authenticate(auth) } catch (t: Throwable) { runCatching { conn.close() }; throw t }
            val shareName = shareOverride ?: cfg.share
            val share = if (shareName.isNotBlank()) session.connectShare(shareName) as DiskShare else null
            return SmbNasClient(host, client, conn, session, share, if (shareOverride != null) "" else cfg.rootPath)
        }

        /** Lists the disk shares on the server (used by the setup screen's share picker). */
        fun listShares(host: String, cfg: NasConfig): List<String> {
            val c = connect(host, cfg.copy(share = ""))
            return try {
                val transport = SMBTransportFactories.SRVSVC.getTransport(c.session)
                ServerService(transport).shares0.map { it.netName }
                    .filter { !it.endsWith("$") }
                    .sortedBy { it.lowercase() }
            } finally { c.close() }
        }

        /** Opens an arbitrary share (for the folder browser in setup). */
        fun browse(host: String, cfg: NasConfig, share: String, path: String): List<NasEntry> {
            val c = connect(host, cfg, shareOverride = share)
            return try { c.list(path) } finally { c.close() }
        }
    }

    private fun disk(): DiskShare = share ?: error("No share selected")
    private fun smb(rel: String) = joinPath(root, rel).replace('/', '\\')

    override fun list(path: String): List<NasEntry> =
        disk().list(smb(path)).filter { it.fileName != "." && it.fileName != ".." }.map {
            val dir = (it.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            NasEntry(it.fileName, joinPath(path, it.fileName), dir, it.endOfFile, it.lastWriteTime.toEpochMillis())
        }

    override fun open(path: String): NasFile {
        val f = disk().openFile(
            smb(path), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN, null,
        )
        val size = f.fileInformation.standardInformation.endOfFile
        return object : NasFile {
            override val size: Long = size
            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (position >= size) return -1
                val n = minOf(length, MAX_READ)
                return f.read(buffer, position, offset, n)
            }
            override fun close() = runCatching { f.close() }.let { }
        }
    }

    override fun ensureDir(path: String) {
        var acc = ""
        for (part in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            acc = joinPath(acc, part)
            if (!disk().folderExists(smb(acc))) disk().mkdir(smb(acc))
        }
    }

    override fun exists(path: String): Boolean = runCatching { disk().fileExists(smb(path)) || disk().folderExists(smb(path)) }.getOrDefault(false)

    override fun upload(local: File, path: String) {
        ensureDir(parentOf(path))
        val f = disk().openFile(
            smb(path), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF, null,
        )
        f.use { remote -> remote.outputStream.use { out -> local.inputStream().use { it.copyTo(out, 256 * 1024) } } }
    }

    override fun close() {
        runCatching { share?.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }
}
