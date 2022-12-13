package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.DateTruncGranularity
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.math.BigDecimal

data class TokenDistributionPaginatedResults(
    val ownerAddress: String,
    val data: CountStrTotal
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
