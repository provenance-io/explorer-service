package io.provenance.explorer.config

import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig {
    init {
        println("INIT CacheConfig")
    }

    @Bean
    fun cacheManager() =
        CaffeineCacheManager("responses").apply {
            setCaffeine(caffieneConfig())
        }.also {
            println("Configuring CacheManager")
        }

    fun caffieneConfig() =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(100)
}
