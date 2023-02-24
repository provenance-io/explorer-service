package io.provenance.explorer.domain.entities

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.domain.extensions.execAndMap
import io.provenance.explorer.domain.models.explorer.download.toFeeTypeData
import io.provenance.explorer.domain.models.explorer.download.toTxHistoryChartData
import io.provenance.explorer.domain.models.explorer.download.toTxTypeData
import io.provenance.explorer.model.base.DateTruncGranularity
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

class TxHistoryDataViews {
    companion object {

        fun refreshViews() = transaction {
            var query = "REFRESH MATERIALIZED VIEW tx_history_chart_data_hourly"
            this.exec(query)
            query = "REFRESH MATERIALIZED VIEW tx_type_data_hourly"
            this.exec(query)
            query = "REFRESH MATERIALIZED VIEW fee_type_data_hourly"
            this.exec(query)
        }

        fun getTxHistoryChartData(
            granularity: DateTruncGranularity,
            fromDate: DateTime? = null,
            toDate: DateTime? = null,
            feepayer: String? = null
        ) =
            transaction {
                val dateWhere =
                    if (fromDate != null && toDate != null) {
                        " hourly between ? and ? "
                    } else if (fromDate != null) {
                        " hourly >= ? "
                    } else {
                        " true "
                    }
                val feepayerWhere = if (feepayer != null) " and feepayer = ? " else " and true "
                val query = "Select " +
                    "date_trunc(?, hourly) as date, " +
                    (if (feepayer != null) "feepayer, " else "") +
                    "sum(tx_count) as tx_count, " +
                    "sum(fee_amount_in_base_token) as fee_amount_in_base_token, " +
                    "sum(gas_wanted) as gas_wanted, " +
                    "sum(gas_used) as gas_used, " +
                    "sum(fee_amount_in_base_token/?::numeric) as fee_amount_in_token, " +
                    "sum((fee_amount_in_base_token/?::numeric) * token_price_usd) fees_paid_in_usd, " +
                    "max(token_price_usd) as max_token_price_usd, " +
                    "min(token_price_usd) as min_token_price_usd, " +
                    "avg(token_price_usd) as avg_token_price_usd " +
                    "from tx_history_chart_data_hourly " +
                    "where " + dateWhere + feepayerWhere +
                    "group by date" + (if (feepayer != null) ", feepayer " else " ") +
                    "order by date;"
                        .trimIndent()
                val arguments = mutableListOf<Pair<ColumnType, *>>(
                    Pair(VarCharColumnType(64), granularity.name),
                    Pair(IntegerColumnType(), UTILITY_TOKEN_BASE_MULTIPLIER),
                    Pair(IntegerColumnType(), UTILITY_TOKEN_BASE_MULTIPLIER)
                )
                if (fromDate != null) {
                    arguments.add(Pair(DateColumnType(true), fromDate))
                    if (toDate != null) {
                        arguments.add(Pair(DateColumnType(true), toDate.plusDays(1).minusMinutes(1)))
                    }
                }
                if (feepayer != null) {
                    arguments.add(Pair(VarCharColumnType(128), feepayer))
                }
                query.execAndMap(arguments) { it.toTxHistoryChartData(feepayer != null) }
            }

        fun getTxTypeData(
            granularity: DateTruncGranularity,
            fromDate: DateTime? = null,
            toDate: DateTime? = null,
            feepayer: String? = null
        ) = transaction {
            val dateWhere =
                if (fromDate != null && toDate != null) {
                    " hourly between ? and ? "
                } else if (fromDate != null) {
                    " hourly >= ? "
                } else {
                    " true "
                }
            val feepayerWhere = if (feepayer != null) " and feepayer = ? " else " and true "
            val query = "Select " +
                "date_trunc(?, hourly) as date, " +
                (if (feepayer != null) "feepayer, " else "") +
                "tx_type, " +
                "sum(tx_type_count) as tx_type_count " +
                "from tx_type_data_hourly " +
                "where " + dateWhere + feepayerWhere +
                "group by date, " + (if (feepayer != null) "feepayer, " else "") + "tx_type " +
                "order by date, tx_type;"
                    .trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(Pair(VarCharColumnType(64), granularity.name))
            if (fromDate != null) {
                arguments.add(Pair(DateColumnType(true), fromDate))
                if (toDate != null) {
                    arguments.add(Pair(DateColumnType(true), toDate.plusDays(1).minusMinutes(1)))
                }
            }
            if (feepayer != null) {
                arguments.add(Pair(VarCharColumnType(128), feepayer))
            }
            query.execAndMap(arguments) { it.toTxTypeData(feepayer != null) }
        }

        fun getFeeTypeData(
            granularity: DateTruncGranularity,
            fromDate: DateTime? = null,
            toDate: DateTime? = null,
            feepayer: String? = null
        ) = transaction {
            val dateWhere =
                if (fromDate != null && toDate != null) {
                    " hourly between ? and ? "
                } else if (fromDate != null) {
                    " hourly >= ? "
                } else {
                    " true "
                }
            val feepayerWhere = if (feepayer != null) " and feepayer = ? " else " and true "
            val query = "Select " +
                "date_trunc(?, hourly) as date, " +
                (if (feepayer != null) "feepayer, " else "") +
                "fee_type, " +
                "msg_type, " +
                "sum(fee_amount_in_base_token) as fee_amount_in_base_token, " +
                "sum(fee_amount_in_base_token/?::numeric) as fee_amount_in_token, " +
                "sum((fee_amount_in_base_token/?::numeric) * token_price_usd) fees_paid_in_usd, " +
                "max(token_price_usd) as max_token_price_usd, " +
                "min(token_price_usd) as min_token_price_usd, " +
                "avg(token_price_usd) as avg_token_price_usd " +
                "from fee_type_data_hourly " +
                "where " + dateWhere + feepayerWhere +
                "group by date, " + (if (feepayer != null) "feepayer, " else "") + "fee_type, msg_type " +
                "order by date, fee_type, msg_type;"
                    .trimIndent()
            val arguments = mutableListOf<Pair<ColumnType, *>>(
                Pair(VarCharColumnType(64), granularity.name),
                Pair(IntegerColumnType(), UTILITY_TOKEN_BASE_MULTIPLIER),
                Pair(IntegerColumnType(), UTILITY_TOKEN_BASE_MULTIPLIER)
            )
            if (fromDate != null) {
                arguments.add(Pair(DateColumnType(true), fromDate))
                if (toDate != null) {
                    arguments.add(Pair(DateColumnType(true), toDate.plusDays(1).minusMinutes(1)))
                }
            }
            if (feepayer != null) {
                arguments.add(Pair(VarCharColumnType(128), feepayer))
            }
            query.execAndMap(arguments) { it.toFeeTypeData(feepayer != null) }
        }
    }
}
