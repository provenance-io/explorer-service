package io.provenance.explorer.service.pricing.fetchers

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.domain.models.OsmosisApiResponse
import io.provenance.explorer.domain.models.OsmosisHistoricalPrice
import io.provenance.explorer.service.TokenService
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import java.net.URLEncoder

class OsmosisPriceFetcher : HistoricalPriceFetcher {

    protected val logger = logger(OsmosisPriceFetcher::class)
    override fun fetchHistoricalPrice(fromDate: DateTime?): List<HistoricalPrice> {
        val osmosisHistoricalPrices = fetchOsmosisData(fromDate)
        return osmosisHistoricalPrices.map { osmosisPrice ->
            HistoricalPrice(
                time = osmosisPrice.time,
                high = osmosisPrice.high,
                low = osmosisPrice.low,
                close = osmosisPrice.close,
                open = osmosisPrice.open,
                volume = osmosisPrice.volume
            )
        }
    }

    fun fetchOsmosisData(fromDate: DateTime?): List<OsmosisHistoricalPrice> = runBlocking {
        val input = buildInputQuery(fromDate, determineTimeFrame(fromDate))
        try {
            val url = """https://app.osmosis.zone/api/edge-trpc-assets/assets.getAssetHistoricalPrice?input=$input"""
            logger.info("Calling $url with fromDate $fromDate")
            val response: HttpResponse = KTOR_CLIENT_JAVA.get(url) {
                accept(ContentType.Application.Json)
            }

            val rawResponse: String = response.bodyAsText()
            logger.debug("Osmosis GET: $url Raw Response: $rawResponse")

            val osmosisApiResponse: OsmosisApiResponse = response.body()
            osmosisApiResponse.result.data.json
        } catch (e: ResponseException) {
            logger.error("Error fetching from Osmosis API: ${e.response}", e)
            emptyList()
        } catch (e: Exception) {
            logger.error("Error fetching from Osmosis API: ${e.message}", e)
            emptyList()
        }
    }

    enum class TimeFrame(val minutes: Int) {
        FIVE_MINUTES(5),
        TWO_HOURS(120),
        ONE_DAY(1440)
    }

    /**
     * Determines the appropriate TimeFrame based on the fromDate.
     *
     * @param fromDate The starting date to determine the time frame.
     * @return The appropriate TimeFrame enum value.
     */
    fun determineTimeFrame(fromDate: DateTime?): TimeFrame {
        val now = DateTime.now(DateTimeZone.UTC)
        val duration = Duration(fromDate, now)
        return when {
            duration.standardDays <= 14 -> TimeFrame.FIVE_MINUTES
            duration.standardDays <= 60 -> TimeFrame.TWO_HOURS
            else -> TimeFrame.ONE_DAY
        }
    }

    /**
     * Builds the input query parameter for fetching historical data.
     *
     * This function constructs a URL-encoded JSON query parameter for fetching historical data based on the given
     * `fromDate` and `timeFrame`. The `timeFrame` represents the number of minutes between updates. The allowed values
     * for `timeFrame` are defined in the `TimeFrame` enum:
     * - FIVE_MINUTES: data goes back 2 weeks.
     * - TWO_HOURS: data goes back 2 months.
     * - ONE_DAY: data goes back to the beginning of time.
     *
     * The function calculates the total number of frames (`numRecentFrames`) from the `fromDate` to the current time,
     * based on the specified `timeFrame`.
     *
     * @param fromDate The starting date from which to calculate the number of frames.
     * @param timeFrame The time interval between updates, specified as a `TimeFrame` enum value.
     * @return A URL-encoded JSON string to be used as a query parameter for fetching historical data.
     */
    fun buildInputQuery(fromDate: DateTime?, timeFrame: TimeFrame): String {
        val coinDenom = "ibc/CE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2"
        val coinMinimalDenom = "ibc/CE5BFF1D9BADA03BB5CCA5F56939392A761B53A10FBD03B37506669C3218D3B2"
        val now = DateTime.now(DateTimeZone.UTC)
        val duration = Duration(fromDate, now)
        val numRecentFrames = (duration.standardMinutes / timeFrame.minutes).toInt()
        return URLEncoder.encode(
            """{"json":{"coinDenom":"$coinDenom","coinMinimalDenom":"$coinMinimalDenom","timeFrame":{"custom":{"timeFrame":${timeFrame.minutes},"numRecentFrames":$numRecentFrames}}}}""",
            "UTF-8"
        )
    }
}
