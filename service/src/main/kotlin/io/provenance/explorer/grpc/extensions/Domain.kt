package io.provenance.explorer.grpc.extensions

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import cosmos.base.query.v1beta1.Pagination
import cosmos.crypto.multisig.Keys
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.edPubKeyToBech32
import io.provenance.explorer.domain.extensions.secpPubKeyToBech32
import io.provenance.explorer.domain.extensions.toValue
import io.provenance.explorer.service.prettyRole
import io.provenance.marker.v1.Access
import io.provenance.marker.v1.MarkerAccount
import io.provenance.marker.v1.MarkerStatus


// Marker Extensions
fun String.getTypeShortName() = this.split(".").last()

fun Any.toMarker(): MarkerAccount =
    this.typeUrl.getTypeShortName().let {
            when(it) {
                MarkerAccount::class.java.simpleName -> this.unpack(MarkerAccount::class.java)
                else -> {
                    throw Exception("This marker type has not been mapped yet")
                }
            }
        }

fun MarkerAccount.isMintable() = this.accessControlList.any { it.permissionsList.contains(Access.ACCESS_MINT) }

fun MarkerAccount.getManagingAccounts(): MutableMap<String, List<String>> {
    val managers = this.accessControlList.associate { addr ->
       addr.address to addr.permissionsList.filter { it.number != 0 }.map { it.name.prettyRole() }
    }.toMutableMap()

    return when {
        this.status >= MarkerStatus.MARKER_STATUS_ACTIVE -> managers
        else -> {
            if (this.manager.isNotBlank())
                managers[this.manager] = Access.values().filter { it.number != 0 }.map { it.name.prettyRole() }
            managers
        }
    }
}

// PubKey Extensions
fun Any.toSingleSigKeyValue() = this.toSingleSig().let { it?.toValue() }

fun Any.toSingleSig(): ByteString? =
    when {
        typeUrl.contains("secp256k1") -> this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key
        typeUrl.contains("ed25519") -> this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key
        else -> null.also { logger().error("This typeUrl is not supported in single sig: $typeUrl") }
    }

fun Any.toMultiSig() =
    when {
        typeUrl.contains("LegacyAminoPubKey") -> this.unpack(Keys.LegacyAminoPubKey::class.java)
        else -> null.also { logger().error("This typeUrl is not supported in multi sig: $typeUrl") }
    }

fun Any.toAddress(hrpPrefix: String) =
    when {
        typeUrl.contains("secp256k1") ->
            this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key.secpPubKeyToBech32(hrpPrefix)
        typeUrl.contains("ed25519") ->
            this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key.edPubKeyToBech32(hrpPrefix)
        else -> null.also { logger().error("This typeUrl is not supported as a consensus address: $typeUrl") }
    }




fun getPaginationBuilder(offset: Int, limit: Int) =
    Pagination.PageRequest.newBuilder().setOffset(offset.toLong()).setLimit(limit.toLong()).setCountTotal(true)
