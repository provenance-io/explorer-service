package io.provenance.explorer.domain.models.clients

import com.fasterxml.jackson.annotation.JsonProperty

data class DenomAmount(val denom: String, val amount: String)

data class PbResponse<T>(val height: String, val result: T)

data class PubKey(val type: String, val value: String)

data class Pagination(
    val nextKey: String?,
    val total: String
)


data class CustomPubKey(
    @JsonProperty("@type") val type: String,
    val key: String
)
