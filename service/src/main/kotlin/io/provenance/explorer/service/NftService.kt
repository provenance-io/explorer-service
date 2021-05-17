package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.domain.core.MdParent
import io.provenance.explorer.domain.core.MetadataAddress
import io.provenance.explorer.domain.core.getParentForType
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toMAddressContractSpec
import io.provenance.explorer.domain.core.toMAddressScope
import io.provenance.explorer.domain.core.toMAddressScopeSpec
import io.provenance.explorer.domain.entities.NftContractSpecRecord
import io.provenance.explorer.domain.entities.NftScopeRecord
import io.provenance.explorer.domain.entities.NftScopeSpecRecord
import io.provenance.explorer.domain.entities.TxNftJoinType
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.grpc.v1.MetadataGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.RoundingMode

@Service
class NftService(
    private val metadataClient: MetadataGrpcClient,
    private val markerClient: MarkerGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer
) {

    fun getAllScopes(page: Int, count: Int) =
        metadataClient.getAllScopes(page.toOffset(count), count).let { res ->
            val uuids = res.scopesList.map { it.scopeIdInfo.scopeUuid }
            PagedResults(res.pagination.total.pageCountOfResults(count), uuids)
        }

    fun getScopesForOwningAddress(address: String, page: Int, count: Int) =
        metadataClient.getScopesByOwner(address, page.toOffset(count), count).let {
            PagedResults(it.pagination.total.pageCountOfResults(count), it.scopeUuidsList)
        }

    fun getScopeByUuid(uuid: String) = metadataClient.getScopeById(uuid)
        .let { it.scope.scopeIdInfo.scopeUuid to it.recordsList.map { rec -> rec.recordIdInfo.recordAddr } }

    fun translateAddress(addr: String) = MetadataAddress.fromBech32(addr)

    fun saveMAddress(md: MetadataAddress) = transaction {
        when(md.getPrefix().getParentForType()) {
            MdParent.SCOPE -> NftScopeRecord
                .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScope().toString())
                .let { Triple(TxNftJoinType.SCOPE, it.id.value, it.uuid) }
            MdParent.SCOPE_SPEC -> NftScopeSpecRecord
                .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScopeSpec().toString())
                .let { Triple(TxNftJoinType.SCOPE_SPEC, it.id.value, it.uuid) }
            MdParent.CONTRACT_SPEC -> NftContractSpecRecord
                .getOrInsert(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressContractSpec().toString())
                .let { Triple(TxNftJoinType.CONTRACT_SPEC, it.id.value, it.uuid) }
            else -> null.also { logger().debug("This prefix doesnt have a parent type: ${md.getPrefix()}") }
        }
    }

    fun markDeleted(md: MetadataAddress) = transaction {
        when(md.getPrefix().getParentForType()) {
            MdParent.SCOPE -> NftScopeRecord
                .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScope().toString())
            MdParent.SCOPE_SPEC -> NftScopeSpecRecord
                .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressScopeSpec().toString())
            MdParent.CONTRACT_SPEC -> NftContractSpecRecord
                .markDeleted(md.getPrimaryUuid().toString(), md.getPrimaryUuid().toMAddressContractSpec().toString())
            else -> null.also { logger().debug("This prefix doesnt have a parent type: ${md.getPrefix()}") }
        }
    }


}
