package io.provenance.explorer.service.utility

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ErrorFinding
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxFeeRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.domain.extensions.fromBase64
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.models.explorer.BlockProposer
import io.provenance.explorer.domain.models.explorer.getCategoryForType
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.async.AsyncCachingV2
import kotlinx.coroutines.runBlocking
import net.pearx.kasechange.toSnakeCase
import net.pearx.kasechange.universalWordSplitter
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class UtilityService(
    private val protoPrinter: JsonFormat.Printer,
    private val protoParser: JsonFormat.Parser,
    private val markerClient: MarkerGrpcClient,
    private val assetService: AssetService,
    private val async: AsyncCachingV2,
    private val txClient: TransactionGrpcClient,
    private val blockService: BlockService,
    private val validatorService: ValidatorService
) {

    protected val logger = logger(UtilityService::class)

    // Updates a TxMsgType with the given info
    fun updateTxMsgType(records: List<UnknownTxType>) = transaction {
        records.forEach { record -> TxMessageTypeRecord.insert(record.type, record.module, record.protoType) }
        "Updated"
    }

    // Retrieves common missing info
    fun getErrors() =
        mapOf(
            "txErrors" to ErrorFinding.getTxErrors(),
            "unknownTxMsgTypes" to ErrorFinding.getUnknownTxTypes()
        )

    // Translates Proto to json, found by tx hash
    fun translateMsgAny(hash: String) = transaction {
        TxMessageRecord.findByHash(hash)
            .first()
            .let {
                MsgObj(
                    it.txMessageType.type,
                    OBJECT_MAPPER.readValue(protoPrinter.print(it.txMessage), ObjectNode::class.java)
                        .let { node ->
                            node.remove("@type")
                            node
                        }
                )
            }
    }

    // searches for accounts that may or may not have the denom balance
    fun searchAccountsForDenom(accounts: List<String>, denom: String): List<Map<String, String>> =
        runBlocking {
            var offset = 0
            val limit = 100

            val results = markerClient.getMarkerHolders(denom, offset, limit) ?: return@runBlocking emptyList()

            val total = results.pagination?.total ?: results.balancesCount.toLong()
            val holders = results.balancesList.toMutableList()

            while (holders.count() < total) {
                offset += limit
                markerClient.getMarkerHolders(denom, offset, limit).let { holders.addAll(it!!.balancesList) }
            }

            val map = holders.associateBy { it.address }

            accounts.toSet().map { a ->
                mapOf(
                    "address" to a,
                    denom to (map[a]?.coinsList?.firstOrNull { c -> c.denom == denom }?.amount ?: "Nothing")
                )
            }
        }

    fun parseRawTxJson(rawJson: String, blockHeight: Int = 1, timestamp: DateTime = DateTime.now()) = transaction {
        val builder = ServiceOuterClass.GetTxResponse.newBuilder()
        protoParser.ignoringUnknownFields().merge(rawJson, builder)
        async.addTxToCache(builder.build(), DateTime.now(), BlockProposer(blockHeight, "", timestamp))
    }

    fun saveRawTxJson(rawJson: String, blockHeight: Int = 1, timestamp: DateTime = DateTime.now()) = transaction {
        val parsed = parseRawTxJson(rawJson, blockHeight, timestamp)
        TxCacheRecord.insertToProcedure(parsed.txUpdate, blockHeight, timestamp)
    }

    fun parseFromTxHash(hash: String, save: Boolean = false) = transaction {
        txClient.getTxByHash(hash).let { tx ->
            val block = blockService.getBlockAtHeightFromChain(tx.txResponse.height.toInt())!!
            val timestamp = block.block.header.time.toDateTime()
            val proposer = validatorService.buildProposerInsert(block, timestamp, block.block.height())
            async.addTxToCache(tx, timestamp, proposer).also {
                if(save)
                    TxCacheRecord.insertToProcedure(it.txUpdate, block.block.height(), timestamp)
            }
        }
    }

    fun stringToJson(str: String) = str.toObjectNode()

    fun decodeToString(str: String) = str.fromBase64()

    fun addMarker(denom: String) = assetService.getAssetRaw(denom)

    fun updateTxFeesFromHeight(height: Int) = TxFeeRecord.updateTxFees(height)

    fun getMsgTypeToProto() = transaction {
        TxMessageTypeRecord.all().map {
            val proto = it.protoType
            val module = if (!it.protoType.startsWith("/ibc")) it.protoType.split(".")[1]
            else it.protoType.split(".").let { list -> "${list[0].drop(1)}_${list[2]}" }
            val type = it.protoType.split("Msg")[1].removeSuffix("Request").toSnakeCase(universalWordSplitter(false))
            val category = type.getCategoryForType()?.mainCategory

            ProtoBreakout(proto, module, type, category)
            "( '$proto', '$module', '$type', '$category' )"
        }
    }
}

data class ProtoBreakout(
    val proto: String,
    val module: String,
    val type: String,
    val category: String?
)

data class MsgObj(
    val type: String,
    val msg: ObjectNode
)
