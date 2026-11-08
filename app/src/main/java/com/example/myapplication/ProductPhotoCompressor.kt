package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

internal object ProductPhotoCompressor {
    fun compressBitmap(context: Context, source: Bitmap): File? {
      return try {
        var bitmap = source
        val output = ByteArrayOutputStream()
        var quality = 82
        fun encode() {
            output.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        }
        encode()
        while (output.size() > S3PhotoManager.MAX_PHOTO_BYTES && quality > 35) {
            quality -= 10
            encode()
        }
        while (output.size() > S3PhotoManager.MAX_PHOTO_BYTES && bitmap.width > 100) {
            bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * .75).toInt().coerceAtLeast(100),
                (bitmap.height * .75).toInt().coerceAtLeast(100), true)
            quality = 72
            encode()
        }
        if (output.size() > S3PhotoManager.MAX_PHOTO_BYTES) return null
        val file = File(context.cacheDir, "product_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { it.write(output.toByteArray()) }
        file
      } catch (_: Exception) { null }
    }

    fun compress(context: Context, uri: Uri): File? {
      return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1400 || bounds.outHeight / sampleSize > 1400) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val bytes = ByteArrayOutputStream()
        var quality = 82
        fun encode() {
            bytes.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
        }
        encode()
        while (bytes.size() > S3PhotoManager.MAX_PHOTO_BYTES && quality > 35) {
            quality -= 10
            encode()
        }
        while (bytes.size() > S3PhotoManager.MAX_PHOTO_BYTES && bitmap.width > 100) {
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.75).toInt().coerceAtLeast(100),
                (bitmap.height * 0.75).toInt().coerceAtLeast(100),
                true
            )
            quality = 72
            encode()
        }
        if (bytes.size() > S3PhotoManager.MAX_PHOTO_BYTES) return null

        val file = File(context.cacheDir, "product_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { it.write(bytes.toByteArray()) }
        file
      } catch (_: Exception) {
        null
      }
    }
}
