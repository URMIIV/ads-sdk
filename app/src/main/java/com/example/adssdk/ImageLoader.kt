package com.example.adssdk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.URL
import java.util.concurrent.Executors

internal object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, view: ImageView) {
        if (url.isBlank()) return
        view.tag = url

        cache.get(url)?.let {
            view.setImageBitmap(it)
            return
        }

        executor.execute {
            try {
                val bmp = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    cache.put(url, bmp)
                    main.post {
                        if (view.tag == url) view.setImageBitmap(bmp)
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}