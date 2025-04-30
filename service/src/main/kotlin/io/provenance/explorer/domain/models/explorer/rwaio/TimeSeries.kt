package io.provenance.explorer.domain.models.explorer.rwaio

import io.provenance.explorer.domain.models.explorer.pulse.PulseCacheType

data class TimeSeriesData(
    val tsId: String,
    val records: List<TimeSeriesRecord>
)

data class TimeSeriesRecord(
    val timestamp: Long,
    val value: String
)

enum class TimeSeriesInterval {
    HOURLY,
    DAILY
}

enum class ProjectInfo(
    val mainnetId: String,
    val testnetId: String,
    val metric: PulseCacheType,
    val interval: TimeSeriesInterval
) {
    HASH_PRICE_USD(
        mainnetId = "67e6f842f796f89f6a62e41d",
        testnetId = "681e6c7423218c8540e51e9b",
        metric = PulseCacheType.HASH_PRICE_METRIC,
        interval = TimeSeriesInterval.HOURLY
    ),
    HASH_MARKET_CAPITALIZATION_USD(
        mainnetId = "67e6f842f796f89f6a62e41e",
        testnetId = "681e6c8b23218c8540e51e9c",
        metric = PulseCacheType.HASH_MARKET_CAP_METRIC,
        interval = TimeSeriesInterval.HOURLY
    ),
    HASH_TOTAL_VOLUME_USD(
        mainnetId = "67e6f842f796f89f6a62e41f",
        testnetId = "681e6cb323218c8540e51e9e",
        metric = PulseCacheType.HASH_VOLUME_METRIC,
        interval = TimeSeriesInterval.HOURLY
    ),
    HASH_TVL_USD(
        mainnetId = "67e6f842f796f89f6a62e420",
        testnetId = "681e6c9623218c8540e51e9d",
        metric = PulseCacheType.HASH_TVL_METRIC,
        interval = TimeSeriesInterval.HOURLY
    ),
    UNIQUE_WALLETS_COUNT(
        mainnetId = "67e6f842b1c3c1935b3b80a6",
        testnetId = "681cc340b8f899cb92ae6edd",
        metric = PulseCacheType.PULSE_PARTICIPANTS_METRIC,
        interval = TimeSeriesInterval.DAILY
    ),
    DAILY_TRANSACTIONS_COUNT(
        mainnetId = "67e6f842b1c3c1935b3b80a7",
        testnetId = "681cc340b8f899cb92ae6edf",
        metric = PulseCacheType.PULSE_TRANSACTION_VOLUME_METRIC,
        interval = TimeSeriesInterval.DAILY
    )
}
