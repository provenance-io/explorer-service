package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.IbcDetail
import io.provenance.explorer.domain.models.explorer.IbcListed
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.grpc.v1.IbcGrpcClient
import org.springframework.stereotype.Service

@Service
class IbcService(
    private val ibcClient: IbcGrpcClient,
    private val assetService: AssetService,
    private val accountService: AccountService,
    private val protoPrinter: JsonFormat.Printer
) {
    protected val logger = logger(IbcService::class)

    fun getIbcDenoms(
        page: Int,
        count: Int
    ): PagedResults<IbcListed> {
        val list =
            MarkerCacheRecord
                .findIbcPaginated(page.toOffset(count), count)
                .map {
                    IbcListed(
                        it.denom,
                        it.supply.toBigInteger().toString(),
                        it.lastTx?.toString()) }
        return PagedResults(MarkerCacheRecord.findCountByIbc().pageCountOfResults(count), list)
    }

    fun getIbcDetail(ibcHash: String) =
        assetService.getAssetFromDB(ibcHash.getIbcDenom())
            ?.let { (id, record) ->
                val txCount = TxMarkerJoinRecord.findCountByDenom(id.value)
                IbcDetail(
                    record.denom,
                    record.supply.toBigInteger().toString(),
                    0,
                    txCount,
                    accountService.getDenomMetadataSingle(record.denom).toObjectNode(protoPrinter),
                    ibcClient.getDenomTrace(ibcHash).toObjectNode(protoPrinter)
                )
            } ?: throw ResourceNotFoundException("Asset does not exist: ${ibcHash.getIbcDenom()}")
}

fun String.getIbcHash() = this.split("ibc/").last()
fun String.getIbcDenom() = "ibc/$this"
