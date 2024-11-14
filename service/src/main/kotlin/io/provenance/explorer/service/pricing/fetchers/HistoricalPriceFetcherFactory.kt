package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN

class HistoricalPriceFetcherFactory() {
    fun createNhashPricingFetchers(): List<HistoricalPriceFetcher> {
        return listOf(
            OsmosisPriceFetcher(),
            NavEventPriceFetcher(UTILITY_TOKEN, listOf("uusd.trading", "uusdc.figure.se", "uusdt.figure.se"))
        )
    }
}
