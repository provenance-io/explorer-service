package io.provenance.explorer.domain.models.explorer.download

import io.provenance.explorer.domain.entities.ValidatorMetricsRecord
import io.provenance.explorer.domain.extensions.CsvData
import io.provenance.explorer.domain.extensions.toPercentage
import io.provenance.explorer.model.download.ValidatorMetricData
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

//region ValidatorMetrics

fun ResultSet.toValidatorMetricData() = ValidatorMetricData(
    this.getInt("year"),
    this.getInt("quarter"),
    this.getString("moniker"),
    this.getString("operator_address"),
    this.getBoolean("is_active"),
    this.getBoolean("is_verified"),
    this.getInt("gov_vote"),
    this.getInt("gov_proposal"),
    this.getInt("gov_vote").toPercentage(100, this.getInt("gov_proposal"), 4),
    this.getInt("blocks_up"),
    this.getInt("blocks_total"),
    this.getInt("blocks_up").toPercentage(100, this.getInt("blocks_total"), 4)
)

//endregion

//region Validator Metric Body

data class ValidatorMetricsRequest(val year: Int, val quarter: Int) {

    fun getFilenameBase() = "$year Q$quarter Validator Metrics"

    fun getFile() = transaction {
        CsvData(
            "Base",
            valMetricsDataCsvHeaders,
            ValidatorMetricsRecord.getDataForPeriod(year, quarter).map { it.toCsv() }
        )
    }

    private val valMetricsDataCsvHeaders: MutableList<String> =
        mutableListOf(
            "Year",
            "Quarter",
            "Validator Moniker",
            "Validator Address",
            "Is Active",
            "Is KYC/AML Verified",
            "Gov Vote Count",
            "Gov Proposal Count",
            "Gov Participation %",
            "Uptime Block Count",
            "Total Blocks for Period",
            "Quarterly Uptime %"
        )
}

//endregion
