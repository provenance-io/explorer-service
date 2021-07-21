package io.provenance.explorer.domain.models.explorer

import io.provenance.metadata.v1.Description
import io.provenance.metadata.v1.Party

data class ScopeListview(
    val scopeAddr: String,
    val specName: String?,
    val specAddr: String,
    val lastUpdated: String
)

data class ScopeDetail(
    val scopeAddr: String,
    val specName: String?,
    val specAddr: String,
    val description: SpecDescrip?,
    val owners: List<PartyAndRole>,
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
    val responsibleParties: List<PartyAndRole>
)

data class RecordSpecDetail(
    val contractSpecAddr: String,
    val recordSpecAddr: String,
    val responsibleParties: List<String>
)
