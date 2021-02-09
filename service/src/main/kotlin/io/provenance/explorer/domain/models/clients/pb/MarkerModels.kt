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
) {
    companion object {
        fun MarkerDetail.isMintable() = this.accessControl.any { it.permissions.contains(MarkerPermissions.MINT_ROLE) }

        fun MarkerDetail.getManagingAccounts() = if (this.manager.isBlank()) {
            this.accessControl.filter { it.permissions.contains(MarkerPermissions.ADMIN_ROLE) }.map { it.address }
        } else {
            listOf(this.manager)
        }
    }
}

data class MarkerBaseAccount(
    val address: String,
    val pubKey: CustomPubKey?,
    val accountNumber: String,
    val sequence: String
)

data class MarkerPermissions(val permissions: List<String>, val address: String) {
    companion object {
        const val MINT_ROLE = "ACCESS_MINT"
        const val ADMIN_ROLE = "ACCESS_ADMIN"
    }
}


data class MarkerHolderPaged(val balances: List<MarkerHolder>, val pagination: Pagination)

data class MarkerHolder(val address: String, val coins: List<DenomAmount>)
