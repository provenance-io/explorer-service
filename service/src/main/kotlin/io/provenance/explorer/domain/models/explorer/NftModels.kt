package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.metadata.v1.Description
import io.provenance.metadata.v1.Party
import io.provenance.metadata.v1.RecordOutput

data class ScopeListview(
    val scopeAddr: String,
    val specName: String?,
    val specAddr: String,
    val lastUpdated: String,
    val isOwner: Boolean,
    val isDataAccess: Boolean,
    val isValueOwner: Boolean
)

data class ScopeDetail(
    val scopeAddr: String,
    val specName: String?,
    val specAddr: String,
    val description: SpecDescrip?,
    val owners: List<PartyAndRole>,
    val dataAccess: List<String>,
    val valueOwner: String?
)

data class SpecDescrip(
    val name: String,
    val description: String,
    val websiteUrl: String,
    val iconUrl: String
)

fun Description.toSpecDescrip() = SpecDescrip(this.name, this.description, this.websiteUrl, this.iconUrl)

data class PartyAndRole(
    val party: String?,
    val role: String
)

fun List<Party>.toOwnerRoles() = this.map { PartyAndRole(it.address, it.role.name) }

enum class RecordStatus { UNFILLED, FILLED, NON_CONFORMING, ORPHAN }

data class ScopeRecord(
    val status: RecordStatus,
    val recordName: String,
    val specList: List<RecordSpecDetail>?,
    val record: RecordDetail?
)

data class RecordDetail(
    val recordAddr: String,
    val recordSpecAddr: String,
    val lastModified: String,
    val responsibleParties: List<PartyAndRole>,
    val outputs: List<RecordInputOutput>
)

data class RecordInputOutput(
    val name: String,
    val hash: String,
    val status: String
)

fun RecordOutput.toDataObject(name: String) = RecordInputOutput(name, this.hash, this.status.name)

data class RecordSpecDetail(
    val contractSpecAddr: String,
    val recordSpecAddr: String,
    val responsibleParties: List<String>
)

data class NftVOTransferObj(
    val scope: NftScopeRecord,
    val address: String,
    val tx: TxCacheRecord
)
