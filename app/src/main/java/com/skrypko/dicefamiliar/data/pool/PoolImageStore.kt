package com.skrypko.dicefamiliar.data.pool

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Item images in `filesDir/pool_images/`. Files get random names; the database stores only the name.
 * Images are scaled down to at most [MAX_IMAGE_PX] on the long side.
 */
@Singleton
class PoolImageStore @Inject constructor(@ApplicationContext context: Context) {
    val dir = File(context.filesDir, DIR)
    private val cache = File(context.cacheDir, "pool_tmp")

    fun file(path: String): File = File(dir, path)

    /** A fresh folder in the cache for unpacking an import; the caller deletes it. */
    fun newStagingDir(): File = File(cache, "import_${UUID.randomUUID()}")

    /** Copies an image from [input] (≤ [MAX_IMAGE_BYTES]); `null` if it isn't a readable image. */
    fun store(input: InputStream): String? {
        cache.mkdirs()
        val tmp = File(cache, UUID.randomUUID().toString())
        return try {
            val size = tmp.outputStream().use { out -> copyCapped(input, out, MAX_IMAGE_BYTES) }
            if (size > MAX_IMAGE_BYTES) null else store(tmp)
        } catch (e: IOException) {
            Log.w(TAG, "Can't read image", e)
            null
        } finally {
            tmp.delete()
        }
    }

    /** Copies an image file into the store, scaling it down if needed; `null` if it isn't a readable image. */
    fun store(source: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val ext = when (bounds.outMimeType) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            else -> return null
        }
        dir.mkdirs()
        return try {
            if (maxOf(bounds.outWidth, bounds.outHeight) <= MAX_IMAGE_PX) {
                val name = "${UUID.randomUUID()}.$ext"
                source.copyTo(File(dir, name))
                name
            } else {
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
                val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
                val scale = MAX_IMAGE_PX.toFloat() / maxOf(decoded.width, decoded.height)
                val scaled = if (scale < 1f) {
                    decoded.scale((decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1))
                } else {
                    decoded
                }
                val jpeg = ext == "jpg"
                val name = "${UUID.randomUUID()}.${if (jpeg) "jpg" else "png"}"
                File(dir, name).outputStream().use {
                    scaled.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, JPEG_QUALITY, it)
                }
                name
            }
        } catch (e: IOException) {
            Log.w(TAG, "Can't store image", e)
            null
        }
    }

    fun delete(paths: Collection<String>) {
        paths.forEach { path -> if ('/' !in path && path.isNotEmpty()) File(dir, path).delete() }
    }

    /** Deletes stored images that nothing references. */
    fun deleteAllExcept(referenced: Set<String>) {
        dir.listFiles()?.forEach { if (it.isFile && it.name !in referenced) it.delete() }
    }

    private fun copyCapped(input: InputStream, out: java.io.OutputStream, cap: Long): Long {
        var copied = 0L
        val buffer = ByteArray(16 * 1024)
        while (copied <= cap) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            copied += n
        }
        return copied
    }

    companion object {
        const val DIR = "pool_images"
        const val MAX_IMAGE_PX = 1024
        const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
        private const val JPEG_QUALITY = 90
        private const val TAG = "PoolImages"
    }
}
