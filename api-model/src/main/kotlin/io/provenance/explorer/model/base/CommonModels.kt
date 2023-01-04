package io.provenance.explorer.model.base

import java.math.BigInteger

const val USD_UPPER = "USD"
const val USD_LOWER = "usd"

data class PagedResults<T>(
    val pages: Int,
    val results: List<T>,
    val total: Long,
    val rollupTotals: Map<String, CoinStr> = emptyMap()
)

enum class DateTruncGranularity { MONTH, DAY, HOUR, MINUTE }

data class CountTotal(
    val count: BigInteger?,
    val total: BigInteger
)

enum class PeriodInSeconds(val seconds: Int) {
    SECOND(1),
    MINUTE(60),
    HOUR(3600),
    DAY(86400),
    WEEK(604800),
    MONTH(2628000),
    QUARTER(7884000),
    YEAR(31536000)
}

enum class Timeframe { QUARTER, MONTH, WEEK, DAY, HOUR, FOREVER }
