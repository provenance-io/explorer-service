package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.service.pricing.utils.HashCalculationUtils
import io.provlabs.flow.api.NavEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime

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
        val fromDate = LocalDateTime.now().minusDays(1)

        val result: List<HistoricalPrice> = flowApiPriceFetcher.fetchHistoricalPrice(fromDate)
        result.forEach {
            println("Time: ${Instant.ofEpochSecond(it.time)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        val totalVolume = result.sumOf { it.volume }
        println("Total Volume: $totalVolume")
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getMarkerNavByPriceDenoms and print results`() {
        val fromDate = LocalDateTime.now().minusDays(1)

        val result: List<NavEvent> = flowApiPriceFetcher.getMarkerNavByPriceDenoms(fromDate)

        val groupedByPriceDenom = result.groupBy { it.priceDenom }

        groupedByPriceDenom.forEach { (priceDenom, events) ->
            println("PriceDenom: $priceDenom, Count: ${events.size}")
        }

        result.forEach { navEvent ->
            val pricePerHash = HashCalculationUtils.getPricePerHashFromMicroUsd(navEvent.priceAmount, navEvent.volume)
            println("NavEvent: Time=${Instant.ofEpochSecond(navEvent.blockTime)}, PriceDenom=${navEvent.priceDenom}, Hash Price: $pricePerHash")
        }

        assert(result.isNotEmpty()) { "Expected non-empty NavEvent list" }
    }
}
