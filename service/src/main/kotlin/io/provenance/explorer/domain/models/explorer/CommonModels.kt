package io.provenance.explorer.domain.models.explorer

import java.math.BigInteger


data class PagedResults<T>(val pages: Int, val results: List<T>)

data class Addresses(
    val baseHash : String,
    val accountAddr: String,
    val validatorAccountAddr: String,
    val consensusAccountAddr: String,
)

data class Signatures(
    val signers: List<String>,
    val threshold: Int?
)

data class DenomAmount(
    val denom: String,
    val amount: BigInteger
)
