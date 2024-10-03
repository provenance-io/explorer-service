package io.provenance.explorer.service

import io.mockk.every
import io.mockk.spyk
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.explorer.service.pricing.fetchers.HistoricalPriceFetcherFactory
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

class TokenServiceTest {

    private lateinit var accountClient: AccountGrpcClient
    private lateinit var flowApiGrpcClient: FlowApiGrpcClient
    private lateinit var historicalPriceFetcherFactory: HistoricalPriceFetcherFactory
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        accountClient = AccountGrpcClient(URI("http://localhost:26657"))
        flowApiGrpcClient = FlowApiGrpcClient(URI("http://localhost:50051"))
        historicalPriceFetcherFactory = HistoricalPriceFetcherFactory(flowApiGrpcClient) // Set the factory as needed
        tokenService = TokenService(accountClient, historicalPriceFetcherFactory)
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchHistoricalPriceData`() = runBlocking {
        val fromDate = DateTime.now().minusDays(7)

        val result: List<HistoricalPrice> = tokenService.fetchHistoricalPriceData(fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        assertTrue(result.isNotEmpty()) { "Expected non-empty list of HistoricalPrice" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchHistoricalPriceData and process `() = runBlocking {
        val fromDate = DateTime.now().minusDays(1)

        val result: List<HistoricalPrice> = tokenService.fetchHistoricalPriceData(fromDate)

        tokenService.processLatestTokenData(result, fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        assertTrue(result.isNotEmpty()) { "Expected non-empty list of HistoricalPrice" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchLegacyHistoricalPriceData`() = runBlocking {
        val fromDate = DateTime.now().minusDays(7)

        val result: List<HistoricalPrice> = tokenService.fetchHistoricalPriceData(fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }

        assertTrue(result.isNotEmpty()) { "Expected non-empty list of HistoricalPrice" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getTokenDistributionStats`() {
        val result = tokenService.getTokenDistributionStats()
        println(result)
        assertTrue(result.isNotEmpty()) { "Expected non-empty token distribution stats" }
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test getTokenBreakdown`() = runBlocking {
        val result = tokenService.getTokenBreakdown()
        println("Max supply: ${result.maxSupply}")
        println("Community pool supply: ${result.communityPool}")
        println("Bonded supply: ${result.bonded}")
        println("Burned supply: ${result.burned}")
    }

    @Test
    fun `test processLatestTokenData with historical prices`() {
        val today = DateTime.now()

        val historicalPrices = listOf(
            HistoricalPrice(
                time = today.minusDays(1).millis / 1000,
                high = "1.5".toBigDecimal(),
                low = "1.0".toBigDecimal(),
                open = "1.2".toBigDecimal(),
                close = "1.4".toBigDecimal(),
                volume = "100".toBigDecimal(),
                source = "source1"
            ),
            HistoricalPrice(
                time = today.minusHours(6).millis / 1000,
                high = "1.6".toBigDecimal(),
                low = "1.1".toBigDecimal(),
                open = "1.3".toBigDecimal(),
                close = "1.5".toBigDecimal(),
                volume = "150".toBigDecimal(),
                source = "source2"
            ),
            HistoricalPrice(
                time = today.minusHours(3).millis / 1000,
                high = "1.7".toBigDecimal(),
                low = "1.2".toBigDecimal(),
                open = "1.4".toBigDecimal(),
                close = "1.6".toBigDecimal(),
                volume = "200".toBigDecimal(),
                source = "source3"
            )
        )

        val totalSupplyMock = "1000".toBigDecimal().multiply(ExplorerProperties.UTILITY_TOKEN_BASE_MULTIPLIER)

        val tokenServiceSpy = spyk(tokenService) {
            every { totalSupply() } returns totalSupplyMock
        }

        val latestData = tokenServiceSpy.processLatestTokenData(historicalPrices, today)

        assertNotNull(latestData, "Latest data should not be null")
        assertEquals("1.600", latestData?.quote?.get(USD_UPPER)?.price?.toPlainString(), "Latest price is incorrect")
        assertEquals("14.3", latestData?.quote?.get(USD_UPPER)?.percent_change_24h?.toPlainString(), "Percent change is incorrect")
        assertEquals("450", latestData?.quote?.get(USD_UPPER)?.volume_24h?.toPlainString(), "Volume sum is incorrect")
        assertEquals("1600.000", latestData?.quote?.get(USD_UPPER)?.market_cap_by_total_supply?.toPlainString(), "Market cap is incorrect")
    }
}
