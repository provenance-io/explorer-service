package io.provenance.explorer.domain.models.explorer.pulse

import java.beans.PropertyEditorSupport

enum class MetricTrendType {
    UP,
    DOWN,
    FLAT
}

enum class MetricTrendPeriod {
    DAY,
}

class MetricRangeTypeConverter : PropertyEditorSupport() {
    override fun setAsText(text: String) {
        value = MetricRangeType.entries.find { it.range == text }
    }
}

enum class MetricRangeType(val range: String) {
    DAY("24h"),
    WEEK("1w"),
    MONTH("1m"),
    YEAR("1y")
}

enum class PulseCacheType {
    HASH_MARKET_CAP_METRIC,
    HASH_VOLUME_METRIC,
    HASH_FDV_METRIC,
    HASH_SUPPLY_METRIC,
    HASH_STAKED_METRIC,
    HASH_CIRCULATING_METRIC,

    PULSE_TVL_METRIC,
    PULSE_TRADING_TVL_METRIC,
    PULSE_TRANSACTION_VOLUME_METRIC,
    PULSE_TODAYS_NAV_METRIC,
    PULSE_TOTAL_NAV_METRIC,
    PULSE_TRADE_SETTLEMENT_METRIC,
    PULSE_TRADE_VALUE_SETTLED_METRIC,
    PULSE_PARTICIPANTS_METRIC,
    PULSE_COMMITTED_ASSETS_VALUE_METRIC,
    PULSE_COMMITTED_ASSETS_METRIC,
    PULSE_ASSET_PRICE_SUMMARY_METRIC,
    PULSE_ASSET_VOLUME_SUMMARY_METRIC,
    PULSE_CHAIN_FEES_VALUE_METRIC,

    LOAN_LEDGER_TOTAL_BALANCE_METRIC,
    LOAN_LEDGER_TOTAL_COUNT_METRIC,
    LOAN_LEDGER_DISBURSEMENTS_METRIC,
    LOAN_LEDGER_DISBURSEMENT_COUNT_METRIC,
    LOAN_LEDGER_PAYMENTS_METRIC,
    LOAN_LEDGER_TOTAL_PAYMENTS_METRIC,
}
