package io.provenance.explorer.service

import com.google.protobuf.Timestamp
import com.google.protobuf.util.JsonFormat
import cosmos.bank.v1beta1.denomUnit
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BaseDenomType
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.MarkerUnitRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.exceptions.requireNotNullToMessage
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetHolder
import io.provenance.explorer.domain.models.explorer.AssetListed
import io.provenance.explorer.domain.models.explorer.AssetManagement
import io.provenance.explorer.domain.models.explorer.CountStrTotal
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TokenCounts
import io.provenance.explorer.domain.models.explorer.toCoinStrWithPrice
import io.provenance.explorer.grpc.extensions.getManagingAccounts
import io.provenance.explorer.grpc.extensions.isMintable
import io.provenance.explorer.grpc.v1.AccountGrpcClient
import io.provenance.explorer.grpc.v1.AttributeGrpcClient
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.marker.v1.MarkerStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class AssetService(
    private val markerClient: MarkerGrpcClient,
    private val attrClient: AttributeGrpcClient,
    private val accountClient: AccountGrpcClient,
    private val protoPrinter: JsonFormat.Printer,
    private val pricingService: PricingService,
    private val tokenService: TokenService
) {
    protected val logger = logger(AssetService::class)

    fun validateDenom(denom: String) =
        requireNotNullToMessage(MarkerCacheRecord.findByDenom(denom)) { "Denom $denom does not exist." }

    fun getAssets(
        statuses: List<MarkerStatus>,
        page: Int,
        count: Int
    ): PagedResults<AssetListed> {
        val records = MarkerCacheRecord.findByStatusPaginated(statuses, page.toOffset(count), count)
        val pricing = pricingService.getPricingInfoIn(records.map { it.denom }, "assetList")
        val list = records.map {
            val supply = if (it.denom != UTILITY_TOKEN) it.toCoinStrWithPrice(pricing[it.denom])
            else tokenService.totalSupply().toCoinStrWithPrice(pricing[it.denom], UTILITY_TOKEN)
            AssetListed(
                it.denom,
                it.markerAddress,
                supply,
                it.status.prettyStatus(),
                it.data?.isMintable() ?: false,
                it.lastTx?.toString(),
                it.markerType.prettyMarkerType()
            )
        }
        val total = MarkerCacheRecord.findCountByStatus(statuses)
        return PagedResults(total.pageCountOfResults(count), list, total)
    }

    fun getAssetRaw(denom: String) = transaction {
        getAssetFromDB(denom) ?: getAndInsertMarker(denom)
    }

    fun getAssetFromDB(denom: String) = transaction {
        MarkerCacheRecord.findByDenom(denom)?.let { Pair(it.id, it) }
    }

    private fun getAndInsertMarker(denom: String) = runBlocking {
        markerClient.getMarkerDetail(denom)?.let {
            MarkerCacheRecord.insertIgnore(
                it.baseAccount.address,
                it.markerType.name,
                it.denom,
                it.status.toString(),
                it,
                getCurrentSupply(denom).toBigDecimal(),
                TxMarkerJoinRecord.findLatestTxByDenom(denom)
            )
        } ?: getCurrentSupply(denom).let {
            val (type, status) = denom.getBaseDenomType()
            MarkerCacheRecord.insertIgnore(
                null,
                type.name,
                denom,
                status.toString(),
                null,
                it.toBigDecimal(),
                TxMarkerJoinRecord.findLatestTxByDenom(denom)
            )
        }
    }

    fun getAssetDetail(denom: String) =
        runBlocking {
            MarkerUnitRecord.findByUnit(denom)?.let { unit ->
                getAssetFromDB(unit.marker)?.let { (id, record) ->
                    val txCount = TxMarkerJoinRecord.findCountByDenom(id.value)
                    val (ftCount, nftCount) = transaction {
                        val markerAccount =
                            if (record.markerAddress != null) AccountRecord.findByAddress(record.markerAddress!!) else null
                        markerAccount?.tokenCounts?.firstOrNull()?.let { it.ftCount to it.nftCount } ?: (0 to 0)
                    }
                    val attributes = async { attrClient.getAllAttributesForAddress(record.markerAddress) }
                    val price = pricingService.getPricingInfoIn(listOf(unit.marker), "assetDetail")[unit.marker]
                    AssetDetail(
                        record.denom,
                        record.markerAddress,
                        if (record.data != null)
                            AssetManagement(record.data!!.getManagingAccounts(), record.data!!.allowGovernanceControl)
                        else null,
                        if (record.denom != UTILITY_TOKEN) record.toCoinStrWithPrice(price)
                        else tokenService.totalSupply().toCoinStrWithPrice(price, UTILITY_TOKEN),
                        record.data?.isMintable() ?: false,
                        accountClient.getDenomHolders(unit.marker, 0, 1).pagination.total.toInt(),
                        txCount,
                        attributes.await().map { attr -> attr.toResponse() },
                        getDenomMetadataSingle(unit.marker).toObjectNode(protoPrinter),
                        TokenCounts(ftCount, nftCount),
                        record.status.prettyStatus(),
                        record.markerType.prettyMarkerType()
                    )
                } ?: throw ResourceNotFoundException("Invalid asset: $denom")
            } ?: throw ResourceNotFoundException("Invalid asset: $denom")
        }

    fun getAssetHolders(denom: String, page: Int, count: Int) = runBlocking {
        val unit = MarkerUnitRecord.findByUnit(denom)?.marker ?: denom
        val supply = getCurrentSupply(unit)
        val res = accountClient.getDenomHolders(unit, page.toOffset(count), count)
        val list = res.denomOwnersList.asFlow().map { bal ->
            AssetHolder(bal.address, CountStrTotal(bal.balance.amount, supply, unit))
        }.toList().sortedWith(compareBy { it.balance.count.toBigDecimal() }).asReversed()
        PagedResults(res.pagination.total.pageCountOfResults(count), list, res.pagination.total)
    }

    fun getMetadata(denom: String?) = getDenomMetadata(denom).map { it.toObjectNode(protoPrinter) }

    // Updates the Marker cache
    fun updateAssets(denoms: Set<String>, txTime: Timestamp) =
        transaction {
            denoms.forEach { marker ->
                runBlocking {
                    val data = markerClient.getMarkerDetail(marker)
                    MarkerCacheRecord.findByDenom(marker)?.apply {
                        if (data != null) {
                            this.status = data.status.toString()
                            this.markerAddress = data.baseAccount.address
                            this.markerType = data.markerType.name
                        }
                        this.supply = getCurrentSupply(marker).toBigDecimal()
                        this.lastTx = txTime.toDateTime()
                        this.data = data
                    }?.also {
                        accountClient.getDenomMetadata(marker).metadata.denomUnitsList.forEach { unit ->
                            MarkerUnitRecord.insert(it.id.value, marker, unit)
                        }
                        MarkerUnitRecord.insert(
                            it.id.value, marker,
                            denomUnit {
                                denom = marker
                                exponent = 0
                            }
                        )
                    }
                }
            }
        }

    fun getCurrentSupply(denom: String) = runBlocking { accountClient.getCurrentSupply(denom).amount }

    fun getDenomMetadataSingle(denom: String) = runBlocking { accountClient.getDenomMetadata(denom).metadata }

    fun getDenomMetadata(denom: String?) = runBlocking {
        if (denom != null) listOf(accountClient.getDenomMetadata(denom).metadata)
        else accountClient.getAllDenomMetadata()
    }

    fun updateMarkerUnit() = transaction {
        getDenomMetadata(null).forEach { meta ->
            val markerId = getAssetRaw(meta.base).first.value
            meta.denomUnitsList.forEach { MarkerUnitRecord.insert(markerId, meta.base, it) }
        }
        MarkerCacheRecord.all().forEach {
            MarkerUnitRecord.insert(
                it.id.value, it.denom,
                denomUnit {
                    denom = it.denom
                    exponent = 0
                }
            )
        }
    }
}

fun String.getDenomByAddress() = MarkerCacheRecord.findByAddress(this)?.denom

fun String.prettyStatus() = this.substringAfter("MARKER_STATUS_")
fun String.prettyMarkerType() = if (this.startsWith("MARKER_TYPE")) this.substringAfter("MARKER_TYPE_") else this
fun String.prettyRole() = this.substringAfter("ACCESS_")

fun String.getBaseDenomType() =
    when {
        this.startsWith("ibc/") -> Pair(BaseDenomType.IBC_DENOM, MarkerStatus.MARKER_STATUS_ACTIVE)
        else -> Pair(BaseDenomType.DENOM, MarkerStatus.MARKER_STATUS_UNSPECIFIED)
    }

// Used to find the base denom for a recieved IBC token that originated on this chain
fun String.unchainDenom() = if (this.contains("channel")) this.split("/").lastOrNull()!! else this
