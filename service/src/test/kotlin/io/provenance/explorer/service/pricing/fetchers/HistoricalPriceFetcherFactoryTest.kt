package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.net.URI

class HistoricalPriceFetcherFactoryTest {

    private lateinit var flowApiGrpcClient: FlowApiGrpcClient
    private lateinit var factory: HistoricalPriceFetcherFactory

    @BeforeEach
    fun setUp() {
        flowApiGrpcClient = FlowApiGrpcClient(URI("http://localhost:50051"))
        factory = HistoricalPriceFetcherFactory(flowApiGrpcClient)
    }

    @Test
    fun `test createNhashFetchers`() {
        val fetchers = factory.createNhashFetchers()
        assertEquals(2, fetchers.size)
        assertTrue(fetchers[0] is OsmosisPriceFetcher)
        assertTrue(fetchers[1] is FlowApiPriceFetcher)

        val flowApiPriceFetcher = fetchers[1] as FlowApiPriceFetcher
        assertEquals(UTILITY_TOKEN, flowApiPriceFetcher.denom)
        assertEquals(listOf("uusd.trading", "uusdc.figure.se"), flowApiPriceFetcher.pricingDenoms)
    }

    @Test
    fun `test createOsmosisPriceFetcher`() {
        val fetchers = factory.createOsmosisPriceFetcher()
        assertEquals(1, fetchers.size)
        assertTrue(fetchers[0] is OsmosisPriceFetcher)
    }
}
