package com.natkibe.videoplayerpro.thumbnail

import android.graphics.Bitmap
import android.util.LruCache

class ThumbnailMemoryPolicy(maxEntries: Int = 32) {
    private val cache = object : LruCache<String, Bitmap>(maxEntries) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)

    fun clear() = cache.evictAll()

    fun trimToSize(maxSize: Int) = cache.trimToSize(maxSize)

    /** Returns human-readable cache info for diagnostics. */
    fun cacheInfo(): String = "${cache.size()} entries / max ${cache.maxSize()}"
}
