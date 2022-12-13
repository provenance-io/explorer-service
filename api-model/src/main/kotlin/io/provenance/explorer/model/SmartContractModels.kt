package io.provenance.explorer.model

data class Contract(
    val contractAddress: String,
    val creationHeight: Int,
    val codeId: Int,
    val creator: String,
    val admin: String?,
    val label: String?
)

data class Code(
    val codeId: Int,
    val creationHeight: Int,
    val creator: String?,
    val dataHash: String?
)

data class CodeWithContractCount(
    val codeId: Int,
    val creationHeight: Int,
    val creator: String?,
    val dataHash: String?,
    val contractCount: Long
)
