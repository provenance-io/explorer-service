package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provlabs.flow.api.NavEvent
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
    fun `test fetchHistoricalPrice and print results`() {
        val fromDate = DateTime.now().minusDays(1)

        val result: List<HistoricalPrice> = flowApiPriceFetcher.fetchHistoricalPrice(fromDate)
        result.forEach {
            println("Time: ${DateTime(it.time * 1000)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        val totalVolume = result.sumOf { it.volume }
        println("Total Volume: $totalVolume")
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getMarkerNavByPriceDenoms and print results`() {
        val fromDate = DateTime.now().minusDays(1)
        val limit = 100

        val result: List<NavEvent> = flowApiPriceFetcher.getMarkerNavByPriceDenoms(fromDate, limit)

        val groupedByPriceDenom = result.groupBy { it.priceDenom }

        groupedByPriceDenom.forEach { (priceDenom, events) ->
            println("PriceDenom: $priceDenom, Count: ${events.size}")
        }

        result.forEach { navEvent ->
            val pricePerHash = flowApiPriceFetcher.getPricePerHashFromMicroUsd(navEvent.priceAmount, navEvent.volume)
            println("NavEvent: Time=${DateTime(navEvent.blockTime * 1000)}, PriceDenom=${navEvent.priceDenom}, Hash Price: $pricePerHash")
        }

        assert(result.isNotEmpty()) { "Expected non-empty NavEvent list" }
    }

    @Test
    fun `test calculatePricePerHashFromMicroUsd`() {
        var result = flowApiPriceFetcher.getPricePerHashFromMicroUsd(
            4800000000L,
            300000000000000
        )
        assertEquals(BigDecimal("0.016"), result, "Price per hash calculation is incorrect")

        result = flowApiPriceFetcher.getPricePerHashFromMicroUsd(12345L, 0L)
        assertEquals(BigDecimal.ZERO, result, "Should return 0.0 when volume is 0")
    }

    @Test
    fun `test calculateVolumeHash`() {
        val volumeNhash = 1000000000000L
        val result = flowApiPriceFetcher.calculateVolumeHash(volumeNhash)
        val expected = 1000.0.toBigDecimal().setScale(10)
        assertEquals(expected, result, "Volume hash calculation is incorrect")
    }
}