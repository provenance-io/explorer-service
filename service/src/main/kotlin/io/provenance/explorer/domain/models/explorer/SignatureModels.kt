package io.provenance.explorer.domain.models.explorer

import com.google.protobuf.Any

const val SECP_256_K1 = "secp256k1"
const val SECP_256_R1 = "secp256r1"
const val ED_25519 = "ed25519"
const val LEGACY_MULTISIG = "LegacyAminoPubKey"

data class TxSignature(
    val idx: Int,
    val type: String,
    val address: String,
    val sequence: Int
)

data class AccountSigData(
    val pubkeyType: String,
    val pubkeyObject: Any,
    val base64Sig: String?,
    val childSigIdx: Int?,
    val childSigAddress: String?
)

data class AccountSigInfo(
    val type: String?,
    val base64: String?,
    val sigList: List<AccountSignature>
)

data class AccountSignature(
    val idx: Int,
    val address: String
)
