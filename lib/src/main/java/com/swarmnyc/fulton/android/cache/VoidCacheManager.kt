package com.swarmnyc.fulton.android.cache

import java.lang.reflect.Type

class VoidCacheManager : CacheManager {
    override fun add(cls: String, url: String, durationMs: Int, data: ByteArray) {}

    override fun <T> get(url: String, type: Type): T? {
        return null
    }

    override fun clean(cls: String?) {}
}