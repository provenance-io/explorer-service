package io.provenance.explorer.service

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.SmCodeRecord
import io.provenance.explorer.domain.entities.SmContractRecord
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.toObjectNodeNonTxMsg
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.Code
import io.provenance.explorer.domain.models.explorer.Contract
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.grpc.extensions.toMsgClearAdmin
import io.provenance.explorer.grpc.extensions.toMsgClearAdminOld
import io.provenance.explorer.grpc.extensions.toMsgExecuteContract
import io.provenance.explorer.grpc.extensions.toMsgExecuteContractOld
import io.provenance.explorer.grpc.extensions.toMsgMigrateContract
import io.provenance.explorer.grpc.extensions.toMsgMigrateContractOld
import io.provenance.explorer.grpc.extensions.toMsgUpdateAdmin
import io.provenance.explorer.grpc.extensions.toMsgUpdateAdminOld
import io.provenance.explorer.grpc.v1.SmartContractGrpcClient
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Service
class SmartContractService(
    private val protoPrinter: JsonFormat.Printer,
    private val accountService: AccountService,
    private val scClient: SmartContractGrpcClient
) {
    protected val logger = logger(SmartContractService::class)

    fun getSmCodeFromNode(codeId: Long) = scClient.getSmCode(codeId)

    fun saveCode(codeId: Long, txInfo: TxData) = transaction {
        getSmCodeFromNode(codeId).let {
            SmCodeRecord.getOrInsert(codeId.toInt(), it, txInfo.blockHeight)
        }
    }

    fun saveContract(contract: String, txInfo: TxData) = transaction {
        scClient.getSmContract(contract).let {
            accountService.saveAccount(contract, true)
            SmContractRecord.getOrInsert(it, txInfo.blockHeight)
        }
    }

    fun getAllScContractsPaginated(page: Int, count: Int, creator: String?, admin: String?, label: String?) =
        transaction {
            SmContractRecord.getPaginated(page.toOffset(count), count, creator, admin, label)
                .map { it.toContractObject() }
                .let {
                    val total = SmContractRecord.getCount(creator, admin, label)
                    PagedResults(total.pageCountOfResults(count), it, total)
                }
        }

    fun getAllScCodesPaginated(page: Int, count: Int, creator: String?, hasContracts: Boolean?) = transaction {
        SmCodeRecord.getPaginated(page.toOffset(count), count, creator, hasContracts)
            .let {
                val total = SmCodeRecord.getCount(creator, hasContracts)
                PagedResults(total.pageCountOfResults(count), it, total)
            }
    }

    fun getCode(code: Int) = transaction {
        SmCodeRecord.findById(code)?.toCodeObject() ?: throw ResourceNotFoundException("Invalid code ID: $code")
    }

    fun getContractsByCode(codeId: Int, page: Int, count: Int, creator: String?, admin: String?) = transaction {
        SmContractRecord.getPaginated(page.toOffset(count), count, creator, admin, codeId = codeId)
            .map { it.toContractObject() }
            .let {
                val total = SmContractRecord.getCount(creator, admin, codeId = codeId)
                PagedResults(total.pageCountOfResults(count), it, total)
            }
    }

    fun getContract(contract: String) = transaction {
        SmContractRecord.findByContractAddress(contract)?.toContractObject()
            ?: throw ResourceNotFoundException("Invalid contract address: $contract")
    }

    fun getHistoryByContract(contract: String) = transaction {
        scClient.getSmContractHistory(contract).entriesList.map { it.toObjectNodeNonTxMsg(protoPrinter, listOf("msg")) }
    }

    fun getContractLabels() = SmContractRecord.getContractLabels()
}

fun SmCodeRecord.toCodeObject() = Code(this.id.value, this.creationHeight, this.creator, this.dataHash)

fun SmContractRecord.toContractObject() =
    Contract(this.contractAddress, this.creationHeight, this.codeId, this.creator, this.admin, this.label)

fun ByteArray.isGZIPStream(): Boolean =
    this[0] == GZIPInputStream.GZIP_MAGIC.toByte() && this[1] == (GZIPInputStream.GZIP_MAGIC ushr 8).toByte()

fun ByteArray.isWASM() = this.sliceArray(0 until 4).contentEquals(byteArrayOf(0x00, 0x61, 0x73, 0x6D))

fun ByteString.gzipUncompress() =
    if (this.toByteArray().isGZIPStream())
        GZIPInputStream(this.toByteArray().inputStream()).use { it.readBytes() }
    else this.toByteArray()

fun ByteArray.gzipCompress(): ByteArray = ByteArrayOutputStream().use { byteStream ->
    GZIPOutputStream(byteStream).use { it.write(this, 0, this.size) }
    byteStream.toByteArray()
}

fun Any.getScMsgDetail(msgIdx: Int, tx: ServiceOuterClass.GetTxResponse): Pair<Int, String?>? =
    when {
        typeUrl.endsWith("v1.MsgStoreCode") -> tx.txResponse.logsList[msgIdx].eventsList
            .first { it.type == "store_code" }!!.attributesList
            .first { it.key == "code_id" }!!.value
            .let { it.toInt() to null }
        typeUrl.endsWith("v1beta1.MsgStoreCode") -> tx.txResponse.logsList[msgIdx].eventsList
            .first { it.type == "message" }!!.attributesList
            .first { it.key == "code_id" }!!.value
            .let { it.toInt() to null }
        typeUrl.endsWith("v1.MsgInstantiateContract") -> tx.txResponse.logsList[msgIdx].eventsList
            .first { it.type == "instantiate" }!!
            .let { e ->
                e.attributesList.first { it.key == "code_id" }!!.value.toInt() to
                    e.attributesList.first { it.key == "_contract_address" }!!.value
            }
        typeUrl.endsWith("v1beta1.MsgInstantiateContract") -> tx.txResponse.logsList[msgIdx].eventsList
            .first { it.type == "message" }!!
            .let { e ->
                e.attributesList.first { it.key == "code_id" }!!.value.toInt() to
                    e.attributesList.first { it.key == "contract_address" }!!.value
            }
        typeUrl.endsWith("v1.MsgExecuteContract") -> this.toMsgExecuteContract().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1beta1.MsgExecuteContract") -> this.toMsgExecuteContractOld().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1.MsgMigrateContract") -> this.toMsgMigrateContract().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1beta1.MsgMigrateContract") -> this.toMsgMigrateContractOld().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1.MsgUpdateAdmin") -> this.toMsgUpdateAdmin().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1beta1.MsgUpdateAdmin") -> this.toMsgUpdateAdminOld().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1.MsgClearAdmin") -> this.toMsgClearAdmin().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        typeUrl.endsWith("v1beta1.MsgClearAdmin") -> this.toMsgClearAdminOld().contract
            .let { SmContractRecord.findByContractAddress(it)!! }
            .let { it.codeId to it.contractAddress }
        else -> null.also { logger().debug("This typeUrl is not a smart-contract-based msg: $typeUrl") }
    }
