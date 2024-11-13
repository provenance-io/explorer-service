package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.service.pricing.utils.HashCalculationUtils
import org.joda.time.DateTime

class NavEventPriceFetcher(
    val denom: String,
    val pricingDenoms: List<String>,
    private val flowApiGrpcClient: FlowApiGrpcClient
) : HistoricalPriceFetcher {

    override fun getSource(): String {
        return "navevent-table"
    }
    override fun fetchHistoricalPrice(fromDate: DateTime?): List<HistoricalPrice> {
        val onChainNavEvents = NavEventsRecord.getNavEvents(denom = denom, priceDenoms = pricingDenoms, fromDate = fromDate)
        return onChainNavEvents.map { navEvent ->
            val volumeHash = HashCalculationUtils.calculateVolumeHash(navEvent.volume)
            val pricePerHash = HashCalculationUtils.getPricePerHashFromMicroUsd(navEvent.priceAmount!!, navEvent.volume)
            HistoricalPrice(
                time = navEvent.blockTime.millis / 1000,
                high = pricePerHash,
                low = pricePerHash,
                close = pricePerHash,
                open = pricePerHash,
                volume = pricePerHash.multiply(volumeHash),
                source = getSource()
            )
        }
    }
}
