package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.service.pricing.utils.HashCalculationUtils
import java.time.LocalDateTime
import java.time.ZoneOffset

class NavEventPriceFetcher(
    val denom: String,
    val pricingDenoms: List<String>
) : HistoricalPriceFetcher {

    override fun getSource(): String {
        return "navevent-table"
    }

    override fun fetchHistoricalPrice(fromDate: LocalDateTime?): List<HistoricalPrice> {
        val onChainNavEvents = NavEventsRecord.getNavEvents(denom = denom, priceDenoms = pricingDenoms, fromDate = fromDate)
        return onChainNavEvents.map { navEvent ->
            val volumeHash = HashCalculationUtils.calculateVolumeHash(navEvent.volume)
            val pricePerHash = HashCalculationUtils.getPricePerHashFromMicroUsd(navEvent.priceAmount!!, navEvent.volume)
            HistoricalPrice(
                time = navEvent.blockTime.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000,
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
