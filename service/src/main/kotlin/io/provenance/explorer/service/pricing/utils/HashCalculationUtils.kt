package io.provenance.explorer.service.pricing.utils

import io.provenance.explorer.config.ExplorerProperties
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Utility class for handling hash-related calculations
 */
object HashCalculationUtils {

    /**
     * Converts volume from nHash (nano Hash) to Hash units.
     * 1 Hash = 1,000,000,000 nHash
     *
     * @param volumeNhash The volume in nHash units
     * @return The volume converted to Hash units, with 10 decimal places precision
     */
    fun calculateVolumeHash(volumeNhash: Long): BigDecimal {
        if (volumeNhash == 0L) {
            return BigDecimal.ZERO
        }
        return BigDecimal(volumeNhash).divide(
            ExplorerProperties.UTILITY_TOKEN_BASE_MULTIPLIER,
            10,
            RoundingMode.HALF_UP
        )
    }

    /**
     * Calculates the price per hash unit based on the total price in micro-USD and the volume in nHash.
     *
     * @param priceAmountMicros The total price in micro-USD (e.g., 123456789 equals $123.456789 USD)
     * @param volumeNhash The volume of the transaction in nHash (nano Hash). 1 Hash = 1,000,000,000 nHash
     * @return The price per hash unit in USD, rounded down to 3 decimal places.
     *         Returns 0.0 if the volumeNhash is 0 to avoid division by zero.
     */
    fun getPricePerHashFromMicroUsd(priceAmountMicros: Long, volumeNhash: Long): BigDecimal {
        if (volumeNhash == 0L) {
            return BigDecimal.ZERO
        }
        val volumeHash = calculateVolumeHash(volumeNhash)
        val priceInUsd = BigDecimal(priceAmountMicros).divide(
            BigDecimal(1_000_000),
            10,
            RoundingMode.HALF_UP
        )
        val pricePerHash = priceInUsd.divide(volumeHash, 10, RoundingMode.HALF_UP)
        return pricePerHash.setScale(3, RoundingMode.FLOOR)
    }
}
