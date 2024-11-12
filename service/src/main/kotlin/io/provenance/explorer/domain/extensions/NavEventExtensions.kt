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

/**
 * Calculates the USD price per unit for a NAV event.
 *
 * The `priceAmount` is in dollar millis (e.g., 1234 = $1.234) and is divided by the volume to get the price per unit.
 * If the `priceDenom` is not "usd" or the volume is 0, it returns `BigDecimal.ZERO`.
 *
 * @return The USD price per unit or `BigDecimal.ZERO` if not a USD event or volume is 0.
 */
private fun calculateUsdPrice(priceDenom: String?, priceAmount: Long?, volume: Long): BigDecimal {
    if (priceDenom != USD_LOWER) {
        return BigDecimal.ZERO
    }

    if (volume == 0L) {
        return BigDecimal.ZERO
    }

    return BigDecimal(priceAmount ?: 0)
        .setScale(3, RoundingMode.DOWN)
        .divide(BigDecimal(1000), RoundingMode.DOWN)
        .divide(BigDecimal(volume), 3, RoundingMode.DOWN)
}
