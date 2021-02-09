package io.provenance.explorer.domain.models.clients.pb

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import io.provenance.explorer.domain.models.clients.CustomPubKey
import io.provenance.explorer.domain.models.clients.DenomAmount
import io.provenance.explorer.domain.models.clients.Pagination


data class AccountSingle(val account: Account)

interface Account { val type: String }

// Only used for unknown account types until they can be enumerated
data class UnknownAccount(
    override val type: String,
    val accountObject: JsonNode
) : Account

data class ModuleAccount(
    @JsonProperty("@type") override val type: String,
    val baseAccount: MarkerBaseAccount,
    val name: String,
    val permissions: List<String>
) : Account {
    companion object {
        const val TYPE = "ModuleAccount"
    }
}

data class BaseAccount(
    @JsonProperty("@type") override val type: String,
    val address: String,
    val pubKey: CustomPubKey?,
    val accountNumber: String,
    val sequence: String
) : Account {
    companion object {
        const val TYPE = "BaseAccount"
    }
}

fun BaseAccount.toMarkerBaseAccount() = MarkerBaseAccount(this.address, this.pubKey, this.accountNumber, this.sequence)

data class MarkerAccount(
    @JsonProperty("@type") override val type: String,
    val baseAccount: MarkerBaseAccount,
    val manager: String,
    val accessControl: List<MarkerPermissions>,
    val status: String,
    val denom: String,
    val supply: String,
    val markerType: String,
    val supplyFixed: Boolean,
    val allowGovernanceControl: Boolean
) : Account {
    companion object {
        const val TYPE = "MarkerAccount"
    }
}

data class AccountBalancesPaged(
    val balances: List<DenomAmount>,
    val pagination: Pagination
)
