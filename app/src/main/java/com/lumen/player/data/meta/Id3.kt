package com.lumen.player.data.meta

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

/** Minimal ID3v2 support: read embedded lyrics (USLT) and write a fresh v2.3 tag for MP3 files. */
object Id3 {

    data class Fields(
        val title: String, val artist: String, val album: String, val albumArtist: String,
        val genre: String, val year: Int?, val track: Int?, val lyrics: String?, val artJpeg: ByteArray?,
    )

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    /** Total size of the ID3v2 tag at the start of [head] (0 if none). */
    fun tagLength(head: ByteArray): Int {
        if (head.size < 10 || head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return 0
        val footer = (head[5].toInt() and 0x10) != 0
        return 10 + syncsafe(head, 6) + if (footer) 10 else 0
    }

    private fun charset(enc: Int): Charset = when (enc) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    /** Reads the USLT (unsynchronised lyrics) frame if present. [input] must start at byte 0 of the file. */
    fun readLyrics(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.readNBytesCompat(header) < 10) return null
        val total = tagLength(header)
        if (total == 0) return null
        val version = header[3].toInt()
        val body = ByteArray(minOf(total - 10, 4 * 1024 * 1024))
        val n = input.readNBytesCompat(body)
        var o = 0
        while (o + 10 <= n) {
            val id = String(body, o, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val size = if (version >= 4) syncsafe(body, o + 4) else int32(body, o + 4)
            if (size <= 0 || o + 10 + size > n) break
            if (id == "USLT") {
                val f = body.copyOfRange(o + 10, o + 10 + size)
                val enc = f[0].toInt()
                var p = 4 // encoding + 3-byte language
                val wide = enc == 1 || enc == 2
                // skip content descriptor
                while (p < f.size) {
                    if (!wide && f[p].toInt() == 0) { p += 1; break }
                    if (wide && p + 1 < f.size && f[p].toInt() == 0 && f[p + 1].toInt() == 0) { p += 2; break }
                    p += if (wide) 2 else 1
                }
                return String(f, p, f.size - p, charset(enc)).trim('\u0000', ' ', '\n').ifBlank { null }
            }
            o += 10 + size
        }
        return null
    }

    private fun textFrame(id: String, value: String): ByteArray {
        val data = ByteArrayOutputStream()
        data.write(1) // UTF-16 with BOM
        data.write(value.toByteArray(Charsets.UTF_16))
        return frame(id, data.toByteArray())
    }

    private fun frame(id: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        val s = data.size
        out.write(byteArrayOf((s ushr 24).toByte(), (s ushr 16).toByte(), (s ushr 8).toByte(), s.toByte(), 0, 0))
        out.write(data)
        return out.toByteArray()
    }

    fun buildTag(f: Fields): ByteArray {
        val frames = ByteArrayOutputStream()
        if (f.title.isNotBlank()) frames.write(textFrame("TIT2", f.title))
        if (f.artist.isNotBlank()) frames.write(textFrame("TPE1", f.artist))
        if (f.album.isNotBlank()) frames.write(textFrame("TALB", f.album))
        if (f.albumArtist.isNotBlank()) frames.write(textFrame("TPE2", f.albumArtist))
        if (f.genre.isNotBlank()) frames.write(textFrame("TCON", f.genre))
        f.year?.let { frames.write(textFrame("TYER", it.toString())) }
        f.track?.let { frames.write(textFrame("TRCK", it.toString())) }
        f.lyrics?.takeIf { it.isNotBlank() }?.let {
            val d = ByteArrayOutputStream()
            d.write(1); d.write("eng".toByteArray()); d.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0))
            d.write(it.toByteArray(Charsets.UTF_16))
            frames.write(frame("USLT", d.toByteArray()))
        }
        f.artJpeg?.let {
            val d = ByteArrayOutputStream()
            d.write(0); d.write("image/jpeg".toByteArray()); d.write(0); d.write(3); d.write(0); d.write(it)
            frames.write(frame("APIC", d.toByteArray()))
        }
        val body = frames.toByteArray()
        val padded = body + ByteArray(1024)
        val s = padded.size
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            ((s shr 21) and 0x7F).toByte(), ((s shr 14) and 0x7F).toByte(), ((s shr 7) and 0x7F).toByte(), (s and 0x7F).toByte(),
        )
        return header + padded
    }

    /** Writes [fields] into a copy of [source] (an MP3), replacing any existing ID3v2 tag. */
    fun rewrite(source: File, dest: File, fields: Fields) {
        source.inputStream().buffered().use { input ->
            val head = ByteArray(10)
            input.mark(16)
            val read = input.readNBytesCompat(head)
            val skip = if (read == 10) tagLength(head) else 0
            input.reset()
            var left = skip.toLong()
            while (left > 0) { val s = input.skip(left); if (s <= 0) break; left -= s }
            dest.outputStream().buffered().use { out ->
                out.write(buildTag(fields))
                input.copyTo(out)
            }
        }
    }
}

fun InputStream.readNBytesCompat(buf: ByteArray): Int {
    var total = 0
    while (total < buf.size) {
        val n = read(buf, total, buf.size - total)
        if (n <= 0) break
        total += n
    }
    return total
}
