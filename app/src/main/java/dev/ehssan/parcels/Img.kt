package dev.ehssan.parcels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object Img {
    private const val MAX = 1280

    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "photos")
        d.mkdirs()
        return d
    }

    fun file(ctx: Context, name: String) = File(dir(ctx), name)

    /** Reads an image, applies its EXIF rotation, downsizes it and stores it privately. Returns the file name. */
    fun saveScaled(ctx: Context, uri: Uri, prefix: String): String? {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2 * MAX) sample *= 2
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            var rot = 0
            try {
                cr.openInputStream(uri)?.use { s ->
                    rot = when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) {
                rot = 0
            }
            val m = Matrix()
            val scale = minOf(1f, MAX.toFloat() / maxOf(bmp.width, bmp.height))
            m.postScale(scale, scale)
            if (rot != 0) m.postRotate(rot.toFloat())
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val name = prefix + "_" + System.currentTimeMillis() + ".jpg"
            FileOutputStream(File(dir(ctx), name)).use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            name
        } catch (e: Exception) {
            null
        }
    }

    fun decode(f: File, maxSide: Int): Bitmap? {
        if (!f.exists()) return null
        val b = BitmapFactory.Options()
        b.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val o = BitmapFactory.Options()
        o.inSampleSize = sample
        return BitmapFactory.decodeFile(f.path, o)
    }
}

object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, name: String, maxSide: Int, done: (Bitmap?) -> Unit) {
        val key = "$name@$maxSide"
        val hit = cache.get(key)
        if (hit != null) {
            done(hit)
            return
        }
        val f = Img.file(ctx, name)
        pool.execute {
            val b = try {
                Img.decode(f, maxSide)
            } catch (e: Throwable) {
                null
            }
            if (b != null) cache.put(key, b)
            main.post { done(b) }
        }
    }
}
