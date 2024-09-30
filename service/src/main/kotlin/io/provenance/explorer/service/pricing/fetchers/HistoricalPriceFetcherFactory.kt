package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient

class HistoricalPriceFetcherFactory(
    private val flowApiGrpcClient: FlowApiGrpcClient
) {
    fun createNhashFetchers(): List<HistoricalPriceFetcher> {
        return listOf(
            OsmosisPriceFetcher(),
            FlowApiPriceFetcher(UTILITY_TOKEN, listOf("uusd.trading", "uusdc.figure.se"), flowApiGrpcClient)
        )
    }

    fun createOsmosisPriceFetcher(): List<HistoricalPriceFetcher> {
        return listOf(
            OsmosisPriceFetcher()
        )
    }
}
