package io.provenance.explorer.service

import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.CacheKeys
import io.provenance.explorer.domain.entities.CacheUpdateRecord
import io.provenance.explorer.model.Spotlight
import org.springframework.stereotype.Service

@Service
class CacheService {

    @Volatile
    private var cachedSpotlight: Spotlight? = null

    protected val logger = logger(CacheService::class)

    fun updateSpotlight(spotlightResponse: Spotlight) {
        cachedSpotlight = spotlightResponse
    }

    fun getSpotlight(): Spotlight? {
        return cachedSpotlight
    }

    fun getCacheValue(key: String) = CacheUpdateRecord.fetchCacheByKey(key)

    fun updateCacheValue(key: String, value: String) = CacheUpdateRecord.updateCacheByKey(key, value)

    fun getAvgBlockTime() =
        getSpotlight()?.avgBlockTime ?: getCacheValue(CacheKeys.STANDARD_BLOCK_TIME.key)!!.cacheValue!!.toBigDecimal()
}
