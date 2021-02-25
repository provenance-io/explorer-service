package io.provenance.explorer.domain.models.explorer


data class Addresses(
    val baseHash : String,
    val accountAddr: String,
    val validatorAccountAddr: String,
    val consensusAccountAddr: String,
)
