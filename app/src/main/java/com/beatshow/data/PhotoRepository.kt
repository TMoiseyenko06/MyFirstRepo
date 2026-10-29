package com.beatshow.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Photos copied into internal storage, so they survive reboots and expired picker grants. */
class PhotoRepository(private val context: Context) {

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val _photos = MutableStateFlow(scan())
    val photos: StateFlow<List<File>> = _photos.asStateFlow()

    private fun scan(): List<File> =
        dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }?.sortedBy { it.lastModified() } ?: emptyList()

    /** Imports [uris] and returns how many failed. */
    suspend fun import(uris: List<Uri>, onProgress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        var failed = 0
        uris.forEachIndexed { i, uri ->
            if (runCatching { importOne(uri) }.isFailure) failed++
            onProgress(i + 1)
            if (i % 5 == 4) _photos.value = scan()
        }
        _photos.value = scan()
        failed
    }

    private fun importOne(uri: Uri) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        val mime = bounds.outMimeType ?: resolver.getType(uri)
        val longEdge = maxOf(width, height)

        val copyOriginal = longEdge in 1..MAX_EDGE && mime in COPYABLE
        val target = File(dir, UUID.randomUUID().toString() + if (copyOriginal) extensionFor(mime) else ".jpg")
        val tmp = File(dir, target.name + ".tmp")
        try {
            if (copyOriginal) {
                // Small enough already: copy the original bytes (EXIF orientation is kept).
                resolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } else {
                writeDownscaled(uri, width, height, tmp)
            }
            check(tmp.renameTo(target))
        } finally {
            tmp.delete()
        }
    }

    private fun writeDownscaled(uri: Uri, width: Int, height: Int, out: File) {
        val resolver = context.contentResolver
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Cannot decode $uri")

        val orientation = runCatching {
            resolver.openInputStream(uri)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        out.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (result !== decoded) result.recycle()
        decoded.recycle()
    }

    private fun extensionFor(mime: String?) = when (mime) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }

    fun delete(files: Collection<File>) {
        files.forEach { it.delete() }
        _photos.value = scan()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
        _photos.value = scan()
    }

    private companion object {
        const val MAX_EDGE = 2560
        val COPYABLE = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
    }
}
