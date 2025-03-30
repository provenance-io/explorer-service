package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.model.PartyAndRole
import io.provenance.explorer.model.RecordInputOutput
import io.provenance.explorer.model.SpecDescrip
import io.provenance.metadata.v1.Description
import io.provenance.metadata.v1.Party
import io.provenance.metadata.v1.RecordOutput
import io.provenance.metadata.v1.Scope
import io.provenance.metadata.v1.ScopeWrapper

fun Description.toSpecDescrip() = SpecDescrip(this.name, this.description, this.websiteUrl, this.iconUrl)

fun List<Party>.toOwnerRoles() = this.map { PartyAndRole(it.address, it.role.name) }

fun RecordOutput.toDataObject(name: String) = RecordInputOutput(name, this.hash, this.status.name)

fun ScopeWrapper.toNftData() = NftData(scopeIdInfo.scopeUuid, scopeIdInfo.scopeAddr, scope)

fun NftScopeRecord.toNftData() = NftData(uuid, address, scope ?: Scope.getDefaultInstance())

data class NftVOTransferObj(
    val scope: NftScopeRecord,
    val address: String,
    val tx: TxCacheRecord
)

data class NftData(
    val uuid: String,
    val address: String,
    val scope: Scope
)
