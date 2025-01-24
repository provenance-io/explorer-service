package io.provenance.explorer.model.download

import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.stringfy
import java.time.LocalDateTime
import java.time.ZoneId
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

val currFormat = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 4 }
fun BigDecimal.currFormat() = currFormat.format(this)

fun LocalDateTime.customFormat(granularity: DateTruncGranularity) =
    when (granularity) {
        DateTruncGranularity.HOUR,
        DateTruncGranularity.MINUTE -> DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss").format(this)
        DateTruncGranularity.DAY -> DateTimeFormatter.ofPattern("yyyy-MM-dd").format(this)
        DateTruncGranularity.MONTH -> DateTimeFormatter.ofPattern("yyyy-MM").format(this)
    }

data class TxHistoryChartData(
    val date: LocalDateTime,
    val feepayer: String? = null,
    val txCount: BigDecimal,
    val feeAmountInBaseToken: BigDecimal,
    val gasWanted: BigDecimal,
    val gasUsed: BigDecimal,
    val feeAmountInToken: BigDecimal,
    val feesPaidInUsd: BigDecimal?,
    val maxTokenPriceUsd: BigDecimal?,
    val minTokenPriceUsd: BigDecimal?,
    val avgTokenPriceUsd: BigDecimal?
) {
    fun toCsv(
        advancedMetrics: Boolean,
        hasFeepayer: Boolean,
        granularity: DateTruncGranularity
    ): MutableList<Any> {
        val base = mutableListOf<Any>(this.date.customFormat(granularity))
        if (hasFeepayer) base.add(this.feepayer!!)
        base.addAll(
            listOf(
                this.txCount,
                this.feeAmountInToken.stringfy(),
                this.feesPaidInUsd?.currFormat() ?: "",
                this.minTokenPriceUsd?.currFormat() ?: "",
                this.maxTokenPriceUsd?.currFormat() ?: "",
                this.avgTokenPriceUsd?.currFormat() ?: ""
            )
        )
        if (advancedMetrics) base.addAll(listOf(this.gasWanted, this.gasUsed))
        return base
    }
}

data class TxTypeData(
    val date: LocalDateTime,
    val feepayer: String? = null,
    val txType: String,
    val txTypeCount: BigDecimal
) {
    fun toCsv(hasFeepayer: Boolean, granularity: DateTruncGranularity): MutableList<Any> {
        val base = mutableListOf<Any>(this.date.customFormat(granularity))
        if (hasFeepayer) base.add(this.feepayer!!)
        base.addAll(listOf(this.txType, this.txTypeCount))
        return base
    }
}

data class FeeTypeData(
    val date: LocalDateTime,
    val feepayer: String? = null,
    val feeType: String,
    val msgType: String?,
    val feeAmountInBaseToken: BigDecimal,
    val feeAmountInToken: BigDecimal,
    val feesPaidInUsd: BigDecimal?,
    val maxTokenPriceUsd: BigDecimal?,
    val minTokenPriceUsd: BigDecimal?,
    val avgTokenPriceUsd: BigDecimal?
) {
    fun toCsv(hasFeepayer: Boolean, granularity: DateTruncGranularity): MutableList<Any> {
        val base = mutableListOf<Any>(this.date.customFormat(granularity))
        if (hasFeepayer) base.add(this.feepayer!!)
        base.addAll(
            listOf(
                this.feeType,
                this.msgType ?: "",
                this.feeAmountInToken.stringfy(),
                this.feesPaidInUsd?.currFormat() ?: "",
                this.minTokenPriceUsd?.currFormat() ?: "",
                this.maxTokenPriceUsd?.currFormat() ?: "",
                this.avgTokenPriceUsd?.currFormat() ?: ""
            )
        )
        return base
    }
}
