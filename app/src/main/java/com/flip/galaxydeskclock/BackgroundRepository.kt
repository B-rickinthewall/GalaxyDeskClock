package com.flip.galaxydeskclock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class BackgroundRepository(private val context: Context) {
    private val directory = File(context.filesDir, "backgrounds").apply { mkdirs() }

    fun list(): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
        ?.sortedBy { it.name }
        .orEmpty()

    fun count(): Int = list().size

    fun remove(file: File): Boolean {
        val canonicalParent = file.canonicalFile.parentFile
        return canonicalParent == directory.canonicalFile && file.delete()
    }

    fun clear() {
        list().forEach { it.delete() }
    }

    fun importUris(uris: List<Uri>): Int {
        var imported = 0
        uris.forEach { uri ->
            runCatching {
                val rotation = readRotation(uri)
                val bitmap = decodeScaled(uri, MAX_IMPORT_WIDTH, MAX_IMPORT_HEIGHT)
                    ?: error("Unable to decode image")
                val rotated = rotateIfNeeded(bitmap, rotation)
                val normalized = scaleInside(rotated, MAX_IMPORT_WIDTH, MAX_IMPORT_HEIGHT)
                val file = File(directory, "bg_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { output ->
                    check(normalized.compress(Bitmap.CompressFormat.JPEG, 90, output))
                }
                if (normalized !== rotated) normalized.recycle()
                if (rotated !== bitmap) rotated.recycle()
                bitmap.recycle()
                imported++
            }
        }
        return imported
    }

    fun decodeFile(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        val safeWidth = targetWidth.coerceAtLeast(1)
        val safeHeight = targetHeight.coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateSample(bounds.outWidth, bounds.outHeight, safeWidth, safeHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun readRotation(uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    private fun decodeScaled(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateSample(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun calculateSample(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun rotateIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleInside(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scale = minOf(maxWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat(), 1f)
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        private const val MAX_IMPORT_WIDTH = 2560
        private const val MAX_IMPORT_HEIGHT = 1440
    }
}
