package io.provenance.explorer.service.async

import com.google.protobuf.Timestamp
import cosmos.base.abci.v1beta1.Abci
import cosmos.base.tendermint.v1beta1.Query
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockProposerRecord
import io.provenance.explorer.domain.entities.SigJoinType
import io.provenance.explorer.domain.entities.SignatureJoinRecord
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinType
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.grpc.extensions.getAssociatedAddresses
import io.provenance.explorer.grpc.extensions.getAssociatedDenoms
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.ValidatorService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class AsyncCaching(
    private val txClient: TransactionGrpcClient,
    private val blockService: BlockService,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val assetService: AssetService,
    private val props: ExplorerProperties
) {

    protected val logger = logger(AsyncCaching::class)

    protected var chainId: String = ""

    fun getBlock(blockHeight: Int, checkTxs: Boolean = false) = transaction {
        BlockCacheRecord.findById(blockHeight)?.also {
            BlockCacheRecord.updateHitCount(blockHeight)
        }?.block?.also {
            if (checkTxs && it.block.data.txsCount > 0)
                addTxsToCache(it.block.height(), it.block.data.txsCount, it.block.header.time)
        } ?: saveBlockEtc(blockService.getBlockAtHeightFromChain(blockHeight))
    }

    fun getChainIdString() =
        if (chainId.isEmpty()) getBlock(blockService.getLatestBlockHeightIndex()).block.header.chainId.also { this.chainId = it }
        else this.chainId

    fun saveBlockEtc(blockRes: Query.GetBlockByHeightResponse): Query.GetBlockByHeightResponse {
        logger.info("saving block ${blockRes.block.height()}")
        blockService.addBlockToCache(
            blockRes.block.height(), blockRes.block.data.txsCount, blockRes.block.header.time.toDateTime(), blockRes)
        validatorService.saveProposerRecord(blockRes, blockRes.block.header.time.toDateTime(), blockRes.block.height())
        validatorService.saveValidatorsAtHeight(blockRes.block.height())
        if (blockRes.block.data.txsCount > 0)
            saveTxs(blockRes)
        return blockRes
    }

    fun saveTxs(blockRes: Query.GetBlockByHeightResponse) {
        val toBeUpdated = addTxsToCache(blockRes.block.height(), blockRes.block.data.txsCount, blockRes.block.header.time)
        toBeUpdated.flatMap { it.first }.toSet().let { assetService.updateAssets(it) }
        toBeUpdated.flatMap { it.second.entries }
            .groupBy({ it.key }) { it.value }
            .mapValues { (_, values) -> values.flatten().toSet() }
            .let {
                it.entries.forEach { ent ->
                    when (ent.key) {
                        TxAddressJoinType.OPERATOR.name -> validatorService.updateStakingValidators(ent.value)
                        TxAddressJoinType.ACCOUNT.name -> accountService.updateAccounts(ent.value)
                    }
                }
            }
    }

    private fun txCountForHeight(blockHeight: Int) = transaction { TxCacheRecord.findByHeight(blockHeight).count() }

    fun addTxsToCache(blockHeight: Int, expectedNumTxs: Int, blockTime: Timestamp) =
        if (txCountForHeight(blockHeight).toInt() == expectedNumTxs)
            logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions").let { listOf() }
        else {
            logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
            tryAddTxs(blockHeight, expectedNumTxs, blockTime)
        }

    private fun tryAddTxs(blockHeight: Int, txCount: Int, blockTime: Timestamp): List<Pair<List<String>, Map<String, List<Int>>>> = try {
        txClient.getTxsByHeight(blockHeight, txCount)
            .also { calculateBlockTxFee(it, blockHeight) }
            .map { addTxToCacheWithTimestamp(txClient.getTxByHash(it.txhash), blockTime) }
    } catch (e: Exception) {
        logger.error("Failed to retrieve transactions at block: $blockHeight", e)
        listOf()
    }

    private fun calculateBlockTxFee(result: List<Abci.TxResponse>, height: Int) = transaction {
        result.map { it.tx.unpack(TxOuterClass.Tx::class.java) }
            .let { list ->
            val numerator = list.sumBy { it.authInfo.fee.amountList.first().amount.toInt() }
            val denominator = list.sumBy { it.authInfo.fee.gasLimit.toInt() }
            numerator.div(denominator.toDouble())
        }.let { BlockProposerRecord.save(height, it, null, null) }
    }

    fun addTxToCacheWithTimestamp(res: ServiceOuterClass.GetTxResponse, blockTime: Timestamp) =
        addTxToCache(res, blockTime.toDateTime())

    // Function that saves all the things under a transaction
    fun addTxToCache(
        res: ServiceOuterClass.GetTxResponse,
        blockTime: DateTime
    ): Pair<List<String>, Map<String, List<Int>>> {
        val txPair = TxCacheRecord.insertIgnore(res, blockTime).let { Pair(it, res) }
        saveMessages(txPair.first, txPair.second)
        val addrs = saveAddresses(txPair.first, txPair.second)
        val markers = saveMarkers(txPair.first, txPair.second)
        saveSignaturesTx(txPair.second)
        return Pair(markers, addrs)
    }

    private fun saveMessages(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
        tx.tx.body.messagesList.forEachIndexed { idx, msg ->
            if (tx.txResponse.logsCount > 0) {
                var type: String
                var module: String
                when (val msgType = TxMessageTypeRecord.findByProtoType(msg.typeUrl)) {
                    null ->
                        (try {
                            tx.txResponse.logsList[idx].eventsList.first { event -> event.type == "message" }
                        } catch (ex: Exception) {
                            tx.txResponse.logsList.first().eventsList.filter { event -> event.type == "message" }[idx]
                        }).let { event ->
                            type = event.attributesList.first { att -> att.key == "action" }.value
                            module = event.attributesList.firstOrNull { att -> att.key == "module" }?.value ?: "unknown"
                        }
                    else -> {
                        type = msgType.type
                        module = msgType.module
                    }
                }
                TxMessageRecord.insert(tx.txResponse.height.toInt(), tx.txResponse.txhash, txId, msg, type, module)
            } else
                TxMessageRecord.insert(tx.txResponse.height.toInt(), tx.txResponse.txhash, txId, msg, "unknown", "unknown")
        }
    }

    private fun saveAddresses(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
        tx.tx.body.messagesList.flatMap { it.getAssociatedAddresses() }.toSet().map { addr ->
            val addrPair = addr.getAddressType(props)
            var pairCopy = addrPair!!.copy()
            if (addrPair.second == null) {
                when (addrPair.first) {
                    TxAddressJoinType.OPERATOR.name -> validatorService.saveValidator(addr)
                        .let { pairCopy = addrPair.copy(second = it.first.value) }
                    TxAddressJoinType.ACCOUNT.name -> accountService.saveAccount(addr)
                        .let { pairCopy = addrPair.copy(second = it.id.value) }
                }
            }
            TxAddressJoinRecord.insert(tx.txResponse.txhash, txId, tx.txResponse.height.toInt(), pairCopy, addr)
            addrPair
        }.filter { it.second != null }.groupBy({ it.first }) { it.second!! }
    }

    private fun saveMarkers(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
        tx.tx.body.messagesList.flatMap { it.getAssociatedDenoms() }.toSet().map { denom ->
            assetService.getAssetRaw(denom).let { (id, _) ->
                TxMarkerJoinRecord.insert(tx.txResponse.txhash, txId, tx.txResponse.height.toInt(), id!!.value, denom)
            }
            denom
        }
    }

    private fun saveSignaturesTx(tx: ServiceOuterClass.GetTxResponse) = transaction {
        tx.tx.authInfo.signerInfosList.forEach { sig ->
            SignatureJoinRecord.insert(
                sig.publicKey,
                SigJoinType.TRANSACTION,
                tx.txResponse.txhash
            )
        }
    }

}

fun String.getAddressType(props: ExplorerProperties) = when {
    this.startsWith(props.provValOperPrefix()) ->
        Pair(TxAddressJoinType.OPERATOR.name, StakingValidatorCacheRecord.findByOperator(this)?.id?.value)
    this.startsWith(props.provAccPrefix()) ->
        Pair(TxAddressJoinType.ACCOUNT.name, AccountRecord.findByAddress(this)?.id?.value)
    else -> logger().debug("Address type is not supported: Addr $this").let { null }
}
