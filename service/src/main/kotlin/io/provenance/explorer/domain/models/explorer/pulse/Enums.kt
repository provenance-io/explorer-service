package io.provenance.explorer.domain.models.explorer.pulse

enum class MetricTrendType {
    UP,
    DOWN,
    FLAT
}

enum class MetricTrendPeriod {
    HOUR,
    DAY,
    WEEK,
    MONTH,
    YEAR
}

enum class DenomType {
    USD,
    USDC // TODO hate this
}
