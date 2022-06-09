package io.provenance.explorer.service.async

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.Any
import com.google.protobuf.Timestamp
import cosmos.base.tendermint.v1beta1.Query
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.isMAddress
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.core.sql.toArray
import io.provenance.explorer.domain.core.sql.toObject
import io.provenance.explorer.domain.core.toMAddress
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockTxRetryRecord
import io.provenance.explorer.domain.entities.FeePayer
import io.provenance.explorer.domain.entities.IbcAckType
import io.provenance.explorer.domain.entities.IbcLedgerRecord
import io.provenance.explorer.domain.entities.IbcRelayerRecord
import io.provenance.explorer.domain.entities.NameRecord
import io.provenance.explorer.domain.entities.SigJoinType
import io.provenance.explorer.domain.entities.SignatureJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinType
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxEventAttrRecord
import io.provenance.explorer.domain.entities.TxEventAttrTable
import io.provenance.explorer.domain.entities.TxEventRecord
import io.provenance.explorer.domain.entities.TxFeeRecord
import io.provenance.explorer.domain.entities.TxFeeRecord.Companion.totalMsgBasedFees
import io.provenance.explorer.domain.entities.TxFeepayerRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxIbcRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxNftJoinRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.TxSmCodeRecord
import io.provenance.explorer.domain.entities.TxSmContractRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.buildInsert
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.extensions.getSigners
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.translateAddress
import io.provenance.explorer.domain.models.explorer.BlockProposer
import io.provenance.explorer.domain.models.explorer.BlockUpdate
import io.provenance.explorer.domain.models.explorer.MsgProtoBreakout
import io.provenance.explorer.domain.models.explorer.Name
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.domain.models.explorer.toProcedureObject
import io.provenance.explorer.grpc.extensions.AddressEvents
import io.provenance.explorer.grpc.extensions.DenomEvents
import io.provenance.explorer.grpc.extensions.GovMsgType
import io.provenance.explorer.grpc.extensions.NameEvents
import io.provenance.explorer.grpc.extensions.SmContractEventKeys
import io.provenance.explorer.grpc.extensions.SmContractValue
import io.provenance.explorer.grpc.extensions.denomEventRegexParse
import io.provenance.explorer.grpc.extensions.getAddressEventByEvent
import io.provenance.explorer.grpc.extensions.getAssociatedAddresses
import io.provenance.explorer.grpc.extensions.getAssociatedDenoms
import io.provenance.explorer.grpc.extensions.getAssociatedGovMsgs
import io.provenance.explorer.grpc.extensions.getAssociatedMetadata
import io.provenance.explorer.grpc.extensions.getAssociatedMetadataEvents
import io.provenance.explorer.grpc.extensions.getAssociatedSmContractMsgs
import io.provenance.explorer.grpc.extensions.getDenomEventByEvent
import io.provenance.explorer.grpc.extensions.getIbcLedgerMsgs
import io.provenance.explorer.grpc.extensions.getNameEventTypes
import io.provenance.explorer.grpc.extensions.getNameMsgs
import io.provenance.explorer.grpc.extensions.getSmContractEventByEvent
import io.provenance.explorer.grpc.extensions.getTxIbcClientChannel
import io.provenance.explorer.grpc.extensions.isIbcTransferMsg
import io.provenance.explorer.grpc.extensions.isMetadataDeletionMsg
import io.provenance.explorer.grpc.extensions.isStandardAddress
import io.provenance.explorer.grpc.extensions.isValidatorAddress
import io.provenance.explorer.grpc.extensions.mapEventAttrValues
import io.provenance.explorer.grpc.extensions.scrubQuotes
import io.provenance.explorer.grpc.extensions.toMsgAcknowledgement
import io.provenance.explorer.grpc.extensions.toMsgBindNameRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteNameRequest
import io.provenance.explorer.grpc.extensions.toMsgDeposit
import io.provenance.explorer.grpc.extensions.toMsgRecvPacket
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposal
import io.provenance.explorer.grpc.extensions.toMsgTimeout
import io.provenance.explorer.grpc.extensions.toMsgTimeoutOnClose
import io.provenance.explorer.grpc.extensions.toMsgTransfer
import io.provenance.explorer.grpc.extensions.toMsgVote
import io.provenance.explorer.grpc.extensions.toMsgVoteWeighted
import io.provenance.explorer.grpc.extensions.toWeightedVote
import io.provenance.explorer.grpc.v1.MsgFeeGrpcClient
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.IbcService
import io.provenance.explorer.service.NftService
import io.provenance.explorer.service.SmartContractService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.splitChildParent
import net.pearx.kasechange.toSnakeCase
import net.pearx.kasechange.universalWordSplitter
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class AsyncCachingV2(
    private val txClient: TransactionGrpcClient,
    private val msgFeeClient: MsgFeeGrpcClient,
    private val blockService: BlockService,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val assetService: AssetService,
    private val nftService: NftService,
    private val govService: GovService,
    private val ibcService: IbcService,
    private val smContractService: SmartContractService,
    private val props: ExplorerProperties
) {

    protected val logger = logger(AsyncCachingV2::class)

    protected var chainId: String = ""

    fun getChainIdString() =
        if (chainId.isEmpty())
            getBlock(blockService.getLatestBlockHeightIndex())!!.block.header.chainId.also { this.chainId = it }
        else this.chainId

    fun getBlock(blockHeight: Int) = transaction {
        BlockCacheRecord.findById(blockHeight)?.also { BlockCacheRecord.updateHitCount(blockHeight) }?.block
    } ?: saveBlockEtc(blockService.getBlockAtHeightFromChain(blockHeight))

    fun saveBlockEtc(
        blockRes: Query.GetBlockByHeightResponse?,
        rerunTxs: Pair<Boolean, Boolean> = Pair(false, false) // rerun txs, pull from db
    ): Query.GetBlockByHeightResponse? {
        if (blockRes == null) return null
        logger.info("saving block ${blockRes.block.height()}")
        val blockTimestamp = blockRes.block.header.time.toDateTime()
        val block =
            BlockCacheRecord.buildInsert(
                blockRes.block.height(),
                blockRes.block.data.txsCount,
                blockTimestamp,
                blockRes
            )
        val proposerRec = validatorService.buildProposerInsert(blockRes, blockTimestamp, blockRes.block.height())
        val valsAtHeight = validatorService.buildValidatorsAtHeight(blockRes.block.height())
        validatorService.saveMissedBlocks(blockRes)
        val txs =
            if (blockRes.block.data.txsCount > 0) saveTxs(
                blockRes,
                proposerRec,
                rerunTxs
            ).map { it.toProcedureObject() }
            else listOf()
        val blockUpdate = BlockUpdate(block, proposerRec.buildInsert(), valsAtHeight, txs)
        try {
            BlockCacheRecord.insertToProcedure(blockUpdate)
        } catch (e: Exception) {
            logger.error("Failed to save block: ${blockRes.block.height()}", e.message)
            BlockTxRetryRecord.insert(blockRes.block.height(), e)
        }
        return blockRes
    }

    data class TxUpdatedItems(
        val addresses: Map<String, List<Int>>,
        val markers: List<String>,
        val txUpdate: TxUpdate
    )

    fun saveTxs(
        blockRes: Query.GetBlockByHeightResponse,
        proposerRec: BlockProposer,
        rerunTxs: Pair<Boolean, Boolean> = Pair(false, false) // rerun txs, pull from db
    ): List<TxUpdate> {
        val toBeUpdated =
            addTxsToCache(
                blockRes.block.height(),
                blockRes.block.data.txsCount,
                blockRes.block.header.time,
                proposerRec,
                rerunTxs
            )
        toBeUpdated.flatMap { it.markers }.toSet().let { assetService.updateAssets(it, blockRes.block.header.time) }
        toBeUpdated.flatMap { it.addresses.entries }
            .groupBy({ it.key }) { it.value }
            .mapValues { (_, values) -> values.flatten().toSet() }
            .let {
                it.entries.forEach { ent ->
                    when (ent.key) {
                        TxAddressJoinType.OPERATOR.name -> validatorService.updateStakingValidators(
                            ent.value,
                            blockRes.block.height()
                        ).also { updated -> if (updated) ValidatorStateRecord.refreshCurrentStateView() }
                    }
                }
            }
        return toBeUpdated.map { it.txUpdate }
    }

    private fun txCountForHeight(blockHeight: Int) = transaction { TxCacheRecord.findByHeight(blockHeight).count() }

    fun addTxsToCache(
        blockHeight: Int,
        expectedNumTxs: Int,
        blockTime: Timestamp,
        proposerRec: BlockProposer,
        rerunTxs: Pair<Boolean, Boolean> = Pair(false, false) // rerun txs, pull from db
    ) =
        if (txCountForHeight(blockHeight).toInt() == expectedNumTxs && !rerunTxs.first)
            logger.info("Cache hit for transaction at height $blockHeight with $expectedNumTxs transactions")
                .let { listOf() }
        else {
            logger.info("Searching for $expectedNumTxs transactions at height $blockHeight")
            tryAddTxs(blockHeight, expectedNumTxs, blockTime, proposerRec, rerunTxs.second)
        }

    private fun tryAddTxs(
        blockHeight: Int,
        txCount: Int,
        blockTime: Timestamp,
        proposerRec: BlockProposer,
        pullFromDb: Boolean = false
    ): List<TxUpdatedItems> = try {
        if (pullFromDb)
            TxCacheRecord.findByHeight(blockHeight).map { addTxToCacheWithTimestamp(it.txV2, blockTime, proposerRec) }
        else
            txClient.getTxsByHeight(blockHeight, txCount)
                .map { addTxToCacheWithTimestamp(txClient.getTxByHash(it.txhash), blockTime, proposerRec) }
    } catch (e: Exception) {
        logger.error("Failed to retrieve transactions at block: $blockHeight", e.message)
        BlockTxRetryRecord.insert(blockHeight, e)
        listOf()
    }

    fun addTxToCacheWithTimestamp(
        res: ServiceOuterClass.GetTxResponse,
        blockTime: Timestamp,
        proposerRec: BlockProposer
    ) =
        addTxToCache(res, blockTime.toDateTime(), proposerRec)

    // Function that saves all the things under a transaction
    fun addTxToCache(
        res: ServiceOuterClass.GetTxResponse,
        blockTime: DateTime,
        proposerRec: BlockProposer
    ): TxUpdatedItems {
        val tx = TxCacheRecord.buildInsert(res, blockTime)
        val txUpdate = TxUpdate(tx)
        val txInfo = TxData(res.txResponse.height.toInt(), null, res.txResponse.txhash, blockTime)
        saveMessages(txInfo, res, txUpdate)
        saveTxFees(res, txInfo, txUpdate, proposerRec)
        val addrs = saveAddresses(txInfo, res, txUpdate)
        val markers = saveMarkers(txInfo, res, txUpdate)
        saveNftData(txInfo, res, txUpdate)
        saveGovData(res, txInfo, txUpdate)
        saveIbcChannelData(res, txInfo, txUpdate)
        saveSmartContractData(res, txInfo, txUpdate)
        saveNameData(res, txInfo)
        saveSignaturesTx(res, txInfo, txUpdate)
        return TxUpdatedItems(addrs, markers, txUpdate)
    }

    private fun saveEvents(
        txInfo: TxData,
        tx: ServiceOuterClass.GetTxResponse,
        msgTypeId: Int,
        idx: Int
    ) = transaction {
        tx.txResponse.logsList[idx].eventsList.map { event ->
            val eventStr = TxEventRecord.buildInsert(
                txInfo.blockHeight,
                txInfo.txHash,
                event.type,
                msgTypeId
            )
            val attrs = event.attributesList.mapIndexed { idx, attr ->
                TxEventAttrRecord.buildInsert(idx, attr.key, attr.value)
            }
            listOf(
                eventStr,
                attrs.toArray(TxEventAttrTable.tableName)
            ).toObject()
        }
    }

    private fun saveTxFees(
        tx: ServiceOuterClass.GetTxResponse,
        txInfo: TxData,
        txUpdate: TxUpdate,
        proposerRec: BlockProposer
    ) =
        txUpdate.apply {
            val msgBasedFeeMap = TxFeeRecord.identifyMsgBasedFees(tx, msgFeeClient)
            this.txFees.addAll(TxFeeRecord.buildInserts(txInfo, tx, assetService, msgBasedFeeMap))
            this.txGasFee = TxGasCacheRecord.buildInsert(tx, txInfo.txTimestamp, msgBasedFeeMap.totalMsgBasedFees())
            this.validatorMarketRate = ValidatorMarketRateRecord.buildInsert(
                txInfo, proposerRec.proposerOperatorAddress, tx, msgBasedFeeMap.totalMsgBasedFees()
            )
        }

    fun saveMessages(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) = transaction {
        tx.tx.body.messagesList.forEachIndexed { idx, msg ->
            val (_, module, type) = msg.getMsgType()
            val msgRec = TxMessageRecord.buildInsert(txInfo.blockHeight, txInfo.txHash, msg, type, module, idx)
            var single: String? = null
            var events = listOf<String>()
            if (tx.txResponse.logsCount > 0) {
                events = saveEvents(txInfo, tx, msgRec.second, idx)
                if (tx.tx.body.messagesCount == 1)
                    single = TxSingleMessageCacheRecord.buildInsert(
                        txInfo.txTimestamp,
                        txInfo.txHash,
                        tx.txResponse.gasUsed.toInt(),
                        type
                    )
            }
            txUpdate.apply {
                if (single != null) this.singleMsgs.add(single)
                this.txMsgs.add(listOf(msgRec.first, events.toArray("tx_event")).toObject())
            }
        }
    }

    fun Any.getMsgType(): MsgProtoBreakout {
        val protoType = this.typeUrl
        val module = if (!protoType.startsWith("/ibc")) protoType.split(".")[1]
        else protoType.split(".").let { list -> "${list[0].drop(1)}_${list[2]}" }
        val type = protoType.split("Msg")[1].removeSuffix("Request").toSnakeCase(universalWordSplitter(false))
        return MsgProtoBreakout(protoType, module, type)
    }

    private fun saveAddresses(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) = transaction {
        val msgAddrs = tx.tx.body.messagesList.flatMap { it.getAssociatedAddresses() }
        val eventAddrs = tx.txResponse.logsList
            .flatMap { it.eventsList }
            .filter { it.type in AddressEvents.values().map { addr -> addr.event } }
            .flatMap { e ->
                getAddressEventByEvent(e.type)!!.let {
                    e.attributesList
                        .filter { attr -> attr.key in it.idField }
                        .map { found -> found.value.scrubQuotes() }
                }
            }

        (msgAddrs + eventAddrs).toSet()
            .filter { it.isNotEmpty() }
            .mapNotNull { saveAddr(it, txInfo, txUpdate) }
            .filter { it.second != null }.groupBy({ it.first }) { it.second!! }
    }

    private fun saveAddr(addr: String, txInfo: TxData, txUpdate: TxUpdate): Pair<String, Int?>? {
        val addrPair = addr.getAddressType(validatorService.getActiveSet(), props) ?: return null
        var pairCopy = addrPair.copy()
        if (addrPair.second == null) {
            try {
                when (addrPair.first) {
                    TxAddressJoinType.OPERATOR.name ->
                        validatorService.saveValidator(addr)
                            .let { pairCopy = addrPair.copy(second = it.operatorAddrId) }
                    TxAddressJoinType.ACCOUNT.name -> accountService.saveAccount(addr)
                        .let { pairCopy = addrPair.copy(second = it.id.value) }
                }
            } catch (ex: Exception) {
                BlockTxRetryRecord.insertNonRetry(txInfo.blockHeight, ex)
            }
        }
        if (pairCopy.second != null)
            txUpdate.apply { this.addressJoin.add(TxAddressJoinRecord.buildInsert(txInfo, pairCopy, addr)) }
        return pairCopy
    }

    private fun saveMarkers(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) = transaction {
        val msgDenoms = tx.tx.body.messagesList.map { it.getAssociatedDenoms() to it.isIbcTransferMsg() }
        val denoms = msgDenoms.flatMap { it.first }
        // captures all events that have a denom
        val eventDenoms =
            tx.txResponse.logsList
                .flatMap { it.eventsList }
                .filter { it.type in DenomEvents.values().map { de -> de.event } }
                .flatMap { e ->
                    getDenomEventByEvent(e.type)!!.let {
                        e.attributesList
                            .filter { attr -> attr.key == it.idField }
                            .mapNotNull { found ->
                                if (it.parse) found.value.scrubQuotes().denomEventRegexParse()
                                else listOf(found.value.scrubQuotes())
                            }.flatten()
                    }
                }

        (denoms + eventDenoms).toSet().mapNotNull { de ->
            val denom = msgDenoms.firstOrNull { it.first.contains(de) }
            if (denom != null && denom.second) if (tx.txResponse.code == 0) saveDenom(de, txInfo, txUpdate) else null
            else saveDenom(de, txInfo, txUpdate)
        }
    }

    private fun saveDenom(denom: String, txInfo: TxData, txUpdate: TxUpdate): String {
        assetService.getAssetRaw(denom).let { (id, _) ->
            txUpdate.apply { this.markerJoin.add(TxMarkerJoinRecord.buildInsert(txInfo, id.value, denom)) }
        }
        return denom
    }

    private fun saveNftData(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) = transaction {
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
            .filter { it.isMAddress() }
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
        txUpdate.apply { this.nftJoin.addAll(nfts.map { nft -> TxNftJoinRecord.buildInsert(txInfo, nft) }) }
    }

    private fun saveGovData(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        if (tx.txResponse.code == 0) {
            tx.tx.body.messagesList.mapNotNull { it.getAssociatedGovMsgs() }
                .forEachIndexed { idx, pair ->
                    when (pair.first) {
                        GovMsgType.PROPOSAL ->
                            // Have to find the proposalId in the log events
                            tx.txResponse.logsList[idx]
                                .eventsList.first { it.type == "submit_proposal" }
                                .attributesList.first { it.key == "proposal_id" }
                                .value.toLong()
                                .let { id ->
                                    pair.second.toMsgSubmitProposal().let {
                                        txUpdate.apply {
                                            govService.buildProposal(id, txInfo, it.proposer, true)
                                                ?.let { this.proposals.add(it) }
                                            this.deposits.addAll(govService.buildDeposit(id, txInfo, null, it))
                                            govService.buildProposalMonitor(it, id, txInfo).let { mon ->
                                                if (mon != null) this.proposalMonitors.add(mon)
                                            }
                                        }
                                    }
                                }
                        GovMsgType.DEPOSIT ->
                            pair.second.toMsgDeposit().let {
                                txUpdate.apply {
                                    govService.buildProposal(it.proposalId, txInfo, it.depositor, isSubmit = false)
                                        ?.let { this.proposals.add(it) }
                                    this.deposits.addAll(govService.buildDeposit(it.proposalId, txInfo, it, null))
                                }
                            }
                        GovMsgType.VOTE -> pair.second.toMsgVote().let {
                            txUpdate.apply {
                                govService.buildProposal(it.proposalId, txInfo, it.voter, isSubmit = false)
                                    ?.let { this.proposals.add(it) }
                                this.votes.addAll(
                                    govService.buildVote(txInfo, listOf(it.toWeightedVote()), it.voter, it.proposalId)
                                )
                            }
                        }
                        GovMsgType.WEIGHTED -> pair.second.toMsgVoteWeighted().let {
                            txUpdate.apply {
                                govService.buildProposal(it.proposalId, txInfo, it.voter, isSubmit = false)
                                    ?.let { this.proposals.add(it) }
                                this.votes.addAll(govService.buildVote(txInfo, it.optionsList, it.voter, it.proposalId))
                            }
                        }
                    }
                }
        }
    }

    private fun saveIbcChannelData(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) =
        transaction {
            val scrapedObjs = tx.tx.body.messagesList.map { it.getTxIbcClientChannel() }

            // save channel data
            // save tx_ibc
            // save ibc_relayer
            scrapedObjs.forEachIndexed { idx, obj ->
                if (obj == null) return@forEachIndexed
                // get channel, or null
                val channel = when {
                    obj.msgSrcChannel != null -> ibcService.saveIbcChannel(obj.msgSrcPort!!, obj.msgSrcChannel)
                    obj.srcChannelAttr != null -> {
                        val (port, channel) = tx.mapEventAttrValues(
                            idx,
                            obj.event,
                            listOf(obj.srcPortAttr!!, obj.srcChannelAttr!!)
                        ).let { it[obj.srcPortAttr]!! to it[obj.srcChannelAttr]!! }
                        ibcService.saveIbcChannel(port, channel)
                    }
                    else -> null
                }

                val client = when {
                    channel != null -> channel.client
                    obj.msgClient != null -> obj.msgClient
                    obj.clientAttr != null ->
                        tx.mapEventAttrValues(idx, obj.event, listOf(obj.clientAttr))[obj.clientAttr]!!
                    else -> null
                }
                txUpdate.apply { this.ibcJoin.add(TxIbcRecord.buildInsert(txInfo, client, channel?.id?.value)) }

                val msg = tx.tx.body.messagesList[idx]
                if (!msg.isIbcTransferMsg() && client != null) {
                    val relayer = msg.getSigners().first()
                    val account = accountService.saveAccount(relayer)
                    IbcRelayerRecord.insertIgnore(client, channel?.id?.value, account)
                }
            }

            val txSuccess = tx.txResponse.code == 0
            val successfulRecvHashes = mutableListOf<String>()
            // save ledgers, acks
            tx.tx.body.messagesList.map { it.getIbcLedgerMsgs() }
                .forEachIndexed { idx, any ->
                    if (any == null) return@forEachIndexed
                    val ledger = when {
                        any.typeUrl.endsWith("MsgTransfer") -> {
                            if (!txSuccess) return@forEachIndexed
                            val msg = any.toMsgTransfer()
                            ibcService.parseTransfer(msg, tx.txResponse.logsList[idx])
                        }
                        any.typeUrl.endsWith("MsgRecvPacket") -> {
                            val msg = any.toMsgRecvPacket()
                            ibcService.parseRecv(txSuccess, msg, tx.txResponse.logsList[idx])
                        }
                        any.typeUrl.endsWith("MsgAcknowledgement") -> {
                            val msg = any.toMsgAcknowledgement()
                            ibcService.parseAcknowledge(txSuccess, msg, tx.txResponse.logsList[idx])
                        }
                        any.typeUrl.endsWith("MsgTimeout") -> {
                            val msg = any.toMsgTimeout()
                            ibcService.parseTimeout(txSuccess, msg, tx.txResponse.logsList[idx])
                        }
                        any.typeUrl.endsWith("MsgTimeoutOnClose") -> {
                            val msg = any.toMsgTimeoutOnClose()
                            ibcService.parseTimeoutOnClose(txSuccess, msg, tx.txResponse.logsList[idx])
                        }
                        else -> logger.debug("This typeUrl is not yet supported in as an ibc ledger msg: ${any.typeUrl}")
                            .let { return@forEachIndexed }
                    }
                    when (ledger.ackType) {
                        IbcAckType.ACKNOWLEDGEMENT, IbcAckType.TIMEOUT ->
                            IbcLedgerRecord.findMatchingRecord(ledger, txInfo.txHash)?.let {
                                txUpdate.apply {
                                    this.ibcLedgers.add(ibcService.buildIbcLedger(ledger, txInfo, it))
                                    this.ibcLedgerAcks.add(ibcService.buildIbcLedgerAck(ledger, txInfo, it.id.value))
                                }
                            } ?: throw InvalidArgumentException(
                                "No matching IBC ledger record for channel " +
                                    "${ledger.channel!!.srcPort}/${ledger.channel!!.srcChannel}, sequence ${ledger.sequence}"
                            )
                        IbcAckType.RECEIVE ->
                            IbcLedgerRecord.findMatchingRecord(ledger, txInfo.txHash)?.let {
                                txUpdate.apply {
                                    this.ibcLedgerAcks.add(ibcService.buildIbcLedgerAck(ledger, txInfo, it.id.value))
                                }
                            } ?: if (ledger.changesEffected) {
                                txUpdate.apply {
                                    this.ibcLedgers.add(ibcService.buildIbcLedger(ledger, txInfo, null))
                                }
                                successfulRecvHashes.add(IbcLedgerRecord.getUniqueHash(ledger))
                            } else if (successfulRecvHashes.contains(IbcLedgerRecord.getUniqueHash(ledger))) {
                                BlockTxRetryRecord.insertNonBlockingRetry(
                                    txInfo.blockHeight,
                                    InvalidArgumentException(
                                        "Matching IBC Ledger record has not been saved yet - " +
                                            "${ledger.channel!!.srcPort}/${ledger.channel!!.srcChannel}, " +
                                            "sequence ${ledger.sequence}. Retrying block to save non-effected RECV record."
                                    )
                                )
                            } else {
                                BlockTxRetryRecord.insertNonBlockingRetry(
                                    txInfo.blockHeight,
                                    InvalidArgumentException(
                                        "Matching IBC Ledger record has not been saved yet - " +
                                            "${ledger.channel!!.srcPort}/${ledger.channel!!.srcChannel}, " +
                                            "sequence ${ledger.sequence}. Could be contained in another Tx in the " +
                                            "same block."
                                    )
                                )
                            }
                        IbcAckType.TRANSFER ->
                            txUpdate.apply { this.ibcLedgers.add(ibcService.buildIbcLedger(ledger, txInfo, null)) }
                        else -> logger.debug("Invalid IBC ack type: ${ledger.ackType}").let { return@forEachIndexed }
                    }
                }
        }

    private fun saveSmartContractData(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) =
        transaction {
            val codesToBeSaved = mutableListOf<Long>()
            val contractsToBeSaved = mutableListOf<String>()
            tx.tx.body.messagesList.mapNotNull { it.getAssociatedSmContractMsgs() }
                .forEach { data ->
                    data.forEach { pair ->
                        when (pair.first) {
                            SmContractValue.CODE -> codesToBeSaved.add(pair.second as Long)
                            SmContractValue.CONTRACT -> contractsToBeSaved.add(pair.second as String)
                        }
                    }
                }

            tx.txResponse.logsList
                .flatMap { it.eventsList }
                .filter { it.type in SmContractEventKeys.values().map { sc -> sc.eventType } }
                .flatMap { e ->
                    getSmContractEventByEvent(e.type)!!.let {
                        e.attributesList
                            .filter { attr -> attr.key in it.eventKey.keys }
                            .map mp@{ found ->
                                when (it.eventKey[found.key]) {
                                    SmContractValue.CODE -> codesToBeSaved.add(found.value.toLong())
                                    SmContractValue.CONTRACT -> contractsToBeSaved.add(found.value)
                                    else -> return@mp // do nothing
                                }
                            }
                    }
                }

            codesToBeSaved.map { smContractService.saveCode(it, txInfo) }
                .map { code -> TxSmCodeRecord.buildInsert(txInfo, code) }
                .let { txUpdate.apply { this.smCodes.addAll(it) } }
            contractsToBeSaved.associateBy { contract -> smContractService.saveContract(contract, txInfo) }
                .map { contract -> TxSmContractRecord.buildInsert(txInfo, contract.key, contract.value) }
                .let { txUpdate.apply { this.smContracts.addAll(it) } }
        }

    private fun saveNameData(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData) = transaction {
        if (tx.txResponse.code == 0) {
            val insertList = mutableListOf<Name>()
            tx.tx.body.messagesList.mapNotNull { it.getNameMsgs() }
                .forEach { any ->
                    when (any.typeUrl) {
                        NameEvents.NAME_BIND.msg ->
                            any.toMsgBindNameRequest().let {
                                Name(
                                    if (it.hasParent()) it.parent.name else null,
                                    it.record.name,
                                    it.record.name + (if (it.hasParent()) ".${it.parent.name}" else ""),
                                    it.record.address,
                                    it.record.restricted,
                                    txInfo.blockHeight
                                )
                            }.let { insertList.add(it) }
                        NameEvents.NAME_DELETE.msg ->
                            any.toMsgDeleteNameRequest().let {
                                NameRecord.deleteByFullNameAndOwner(
                                    it.record.name,
                                    it.record.address,
                                    txInfo.blockHeight
                                )
                            }
                    }
                }

            tx.txResponse.logsList
                .flatMap { it.eventsList }
                .filter { getNameEventTypes().contains(it.type) }
                .map { e ->
                    when (e.type) {
                        NameEvents.NAME_BIND.event -> {
                            val groupedAtts = e.attributesList.groupBy({ it.key }) { it.value.scrubQuotes() }
                            val addrList = groupedAtts["address"]!!
                            groupedAtts["name"]!!.forEachIndexed { idx, name ->
                                val (child, parent) = name.splitChildParent()
                                val obj = Name(parent, child, name, addrList[idx], false, txInfo.blockHeight)
                                if (insertList.firstOrNull { it.fullName == name } == null)
                                    insertList.add(obj)
                            }
                        }
                        NameEvents.NAME_DELETE.event -> {
                            val groupedAtts = e.attributesList.groupBy({ it.key }) { it.value.scrubQuotes() }
                            val addrList = groupedAtts["address"]!!
                            groupedAtts["name"]!!.forEachIndexed { idx, name ->
                                NameRecord.deleteByFullNameAndOwner(name, addrList[idx], txInfo.blockHeight)
                            }
                        }
                    }
                }
            insertList.forEach { NameRecord.insertOrUpdate(it) }
        }
    }

    fun saveSignaturesTx(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        tx.tx.authInfo.signerInfosList.flatMap { sig ->
            SignatureJoinRecord.buildInsert(sig.publicKey, SigJoinType.TRANSACTION, tx.txResponse.txhash)
        }.let { txUpdate.apply { this.sigs.addAll(it) } }

        AccountRecord.findByAddress(tx.tx.authInfo.fee.granter)
            ?.let { TxFeepayerRecord.buildInsert(txInfo, FeePayer.GRANTER.name, it.id.value, it.accountAddress) }
            ?.let { txUpdate.apply { this.feePayers.add(it) } }

        AccountRecord.findByAddress(tx.tx.authInfo.fee.payer)
            ?.let { TxFeepayerRecord.buildInsert(txInfo, FeePayer.PAYER.name, it.id.value, it.accountAddress) }
            ?.let { txUpdate.apply { this.feePayers.add(it) } }

        // get signers
        tx.tx.body.messagesList.flatMap { it.getSigners() }.toSet()
            .mapNotNull {
                when {
                    it.startsWith(props.provValOperPrefix()) -> it.translateAddress(props).accountAddr
                    it.startsWith(props.provAccPrefix()) -> it
                    else -> logger().debug("Address type is not supported: Addr $this").let { null }
                }
            }.map { accountService.saveAccount(it) }
            .also {
                TxFeepayerRecord.buildInsert(txInfo, FeePayer.FIRST_SIGNER.name, it[0].id.value, it[0].accountAddress)
                    .let { txUpdate.apply { this.feePayers.add(it) } }
            }
            .flatMap {
                SignatureJoinRecord.buildInsert(
                    it.baseAccount!!.pubKey,
                    SigJoinType.TRANSACTION,
                    tx.txResponse.txhash
                )
            }.let { txUpdate.apply { this.sigs.addAll(it) } }
    }
}

fun String.getAddressType(activeSet: Int, props: ExplorerProperties) = when {
    this.isValidatorAddress(props) ->
        Pair(TxAddressJoinType.OPERATOR.name, ValidatorStateRecord.findByOperator(activeSet, this)?.operatorAddrId)
    this.isStandardAddress(props) ->
        Pair(TxAddressJoinType.ACCOUNT.name, AccountRecord.findByAddress(this)?.id?.value)
    else -> logger().debug("Address type is not supported: Addr $this").let { null }
}
