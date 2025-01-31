package io.provenance.explorer.domain.models.explorer.download

import io.provenance.explorer.domain.entities.TxHistoryDataViews
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.download.FeeTypeData
import io.provenance.explorer.model.download.TxHistoryChartData
import io.provenance.explorer.model.download.TxTypeData
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

//region TxHistoryChart

fun ResultSet.toTxHistoryChartData(byFeepayer: Boolean) = TxHistoryChartData(
    this.getTimestamp("date").toLocalDateTime(),
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
    this.getTimestamp("date").toLocalDateTime(),
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
    this.getTimestamp("date").toLocalDateTime(),
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

data class TxHistoryDataRequest(
    val fromDate: LocalDateTime? = null,
    val toDate: LocalDateTime? = null,
    val granularity: DateTruncGranularity = DateTruncGranularity.DAY,
    val advancedMetrics: Boolean = false
) {
    fun granularityValidation() =
        requireToMessage(
            listOf(DateTruncGranularity.MONTH, DateTruncGranularity.DAY, DateTruncGranularity.HOUR).contains(granularity)
        ) { "The specified granularity is not supported: $granularity" }

    fun datesValidation() =
        if (fromDate != null && toDate != null) {
            requireToMessage(fromDate.isBefore(toDate)) { "'fromDate' ($fromDate) must be before 'toDate' ($toDate)" }
        } else {
            null
        }

    fun getFileList(feepayer: String?): MutableList<CsvData> {
        val hasFeepayer = feepayer != null
        val fileList = mutableListOf(
            CsvData(
                "TxHistoryChartData",
                txHistoryDataCsvBaseHeaders(advancedMetrics, hasFeepayer),
                TxHistoryDataViews.getTxHistoryChartData(granularity, fromDate, toDate, feepayer)
                    .map { it.toCsv(advancedMetrics, hasFeepayer, granularity) }
            )
        )
        if (advancedMetrics) {
            fileList.add(
                CsvData(
                    "TxTypeData",
                    txTypeDataCsvBaseHeaders(hasFeepayer),
                    TxHistoryDataViews.getTxTypeData(granularity, fromDate, toDate, feepayer)
                        .map { it.toCsv(hasFeepayer, granularity) }
                )
            )
            fileList.add(
                CsvData(
                    "FeeTypeData",
                    feeTypeDataCsvBaseHeaders(hasFeepayer),
                    TxHistoryDataViews.getFeeTypeData(granularity, fromDate, toDate, feepayer)
                        .map { it.toCsv(hasFeepayer, granularity) }
                )
            )
        }
        return fileList
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getFileNameBase(feepayer: String?): String {
        val to = if (toDate != null) dateFormat.format(toDate) else "CURRENT"
        val full = if (fromDate != null) "${dateFormat.format(fromDate)} thru $to" else "ALL"
        val metrics = if (advancedMetrics) " WITH Advanced Metrics" else ""
        val payer = if (feepayer != null) " FOR $feepayer" else ""
        return "$full BY ${granularity.name}$payer$metrics - Tx History Data"
    }

    fun writeFilters(feepayer: String?): ByteArray {
        val baos = ByteArrayOutputStream()
        PrintWriter(baos).use { writer ->
            writer.println("Filters used --")
            writer.println("fromDate: ${if (fromDate != null) dateFormat.format(fromDate) else "NULL"}")
            writer.println("toDate: ${if (toDate != null) dateFormat.format(toDate) else "NULL"}")
            writer.println("granularity: ${granularity.name}")
            writer.println("advancedMetrics: $advancedMetrics")
            if (feepayer != null) {
                writer.println("feepayer: $feepayer")
            }
            writer.flush()
            return baos.toByteArray()
        }
    }
}

//endregion
