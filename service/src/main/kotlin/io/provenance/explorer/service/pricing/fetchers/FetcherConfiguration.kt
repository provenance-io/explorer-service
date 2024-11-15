package io.provenance.explorer.service.pricing.fetchers

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FetcherConfiguration {

    @Bean
    fun historicalPriceFetcherFactory(): HistoricalPriceFetcherFactory {
        return HistoricalPriceFetcherFactory()
    }
}
