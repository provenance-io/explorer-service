package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provlabs.flow.api.NavEvent
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

class FlowApiPriceFetcherTest {

    private lateinit var flowApiPriceFetcher: FlowApiPriceFetcher

    @BeforeEach
    fun setUp() {
        flowApiPriceFetcher = FlowApiPriceFetcher(
            ExplorerProperties.UTILITY_TOKEN,
            listOf("uusd.trading", "uusdc.figure.se"),
            FlowApiGrpcClient(URI("http://localhost:50051"))
        )
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchOnChainNavData and print results`() {
        val fromDate = DateTime.now().minusDays(7)
        val limit = 100

        val result: List<NavEvent> = flowApiPriceFetcher.getMarkerNavByPriceDenoms(fromDate, limit)

        val groupedByPriceDenom = result.groupBy { it.priceDenom }

        groupedByPriceDenom.forEach { (priceDenom, events) ->
            println("PriceDenom: $priceDenom, Count: ${events.size}")
        }

        result.forEach { navEvent ->
            val pricePerHash = flowApiPriceFetcher.calculatePricePerHash(navEvent.priceAmount, navEvent.volume)
            println("NavEvent: Time=${DateTime(navEvent.blockTime * 1000)}, PriceDenom=${navEvent.priceDenom}, Hash Price: $pricePerHash")
        }

        assert(result.isNotEmpty()) { "Expected non-empty NavEvent list" }
    }

    @Test
    fun `test calculatePricePerHash with multiple scenarios`() {
        var result = flowApiPriceFetcher.calculatePricePerHash(12345L, ExplorerProperties.UTILITY_TOKEN_BASE_MULTIPLIER.toLong())
        assertEquals(12.345, result, "Price per hash calculation is incorrect")

        result = flowApiPriceFetcher.calculatePricePerHash(12345L, 0L)
        assertEquals(0.0, result, "Should return 0.0 when volume is 0")
    }
}
