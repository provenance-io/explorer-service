package io.provenance.explorer.domain.models.clients.pb

import com.fasterxml.jackson.annotation.JsonProperty
import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.Pagination

data class MarkersPaged(val markers: List<MarkerDetail>, val pagination: Pagination)

data class MarkerDetailSingle(val marker : MarkerDetail)

data class MarkerDetail (
    @JsonProperty("@type") val type: String,
    val baseAccount: MarkerBaseAccount,
    val manager: String,
    val accessControl : List<MarkerPermissions>,
    val status : String,
    val denom: String,
    val supply: String,
    val markerType: String,
    val supplyFixed : Boolean,
    val allowGovernanceControl : Boolean
)

data class MarkerBaseAccount(
    val address: String,
    val pubKey: CustomPubKey?,
    val accountNumber: String,
    val sequence: String
)

data class MarkerPermissions(val permissions: List<String>, val address: String)

data class MarkerHolderPaged(val balances: List<MarkerHolder>, val pagination: Pagination)

data class MarkerHolder(val address: String, val coins: List<DenomAmount>)
