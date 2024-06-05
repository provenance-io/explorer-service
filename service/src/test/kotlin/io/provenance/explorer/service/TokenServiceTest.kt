package io.provenance.explorer.service

import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLEncoder

class TokenServiceTest {

    private lateinit var accountClient: AccountGrpcClient
    private lateinit var tokenService: TokenService

    @BeforeEach
    fun setUp() {
        accountClient = AccountGrpcClient(URI("https://www.google.com"))
        tokenService = TokenService(accountClient)
    }

    @Test
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
        val expectedQuery1 = """%7B%22json%22%3A%7B%22coinDenom%22%3A%22ibc%2FCE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2%22%2C%22timeFrame%22%3A%7B%22custom%22%3A%7B%22timeFrame%22%3A5%2C%22numRecentFrames%22%3A2880%7D%7D%7D%7D"""
        assertEquals(expectedQuery1, inputQuery1)
    }
}
