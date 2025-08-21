package io.provenance.explorer.domain.models.explorer.pulse

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class UpbToken(
    @JsonProperty("supply") val supply: BigDecimal,
    @JsonProperty("account_count") val accountCount: BigDecimal,
    @JsonProperty("structures_count") val structuresCount: BigDecimal,
    @JsonProperty("total_securitization") val totalSecuritization: BigDecimal,
    @JsonProperty("total_participation") val totalParticipation: BigDecimal,
    @JsonProperty("total_warehoused") val totalWarehoused: BigDecimal,
    @JsonProperty("total_pools") val totalPools: BigDecimal,
    @JsonProperty("total_unstructured") val totalUnstructured: BigDecimal,
    @JsonProperty("as_of_time") val asOfTime: Long,
)
