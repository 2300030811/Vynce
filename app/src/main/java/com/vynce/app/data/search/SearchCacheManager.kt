package com.vynce.app.data.search

import android.util.LruCache
import com.vynce.app.models.UnifiedSearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchCacheManager @Inject constructor() {
    private val cache = LruCache<String, UnifiedSearchResult>(30)

    fun get(query: String): UnifiedSearchResult? {
        return synchronized(cache) {
            cache.get(query.lowercase().trim())
        }
    }

    fun put(query: String, result: UnifiedSearchResult) {
        synchronized(cache) {
            cache.put(query.lowercase().trim(), result)
        }
    }

    fun remove(query: String) {
        synchronized(cache) {
            cache.remove(query.lowercase().trim())
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}
