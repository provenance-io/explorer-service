import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {
    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = CaffeineCacheManager("responses")
        cacheManager.setCaffeine(
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS) // Cache expires after 10 seconds
                .maximumSize(100)
        ) // Optional, limits the number of cached items
        return cacheManager
    }
}
