package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provlabs.flow.api.NavEvent
import org.joda.time.DateTime
import java.math.BigDecimal
import java.math.RoundingMode

class FlowApiPriceFetcher(
    val denom: String,
    val pricingDenoms: List<String>,
    private val flowApiGrpcClient: FlowApiGrpcClient
) : HistoricalPriceFetcher {

    override fun fetchHistoricalPrice(fromDate: DateTime?): List<HistoricalPrice> {
        val onChainNavEvents = getMarkerNavByPriceDenoms(fromDate, 17800)
        return onChainNavEvents.map { navEvent ->
            val volumeHash = calculateVolumeHash(navEvent.volume)
            val pricePerHash = calculatePricePerHash(navEvent.priceAmount, navEvent.volume)
            HistoricalPrice(
                time = navEvent.blockTime,
                high = BigDecimal(pricePerHash),
                low = BigDecimal(pricePerHash),
                close = BigDecimal(pricePerHash),
                open = BigDecimal(pricePerHash),
                volume = BigDecimal(pricePerHash).multiply(volumeHash)
            )
        }
    }

    fun getMarkerNavByPriceDenoms(fromDate: DateTime?,  limit: Int): List<NavEvent>{
        return flowApiGrpcClient.getMarkerNavByPriceDenoms(denom, pricingDenoms, fromDate, limit)
    }

    fun calculateVolumeHash(volumeNhash: Long): BigDecimal {
        if (volumeNhash == 0L) {
            return BigDecimal.ZERO
        }
        return BigDecimal(volumeNhash).divide(ExplorerProperties.UTILITY_TOKEN_BASE_MULTIPLIER, 10, RoundingMode.HALF_UP)
    }

    /**
     * Calculates the price per hash unit based on the total price in USD (expressed as whole numbers
     * where 12345 equals $12.345 USD) and the volume in nHash (nano Hash).
     *
     * @param priceAmount The total price in whole-number USD cents (e.g., 12345 equals $12.345 USD).
     * @param volumeNhash The volume of the transaction in nHash (nano Hash).
     *                    1 Hash = 1,000,000,000 nHash.
     * @return The price per hash unit. Returns 0.0 if the volumeNhash is 0 to avoid division by zero.
     */
    fun calculatePricePerHash(priceAmountMillis: Long, volumeNhash: Long): Double {
        val volumeHash = io.provenance.explorer.service.calculateVolumeHash(volumeNhash)
        if (volumeHash == BigDecimal.ZERO) {
            return 0.0
        }
        val pricePerHash = BigDecimal(priceAmountMillis).divide(volumeHash, 10, RoundingMode.HALF_UP)
        return pricePerHash.divide(BigDecimal(1000), 10, RoundingMode.HALF_UP).toDouble()
    }
}
