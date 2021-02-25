package io.provenance.explorer.grpc

import com.google.protobuf.Any
import cosmos.base.query.v1beta1.Pagination
import io.provenance.explorer.domain.extensions.edPubKeyToBech32
import io.provenance.explorer.domain.extensions.secpPubKeyToBech32
import io.provenance.explorer.domain.extensions.toValue
import io.provenance.marker.v1.Access
import io.provenance.marker.v1.MarkerAccount
import java.util.Base64


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

// TODO: Update when I have access to this api, if this'll work
//fun Any.getMarker(reflClient: CosmosReflectionGrpcClient) {
//    val interfaceName = MarkerAccount.getDescriptor().options.getExtension(Cosmos.implementsInterface)
//    reflClient.listImplementations()[interfaceName].firstOrNull { it == this.typeUrl }.let { this.unpack(Class.forName
//        (it)) }
//}

fun Any.toKeyValue() =
        when {
            typeUrl.contains("secp256k1") -> this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key
            typeUrl.contains("ed25519") -> this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key
            else -> null
        }.let { it?.toValue() }

fun Any.toConsAddress(hrpPrefix: String) =
    when {
        typeUrl.contains("secp256k1") ->
            this.unpack(cosmos.crypto.secp256k1.Keys.PubKey::class.java).key.secpPubKeyToBech32(hrpPrefix)
        typeUrl.contains("ed25519") ->
            this.unpack(cosmos.crypto.ed25519.Keys.PubKey::class.java).key.edPubKeyToBech32(hrpPrefix)
        else -> null
    }


fun MarkerAccount.isMintable() = this.accessControlList.any { it.permissionsList.contains(Access.ACCESS_MINT) }

fun MarkerAccount.getManagingAccounts() = if (this.manager.isBlank()) {
    this.accessControlList.filter { it.permissionsList.contains(Access.ACCESS_ADMIN) }.map { it.address }
} else {
    listOf(this.manager)
}

fun getPaginationBuilder(offset: Int, limit: Int) =
    Pagination.PageRequest.newBuilder().setOffset(offset.toLong()).setLimit(limit.toLong())
