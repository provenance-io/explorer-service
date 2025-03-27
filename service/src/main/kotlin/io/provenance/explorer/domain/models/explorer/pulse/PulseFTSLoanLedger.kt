package io.provenance.explorer.domain.models.explorer.pulse

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class PulseFTSLoanLedger(
    @JsonProperty("AssetUUID") val assetUUID: String,
    @JsonProperty("LedgerUUID") val ledgerUUID: String,
    @JsonProperty("LedgerEntryType") val ledgerEntryType: String,
    @JsonProperty("PostDate") val postDate: LocalDateTime,
    @JsonProperty("EffectiveDate") val effectiveDate: LocalDateTime,
    @JsonProperty("Bucket") val bucket: String,
    @JsonProperty("EntryAmount") val entryAmount: Double,
    @JsonProperty("PrinApplied") val prinApplied: Double,
    @JsonProperty("PrinBalance") val prinBalance: Double,
    @JsonProperty("IntApplied") val intApplied: Double,
    @JsonProperty("IntBalance") val intBalance: Double,
    @JsonProperty("OtherApplied") val otherApplied: Double,
    @JsonProperty("OtherBalance") val otherBalance: Double
)
