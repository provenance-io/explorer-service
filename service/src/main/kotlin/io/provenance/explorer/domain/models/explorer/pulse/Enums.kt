package io.provenance.explorer.domain.models.explorer.pulse

enum class MetricTrendType {
    UP,
    DOWN,
    FLAT
}

enum class MetricTrendPeriod {
    DAY,
}

enum class HashMetricType {
    MARKET_CAP_METRIC,
    STAKED_METRIC,
    CIRCULATING_METRIC,
    SUPPLY_METRIC,
}

enum class PulseCacheType {
    HASH_SUPPLY_METRIC,
    PULSE_MARKET_CAP_METRIC,
    PULSE_TRANSACTION_VOLUME_METRIC,
    PULSE_FEES_AUCTIONS_METRIC,
    PULSE_RECEIVABLES_METRIC,
    PULSE_TRADE_SETTLEMENT_METRIC,
    PULSE_PARTICIPANTS_METRIC,
    PULSE_MARGIN_LOANS_METRIC,
    PULSE_DEMOCRATIZED_PRIME_POOLS_METRIC
}
