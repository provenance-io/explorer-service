package io.provenance.explorer.domain.models.clients.pb

import io.provenance.explorer.domain.models.clients.DenomAmount


data class MarkerHolder(
    val address: String,
    val coins: List<DenomAmount>,
    val publicKey: String?,
    val accountNumber: String,
    val sequence: String
)


data class MarkerDetail(val type: String, val value: MarkerValue)

data class MarkerValue(
    val address: String,
    val coins: List<DenomAmount>,
    val accountNumber: Int,
    val sequence: Int,
    val permissions: List<MarkerPermissions>,
    val status: String,
    val denom: String,
    val totalSupply: String,
    val markerType: String
)

data class MarkerPermissions(val permissions: List<String>, val address: String)
