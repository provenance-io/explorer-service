package io.provenance.explorer.domain.extensions

import org.joda.time.DateTime
import io.provlabs.flow.api.NavEvent as FlowNavEvent
import io.provenance.explorer.domain.entities.NavEvent as ExplorerNavEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NavEventExtensionsKtTest {

    @Test
    fun `test calculateUsdPricePerUnit for flow NavEvent`() {
        val usdDenom = "usd"
        val nonUsdDenom = "non-usd"

        val navEventUsdVolume1Price1 = FlowNavEvent.newBuilder()
            .setPriceAmount(1)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventUsdVolume1Price12 = FlowNavEvent.newBuilder()
            .setPriceAmount(12)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventUsdVolume1Price123 = FlowNavEvent.newBuilder()
            .setPriceAmount(123)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventUsdVolume1Price1234 = FlowNavEvent.newBuilder()
            .setPriceAmount(1234)
            .setPriceDenom(usdDenom)
            .setVolume(1L)
            .build()

        val navEventNonUsd = FlowNavEvent.newBuilder()
            .setPriceAmount(1234)
            .setPriceDenom(nonUsdDenom)
            .setVolume(1L)
            .build()

        val navEventZeroVolume = FlowNavEvent.newBuilder()
            .setPriceAmount(1234)
            .setPriceDenom(usdDenom)
            .setVolume(0L)
            .build()

        assertEquals(BigDecimal("0.001"), navEventUsdVolume1Price1.calculateUsdPricePerUnit())
        assertEquals(BigDecimal("0.012"), navEventUsdVolume1Price12.calculateUsdPricePerUnit())
        assertEquals(BigDecimal("0.123"), navEventUsdVolume1Price123.calculateUsdPricePerUnit())
        assertEquals(BigDecimal("1.234"), navEventUsdVolume1Price1234.calculateUsdPricePerUnit())
        assertEquals(BigDecimal.ZERO, navEventNonUsd.calculateUsdPricePerUnit())
        assertEquals(BigDecimal.ZERO, navEventZeroVolume.calculateUsdPricePerUnit())
    }

    @Test
    fun `test calculateUsdPricePerUnit for explorer NavEvent`() {
        val usdDenom = "usd"
        val nonUsdDenom = "non-usd"

        val navEventUsdVolume1Price1 = ExplorerNavEvent(
            blockHeight = 1,
            blockTime = DateTime.now(),
            txHash = "hash1",
            eventOrder = 1,
            eventType = "type",
            scopeId = "scope1",
            denom = "denom1",
            priceAmount = 1,
            priceDenom = usdDenom,
            volume = 1L,
            source = "source1"
        )

        val navEventUsdVolume1Price12 = navEventUsdVolume1Price1.copy(priceAmount = 12)
        val navEventUsdVolume1Price123 = navEventUsdVolume1Price1.copy(priceAmount = 123)
        val navEventUsdVolume1Price1234 = navEventUsdVolume1Price1.copy(priceAmount = 1234)
        val navEventNonUsd = navEventUsdVolume1Price1.copy(priceDenom = nonUsdDenom)
        val navEventZeroVolume = navEventUsdVolume1Price1.copy(volume = 0L)

        assertEquals(BigDecimal("0.001"), navEventUsdVolume1Price1.calculateUsdPricePerUnit())
        assertEquals(BigDecimal("0.012"), navEventUsdVolume1Price12.calculateUsdPricePerUnit())
        assertEquals(BigDecimal("0.123"), navEventUsdVolume1Price123.calculateUsdPricePerUnit())
        assertEquals(BigDecimal("1.234"), navEventUsdVolume1Price1234.calculateUsdPricePerUnit())
        assertEquals(BigDecimal.ZERO, navEventNonUsd.calculateUsdPricePerUnit())
        assertEquals(BigDecimal.ZERO, navEventZeroVolume.calculateUsdPricePerUnit())
    }
}
