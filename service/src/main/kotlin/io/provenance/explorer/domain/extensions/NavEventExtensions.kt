package io.provenance.explorer.domain.extensions

import io.provenance.explorer.model.base.USD_LOWER
import io.provlabs.flow.api.NavEvent
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculates the USD price per unit for a NAV event.
 *
 * The `priceAmount` is in dollar millis (e.g., 1234 = $1.234) and is divided by the volume to get the price per unit.
 * If the `priceDenom` is not "usd" or the volume is 0, it returns `BigDecimal.ZERO`.
 *
 * @return The USD price per unit or `BigDecimal.ZERO` if not a USD event or volume is 0.
 */
fun NavEvent.calculateUsdPricePerUnit(): BigDecimal {
    if (this.priceDenom != USD_LOWER) {
        return BigDecimal.ZERO
    }

    if (this.volume == 0L) {
        return BigDecimal.ZERO
    }

    return BigDecimal(this.priceAmount)
        .setScale(3, RoundingMode.DOWN)
        .divide(BigDecimal(1000), RoundingMode.DOWN)
        .divide(BigDecimal(this.volume), 3, RoundingMode.DOWN)
}
