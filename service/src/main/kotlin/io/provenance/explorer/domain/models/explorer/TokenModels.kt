package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.TokenHistoricalDailyRecord
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.CountStrTotal
import io.provenance.explorer.model.base.DateTruncGranularity
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class TokenDistributionPaginatedResults(
    val ownerAddress: String,
    val data: CountStrTotal,
    val spendable: CoinStr
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

data class TokenHistoricalDataRequest(
    val fromDate: LocalDateTime? = null,
    val toDate: LocalDateTime? = null
) {
    fun getFileList(): MutableList<CsvData> =
        mutableListOf(
            CsvData(
                "TokenHistoricalData",
                tokenHistoricalCsvBaseHeaders,
                TokenHistoricalDailyRecord.findForDates(fromDate?.startOfDay(), toDate?.startOfDay()).map { it.toCsv() }
            )
        )

    val tokenHistoricalCsvBaseHeaders: MutableList<String> =
        mutableListOf("Date", "Open", "High", "Low", "Close", "Volume - USD")

    fun datesValidation() =
        if (fromDate != null && toDate != null) {
            requireToMessage(fromDate.isBefore(toDate)) { "'fromDate' ($fromDate) must be before 'toDate' ($toDate)" }
        } else {
            null
        }

    private val dateFormat = DateTimeFormatter.ofPattern("yyy-MM-dd")

    fun getFileNameBase(): String {
        val to = if (toDate != null) dateFormat.format(toDate) else "CURRENT"
        val full = if (fromDate != null) "${dateFormat.format(fromDate)} thru $to" else "ALL"
        return "$full BY ${DateTruncGranularity.DAY.name} - Token Historical Data"
    }

    fun writeFilters(): ByteArray {
        val baos = ByteArrayOutputStream()
        PrintWriter(baos).use { writer ->
            writer.println("Filters used --")
            writer.println("fromDate: ${if (fromDate != null) dateFormat.format(fromDate) else "NULL"}")
            writer.println("toDate: ${if (toDate != null) dateFormat.format(toDate) else "NULL"}")
            writer.println("granularity: ${DateTruncGranularity.DAY.name}")
            writer.flush()
            return baos.toByteArray()
        }
    }
}
