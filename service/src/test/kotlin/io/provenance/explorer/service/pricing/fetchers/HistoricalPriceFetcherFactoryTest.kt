package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI

class HistoricalPriceFetcherFactoryTest {

    private lateinit var factory: HistoricalPriceFetcherFactory

    @BeforeEach
    fun setUp() {
        factory = HistoricalPriceFetcherFactory()
    }

    @Test
    fun `test createNhashPricingFetchers`() {
        val fetchers = factory.createNhashPricingFetchers()
        assertEquals(2, fetchers.size)
        assertTrue(fetchers[0] is OsmosisPriceFetcher)
        assertEquals("osmosis", fetchers[0].getSource(), "Fetcher source is incorrect")
        assertTrue(fetchers[1] is NavEventPriceFetcher)
        assertEquals("navevent-table", fetchers[1].getSource(), "Fetcher source is incorrect")

        val navEventPriceFetcher = fetchers[1] as NavEventPriceFetcher
        assertEquals(UTILITY_TOKEN, navEventPriceFetcher.denom)
        assertEquals(listOf("uusd.trading", "uusdc.figure.se", "uusdt.figure.se"), navEventPriceFetcher.pricingDenoms)
    }
}
