package io.provenance.explorer.domain.models.explorer.pulse

import java.time.OffsetDateTime

/**
 * [cacheRefreshedAt] is the latest 5-minute Pulse cache write for today
 * (excludes the NAV walk and per-denom price/volume rows).
 * [snapshotAsOf] is when today's live scope-NAV walk finished
 * (`PULSE_SCOPE_NAV_SNAPSHOT_METRIC`), which is the as-of for loan tiles.
 */
data class PulseFreshness(
    val cacheRefreshedAt: OffsetDateTime?,
    val snapshotAsOf: OffsetDateTime?
)
