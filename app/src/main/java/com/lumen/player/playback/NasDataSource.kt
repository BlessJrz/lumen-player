package com.lumen.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.common.PlaybackException
import com.lumen.player.data.nas.NasFile
import com.lumen.player.data.nas.NasManager
import java.io.IOException

/** URI scheme for tracks that live on the NAS: nas:///Playlists/Emotional/song.mp3 */
object NasUri {
    const val SCHEME = "nas"
    fun of(path: String): Uri = Uri.Builder().scheme(SCHEME).authority("lumen").path("/" + path.trim('/')).build()
    fun pathOf(uri: Uri): String = uri.path.orEmpty().trimStart('/')
}

/** Streams audio straight from the NAS (SMB or WebDAV) with a read-ahead buffer. */
@UnstableApi
class NasDataSource(private val nas: NasManager) : BaseDataSource(true) {

    class Factory(private val nas: NasManager) : DataSource.Factory {
        override fun createDataSource(): DataSource = NasDataSource(nas)
    }

    private var uri: Uri? = null
    private var file: NasFile? = null
    private var position = 0L
    private var remaining = 0L
    private val buf = ByteArray(256 * 1024)
    private var bufStart = 0L
    private var bufLen = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val path = NasUri.pathOf(dataSpec.uri)
        val f = try {
            nas.call { it.open(path) }
        } catch (t: Throwable) {
            throw DataSourceException(t, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        file = f
        position = dataSpec.position
        val size = f.size
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length
        else if (size >= 0) size - position else C.LENGTH_UNSET.toLong()
        if (size in 0 until position) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        bufLen = 0
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val f = file ?: throw IOException("closed")
        if (position < bufStart || position >= bufStart + bufLen) {
            val n = try { f.read(position, buf, 0, buf.size) } catch (t: Throwable) {
                throw DataSourceException(t, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            }
            if (n <= 0) return C.RESULT_END_OF_INPUT
            bufStart = position; bufLen = n
        }
        val inBuf = (bufStart + bufLen - position).toInt()
        var n = minOf(length, inBuf)
        if (remaining != C.LENGTH_UNSET.toLong()) n = minOf(n.toLong(), remaining).toInt()
        System.arraycopy(buf, (position - bufStart).toInt(), buffer, offset, n)
        position += n
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { file?.close() }
        file = null
        bufLen = 0
        if (opened) { opened = false; transferEnded() }
    }
}
