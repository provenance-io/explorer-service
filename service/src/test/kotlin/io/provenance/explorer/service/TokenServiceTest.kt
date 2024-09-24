package io.provenance.explorer.service

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provlabs.flow.api.NavEvent
import io.provlabs.flow.api.NavServiceGrpc
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.URI

class TokenServiceTest {

    private lateinit var accountClient: AccountGrpcClient
    private lateinit var tokenService: TokenService
    private lateinit var channel: ManagedChannel
    private lateinit var navServiceStub: NavServiceGrpc.NavServiceBlockingStub

    @BeforeEach
    fun setUp() {
        accountClient = AccountGrpcClient(URI("https://www.google.com"))
        channel = ManagedChannelBuilder.forAddress("localhost", 50051)
            .usePlaintext()
            .build()

        navServiceStub = NavServiceGrpc.newBlockingStub(channel)
        tokenService = TokenService(accountClient, navServiceStub)
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchOsmosisData and print results`() = runBlocking {
        val fromDate = DateTime.parse("2024-05-08")

        val result: List<OsmosisHistoricalPrice> = tokenService.fetchOsmosisData(fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time * 1000)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }
    }

    @Test
    fun `test determineTimeFrame`() {
        val now = DateTime.now(DateTimeZone.UTC)

        val fromDate1 = now.minusDays(10)
        val timeFrame1 = tokenService.determineTimeFrame(fromDate1)
        assertEquals(TokenService.TimeFrame.FIVE_MINUTES, timeFrame1)

        val fromDate2 = now.minusDays(30)
        val timeFrame2 = tokenService.determineTimeFrame(fromDate2)
        assertEquals(TokenService.TimeFrame.TWO_HOURS, timeFrame2)

        val fromDate3 = now.minusDays(90)
        val timeFrame3 = tokenService.determineTimeFrame(fromDate3)
        assertEquals(TokenService.TimeFrame.ONE_DAY, timeFrame3)
    }

    @Test
    fun `test buildInputQuery`() {
        val now = DateTime.now(DateTimeZone.UTC)

        val fromDate1 = now.minusDays(10)
        val timeFrame1 = TokenService.TimeFrame.FIVE_MINUTES
        val inputQuery1 = tokenService.buildInputQuery(fromDate1, timeFrame1)
        val expectedQuery1 = """%7B%22json%22%3A%7B%22coinDenom%22%3A%22ibc%2FCE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2%22%2C%22coinMinimalDenom%22%3A%22ibc%2FCE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2%22%2C%22timeFrame%22%3A%7B%22custom%22%3A%7B%22timeFrame%22%3A5%2C%22numRecentFrames%22%3A2880%7D%7D%7D%7D"""
        assertEquals(expectedQuery1, inputQuery1)
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchOnChainNavData`() {
        val denom = "nhash"
        val fromDate = DateTime.now().minusDays(7)
        val limit = 100

        val result: List<NavEvent> = tokenService.fetchOnChainNavData(denom, fromDate, limit)

        val groupedByPriceDenom = result.groupBy { it.priceDenom }

        groupedByPriceDenom.forEach { (priceDenom, events) ->
            println("PriceDenom: $priceDenom, Count: ${events.size}")
        }

        result.forEach { navEvent ->
            val pricePerHash = calculatePricePerHash(navEvent.priceAmount, navEvent.volume)
            println("NavEvent: Time=${DateTime(navEvent.blockTime * 1000, DateTimeZone.getDefault())}, PriceDenom=${navEvent.priceDenom}, Hash Price: $pricePerHash")
        }

        assert(result.isNotEmpty()) { "Expected non-empty NavEvent list" }
    }

    @Test
    fun `test calculatePricePerHash with multiple scenarios`() {
        var result = calculatePricePerHash(12345L, ExplorerProperties.UTILITY_TOKEN_BASE_MULTIPLIER.toLong())
        assertEquals(12.345, result, "price per hash calculation is incorrect")

        result = calculatePricePerHash(12345L, 0L)
        assertEquals(0.0, result, "Should return 0.0 when volume is 0")
    }
}
