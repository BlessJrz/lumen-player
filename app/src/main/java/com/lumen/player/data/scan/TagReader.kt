package com.lumen.player.data.scan

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import com.lumen.player.data.meta.Id3
import com.lumen.player.data.nas.NasFile
import com.lumen.player.data.nas.NasFileInputStream

data class Tags(
    val title: String?, val artist: String?, val album: String?, val albumArtist: String?, val genre: String?,
    val year: Int?, val track: Int?, val durationMs: Long, val picture: ByteArray?, val lyrics: String?,
)

/** Reads embedded tags directly from a NAS file without downloading the whole thing. */
object TagReader {
    private class NasMediaSource(private val f: NasFile) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= f.size) return -1
            var total = 0
            while (total < size) {
                val n = f.read(position + total, buffer, offset + total, size - total)
                if (n <= 0) break
                total += n
            }
            return if (total == 0) -1 else total
        }
        override fun getSize(): Long = f.size
        override fun close() {}
    }

    fun read(file: NasFile, isMp3: Boolean): Tags {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(NasMediaSource(file))
            fun s(k: Int) = mmr.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
            val lyrics = if (isMp3) runCatching { Id3.readLyrics(NasFileInputStream(file)) }.getOrNull() else null
            return Tags(
                title = s(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = s(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = s(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = s(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = s(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let(::cleanGenre),
                year = s(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()
                    ?: s(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)?.toIntOrNull(),
                track = s(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull(),
                durationMs = s(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                picture = mmr.embeddedPicture,
                lyrics = lyrics,
            )
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** ID3v1 numeric genres like "(13)" → name for the common ones; otherwise passthrough. */
    private fun cleanGenre(g: String): String {
        val m = Regex("""^\((\d+)\)$""").find(g) ?: return g
        val names = mapOf(0 to "Blues", 2 to "Country", 7 to "Hip-Hop", 8 to "Jazz", 9 to "Metal", 12 to "Other", 13 to "Pop",
            14 to "R&B", 15 to "Rap", 16 to "Reggae", 17 to "Rock", 18 to "Techno", 24 to "Soundtrack", 32 to "Classical", 52 to "Electronic")
        return names[m.groupValues[1].toInt()] ?: g
    }
}
