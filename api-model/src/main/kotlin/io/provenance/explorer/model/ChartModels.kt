package io.provenance.explorer.model

import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.stringfy
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

val currFormat = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 4 }
fun BigDecimal.currFormat() = currFormat.format(this)

fun DateTime.customFormat(granularity: DateTruncGranularity) =
    when (granularity) {
        DateTruncGranularity.HOUR,
        DateTruncGranularity.MINUTE -> DateTimeFormat.forPattern("yyy-MM-dd hh:mm:ss").print(this)
        DateTruncGranularity.DAY -> DateTimeFormat.forPattern("yyy-MM-dd").print(this)
        DateTruncGranularity.MONTH -> DateTimeFormat.forPattern("yyy-MM").print(this)
    }

data class TxHistoryChartData(
    val date: DateTime,
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
        val base = mutableListOf<Any>(this.date.withZone(DateTimeZone.UTC).customFormat(granularity))
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
    val date: DateTime,
    val feepayer: String? = null,
    val txType: String,
    val txTypeCount: BigDecimal,
) {
    fun toCsv(hasFeepayer: Boolean, granularity: DateTruncGranularity): MutableList<Any> {
        val base = mutableListOf<Any>(this.date.withZone(DateTimeZone.UTC).customFormat(granularity))
        if (hasFeepayer) base.add(this.feepayer!!)
        base.addAll(listOf(this.txType, this.txTypeCount))
        return base
    }
}

data class FeeTypeData(
    val date: DateTime,
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
        val base = mutableListOf<Any>(this.date.withZone(DateTimeZone.UTC).customFormat(granularity))
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
