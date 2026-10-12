package com.lumen.player.data.meta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** Caches album art on the phone so it's never downloaded twice. One JPEG per track (or per album key). */
class ArtworkStore(context: Context) {
    val dir = File(context.filesDir, "art").apply { mkdirs() }

    fun fileFor(key: String): File = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120) + ".jpg")

    /** Decodes, downsizes to at most [max]px and stores as JPEG. Returns the saved file or null if not an image. */
    fun save(key: String, bytes: ByteArray, max: Int = 900): File? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= max && opts.outHeight / (sample * 2) >= max) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scaled = if (bmp.width > max || bmp.height > max) {
            val r = max.toFloat() / maxOf(bmp.width, bmp.height)
            Bitmap.createScaledBitmap(bmp, (bmp.width * r).toInt(), (bmp.height * r).toInt(), true)
        } else bmp
        val f = fileFor(key)
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    fun jpegBytes(path: String?): ByteArray? = path?.let { p ->
        val f = File(p)
        if (!f.exists()) null else {
            val bmp = BitmapFactory.decodeFile(p) ?: return null
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}
