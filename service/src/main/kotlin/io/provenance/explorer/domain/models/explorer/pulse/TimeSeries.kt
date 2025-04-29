package io.provenance.explorer.domain.models.explorer.pulse

data class TimeSeriesData(
    val tsId: String,
    val records: List<TimeSeriesRecord>
)

data class TimeSeriesRecord(
    val timestamp: Long,
    val value: String
)

enum class ProjectInfo(val id: String, val metric: PulseCacheType? = null) {
    PRICE_USD("67e6f842f796f89f6a62e41d", PulseCacheType.HASH_PRICE_METRIC),
    MARKET_CAPITALIZATION_USD("67e6f842f796f89f6a62e41e", PulseCacheType.HASH_MARKET_CAP_METRIC),
    TOTAL_VOLUME_USD("67e6f842f796f89f6a62e41f", PulseCacheType.HASH_VOLUME_METRIC),
    TVL_USD("67e6f842f796f89f6a62e420", PulseCacheType.HASH_STAKED_METRIC),
    UNIQUE_WALLETS_COUNT("67e6f842b1c3c1935b3b80a6", PulseCacheType.PULSE_PARTICIPANTS_METRIC),
    DAILY_TRANSACTIONS_COUNT("67e6f842b1c3c1935b3b80a7", PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC)
}
