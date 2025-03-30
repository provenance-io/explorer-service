package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.NavEventsRecord
import io.provenance.explorer.domain.entities.NftContractSpecRecord
import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.NftScopeSpecRecord
import io.provenance.explorer.domain.entities.TxNftJoinRecord
import io.provenance.explorer.domain.extensions.calculateUsdPricePerUnit
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.protoTypesFieldsToCheckForMetadata
import io.provenance.explorer.domain.extensions.toObjectNodeMAddressValues
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.NftData
import io.provenance.explorer.domain.models.explorer.toDataObject
import io.provenance.explorer.domain.models.explorer.toNftData
import io.provenance.explorer.domain.models.explorer.toOwnerRoles
import io.provenance.explorer.domain.models.explorer.toSpecDescrip
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import io.provenance.explorer.model.PartyAndRole
import io.provenance.explorer.model.RecordDetail
import io.provenance.explorer.model.RecordSpecDetail
import io.provenance.explorer.model.RecordStatus
import io.provenance.explorer.model.ScopeDetail
import io.provenance.explorer.model.ScopeListview
import io.provenance.explorer.model.ScopeRecord
import io.provenance.explorer.model.base.MdParent
import io.provenance.explorer.model.base.MetadataAddress
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.explorer.model.base.getParentForType
import io.provenance.explorer.model.base.toMAddress
import io.provenance.explorer.model.base.toMAddressContractSpec
import io.provenance.explorer.model.base.toMAddressScope
import io.provenance.explorer.model.base.toMAddressScopeSpec
import io.provenance.metadata.v1.PartyType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class NftService(
    private val metadataClient: MetadataGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val protoPrinter: JsonFormat.Printer,
) {

    suspend fun getScopeDescrip(addr: String) =
        metadataClient.getScopeSpecById(addr).scopeSpecification.specification.description

    // TODO: switch to getScopeDetailsForOwningAddressDb after data migration
    fun getScopeDetailsForOwningAddress(ownerAddress: String, page: Int, count: Int) = runBlocking {
        metadataClient.getScopesByOwner(ownerAddress, page.toOffset(count), count).let {
            val records = it.scopeUuidsList.map { uuid ->
                scopeToListview(metadataClient.getScopeById(uuid).scope.toNftData(), ownerAddress)
            }
            PagedResults(it.pagination.total.pageCountOfResults(count), records, it.pagination.total)
        }
    }

    fun getScopeDetailsForOwningAddressDb(ownerAddress: String, page: Int, count: Int): PagedResults<ScopeListview> {
        val scopes = NftScopeRecord.findAllByOwner(ownerAddress, count, page.toOffset(count))
            .map { scopeToListview(it, ownerAddress) }
        val total = NftScopeRecord.findAllByOwner(ownerAddress).size.toLong()
        return PagedResults(total.pageCountOfResults(count), scopes, total)
    }

    fun getScopesForOwningAddress(ownerAddress: String, valueOwnerOnly: Boolean?) =
        when (valueOwnerOnly) {
            true -> getScopesForValueOwningAddress(ownerAddress)
            else -> NftScopeRecord.findAllByOwner(ownerAddress)
        }

    fun getScopesForValueOwningAddress(ownerAddress: String) = NftScopeRecord.findByValueOwner(ownerAddress)

    fun getScopeDetailsForOwnerAndType(ownerAddress: String, partyType: PartyType, page: Int, count: Int): PagedResults<ScopeListview> {
        val scopes = NftScopeRecord.findByOwnerAndType(ownerAddress, partyType.name, count, page.toOffset(count))
            .map { scopeToListview(it, ownerAddress) }
        val total = NftScopeRecord.findByOwnerAndType(ownerAddress, partyType.name).size.toLong()
        return PagedResults(total.pageCountOfResults(count), scopes, total)
    }

    fun getScopesForOwnerAndType(ownerAddress: String, partyType: PartyType) =
        NftScopeRecord.findByOwnerAndType(ownerAddress, partyType.name)

    fun getScopeOwnersByPartyType(scopeId: String, partyType: PartyType): List<String> {
        return NftScopeRecord.findByScopeId(scopeId)?.scope?.ownersList
            ?.filter { it.role == partyType }
            ?.map { it.address }
            ?: emptyList()
    }

    fun getScopeBasic(scopeId: String) = NftScopeRecord.findByScopeId(scopeId)?.toNftData()

    fun getScopeDetail(addr: String) = runBlocking {
        val nftRecord = NftScopeRecord.findByScopeId(addr)
        val scope = nftRecord?.scope ?: metadataClient.getScopeById(addr).scope.scope
        val scopeSpecAddr = scope.specificationId.toMAddress().getPrimaryUuid().toMAddressScopeSpec().toString()
        val spec = getScopeDescrip(scopeSpecAddr)
        val attributes = async { attrClient.getAllAttributesForAddress(addr) }

        ScopeDetail(
            nftRecord?.uuid ?: scope.scopeId.toMAddress().getPrimaryUuid().toString(),
            addr,
            spec?.name,
            scopeSpecAddr,
            spec?.toSpecDescrip(),
            scope.ownersList.toOwnerRoles(),
            scope.dataAccessList,
            scope.valueOwnerAddress,
            attributes.await().map { attr -> attr.toResponse() }
        )
    }

    fun getRecordsForScope(addr: String): List<ScopeRecord> = runBlocking {
        // get scope
        val scope = metadataClient.getScopeById(addr, true, true)
        // get scope spec -> contract specs
        val scopeSpec = metadataClient.getScopeSpecById(scope.scope.scopeSpecIdInfo.scopeSpecAddr)
        val contractSpecs = scopeSpec.scopeSpecification.specification.contractSpecIdsList.map { it.toMAddress() }.toSet()
        // get record specs for each contract spec
        val recordSpecs = contractSpecs.asFlow()
            .flatMapMerge { cs -> metadataClient.getRecordSpecsForContractSpec(cs.toString()).recordSpecificationsList.asFlow() }
            .toList()
            .groupBy { it.specification.name }
        // get records/sessions
        val records = scope.recordsList.associateBy { it.record.name }
        val sessions = scope.sessionsList.associateBy { it.sessionIdInfo.sessionAddr }

        // Given current record specs, match existing records by name / record spec addr.
        val list = recordSpecs.toList().asFlow().map { (k, v) ->
            val record = records[k]

            // if the record for this spec name exists, make a record detail
            val recDetail = record?.let { r ->
                val session = sessions[record.record.sessionId.toMAddress().toString()]!!
                RecordDetail(
                    r.recordIdInfo.recordAddr,
                    r.recordSpecIdInfo.recordSpecAddr,
                    session.session.audit.createdDate.formattedString(),
                    session.session.partiesList.map { p -> PartyAndRole(p.address, p.role.name) },
                    r.record.outputsList.map { it.toDataObject(k) }
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
        }.toList()

        // For any remaining records that do not match a spec name, set the record, status ORPHAN
        val seen = list.map { it.recordName }
        val noSpecOrphans = records.filter { !seen.contains(it.key) }.map { (k, v) ->
            val session = sessions[v.record.sessionId.toMAddress().toString()]!!
            val recDetail = RecordDetail(
                v.recordIdInfo.recordAddr,
                v.recordSpecIdInfo.recordSpecAddr,
                session.session.audit.createdDate.formattedString(),
                session.session.partiesList.map { p -> PartyAndRole(p.address, p.role.name) },
                v.record.outputsList.map { it.toDataObject(k) }
            )
            ScopeRecord(RecordStatus.ORPHAN, k, null, recDetail)
        }

        // Sort by name
        (list + noSpecOrphans).sortedBy { it.recordName }
    }

    fun getScopeSpecJson(scopeSpec: String) = runBlocking {
        metadataClient.getScopeSpecById(scopeSpec)
            .toObjectNodeMAddressValues(protoPrinter, protoTypesFieldsToCheckForMetadata)
    }

    fun getContractSpecJson(contractSpec: String) = runBlocking {
        metadataClient.getContractSpecById(contractSpec, true)
            .toObjectNodeMAddressValues(protoPrinter, protoTypesFieldsToCheckForMetadata)
    }

    fun getRecordSpecJson(recordSpec: String) = runBlocking {
        metadataClient.getRecordSpecById(recordSpec)
            .toObjectNodeMAddressValues(protoPrinter, protoTypesFieldsToCheckForMetadata)
    }

    fun translateAddress(addr: String) = MetadataAddress.fromBech32(addr)

    fun insertOrUpdateNft(uuid: String, address: String) = runBlocking {
        val scope = metadataClient.getScopeById(address, false, false).scope.scope
        NftScopeRecord.insertOrUpdate(uuid, address, scope)
    }

    fun updateNft(scopeAddr: String) = runBlocking {
        val scope = metadataClient.getScopeById(scopeAddr, false, false).scope
        NftScopeRecord.insertOrUpdate(scope.scopeIdInfo.scopeUuid, scopeAddr, scope.scope)
    }

    fun saveMAddress(md: MetadataAddress) = transaction {
        when (md.getParentForType()) {
            MdParent.SCOPE ->
                insertOrUpdateNft(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScope().toString())
                    .let { Triple(MdParent.SCOPE, it.id.value, it.uuid) }
            MdParent.SCOPE_SPEC ->
                NftScopeSpecRecord
                    .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScopeSpec().toString())
                    .let { Triple(MdParent.SCOPE_SPEC, it.id.value, it.uuid) }
            MdParent.CONTRACT_SPEC ->
                NftContractSpecRecord
                    .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressContractSpec().toString())
                    .let { Triple(MdParent.CONTRACT_SPEC, it.id.value, it.uuid) }

            else -> null.also { logger().debug("This prefix doesn't have a parent type: ${md.getPrefix()}") }
        }
    }

    fun markDeleted(md: MetadataAddress) = transaction {
        when (md.getParentForType()) {
            MdParent.SCOPE ->
                NftScopeRecord
                    .markDeleted(md.getPrimaryUuid().toString())
            MdParent.SCOPE_SPEC ->
                NftScopeSpecRecord
                    .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScopeSpec().toString())
            MdParent.CONTRACT_SPEC ->
                NftContractSpecRecord
                    .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressContractSpec().toString())
            else -> null.also { logger().debug("This prefix doesn't have a parent type: ${md.getPrefix()}") }
        }
    }

    fun getNftDbId(md: MetadataAddress?) = transaction {
        when (md?.getParentForType()) {
            MdParent.SCOPE -> NftScopeRecord.findByUuid(md.getPrimaryUuid().toString())!!.id.value
            MdParent.SCOPE_SPEC -> NftScopeSpecRecord.findByUuid(md.getPrimaryUuid().toString())!!.id.value
            MdParent.CONTRACT_SPEC -> NftContractSpecRecord.findByUuid(md.getPrimaryUuid().toString())!!.id.value
            else -> null.also { logger().debug("This prefix doesn't have a parent type: ${md?.getPrefix()}") }
        }
    }

    fun getScopeTotalForNavEvents() =
        // TODO could query for marker owned scopes and filter them out here after scope migration
        NavEventsRecord.getLatestNavEvents(
            priceDenoms = listOf(USD_LOWER),
            includeMarkers = false,
            includeScopes = true,
        ).sumOf {
            it.calculateUsdPricePerUnit()
        }

    fun populateScopes() = runBlocking {
        NftScopeRecord.findWithMissingScope().forEach {
            insertOrUpdateNft(it.uuid, it.address)
        }
    }

    private fun scopeToListview(nft: NftData, ownerAddress: String) = runBlocking {
        val scopeSpecAddr = nft.scope.specificationId.toMAddress().getPrimaryUuid().toMAddressScopeSpec().toString()
        val lastTx = TxNftJoinRecord.findTxByUuid(nft.uuid, 0, 1).firstOrNull()
        ScopeListview(
            nft.uuid,
            nft.address,
            getScopeDescrip(scopeSpecAddr)?.name,
            scopeSpecAddr,
            lastTx?.txTimestamp?.toString() ?: "",
            nft.scope.ownersList.map { own -> own.address }.contains(ownerAddress),
            nft.scope.dataAccessList.contains(ownerAddress),
            nft.scope.valueOwnerAddress == ownerAddress
        )
    }
}
