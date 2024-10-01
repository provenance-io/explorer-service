package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
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

    val logger = logger(FlowApiPriceFetcher::class)
    override fun fetchHistoricalPrice(fromDate: DateTime?): List<HistoricalPrice> {
        logger.info("fetching navs from $fromDate")
        val onChainNavEvents = getMarkerNavByPriceDenoms(fromDate, 17800)
        return onChainNavEvents.map { navEvent ->
            val volumeHash = calculateVolumeHash(navEvent.volume)
            val pricePerHash = getPricePerHashFromMicroUsd(navEvent.priceAmount, navEvent.volume)
            HistoricalPrice(
                time = navEvent.blockTime,
                high = pricePerHash,
                low = pricePerHash,
                close = pricePerHash,
                open = pricePerHash,
                volume = pricePerHash.multiply(volumeHash)
            )
        }
    }

    fun getMarkerNavByPriceDenoms(fromDate: DateTime?, limit: Int): List<NavEvent> {
        return flowApiGrpcClient.getMarkerNavByPriceDenoms(denom, pricingDenoms, fromDate, limit)
    }

    fun calculateVolumeHash(volumeNhash: Long): BigDecimal {
        if (volumeNhash == 0L) {
            return BigDecimal.ZERO
        }
        return BigDecimal(volumeNhash).divide(ExplorerProperties.UTILITY_TOKEN_BASE_MULTIPLIER, 10, RoundingMode.HALF_UP)
    }

    /**
     * Calculates the price per hash unit based on the total price in micro-USD and the volume in nHash.
     *
     * @param priceAmountMicros The total price in micro-USD (e.g., 123456789 equals $123.456789 USD).
     * @param volumeNhash The volume of the transaction in nHash (nano Hash).
     *                    1 Hash = 1,000,000,000 nHash.
     * @return The price per hash unit in USD, rounded down to 3 decimal places.
     *         Returns 0.0 if the volumeNhash is 0 to avoid division by zero.
     */
    fun getPricePerHashFromMicroUsd(priceAmountMicros: Long, volumeNhash: Long): BigDecimal {
        if (volumeNhash == 0L) {
            return BigDecimal.ZERO
        }
        val volumeHash = calculateVolumeHash(volumeNhash)
        val priceInUsd = BigDecimal(priceAmountMicros).divide(BigDecimal(1_000_000), 10, RoundingMode.HALF_UP)
        val pricePerHash = priceInUsd.divide(volumeHash, 10, RoundingMode.HALF_UP)
        return pricePerHash.setScale(3, RoundingMode.FLOOR)
    }
}
