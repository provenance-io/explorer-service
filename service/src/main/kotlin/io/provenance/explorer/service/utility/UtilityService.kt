package io.provenance.explorer.service.utility

import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.hash.Hashing
import com.google.protobuf.ByteString
import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.ErrorFinding
import io.provenance.explorer.domain.entities.SmCodeRecord
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.domain.extensions.base64EncodeString
import io.provenance.explorer.domain.extensions.fromBase64
import io.provenance.explorer.domain.extensions.to256Hash
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toByteString
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.models.explorer.getCategoryForType
import io.provenance.explorer.grpc.extensions.toMsgStoreCode
import io.provenance.explorer.grpc.v1.MarkerGrpcClient
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.async.AsyncCaching
import io.provenance.explorer.service.gzipUncompress
import net.pearx.kasechange.toSnakeCase
import net.pearx.kasechange.universalWordSplitter
import org.bouncycastle.util.encoders.Hex
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class UtilityService(
    private val protoPrinter: JsonFormat.Printer,
    private val markerClient: MarkerGrpcClient,
    private val assetService: AssetService,
    private val async: AsyncCaching,
    private val govService: GovService,
    private val props: ExplorerProperties
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
    fun searchAccountsForDenom(accounts: List<String>, denom: String): List<Map<String, String>> {
        var offset = 0
        val limit = 100

        val results = markerClient.getMarkerHolders(denom, offset, limit)
        val total = results.pagination?.total ?: results.balancesCount.toLong()
        val holders = results.balancesList.toMutableList()

        while (holders.count() < total) {
            offset += limit
            markerClient.getMarkerHolders(denom, offset, limit).let { holders.addAll(it.balancesList) }
        }

        val map = holders.associateBy { it.address }

        return accounts.toSet().map { a ->
            mapOf("address" to a, denom to (map[a]?.coinsList?.firstOrNull { c -> c.denom == denom }?.amount ?: "Nothing"))
        }
    }

    fun stringToJson(str: String) = str.toObjectNode()

    fun decodeToString(str: String) = str.fromBase64()

    fun addMarker(denom: String) = assetService.getAssetRaw(denom)

    fun funWithSignature(txHash: List<String>) = transaction {
        txHash.forEach { hash ->
            val tx = TxCacheRecord.findByHash(hash)!!
            async.saveSignaturesTx(tx.txV2)
        }
    }

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

    fun test(propId: Long) = transaction {
        val tx = TxCacheRecord.findByHash("C29927FD214FF7726AAB739285B14A410B447EA7AB7C917E09B7DF2942AA0401")!!
        val proposal = tx.txV2.tx.body.messagesList[0].toMsgStoreCode()
        val submittedByteHash = proposal.wasmByteCode.to256Hash() // Ff9daY2v2FbMmseyuC2SHc4BgChYR9RWXsv/pX0UMVs=
        val uncompress = proposal.wasmByteCode.gzipUncompress()
        val uncompressHash = uncompress.to256Hash()

        val hexSubmitted = Hex.encode(submittedByteHash.toByteArray()).toByteString().toBase64() // NDY2NjM5NjQ2MTU5MzI3NjMyNDY2MjRkNmQ3MzY1Nzk3NTQzMzI1MzQ4NjMzNDQyNjc0MzY4NTk1MjM5NTI1NzU4NzM3NjJmNzA1ODMwNTU0ZDU2NzMzZA==
        val smCode = SmCodeRecord.findById(propId.toInt())!!
        val codeHash = smCode.data!!.data.to256Hash() // 4K4DwidjCIrqeWThXihcQrcvcieH2DzRtYlYuvUkYCA=
        val dataHash = smCode.data!!.codeInfo.dataHash
        val smHash = smCode.dataHash // 4K4DwidjCIrqeWThXihcQrcvcieH2DzRtYlYuvUkYCA=
    }
}

fun ByteString.to256Hash() = Hashing.sha256().hashBytes(this.toByteArray()).asBytes().base64EncodeString()

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
