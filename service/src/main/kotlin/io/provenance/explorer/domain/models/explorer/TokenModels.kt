package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.domain.extensions.USD_UPPER
import io.provenance.explorer.domain.extensions.startOfDay
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.math.BigDecimal

data class TokenSupply(
    val maxSupply: CoinStr,
    val currentSupply: CoinStr,
    val circulation: CoinStr,
    val communityPool: CoinStr,
    val bonded: CoinStr,
    val burned: CoinStr
)

data class TokenDistributionPaginatedResults(
    val ownerAddress: String,
    val data: CountStrTotal
)

data class TokenDistributionAmount(
    val denom: String,
    val amount: String
)

data class TokenDistribution(
    val range: String,
    val amount: TokenDistributionAmount,
    val percent: String
)

data class RichAccount(
    val address: String,
    val amount: CoinStr,
    val percentage: String
)

data class CmcHistoricalQuote(
    val time_open: DateTime,
    val time_close: DateTime,
    val time_high: DateTime,
    val time_low: DateTime,
    val quote: Map<String, CmcQuote>
)

data class CmcQuote(
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val market_cap: BigDecimal,
    val timestamp: DateTime
)

data class CmcLatestDataAbbrev(
    val last_updated: DateTime,
    val quote: Map<String, CmcLatestQuoteAbbrev>
)

data class CmcLatestQuoteAbbrev(
    val price: BigDecimal,
    val percent_change_24h: BigDecimal,
    val volume_24h: BigDecimal,
    val market_cap_by_total_supply: BigDecimal?,
    val last_updated: DateTime
)

data class DlobHistBase(
    val buy: List<DlobHistorical>
)

data class DlobHistorical(
    val trade_id: Long,
    val price: BigDecimal,
    val base_volume: Long,
    val target_volume: BigDecimal,
    val trade_timestamp: Long,
    val type: String
)

fun getFileListToken(filters: TokenHistoricalDataRequest): MutableList<CsvData> =
    mutableListOf(
        CsvData(
            "TokenHistoricalData",
            tokenHistoricalCsvBaseHeaders(),
            TokenHistoricalDailyRecord.findForDates(filters.fromDate?.startOfDay(), filters.toDate?.startOfDay())
                .map { it.toCsv() }
        )
    )

fun tokenHistoricalCsvBaseHeaders(): MutableList<String> =
    mutableListOf("Date", "Open", "High", "Low", "Close", "Volume - USD")

fun CmcHistoricalQuote.toCsv(): MutableList<Any> =
    this.quote[USD_UPPER]!!.let {
        mutableListOf(
            it.timestamp.withZone(DateTimeZone.UTC).customFormat(DateTruncGranularity.DAY),
            it.open,
            it.high,
            it.low,
            it.close,
            it.volume.currFormat()
        )
    }

data class TokenHistoricalDataRequest(
    val fromDate: DateTime? = null,
    val toDate: DateTime? = null
) {
    private val dateFormat = DateTimeFormat.forPattern("yyy-MM-dd")

    fun getFileNameBase(): String {
        val to = if (toDate != null) dateFormat.print(toDate) else "CURRENT"
        val full = if (fromDate != null) "${dateFormat.print(fromDate)} thru $to" else "ALL"
        return "$full BY ${DateTruncGranularity.DAY.name} - Token Historical Data"
    }

    fun writeFilters(): ByteArray {
        val baos = ByteArrayOutputStream()
        PrintWriter(baos).use { writer ->
            writer.println("Filters used --")
            writer.println("fromDate: ${if (fromDate != null) dateFormat.print(fromDate) else "NULL"}")
            writer.println("toDate: ${if (toDate != null) dateFormat.print(toDate) else "NULL"}")
            writer.println("granularity: ${DateTruncGranularity.DAY.name}")
            writer.flush()
            return baos.toByteArray()
        }
    }
}
