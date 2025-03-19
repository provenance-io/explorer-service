package io.provenance.explorer.domain.extensions

import io.provenance.explorer.model.base.USD_LOWER
import java.math.BigDecimal
import java.math.RoundingMode

fun io.provlabs.flow.api.NavEvent.calculateUsdPricePerUnit(): BigDecimal {
    return calculateUsdPrice(this.priceDenom, this.priceAmount, this.volume)
}

fun io.provenance.explorer.domain.entities.NavEvent.calculateUsdPricePerUnit(): BigDecimal {
    return calculateUsdPrice(this.priceDenom, this.priceAmount, this.volume)
}

val usdPriceDenoms = listOf(USD_LOWER, "uusd.trading", "uusdc.figure.se", "uusdt.figure.se")

/**
 * Calculates the USD price per unit for a NAV event.
 *
 * The `priceAmount` will either be in dollar millis (e.g., 1234 = $1.234) or micro (e.g., 1234567 = $1.234567)
 * and is divided by the volume to get the price per unit.
 * If the `priceDenom` is not a "usd" equivalent or the volume is 0, it returns `BigDecimal.ZERO`.
 *
 * @return The USD price per unit or `BigDecimal.ZERO` if not a USD event or volume is 0.
 */
private fun calculateUsdPrice(priceDenom: String?, priceAmount: Long?, volume: Long): BigDecimal {
    if (priceDenom !in usdPriceDenoms) {
        return BigDecimal.ZERO
    }

    if (volume == 0L) {
        return BigDecimal.ZERO
    }

    val divisor = when (priceDenom) {
        USD_LOWER -> 1000
        else -> 1000000
    }
    return BigDecimal(priceAmount ?: 0)
        .setScale(3, RoundingMode.DOWN)
        .divide(BigDecimal(divisor), RoundingMode.DOWN)
        .divide(BigDecimal(volume), 3, RoundingMode.DOWN)
}
