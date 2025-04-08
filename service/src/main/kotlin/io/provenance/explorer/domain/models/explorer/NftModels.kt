package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.model.PartyAndRole
import io.provenance.explorer.model.RecordInputOutput
import io.provenance.explorer.model.SpecDescrip
import io.provenance.explorer.model.base.toMAddress
import io.provenance.explorer.model.base.toMAddressScopeSpec
import io.provenance.metadata.v1.Description
import io.provenance.metadata.v1.Party
import io.provenance.metadata.v1.RecordOutput
import io.provenance.metadata.v1.Scope
import io.provenance.metadata.v1.ScopeWrapper
import java.sql.ResultSet

fun Description.toSpecDescrip() = SpecDescrip(this.name, this.description, this.websiteUrl, this.iconUrl)

fun List<Party>.toOwnerRoles() = this.map { PartyAndRole(it.address, it.role.name) }

fun RecordOutput.toDataObject(name: String) = RecordInputOutput(name, this.hash, this.status.name)

fun ResultSet.toNftData() = buildNftData(
    getString("uuid"),
    getString("address"),
    OBJECT_MAPPER.readValue(getString("scope"), Scope::class.java)
)

fun ScopeWrapper.toNftData() = buildNftData(scopeIdInfo.scopeUuid, scopeIdInfo.scopeAddr, scope)

fun NftScopeRecord.toNftData() = buildNftData(uuid, address, scope ?: Scope.getDefaultInstance())

fun buildNftData(uuid: String, address: String, scope: Scope) = NftData(
    scopeUuid = uuid,
    scopeAddr = address,
    specUuid = scope.specificationId.toMAddress().getPrimaryUuid().toString(),
    specAddr = scope.specificationId.toMAddress().getPrimaryUuid().toMAddressScopeSpec().toString(),
    owners = scope.ownersList.toOwnerRoles(),
    dataAccess = scope.dataAccessList,
    valueOwner = scope.valueOwnerAddress
)

data class NftVOTransferObj(
    val scope: NftScopeRecord,
    val address: String,
    val tx: TxCacheRecord
)

data class NftData(
    val scopeUuid: String,
    val scopeAddr: String,
    val specUuid: String,
    val specAddr: String,
    val owners: List<PartyAndRole>,
    val dataAccess: List<String>,
    val valueOwner: String,
)
