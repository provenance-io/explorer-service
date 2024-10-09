package io.provenance.explorer.domain.extensions

import io.provlabs.flow.api.NavEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NavEventExtensionsKtTest {

    @Test
    fun `test calculateUsdPricePerUnit for flow nav events`() {
        val usdDenom = "usd"
        val nonUsdDenom = "non-usd"

        val navEventUsdVolume1Price1 = NavEvent.newBuilder()
            .setPriceAmount(1)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventUsdVolume1Price12 = NavEvent.newBuilder()
            .setPriceAmount(12)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventUsdVolume1Price123 = NavEvent.newBuilder()
            .setPriceAmount(123)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventUsdVolume1Price1234 = NavEvent.newBuilder()
            .setPriceAmount(1234)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventNonUsd = NavEvent.newBuilder()
            .setPriceAmount(1234)
            .setPriceDenom(nonUsdDenom)
            .setVolume(1L)
            .build()

        val navEventZeroVolume = NavEvent.newBuilder()
            .setPriceAmount(1234)
            .setPriceDenom(usdDenom)
            .setVolume(0L)
            .build()

        assertEquals(BigDecimal("0.001"), navEventUsdVolume1Price1.calculateUsdPricePerUnit(), "Price amount 1 should be converted to 0.001")
        assertEquals(BigDecimal("0.012"), navEventUsdVolume1Price12.calculateUsdPricePerUnit(), "Price amount 12 should be converted to 0.012")
        assertEquals(BigDecimal("0.123"), navEventUsdVolume1Price123.calculateUsdPricePerUnit(), "Price amount 123 should be converted to 0.123")
        assertEquals(BigDecimal("1.234"), navEventUsdVolume1Price1234.calculateUsdPricePerUnit(), "Price amount 1234 should be converted to 1.234")
        assertEquals(BigDecimal.ZERO, navEventNonUsd.calculateUsdPricePerUnit(), "Non-USD denomination should return 0")
        assertEquals(BigDecimal.ZERO, navEventZeroVolume.calculateUsdPricePerUnit(), "Zero volume should return 0")
    }
}
