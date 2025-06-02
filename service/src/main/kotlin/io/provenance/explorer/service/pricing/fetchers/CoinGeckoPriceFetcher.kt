package io.provenance.explorer.service.pricing.fetchers

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.CoinGeckoHistoricalChart
import io.provenance.explorer.domain.models.CoinGeckoMarket
import io.provenance.explorer.domain.models.CoinGeckoOHLC
import io.provenance.explorer.domain.models.CoinGeckoTotalVolume
import io.provenance.explorer.domain.models.HistoricalPrice
import io.provenance.explorer.model.base.PeriodInSeconds
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

class CoinGeckoPriceFetcher : HistoricalPriceFetcher {

    val logger = logger(CoinGeckoPriceFetcher::class)
    val hashId = "hash-2"

    override fun getSource(): String {
        return "coin-gecko"
    }

    override fun fetchHistoricalPrice(fromDate: LocalDateTime?): List<HistoricalPrice> {
        val now = LocalDateTime.now()
        val days = fromDate?.let { (Duration.between(it, now).toDays().toInt()) } ?: 0
        return when (days > 1) {
            true -> buildCoinGeckoHistorical(days)
            else -> fetchCoinGeckoMarket()
        }
    }

    private fun buildCoinGeckoHistorical(days: Int): List<HistoricalPrice> {
        // list of open high low close
        val ohlc = fetchCoinGeckoOHLC()
        // list of total volumes per day for given number of days
        return fetchCoinGeckoDailyChart(days)?.total_volumes?.map {
            // first item is the unix timestamp for the start of the day, second item is the total volume
            CoinGeckoTotalVolume(
                timestamp = it[0].toLong(),
                totalVolume = it[1]
            )
        }?.mapNotNull { totalVolumes ->
            val startOfDayMillis = totalVolumes.timestamp
            val startOfNextPeriod = Instant.ofEpochMilli(startOfDayMillis)
                .plusSeconds(PeriodInSeconds.DAY.seconds.toLong()).toEpochMilli()
            val volume24Hr = totalVolumes.totalVolume
            // find the first matching ohlc for the period
            val firstOhlc = ohlc.find { it.timestamp == startOfDayMillis } ?: ohlc.first()
            // find the matching ohlc for the start of the next period
            val firstOhlcNextPeriod = ohlc.find { it.timestamp == startOfNextPeriod } ?: return@mapNotNull null
            // ohlc data is listed every 4 hours so collect all for the period
            val todaysOhlcList = ohlc.subList(ohlc.indexOf(firstOhlc), ohlc.indexOf(firstOhlcNextPeriod))
            // todays close pulled from following periods ohlc
            val close = firstOhlcNextPeriod.close

            HistoricalPrice(
                time = startOfDayMillis.div(1000),
                volume = volume24Hr,
                high = todaysOhlcList.maxOfOrNull { it.high } ?: close,
                low = todaysOhlcList.minOfOrNull { it.low } ?: close,
                close = close,
                open = firstOhlc.open,
                source = getSource()
            )
        } ?: emptyList()
    }

    private fun fetchCoinGeckoOHLC() = runBlocking {
        try {
            KTOR_CLIENT_JAVA.get {
                url("https://api.coingecko.com/api/v3/coins/$hashId/ohlc?vs_currency=usd&days=30")
            }.body<List<List<BigDecimal>>>().map {
                // open high low close is an array of arrays of numbers
                // array of numbers by index - 0 = unix timestamp; 1 = open; 2 = high; 3 = low; 4 = close
                CoinGeckoOHLC(
                    timestamp = it[0].toLong(),
                    open = it[1],
                    high = it[2],
                    low = it[3],
                    close = it[4],
                )
            }
        } catch (e: Exception) {
            logger.error("Unable to fetch hash OHLC data from coin gecko", e)
            emptyList()
        }
    }

    private fun fetchCoinGeckoDailyChart(days: Int) = runBlocking {
        try {
            KTOR_CLIENT_JAVA.get {
                url("https://api.coingecko.com/api/v3/coins/$hashId/market_chart?vs_currency=USD&days=$days&interval=daily")
            }.body<CoinGeckoHistoricalChart>()
        } catch (e: Exception) {
            logger.error("Unable to fetch hash daily chart data from coin gecko: ${e.message}")
            null
        }
    }

    private fun fetchCoinGeckoMarket() = runBlocking {
        try {
            KTOR_CLIENT_JAVA.get {
                url("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=$hashId")
            }.body<List<CoinGeckoMarket>>().first().let {
                listOf(
                    HistoricalPrice(
                        time = Instant.parse(it.last_updated).epochSecond,
                        high = it.high_24h,
                        low = it.low_24h,
                        close = it.current_price,
                        open = it.current_price.minus(it.price_change_24h),
                        volume = it.total_volume,
                        priceChangePercentage24h = it.price_change_percentage_24h,
                        source = getSource(),
                    )
                )
            }
        } catch (e: Exception) {
            logger.error("Unable to fetch hash market data from coin gecko", e)
            emptyList()
        }
    }
}
