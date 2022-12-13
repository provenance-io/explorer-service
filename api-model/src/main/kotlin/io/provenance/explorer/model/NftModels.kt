package io.provenance.explorer.model

data class ScopeListview(
    val scopeUuid: String,
    val scopeAddr: String,
    val specName: String?,
    val specAddr: String,
    val lastUpdated: String,
    val isOwner: Boolean,
    val isDataAccess: Boolean,
    val isValueOwner: Boolean
)

data class ScopeDetail(
    val scopeUuid: String,
    val scopeAddr: String,
    val specName: String?,
    val specAddr: String,
    val description: SpecDescrip?,
    val owners: List<PartyAndRole>,
    val dataAccess: List<String>,
    val valueOwner: String?,
    val attributes: List<AttributeObj>
)

data class SpecDescrip(
    val name: String,
    val description: String,
    val websiteUrl: String,
    val iconUrl: String
)

data class PartyAndRole(
    val party: String?,
    val role: String
)

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

data class RecordSpecDetail(
    val contractSpecAddr: String,
    val recordSpecAddr: String,
    val responsibleParties: List<String>
)
