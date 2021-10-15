package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import cosmos.auth.v1beta1.Auth
import cosmos.base.query.v1beta1.Pagination
import cosmos.crypto.multisig.Keys
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toBech32Data
import io.provenance.explorer.domain.extensions.edPubKeyToBech32
import io.provenance.explorer.domain.extensions.secp256k1PubKeyToBech32
import io.provenance.explorer.domain.extensions.secp256r1PubKeyToBech32
import io.provenance.explorer.domain.extensions.toSha256
import io.provenance.explorer.service.prettyRole
import io.provenance.marker.v1.Access
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus

// Marker Extensions
fun String.getTypeShortName() = this.split(".").last()

fun Any.toMarker(): MarkerAccount =
    this.typeUrl.getTypeShortName().let {
        when (it) {
            MarkerAccount::class.java.simpleName -> this.unpack(MarkerAccount::class.java)
            else -> {
                throw Exception("This marker type has not been mapped yet")
            }
        }
    }

fun MarkerAccount.isMintable() = this.accessControlList.any { it.permissionsList.contains(Access.ACCESS_MINT) }

fun Array<Access>.filterRoles() = this.filter { it.roleFilter() }
fun List<Access>.filterRoles() = this.filter { it.roleFilter() }
fun Access.roleFilter() = this != Access.UNRECOGNIZED && this != Access.ACCESS_UNSPECIFIED

fun MarkerAccount.getManagingAccounts(): MutableMap<String, List<String>> {
    val managers = this.accessControlList.associate { addr ->
        addr.address to addr.permissionsList.filterRoles().map { it.name.prettyRole() }
    }.toMutableMap()

    return when {
        this.status.number >= MarkerStatus.MARKER_STATUS_ACTIVE.number -> managers
        else -> {
            if (this.manager.isNotBlank())
                managers[this.manager] = Access.values().filterRoles().map { it.name.prettyRole() }
            managers
        }
    }
}

// Account Extensions
fun Any.getModuleAccName() =
    if (this.typeUrl.getTypeShortName() == Auth.ModuleAccount::class.java.simpleName)
        this.unpack(Auth.ModuleAccount::class.java).name
    else null

fun Any.toAddress(hrpPrefix: String) =
    when {
        typeUrl.contains("secp256k1") ->
            this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key.secp256k1PubKeyToBech32(hrpPrefix)
        typeUrl.contains("secp256r1") ->
            this.unpack(cosmos.crypto.secp256r1.Keys.PubKey::class.java).key.secp256r1PubKeyToBech32(
                hrpPrefix,
                typeUrl.split("/")[1]
            )
        typeUrl.contains("ed25519") ->
            this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key.edPubKeyToBech32(hrpPrefix)
        else -> null.also { logger().error("This typeUrl is not supported as a consensus address: $typeUrl") }
    }

// TODO: Once cosmos-sdk implements a grpc endpoint for this we can replace this with grpc Issue: https://github.com/cosmos/cosmos-sdk/issues/9437
fun getEscrowAccountAddress(portId: String, channelId: String, hrpPrefix: String): String {
    val contents = "$portId/$channelId".toByteArray()
    val preImage = "ics20-1".encodeToByteArray() + 0x0.toByte() + contents
    val hash = preImage.toSha256().copyOfRange(0, 20)
    return hash.toBech32Data(hrpPrefix).address
}

fun getPaginationBuilder(offset: Int, limit: Int) =
    Pagination.PageRequest.newBuilder().setOffset(offset.toLong()).setLimit(limit.toLong()).setCountTotal(true)
