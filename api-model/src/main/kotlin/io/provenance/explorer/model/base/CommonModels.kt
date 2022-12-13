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
