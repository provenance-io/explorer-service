package io.provenance.explorer.service

import io.provenance.explorer.domain.core.MdParent
import io.provenance.explorer.domain.core.MetadataAddress
import io.provenance.explorer.domain.core.getParentForType
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.core.toMAddressContractSpec
import io.provenance.explorer.domain.core.toMAddressScope
import io.provenance.explorer.domain.core.toMAddressScopeSpec
import io.provenance.explorer.domain.entities.NftContractSpecRecord
import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.NftScopeSpecRecord
import io.provenance.explorer.domain.entities.TxNftJoinRecord
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.PartyAndRole
import io.provenance.explorer.domain.models.explorer.RecordDetail
import io.provenance.explorer.domain.models.explorer.RecordSpecDetail
import io.provenance.explorer.domain.models.explorer.RecordStatus
import io.provenance.explorer.domain.models.explorer.ScopeDetail
import io.provenance.explorer.domain.models.explorer.ScopeListview
import io.provenance.explorer.domain.models.explorer.ScopeRecord
import io.provenance.explorer.domain.models.explorer.toOwnerRoles
import io.provenance.explorer.domain.models.explorer.toSpecDescrip
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class NftService(
    private val metadataClient: MetadataGrpcClient,
) {

    fun getScopeDescrip(addr: String) =
        metadataClient.getScopeSpecById(addr).scopeSpecification.specification.description

    fun getScopesForOwningAddress(address: String, page: Int, count: Int) =
        metadataClient.getScopesByOwner(address, page.toOffset(count), count).let {
            val records = it.scopeUuidsList.map { uuid -> scopeToListview(uuid) }
            PagedResults(it.pagination.total.pageCountOfResults(count), records, it.pagination.total)
        }

    private fun scopeToListview(addr: String) =
        metadataClient.getScopeById(addr).let {
            val lastTx = TxNftJoinRecord.findTxByUuid(it.scope.scopeIdInfo.scopeUuid, 0, 1).firstOrNull()
            ScopeListview(
                it.scope.scopeIdInfo.scopeAddr,
                getScopeDescrip(it.scope.scopeSpecIdInfo.scopeSpecAddr)?.name,
                it.scope.scopeSpecIdInfo.scopeSpecAddr,
                lastTx?.txTimestamp?.toString() ?: ""
            )
        }

    fun getScopeDetail(addr: String) = metadataClient.getScopeById(addr)
        .let {
            val spec = getScopeDescrip(it.scope.scopeSpecIdInfo.scopeSpecAddr)
            ScopeDetail(
               it.scope.scopeIdInfo.scopeAddr,
                spec?.name,
                it.scope.scopeSpecIdInfo.scopeSpecAddr,
                spec?.toSpecDescrip(),
                it.scope.scope.ownersList.toOwnerRoles(),
                it.scope.scope.valueOwnerAddress
            )
        }

    fun getRecordsForScope(addr: String): List<ScopeRecord> {
        // get scope
        val scope = metadataClient.getScopeById(addr, true)
        // get scope spec -> contract specs
        val scopeSpec = metadataClient.getScopeSpecById(scope.scope.scopeSpecIdInfo.scopeSpecAddr)
        val contractSpecs = scopeSpec.scopeSpecification.specification.contractSpecIdsList.map { it.toMAddress() }
        // get record specs for each contract spec
        val recordSpecs = contractSpecs
            .flatMap { cs -> metadataClient.getRecordSpecsForContractSpec(cs.toString()).recordSpecificationsList }
            .groupBy { it.specification.name }
        // get records/sessions
        val records = scope.recordsList.associateBy { it.record.name }
        val sessions = scope.sessionsList.associateBy { it.sessionIdInfo.sessionAddr }

        // Given current record specs, match existing records by name / record spec addr.
        val list = recordSpecs.map { (k, v) ->
            val record = records[k]

            // if the record for this spec name exists, make a record detail
            val recDetail = record?.let { r ->
                val session = sessions[record.record.sessionId.toMAddress().toString()]!!
                RecordDetail(
                    r.recordIdInfo.recordAddr,
                    r.recordSpecIdInfo.recordSpecAddr,
                    session.session.audit.createdDate.formattedString(),
                    session.session.partiesList.map { p -> PartyAndRole(p.address, p.role.name) }
                )
            }

            // map all current specs for this name
            val specDetail = v.map {
                RecordSpecDetail(
                    it.recordSpecIdInfo.contractSpecIdInfo.contractSpecAddr,
                    it.recordSpecIdInfo.recordSpecAddr,
                    it.specification.responsiblePartiesList.map { p -> p.name }
                )
            }

            // If the record is null, the specs are set and status UNFILLED
            // If the record is set and the record.recordSpecAddr matches the current spec addrs, the record is set, status FILLED
            // If the record is set, and the record.recordSpecAddr DOES NOT match spec addrs, the record is set, status NON_COMFORMING
            when {
                record == null -> ScopeRecord(RecordStatus.UNFILLED, k, specDetail, null)
                v.map { it.recordSpecIdInfo.recordSpecAddr }.contains(record.recordSpecIdInfo.recordSpecAddr) ->
                    ScopeRecord(RecordStatus.FILLED, k, null, recDetail)
                else -> ScopeRecord(RecordStatus.NON_CONFORMING, k, null, recDetail)

            }
        }
        // For any remaining records that do not match a spec name, set the record, status ORPHAN
        val seen = list.map { it.recordName }
        val noSpecOrphans = records.filter { !seen.contains(it.key) }.map { (k, v) ->
            val session = sessions[v.record.sessionId.toMAddress().toString()]!!
            val recDetail = RecordDetail(
                v.recordIdInfo.recordAddr,
                v.recordSpecIdInfo.recordSpecAddr,
                session.session.audit.createdDate.formattedString(),
                session.session.partiesList.map { p -> PartyAndRole(p.address, p.role.name) }
            )
            ScopeRecord(RecordStatus.ORPHAN, k, null, recDetail)
        }

        // Sort by name
        return (list + noSpecOrphans).sortedBy { it.recordName }

    }

    fun translateAddress(addr: String) = MetadataAddress.fromBech32(addr)

    fun saveMAddress(md: MetadataAddress) = transaction {
        when(md.getParentForType()) {
            MdParent.SCOPE -> NftScopeRecord
                .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScope().toString())
                .let { Triple(MdParent.SCOPE, it.id.value, it.uuid) }
            MdParent.SCOPE_SPEC -> NftScopeSpecRecord
                .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScopeSpec().toString())
                .let { Triple(MdParent.SCOPE_SPEC, it.id.value, it.uuid) }
            MdParent.CONTRACT_SPEC -> NftContractSpecRecord
                .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressContractSpec().toString())
                .let { Triple(MdParent.CONTRACT_SPEC, it.id.value, it.uuid) }
            else -> null.also { logger().debug("This prefix doesnt have a parent type: ${md.getPrefix()}") }
        }
    }

    fun markDeleted(md: MetadataAddress) = transaction {
        when(md.getParentForType()) {
            MdParent.SCOPE -> NftScopeRecord
                .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScope().toString())
            MdParent.SCOPE_SPEC -> NftScopeSpecRecord
                .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScopeSpec().toString())
            MdParent.CONTRACT_SPEC -> NftContractSpecRecord
                .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressContractSpec().toString())
            else -> null.also { logger().debug("This prefix doesnt have a parent type: ${md.getPrefix()}") }
        }
    }

    fun getNftDbId(md: MetadataAddress?) = transaction {
        when(md?.getParentForType()) {
            MdParent.SCOPE -> NftScopeRecord.findByUuid(md.getPrimaryUuid().toString())!!.id.value
            MdParent.SCOPE_SPEC -> NftScopeSpecRecord.findByUuid(md.getPrimaryUuid().toString())!!.id.value
            MdParent.CONTRACT_SPEC -> NftContractSpecRecord.findByUuid(md.getPrimaryUuid().toString())!!.id.value
            else -> null.also { logger().debug("This prefix doesnt have a parent type: ${md?.getPrefix()}") }
        }
    }


}
