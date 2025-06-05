package io.provenance.explorer.service.pricing.fetchers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HistoricalPriceFetcherFactoryTest {

    private lateinit var factory: HistoricalPriceFetcherFactory

    @BeforeEach
    fun setUp() {
        factory = HistoricalPriceFetcherFactory()
    }

    @Test
    fun `test createNhashPricingFetchers`() {
        val fetchers = factory.createNhashPricingFetchers()
        assertEquals(1, fetchers.size)
        assertTrue(fetchers[0] is CoinGeckoPriceFetcher)
        assertEquals("coin-gecko", fetchers[0].getSource(), "Fetcher source is incorrect")
    }
}
