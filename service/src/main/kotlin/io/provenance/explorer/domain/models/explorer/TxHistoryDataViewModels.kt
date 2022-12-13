package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.TxHistoryDataViews
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.model.FeeTypeData
import io.provenance.explorer.model.TxHistoryChartData
import io.provenance.explorer.model.TxTypeData
import io.provenance.explorer.model.base.DateTruncGranularity
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.sql.ResultSet

fun getFileList(filters: TxHistoryDataRequest, feepayer: String?): MutableList<CsvData> {
    val hasFeepayer = feepayer != null
    val fileList = mutableListOf(
        CsvData(
            "TxHistoryChartData",
            txHistoryDataCsvBaseHeaders(filters.advancedMetrics, hasFeepayer),
            TxHistoryDataViews.getTxHistoryChartData(filters.granularity, filters.fromDate, filters.toDate, feepayer)
                .map { it.toCsv(filters.advancedMetrics, hasFeepayer, filters.granularity) }
        )
    )
    if (filters.advancedMetrics) {
        fileList.add(
            CsvData(
                "TxTypeData",
                txTypeDataCsvBaseHeaders(hasFeepayer),
                TxHistoryDataViews.getTxTypeData(filters.granularity, filters.fromDate, filters.toDate, feepayer)
                    .map { it.toCsv(hasFeepayer, filters.granularity) }
            )
        )
        fileList.add(
            CsvData(
                "FeeTypeData",
                feeTypeDataCsvBaseHeaders(hasFeepayer),
                TxHistoryDataViews.getFeeTypeData(filters.granularity, filters.fromDate, filters.toDate, feepayer)
                    .map { it.toCsv(hasFeepayer, filters.granularity) }
            )
        )
    }
    return fileList
}

//region TxHistoryChart

fun ResultSet.toTxHistoryChartData(byFeepayer: Boolean) = TxHistoryChartData(
    DateTime(this.getTimestamp("date").time),
    if (byFeepayer) this.getString("feepayer") else null,
    this.getBigDecimal("tx_count"),
    this.getBigDecimal("fee_amount_in_base_token"),
    this.getBigDecimal("gas_wanted"),
    this.getBigDecimal("gas_used"),
    this.getBigDecimal("fee_amount_in_token"),
    this.getBigDecimal("fees_paid_in_usd"),
    this.getBigDecimal("max_token_price_usd"),
    this.getBigDecimal("min_token_price_usd"),
    this.getBigDecimal("avg_token_price_usd")
)

fun txHistoryDataCsvBaseHeaders(advancedMetrics: Boolean, hasFeepayer: Boolean): MutableList<String> {
    val base = mutableListOf("Date")
    if (hasFeepayer) base.add("Feepayer")
    base.addAll(listOf("Tx Count", "Fee Amount - Token", "Fees Paid - USD", "Min Token Price - USD", "Max Token Price - USD", "Avg Token Price - USD"))
    if (advancedMetrics) base.addAll(listOf("Gas Wanted", "Gas Used"))
    return base
}

//endregion

//region TxTypeData

fun ResultSet.toTxTypeData(byFeepayer: Boolean) = TxTypeData(
    DateTime(this.getTimestamp("date").time),
    if (byFeepayer) this.getString("feepayer") else null,
    this.getString("tx_type"),
    this.getBigDecimal("tx_type_count")
)

fun txTypeDataCsvBaseHeaders(hasFeepayer: Boolean): MutableList<String> {
    val base = mutableListOf("Date")
    if (hasFeepayer) base.add("Feepayer")
    base.addAll(listOf("Tx Type", "Tx Type Count"))
    return base
}

//endregion

//region FeeTypeData

fun ResultSet.toFeeTypeData(byFeepayer: Boolean) = FeeTypeData(
    DateTime(this.getTimestamp("date").time),
    if (byFeepayer) this.getString("feepayer") else null,
    this.getString("fee_type"),
    this.getString("msg_type"),
    this.getBigDecimal("fee_amount_in_base_token"),
    this.getBigDecimal("fee_amount_in_token"),
    this.getBigDecimal("fees_paid_in_usd"),
    this.getBigDecimal("max_token_price_usd"),
    this.getBigDecimal("min_token_price_usd"),
    this.getBigDecimal("avg_token_price_usd")
)

fun feeTypeDataCsvBaseHeaders(hasFeepayer: Boolean): MutableList<String> {
    val base = mutableListOf("Date")
    if (hasFeepayer) base.add("Feepayer")
    base.addAll(listOf("Fee Type", "Msg Type", "Fee Amount - Token", "Fees Paid - USD", "Min Token Price - USD", "Max Token Price - USD", "Avg Token Price - USD"))
    return base
}

//endregion

//region TxHistory API Request Bodies

fun granularityValidation(granularity: DateTruncGranularity) =
    requireToMessage(
        listOf(DateTruncGranularity.MONTH, DateTruncGranularity.DAY, DateTruncGranularity.HOUR).contains(granularity)
    ) { "The specified granularity is not supported: $granularity" }

fun datesValidation(fromDate: DateTime?, toDate: DateTime?) =
    if (fromDate != null && toDate != null)
        requireToMessage(fromDate.isBefore(toDate)) { "'fromDate' ($fromDate) must be before 'toDate' ($toDate)" }
    else null

data class TxHistoryDataRequest(
    val fromDate: DateTime? = null,
    val toDate: DateTime? = null,
    val granularity: DateTruncGranularity = DateTruncGranularity.DAY,
    val advancedMetrics: Boolean = false
) {
    private val dateFormat = DateTimeFormat.forPattern("yyy-MM-dd")

    fun getFileNameBase(feepayer: String?): String {
        val to = if (toDate != null) dateFormat.print(toDate) else "CURRENT"
        val full = if (fromDate != null) "${dateFormat.print(fromDate)} thru $to" else "ALL"
        val metrics = if (advancedMetrics) " WITH Advanced Metrics" else ""
        val payer = if (feepayer != null) " FOR $feepayer" else ""
        return "$full BY ${granularity.name}$payer$metrics - Tx History Data"
    }

    fun writeFilters(feepayer: String?): ByteArray {
        val baos = ByteArrayOutputStream()
        PrintWriter(baos).use { writer ->
            writer.println("Filters used --")
            writer.println("fromDate: ${if (fromDate != null) dateFormat.print(fromDate) else "NULL"}")
            writer.println("toDate: ${if (toDate != null) dateFormat.print(toDate) else "NULL"}")
            writer.println("granularity: ${granularity.name}")
            writer.println("advancedMetrics: $advancedMetrics")
            if (feepayer != null)
                writer.println("feepayer: $feepayer")
            writer.flush()
            return baos.toByteArray()
        }
    }
}

//endregion
