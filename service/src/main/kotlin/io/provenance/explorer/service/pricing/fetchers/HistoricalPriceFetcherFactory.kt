package io.provenance.explorer.service.pricing.fetchers

class HistoricalPriceFetcherFactory() {
    fun createNhashPricingFetchers(): List<HistoricalPriceFetcher> {
        return listOf(
            CoinGeckoPriceFetcher(),
        )
    }
}
