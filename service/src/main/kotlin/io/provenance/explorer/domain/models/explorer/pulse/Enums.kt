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
    HASH_SUPPLY_METRIC,
    HASH_STAKED_METRIC,
    HASH_CIRCULATING_METRIC,

    PULSE_MARKET_CAP_METRIC,
    PULSE_TRANSACTION_VOLUME_METRIC,
    PULSE_FEES_AUCTIONS_METRIC,
    PULSE_TODAYS_NAV_METRIC,
    PULSE_NAV_DECREASE_METRIC,
    PULSE_TOTAL_NAV_METRIC,
    PULSE_TRADE_SETTLEMENT_METRIC,
    PULSE_TRADE_VALUE_SETTLED_METRIC,
    PULSE_PARTICIPANTS_METRIC,
    PULSE_MARGIN_LOANS_METRIC,
    PULSE_DEMOCRATIZED_PRIME_POOLS_METRIC,
    PULSE_COMMITTED_ASSETS_VALUE_METRIC,
    PULSE_COMMITTED_ASSETS_METRIC,
    PULSE_ASSET_PRICE_SUMMARY_METRIC,
    PULSE_ASSET_VOLUME_SUMMARY_METRIC,

    FTS_LOAN_TOTAL_BALANCE_METRIC,
    FTS_LOAN_TOTAL_COUNT_METRIC,
    FTS_LOAN_DISBURSEMENTS_METRIC,
    FTS_LOAN_DISBURSEMENT_COUNT_METRIC,
    FTS_LOAN_PAYMENTS_METRIC,
    FTS_LOAN_TOTAL_PAYMENTS_METRIC,
}
