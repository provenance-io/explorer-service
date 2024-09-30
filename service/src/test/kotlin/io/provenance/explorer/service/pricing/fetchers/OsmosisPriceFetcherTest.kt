package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class OsmosisPriceFetcherTest {

    private lateinit var osmosisPriceFetcher: OsmosisPriceFetcher

    @BeforeEach
    fun setUp() {
        osmosisPriceFetcher = OsmosisPriceFetcher()
    }

    @Test
    @Disabled("Test was used to manually call the endpoint")
    fun `test fetchOsmosisData and print results`() = runBlocking {
        val fromDate = DateTime.parse("2024-05-08")

        val result: List<OsmosisHistoricalPrice> = osmosisPriceFetcher.fetchOsmosisData(fromDate)

        result.forEach {
            println("Time: ${DateTime(it.time * 1000)}, Open: ${it.open}, High: ${it.high}, Low: ${it.low}, Close: ${it.close}, Volume: ${it.volume}")
        }
    }

    @Test
    fun `test determineTimeFrame`() {
        val now = DateTime.now(DateTimeZone.UTC)

        val fromDate1 = now.minusDays(10)
        val timeFrame1 = osmosisPriceFetcher.determineTimeFrame(fromDate1)
        assertEquals(OsmosisPriceFetcher.TimeFrame.FIVE_MINUTES, timeFrame1)

        val fromDate2 = now.minusDays(30)
        val timeFrame2 = osmosisPriceFetcher.determineTimeFrame(fromDate2)
        assertEquals(OsmosisPriceFetcher.TimeFrame.TWO_HOURS, timeFrame2)

        val fromDate3 = now.minusDays(90)
        val timeFrame3 = osmosisPriceFetcher.determineTimeFrame(fromDate3)
        assertEquals(OsmosisPriceFetcher.TimeFrame.ONE_DAY, timeFrame3)
    }

    @Test
    fun `test buildInputQuery`() {
        val now = DateTime.now(DateTimeZone.UTC)

        val fromDate1 = now.minusDays(10)
        val timeFrame1 = OsmosisPriceFetcher.TimeFrame.FIVE_MINUTES
        val inputQuery1 = osmosisPriceFetcher.buildInputQuery(fromDate1, timeFrame1)
        val expectedQuery1 = """%7B%22json%22%3A%7B%22coinDenom%22%3A%22ibc%2FCE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2%22%2C%22coinMinimalDenom%22%3A%22ibc%2FCE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2%22%2C%22timeFrame%22%3A%7B%22custom%22%3A%7B%22timeFrame%22%3A5%2C%22numRecentFrames%22%3A2880%7D%7D%7D%7D"""
        assertEquals(expectedQuery1, inputQuery1)
    }
}
