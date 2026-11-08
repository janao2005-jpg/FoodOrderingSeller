package com.example.myapplication

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object RemoteImageLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, target: ImageView) {
        target.setImageResource(R.drawable.food_image_placeholder)
        target.tag = url
        if (!url.startsWith("https://", ignoreCase = true)) return
        executor.execute {
            val bitmap = try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                // S3 can redirect to its regional endpoint. Following redirects is
                // required for those objects to render instead of leaving the placeholder.
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "image/*")
                try {
                    if (connection.responseCode !in 200..299 || connection.contentLengthLong > 5_000_000L) null
                    else connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally { connection.disconnect() }
            } catch (_: Exception) { null }
            if (bitmap != null) main.post { if (target.tag == url) target.setImageBitmap(bitmap) }
        }
    }

    fun loadProduct(photoKey: String, target: ImageView) {
        target.setImageResource(R.drawable.food_image_placeholder)
        target.tag = photoKey
        if (photoKey.isBlank()) return
        S3PhotoManager().downloadProductPhoto(photoKey) { bitmap, _ ->
            if (bitmap != null && target.tag == photoKey) target.setImageBitmap(bitmap)
        }
    }
}
