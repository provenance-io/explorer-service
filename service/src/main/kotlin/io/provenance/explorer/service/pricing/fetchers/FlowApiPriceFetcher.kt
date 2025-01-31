package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.service.pricing.utils.HashCalculationUtils
import io.provlabs.flow.api.NavEvent
import java.time.LocalDateTime

class FlowApiPriceFetcher(
    val denom: String,
    val pricingDenoms: List<String>,
    private val flowApiGrpcClient: FlowApiGrpcClient
) : HistoricalPriceFetcher {

    override fun getSource(): String {
        return "flow-api"
    }
    override fun fetchHistoricalPrice(fromDate: LocalDateTime?): List<HistoricalPrice> {
        val onChainNavEvents = getMarkerNavByPriceDenoms(fromDate)
        return onChainNavEvents.map { navEvent ->
            val volumeHash = HashCalculationUtils.calculateVolumeHash(navEvent.volume)
            val pricePerHash = HashCalculationUtils.getPricePerHashFromMicroUsd(navEvent.priceAmount, navEvent.volume)
            HistoricalPrice(
                time = navEvent.blockTime,
                high = pricePerHash,
                low = pricePerHash,
                close = pricePerHash,
                open = pricePerHash,
                volume = pricePerHash.multiply(volumeHash),
                source = getSource()
            )
        }
    }

    fun getMarkerNavByPriceDenoms(fromDate: LocalDateTime?): List<NavEvent> {
        return flowApiGrpcClient.getAllMarkerNavByPriceDenoms(denom = denom, priceDenoms = pricingDenoms, fromDate = fromDate)
    }
}
