package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient

class HistoricalPriceFetcherFactory(
    private val flowApiGrpcClient: FlowApiGrpcClient
) {
    fun createNhashPricingFetchers(): List<HistoricalPriceFetcher> {
        return listOf(
            OsmosisPriceFetcher(),
            FlowApiPriceFetcher(UTILITY_TOKEN, listOf("uusd.trading", "uusdc.figure.se", "uusdt.figure.se"), flowApiGrpcClient)
        )
    }
}
