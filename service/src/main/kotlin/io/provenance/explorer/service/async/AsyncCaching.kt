package io.provenance.explorer.service.async

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.Timestamp
import cosmos.base.abci.v1beta1.Abci
import cosmos.base.tendermint.v1beta1.Query
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.entities.*
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toObjectNode
import io.provenance.explorer.domain.models.explorer.LedgerInfo
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.grpc.extensions.GovMsgType
import io.provenance.explorer.grpc.extensions.IbcEventType
import io.provenance.explorer.grpc.extensions.getAssociatedAddresses
import io.provenance.explorer.grpc.extensions.getAssociatedDenoms
import io.provenance.explorer.grpc.extensions.getAssociatedGovMsgs
import io.provenance.explorer.grpc.extensions.getAssociatedMetadata
import io.provenance.explorer.grpc.extensions.getAssociatedMetadataEvents
import io.provenance.explorer.grpc.extensions.getIbcChannelEvents
import io.provenance.explorer.grpc.extensions.getIbcDenomEvents
import io.provenance.explorer.grpc.extensions.getIbcLedgerMsgs
import io.provenance.explorer.grpc.extensions.isIbcTimeoutOnClose
import io.provenance.explorer.grpc.extensions.isIbcTransferMsg
import io.provenance.explorer.grpc.extensions.isMetadataDeletionMsg
import io.provenance.explorer.grpc.extensions.toMsgAcknowledgement
import io.provenance.explorer.grpc.extensions.toMsgDeposit
import io.provenance.explorer.grpc.extensions.toMsgRecvPacket
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposal
import io.provenance.explorer.grpc.extensions.toMsgTimeoutOnClose
import io.provenance.explorer.grpc.extensions.toMsgTransfer
import io.provenance.explorer.grpc.extensions.toMsgVote
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.IbcService
import io.provenance.explorer.service.NftService
import io.provenance.explorer.service.ValidatorService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigInteger

@Service
class AsyncCaching(
    private val txClient: TransactionGrpcClient,
    private val blockService: BlockService,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val assetService: AssetService,
    private val nftService: NftService,
    private val govService: GovService,
    private val ibcService: IbcService,
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
            blockRes.block.height(), blockRes.block.data.txsCount, blockRes.block.header.time.toDateTime(), blockRes
        )
        validatorService.saveProposerRecord(blockRes, blockRes.block.header.time.toDateTime(), blockRes.block.height())
        validatorService.saveValidatorsAtHeight(blockRes.block.height())
        validatorService.saveMissedBlocks(blockRes)
        if (blockRes.block.data.txsCount > 0)
            saveTxs(blockRes)
        return blockRes
    }

    data class TxUpdatedItems(
        val addresses: Map<String, List<Int>>,
        val markers: List<String>,
        val scopes: List<String> = listOf()
    )

    fun saveTxs(blockRes: Query.GetBlockByHeightResponse) {
        val toBeUpdated = addTxsToCache(blockRes.block.height(), blockRes.block.data.txsCount, blockRes.block.header.time)
        toBeUpdated.flatMap { it.markers }.toSet().let { assetService.updateAssets(it, blockRes.block.header.time) }
        toBeUpdated.flatMap { it.addresses.entries }
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

    private fun tryAddTxs(blockHeight: Int, txCount: Int, blockTime: Timestamp): List<TxUpdatedItems> = try {
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
                val numerator = list.sumOf { it.authInfo.fee.amountList.firstOrNull()?.amount?.toBigInteger() ?: BigInteger.ZERO }
                val denominator = list.sumOf { it.authInfo.fee.gasLimit.toBigInteger() }
                numerator.toDouble().div(denominator.toDouble())
            }.let { BlockProposerRecord.save(height, it, null, null) }
    }

    fun addTxToCacheWithTimestamp(res: ServiceOuterClass.GetTxResponse, blockTime: Timestamp) =
        addTxToCache(res, blockTime.toDateTime())

    // Function that saves all the things under a transaction
    fun addTxToCache(
        res: ServiceOuterClass.GetTxResponse,
        blockTime: DateTime
    ): TxUpdatedItems {
        val txPair = TxCacheRecord.insertIgnore(res, blockTime).let { Pair(it, res) }
        saveMessages(txPair.first, txPair.second)
        val addrs = saveAddresses(txPair.first, txPair.second)
        val markers = saveMarkers(txPair.first, txPair.second)
        saveNftData(txPair.first, txPair.second)
        saveGovData(txPair.second, blockTime)
        saveIbcChannelData(txPair.first, txPair.second, blockTime)
        saveSignaturesTx(txPair.second)
        return TxUpdatedItems(addrs, markers)
    }

    private fun saveEvents(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse, msg: Any, msgId: String) = transaction {
        tx.txResponse.logsList.forEach { log ->
            log.eventsList.forEach { event ->
                val eventId = TxEventRecord.insert(tx.txResponse.height.toInt(), tx.txResponse.txhash, txId, msg, event.type, msgId)
                event.attributesList.forEach { attr ->
                    attr.key
                    attr.value
                }
            }
        }
    }

        private fun saveMessages(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
            tx.tx.body.messagesList.forEachIndexed { idx, msg ->
                if (tx.txResponse.logsCount > 0) {
                    val type: String
                    val module: String
                    when (val msgType = TxMessageTypeRecord.findByProtoType(msg.typeUrl)) {
                        null -> {
                            val typePair = getMsgType(tx, idx)
                            type = typePair.first
                            module = typePair.second
                        }
                        else -> {
                            if (msgType.module == UNKNOWN) {
                                val typePair = getMsgType(tx, idx)
                                type = typePair.first
                                module = typePair.second
                            } else {
                                type = msgType.type
                                module = msgType.module
                            }
                        }
                    }
                    val msgId = TxMessageRecord.insert(tx.txResponse.height.toInt(), tx.txResponse.txhash, txId, msg, type, module)
                    // Could we pass in message_id and other info directly from here?
                    saveEvents(txId, tx, msg, msgId)
                } else
                    TxMessageRecord.insert(tx.txResponse.height.toInt(), tx.txResponse.txhash, txId, msg, UNKNOWN, UNKNOWN)
            }
        }
    }

    private fun getMsgType(tx: ServiceOuterClass.GetTxResponse, idx: Int) =
        (
            try {
                tx.txResponse.logsList[idx].eventsList.first { event -> event.type == "message" }
            } catch (ex: Exception) {
                tx.txResponse.logsList.first().eventsList.filter { event -> event.type == "message" }[idx]
            }
            ).let { event ->
            val type = event.attributesList.first { att -> att.key == "action" }.value
            val module = event.attributesList.firstOrNull { att -> att.key == "module" }?.value ?: UNKNOWN
            Pair(type, module)
        }

    private fun saveAddresses(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
        val msgAddrs = tx.tx.body.messagesList.flatMap { it.getAssociatedAddresses() }
        val eventAddrs = tx.tx.body.messagesList.flatMapIndexed { idx, msg ->
            val events = msg.getIbcDenomEvents().filter { it.eventType == IbcEventType.ADDRESS }
                .associate { it.event to it.idField }
            if (tx.txResponse.logsCount > 0)
                tx.txResponse.logsList[idx].eventsList
                    .filter { it.type in events.map { e -> e.key } }
                    .flatMap { e -> e.attributesList.filter { a -> a.key == events[e.type] }.map { it.value } }
            else listOf()
        }
        (msgAddrs + eventAddrs).toSet()
            .filter { !it.isNullOrEmpty() }
            .map { saveAddr(it, txId, tx) }
            .filter { it.second != null }.groupBy({ it.first }) { it.second!! }
    }

    private fun saveAddr(addr: String, txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse): Pair<String, Int?> {
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
        return addrPair
    }

    private fun saveMarkers(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
        val msgDenoms = tx.tx.body.messagesList.map { it.getAssociatedDenoms() to it.isIbcTransferMsg() }
        val denoms = msgDenoms.flatMap { it.first }
        val eventDenoms = tx.tx.body.messagesList.flatMapIndexed { idx, msg ->
            val events = msg.getIbcDenomEvents().filter { it.eventType == IbcEventType.DENOM }.associate { it.event to it.idField }
            if (tx.txResponse.logsCount > 0)
                tx.txResponse.logsList[idx].eventsList
                    .filter { it.type in events.map { e -> e.key } }
                    .flatMap { e -> e.attributesList.filter { a -> a.key == events[e.type] }.map { it.value } }
            else listOf()
        }
        (denoms + eventDenoms).toSet().mapNotNull { de ->
            val denom = msgDenoms.firstOrNull { it.first.contains(de) }
            if (denom != null && denom.second)
                if (tx.txResponse.code == 0) saveDenom(de, txId, tx) else null
            else
                saveDenom(de, txId, tx)
        }
    }

    private fun saveDenom(denom: String, txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse): String {
        assetService.getAssetRaw(denom).let { (id, _) ->
            TxMarkerJoinRecord.insert(tx.txResponse.txhash, txId, tx.txResponse.height.toInt(), id!!.value, denom)
        }
        return denom
    }

    private fun saveNftData(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse) = transaction {
        // Gather MetadataAddresses from the Msgs themselves
        val msgAddrPairs = tx.tx.body.messagesList.map { it.getAssociatedMetadata() to it.isMetadataDeletionMsg() }
        val msgAddrs = msgAddrPairs.flatMap { it.first }.filterNotNull()

        // Gather event-only MetadataAddresses from the events
        val me = tx.tx.body.messagesList.flatMap { it.getAssociatedMetadataEvents() }.toSet()
        val meAddrs = tx.txResponse.logsList
            .flatMap { log -> log.eventsList }
            .filter { it.type in me.map { m -> m.event } }
            .flatMap { e ->
                e.attributesList
                    .filter { a -> a.key in me.map { m -> m.idField } }
                    .map { jacksonObjectMapper().readValue(it.value, String::class.java) }
            }
            .map { addr -> addr.toMAddress() }

        // Save the nft addresses
        val nfts = (msgAddrs + meAddrs).mapNotNull { md ->
            nftService.saveMAddress(md)
                // mark deleted if necessary
                .also {
                    if (tx.txResponse.code == 0 && (msgAddrPairs.firstOrNull { it.first == listOf(md) }?.second == true))
                        nftService.markDeleted(md)
                }
        }
        // Save the nft joins
        nfts.forEach { nft -> TxNftJoinRecord.insert(tx.txResponse.txhash, txId, tx.txResponse.height.toInt(), nft) }
    }

    private fun saveGovData(tx: ServiceOuterClass.GetTxResponse, blockTime: DateTime) = transaction {
        if (tx.txResponse.code == 0) {
            val txInfo = TxData(tx.txResponse.height.toInt(), null, tx.txResponse.txhash, blockTime)
            tx.tx.body.messagesList.map { it.getAssociatedGovMsgs() }
                .forEachIndexed fe@{ idx, pair ->
                    if (pair == null) return@fe
                    when (pair.first) {
                        GovMsgType.PROPOSAL ->
                            // Have to find the proposalId in the log events
                            tx.txResponse.logsList[idx]
                                .eventsList.first { it.type == "submit_proposal" }
                                .attributesList.first { it.key == "proposal_id" }
                                .value.toLong()
                                .let { id ->
                                    pair.second.toMsgSubmitProposal().let {
                                        govService.saveProposal(id, txInfo, it.proposer)
                                        govService.saveDeposit(id, txInfo, null, it)
                                    }
                                }
                        GovMsgType.DEPOSIT ->
                            pair.second.toMsgDeposit().let {
                                govService.saveProposal(it.proposalId, txInfo, it.depositor)
                                govService.saveDeposit(it.proposalId, txInfo, it, null)
                            }
                        GovMsgType.VOTE -> pair.second.toMsgVote().let {
                            govService.saveProposal(it.proposalId, txInfo, it.voter)
                            govService.saveVote(txInfo, it)
                        }
                    }
                }
        }
    }

    private fun saveIbcChannelData(txId: EntityID<Int>, tx: ServiceOuterClass.GetTxResponse, blockTime: DateTime) =
        transaction {
            if (tx.txResponse.code == 0) {
                tx.tx.body.messagesList.filter { it.isIbcTimeoutOnClose() }
                    .forEach { msg -> msg.toMsgTimeoutOnClose().packet.let { ibcService.saveIbcChannel(it.sourcePort, it.sourceChannel) } }
                tx.tx.body.messagesList.map { it.getIbcChannelEvents() }
                    .forEachIndexed fe@{ idx, pair ->
                        if (pair == null) return@fe
                        val portAttr = pair.second.first
                        val channelAttr = pair.second.second
                        val (port, channel) = tx.txResponse.logsList[idx]
                            .eventsList.first { it.type == pair.first }
                            .attributesList.let { list ->
                                list.first { it.key == portAttr }.value to list.first { it.key == channelAttr }.value
                            }
                        ibcService.saveIbcChannel(port, channel)
                    }

                val txInfo = TxData(tx.txResponse.height.toInt(), txId.value, tx.txResponse.txhash, blockTime)
                tx.tx.body.messagesList
                    .map { msg -> msg.getIbcLedgerMsgs() }
                    .forEachIndexed fe@{ idx, any ->
                        if (any == null) return@fe
                        val ledger = when {
                            any.typeUrl.endsWith("MsgTransfer") -> {
                                val msg = any.toMsgTransfer()
                                val channel = IbcChannelRecord.findBySrcPortSrcChannel(msg.sourcePort, msg.sourceChannel)
                                ibcService.parseTransfer(
                                    LedgerInfo(
                                        channel = channel!!,
                                        denom = msg.token.denom,
                                        logs = tx.txResponse.logsList[idx],
                                        balanceOut = msg.token.amount
                                    )
                                )
                            }
                            any.typeUrl.endsWith("MsgRecvPacket") -> {
                                val msg = any.toMsgRecvPacket()
                                val channel = IbcChannelRecord.findBySrcPortSrcChannel(
                                    msg.packet.destinationPort,
                                    msg.packet.destinationChannel
                                )
                                ibcService.parseRecv(
                                    LedgerInfo(
                                        channel = channel!!,
                                        logs = tx.txResponse.logsList[idx]
                                    )
                                )
                            }
                            any.typeUrl.endsWith("MsgAcknowledgement") -> {
                                val msg = any.toMsgAcknowledgement()
                                val channel = IbcChannelRecord.findBySrcPortSrcChannel(
                                    msg.packet.sourcePort,
                                    msg.packet.sourceChannel
                                )
                                val data = msg.packet.data.toStringUtf8().toObjectNode()
                                ibcService.parseAcknowledge(
                                    LedgerInfo(
                                        channel = channel!!,
                                        logs = tx.txResponse.logsList[idx]
                                    ),
                                    data
                                )
                            }
                            else -> logger.debug("This typeUrl is not yet supported in as an ibc ledger msg: ${any.typeUrl}")
                                .let { return@fe }
                        }
                        ibcService.saveIbcLedger(ledger, txInfo)
                    }
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
