package io.provenance.explorer.service.async

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.protobuf.Timestamp
import cosmos.base.tendermint.v1beta1.Query
import cosmos.group.v1.Types
import cosmos.group.v1.Types.ProposalStatus
import cosmos.group.v1.vote
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
import io.provenance.explorer.domain.entities.ProcessQueueRecord
import io.provenance.explorer.domain.entities.ProcessQueueType
import io.provenance.explorer.domain.entities.SignatureRecord
import io.provenance.explorer.domain.entities.SignatureTxRecord
import io.provenance.explorer.domain.entities.TxAddressJoinRecord
import io.provenance.explorer.domain.entities.TxAddressJoinType
import io.provenance.explorer.domain.entities.TxCacheRecord
import io.provenance.explorer.domain.entities.TxEventAttrRecord
import io.provenance.explorer.domain.entities.TxEventAttrTable
import io.provenance.explorer.domain.entities.TxEventRecord
import io.provenance.explorer.domain.entities.TxFeeRecord
import io.provenance.explorer.domain.entities.TxFeepayerRecord
import io.provenance.explorer.domain.entities.TxGasCacheRecord
import io.provenance.explorer.domain.entities.TxGroupsPolicyTable
import io.provenance.explorer.domain.entities.TxIbcRecord
import io.provenance.explorer.domain.entities.TxMarkerJoinRecord
import io.provenance.explorer.domain.entities.TxMessageRecord
import io.provenance.explorer.domain.entities.TxMsgTypeSubtypeRecord
import io.provenance.explorer.domain.entities.TxMsgTypeSubtypeTable
import io.provenance.explorer.domain.entities.TxNftJoinRecord
import io.provenance.explorer.domain.entities.TxSingleMessageCacheRecord
import io.provenance.explorer.domain.entities.TxSmCodeRecord
import io.provenance.explorer.domain.entities.TxSmContractRecord
import io.provenance.explorer.domain.entities.ValidatorMarketRateRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.entities.buildInsert
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.extensions.TX_ACC_SEQ
import io.provenance.explorer.domain.extensions.TX_EVENT
import io.provenance.explorer.domain.extensions.getAddrFromAccSeq
import io.provenance.explorer.domain.extensions.getFirstSigner
import io.provenance.explorer.domain.extensions.getTotalBaseFees
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.models.explorer.BlockProposer
import io.provenance.explorer.domain.models.explorer.BlockUpdate
import io.provenance.explorer.domain.models.explorer.GroupsProposalData
import io.provenance.explorer.domain.models.explorer.Name
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.domain.models.explorer.toProcedureObject
import io.provenance.explorer.grpc.extensions.AddressEvents
import io.provenance.explorer.grpc.extensions.DenomEvents
import io.provenance.explorer.grpc.extensions.GovMsgType
import io.provenance.explorer.grpc.extensions.GroupEvents
import io.provenance.explorer.grpc.extensions.GroupGovMsgType
import io.provenance.explorer.grpc.extensions.GroupPolicyEvents
import io.provenance.explorer.grpc.extensions.GroupProposalEvents
import io.provenance.explorer.grpc.extensions.NameEvents
import io.provenance.explorer.grpc.extensions.SmContractEventKeys
import io.provenance.explorer.grpc.extensions.SmContractValue
import io.provenance.explorer.grpc.extensions.denomEventRegexParse
import io.provenance.explorer.grpc.extensions.findAllMatchingEvents
import io.provenance.explorer.grpc.extensions.getAddressEventByEvent
import io.provenance.explorer.grpc.extensions.getAssociatedAddresses
import io.provenance.explorer.grpc.extensions.getAssociatedDenoms
import io.provenance.explorer.grpc.extensions.getAssociatedGovMsgs
import io.provenance.explorer.grpc.extensions.getAssociatedGroupPolicies
import io.provenance.explorer.grpc.extensions.getAssociatedGroupProposals
import io.provenance.explorer.grpc.extensions.getAssociatedGroups
import io.provenance.explorer.grpc.extensions.getAssociatedMetadata
import io.provenance.explorer.grpc.extensions.getAssociatedMetadataEvents
import io.provenance.explorer.grpc.extensions.getAssociatedSmContractMsgs
import io.provenance.explorer.grpc.extensions.getDenomEventByEvent
import io.provenance.explorer.grpc.extensions.getGroupEventByEvent
import io.provenance.explorer.grpc.extensions.getGroupPolicyEventByEvent
import io.provenance.explorer.grpc.extensions.getGroupsExecutorResult
import io.provenance.explorer.grpc.extensions.getGroupsProposalStatus
import io.provenance.explorer.grpc.extensions.getIbcLedgerMsgs
import io.provenance.explorer.grpc.extensions.getMsgSubTypes
import io.provenance.explorer.grpc.extensions.getMsgType
import io.provenance.explorer.grpc.extensions.getNameEventTypes
import io.provenance.explorer.grpc.extensions.getNameMsgs
import io.provenance.explorer.grpc.extensions.getSmContractEventByEvent
import io.provenance.explorer.grpc.extensions.getTxIbcClientChannel
import io.provenance.explorer.grpc.extensions.isIbcTransferMsg
import io.provenance.explorer.grpc.extensions.isMetadataDeletionMsg
import io.provenance.explorer.grpc.extensions.isStandardAddress
import io.provenance.explorer.grpc.extensions.isValidatorAddress
import io.provenance.explorer.grpc.extensions.mapEventAttrValues
import io.provenance.explorer.grpc.extensions.mapTxEventAttrValues
import io.provenance.explorer.grpc.extensions.scrubQuotes
import io.provenance.explorer.grpc.extensions.toMsgAcknowledgement
import io.provenance.explorer.grpc.extensions.toMsgBindNameRequest
import io.provenance.explorer.grpc.extensions.toMsgDeleteNameRequest
import io.provenance.explorer.grpc.extensions.toMsgDeposit
import io.provenance.explorer.grpc.extensions.toMsgDepositOld
import io.provenance.explorer.grpc.extensions.toMsgExecGroup
import io.provenance.explorer.grpc.extensions.toMsgIbcTransferRequest
import io.provenance.explorer.grpc.extensions.toMsgRecvPacket
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposal
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposalGroup
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposalOld
import io.provenance.explorer.grpc.extensions.toMsgTimeout
import io.provenance.explorer.grpc.extensions.toMsgTimeoutOnClose
import io.provenance.explorer.grpc.extensions.toMsgTransfer
import io.provenance.explorer.grpc.extensions.toMsgVote
import io.provenance.explorer.grpc.extensions.toMsgVoteGroup
import io.provenance.explorer.grpc.extensions.toMsgVoteOld
import io.provenance.explorer.grpc.extensions.toMsgVoteWeighted
import io.provenance.explorer.grpc.extensions.toMsgVoteWeightedOld
import io.provenance.explorer.grpc.extensions.toMsgWithdrawProposalGroup
import io.provenance.explorer.grpc.v1.MsgFeeGrpcClient
import io.provenance.explorer.grpc.v1.TransactionGrpcClient
import io.provenance.explorer.service.AccountService
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.BlockService
import io.provenance.explorer.service.GovService
import io.provenance.explorer.service.GroupService
import io.provenance.explorer.service.IbcService
import io.provenance.explorer.service.NftService
import io.provenance.explorer.service.SmartContractService
import io.provenance.explorer.service.ValidatorService
import io.provenance.explorer.service.splitChildParent
import io.provenance.explorer.service.toVoteMetadata
import io.provenance.explorer.service.toWeightedVoteList
import io.provenance.explorer.service.unchainDenom
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class AsyncCachingV2(
    private val txClient: TransactionGrpcClient,
    private val blockService: BlockService,
    private val validatorService: ValidatorService,
    private val accountService: AccountService,
    private val assetService: AssetService,
    private val nftService: NftService,
    private val govService: GovService,
    private val ibcService: IbcService,
    private val smContractService: SmartContractService,
    private val props: ExplorerProperties,
    private val msgFeeClient: MsgFeeGrpcClient,
    private val groupService: GroupService
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
            transaction {
                TxCacheRecord.findByHeight(blockHeight)
                    .map { addTxToCacheWithTimestamp(it.txV2, blockTime, proposerRec) }
            }
        else
            runBlocking { txClient.getTxsByHeight(blockHeight, txCount) }
                .map { addTxToCacheWithTimestamp(it, blockTime, proposerRec) }
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
        val txInfo = TxData(proposerRec.blockHeight, null, res.txResponse.txhash, blockTime)
        saveMessages(txInfo, res, txUpdate)
        saveTxFees(res, txInfo, txUpdate, proposerRec)
        val addrs = saveAddresses(txInfo, res, txUpdate)
        val markers = saveMarkers(txInfo, res, txUpdate)
        saveNftData(txInfo, res, txUpdate)
        saveGovData(res, txInfo, txUpdate)
        saveIbcChannelData(res, txInfo, txUpdate)
        saveSmartContractData(res, txInfo, txUpdate)
        saveNameData(res, txInfo)
        saveGroups(res, txInfo, txUpdate)
        saveSignaturesTx(res, txInfo, txUpdate)
        return TxUpdatedItems(addrs, markers, txUpdate)
    }

    private fun saveTxFees(
        tx: ServiceOuterClass.GetTxResponse,
        txInfo: TxData,
        txUpdate: TxUpdate,
        proposerRec: BlockProposer
    ) =
        txUpdate.apply {
            val msgBasedFeeMap = TxFeeRecord.identifyMsgBasedFees(tx, msgFeeClient, proposerRec.blockHeight)
            val totalBaseFees = tx.txResponse.getTotalBaseFees(msgFeeClient, proposerRec.blockHeight, props, msgBasedFeeMap.isNotEmpty())
            this.txFees.addAll(TxFeeRecord.buildInserts(txInfo, tx, assetService, msgBasedFeeMap, totalBaseFees))
            this.txGasFee = TxGasCacheRecord.buildInsert(tx, txInfo, totalBaseFees)
            this.validatorMarketRate =
                ValidatorMarketRateRecord.buildInsert(txInfo, proposerRec.proposerOperatorAddress, tx, totalBaseFees)
        }

    fun saveMessages(txInfo: TxData, tx: ServiceOuterClass.GetTxResponse, txUpdate: TxUpdate) = transaction {
        tx.tx.body.messagesList.forEachIndexed { idx, msg ->
            val primaryType = msg.typeUrl.getMsgType()
            val secondaryTypes = msg.getMsgSubTypes().filterNotNull().map { it.getMsgType() }
            val (primTypeId, subTypeRecs) = TxMsgTypeSubtypeRecord.buildInserts(primaryType, secondaryTypes, txInfo)
            val msgRec = TxMessageRecord.buildInsert(txInfo, msg, idx)
            var single: String? = null
            var events = listOf<String>()
            if (tx.txResponse.logsCount > 0) {
                events = saveEvents(txInfo, tx, primTypeId.value, idx)
                if (tx.tx.body.messagesCount == 1)
                    single = TxSingleMessageCacheRecord.buildInsert(txInfo, tx.txResponse.gasUsed.toInt(), primaryType.type)
            }
            txUpdate.apply {
                if (single != null) this.singleMsgs.add(single)
                this.txMsgs.add(
                    listOf(
                        msgRec,
                        subTypeRecs.toArray(TxMsgTypeSubtypeTable.tableName),
                        events.toArray("tx_event")
                    ).toObject()
                )
            }
        }
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
            val attrs = event.attributesList.mapIndexed { idx, attr -> TxEventAttrRecord.buildInsert(idx, attr.key, attr.value) }
            listOf(eventStr, attrs.toArray(TxEventAttrTable.tableName)).toObject()
        }
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
        val addrPair = addr.getAddressType(validatorService.getActiveSet()) ?: return null
        var pairCopy = addrPair.copy()
        try {
            when (addrPair.first) {
                TxAddressJoinType.OPERATOR.name ->
                    if (addrPair.second == null) {
                        validatorService.saveValidator(addr)
                            .let { pairCopy = addrPair.copy(second = it.operatorAddrId) }
                    }
                TxAddressJoinType.ACCOUNT.name -> accountService.saveAccount(addr)
                    .let { pairCopy = addrPair.copy(second = it.id.value) }
            }
        } catch (ex: Exception) {
            BlockTxRetryRecord.insertNonRetry(txInfo.blockHeight, ex)
        }
        if (pairCopy.second != null) {
            txUpdate.apply { this.addressJoin.add(TxAddressJoinRecord.buildInsert(txInfo, pairCopy, addr)) }
            if (pairCopy.first == TxAddressJoinType.ACCOUNT.name)
                ProcessQueueRecord.insertIgnore(ProcessQueueType.ACCOUNT, addr)
        }
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
            else saveDenom(de.unchainDenom(), txInfo, txUpdate)
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
        val meAddrs = tx.findAllMatchingEvents(me.map { m -> m.event })
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
                                    val proposer = when {
                                        pair.second.typeUrl.endsWith("gov.v1beta1.MsgSubmitProposal") -> pair.second.toMsgSubmitProposalOld().proposer
                                        pair.second.typeUrl.endsWith("gov.v1.MsgSubmitProposal") -> pair.second.toMsgSubmitProposal().proposer
                                        else -> throw InvalidArgumentException("Invalid gov proposal msg type: ${pair.second.typeUrl}")
                                    }
                                    txUpdate.apply {
                                        govService.buildProposal(id, txInfo, proposer, true)?.let { this.proposals.add(it) }
                                        govService.buildDeposit(id, txInfo, null, pair.second)?.let { this.deposits.addAll(it) }
                                        govService.buildProposalMonitor(pair.second, id, txInfo).let { mon ->
                                            if (mon.isNotEmpty()) this.proposalMonitors.addAll(mon)
                                        }
                                    }
                                }
                        GovMsgType.DEPOSIT -> {
                            val (proposalId, depositor) = when {
                                pair.second.typeUrl.endsWith("gov.v1beta1.MsgDeposit") ->
                                    pair.second.toMsgDepositOld().let { it.proposalId to it.depositor }
                                pair.second.typeUrl.endsWith("gov.v1.MsgDeposit") ->
                                    pair.second.toMsgDeposit().let { it.proposalId to it.depositor }
                                else -> throw InvalidArgumentException("Invalid gov deposit msg type: ${pair.second.typeUrl}")
                            }
                            txUpdate.apply {

                                govService.buildProposal(proposalId, txInfo, depositor, isSubmit = false)?.let { this.proposals.add(it) }
                                govService.buildDeposit(proposalId, txInfo, pair.second, null)?.let { this.deposits.addAll(it) }
                            }
                        }
                        GovMsgType.VOTE -> {
                            val (proposalId, voter, justification) = when {
                                pair.second.typeUrl.endsWith("gov.v1beta1.MsgVote") ->
                                    pair.second.toMsgVoteOld().let { Triple(it.proposalId, it.voter, null) }
                                pair.second.typeUrl.endsWith("gov.v1.MsgVote") ->
                                    pair.second.toMsgVote().let { Triple(it.proposalId, it.voter, it.metadata.toVoteMetadata()) }
                                else -> throw InvalidArgumentException("Invalid gov vote msg type: ${pair.second.typeUrl}")
                            }
                            txUpdate.apply {
                                govService.buildProposal(proposalId, txInfo, voter, isSubmit = false)?.let { this.proposals.add(it) }
                                this.votes.addAll(
                                    govService.buildVote(
                                        txInfo,
                                        pair.second.toWeightedVoteList(),
                                        voter,
                                        proposalId,
                                        justification
                                    )
                                )
                            }
                        }
                        GovMsgType.WEIGHTED -> {
                            val (proposalId, voter, justification) = when {
                                pair.second.typeUrl.endsWith("gov.v1beta1.MsgVoteWeighted") ->
                                    pair.second.toMsgVoteWeightedOld().let { Triple(it.proposalId, it.voter, null) }
                                pair.second.typeUrl.endsWith("gov.v1.MsgVoteWeighted") ->
                                    pair.second.toMsgVoteWeighted().let { Triple(it.proposalId, it.voter, it.metadata.toVoteMetadata()) }
                                else -> throw InvalidArgumentException("Invalid gov vote weighted msg type: ${pair.second.typeUrl}")
                            }
                            txUpdate.apply {
                                govService.buildProposal(proposalId, txInfo, voter, isSubmit = false)?.let { this.proposals.add(it) }
                                this.votes.addAll(
                                    govService.buildVote(
                                        txInfo,
                                        pair.second.toWeightedVoteList(),
                                        voter,
                                        proposalId,
                                        justification
                                    )
                                )
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
                    val relayer = tx.getFirstSigner()
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
                        any.typeUrl.endsWith("MsgIbcTransferRequest") -> {
                            if (!txSuccess) return@forEachIndexed
                            val msg = any.toMsgIbcTransferRequest()
                            ibcService.parseTransfer(msg.transfer, tx.txResponse.logsList[idx])
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
            contractsToBeSaved.associateBy { contract ->
                smContractService.saveContract(contract, txInfo)
                    .also { ProcessQueueRecord.insertIgnore(ProcessQueueType.ACCOUNT, contract) }
            }
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

    private fun saveGroups(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        // get groups, save
        val msgGroups = tx.tx.body.messagesList.mapNotNull { it.getAssociatedGroups() }
        val eventGroups = tx.txResponse.logsList
            .flatMap { it.eventsList }
            .filter { it.type in GroupEvents.values().map { grp -> grp.event } }
            .flatMap { e ->
                getGroupEventByEvent(e.type)!!.let {
                    e.attributesList
                        .filter { attr -> attr.key in it.idField }
                        .map { found -> found.value.scrubQuotes().toLong() }
                }
            }
        (msgGroups + eventGroups).toSet().forEach { id ->
            groupService.buildGroup(id, txInfo)?.let {
                txUpdate.apply {
                    if (tx.txResponse.code == 0)
                        this.groupsList.add(it)
                    this.groupJoin.add(groupService.buildTxGroup(id, txInfo))
                }
            }
        }

        // get policies, save
        val msgPolicies = tx.tx.body.messagesList.mapNotNull { it.getAssociatedGroupPolicies() }
        val eventPolicies = tx.txResponse.logsList
            .flatMap { it.eventsList }
            .filter { it.type in GroupPolicyEvents.values().map { pol -> pol.event } }
            .flatMap { e ->
                getGroupPolicyEventByEvent(e.type)!!.let {
                    e.attributesList
                        .filter { attr -> attr.key in it.idField }
                        .map { found -> found.value.scrubQuotes() }
                }
            }
        (msgPolicies + eventPolicies).toSet().forEach { addr ->
            groupService.buildGroupPolicy(addr, txInfo)
                ?.also { ProcessQueueRecord.insertIgnore(ProcessQueueType.ACCOUNT, addr) }
                ?.let { policy ->
                    val (join, savedPolicy) = groupService.buildTxGroupPolicy(addr, txInfo)
                    txUpdate.apply {
                        if (tx.txResponse.code == 0)
                            this.groupPolicies
                                .add(listOf(policy, listOf(join).toArray(TxGroupsPolicyTable.tableName)).toObject())
                        else if (savedPolicy)
                            this.policyJoinAlt.add(join)
                    }
                }
        }

        // get group gov msgs, and process
        if (tx.txResponse.code == 0)
            tx.tx.body.messagesList.mapNotNull { it.getAssociatedGroupProposals() }
                .forEachIndexed { idx, pair ->
                    when (pair.first) {
                        GroupGovMsgType.PROPOSAL -> {
                            // Have to find the proposalId in the log events
                            val proposalId = tx.mapEventAttrValues(
                                idx,
                                GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.event,
                                GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.toList()
                            )[GroupProposalEvents.GROUP_SUBMIT_PROPOSAL.idField.first()]!!.toLong()

                            val msg = pair.second.toMsgSubmitProposalGroup()
                            val nodeData = groupService.proposalAtHeight(proposalId, txInfo.blockHeight)?.proposal
                            val policy = groupService.policyAtHeight(msg.groupPolicyAddress, txInfo.blockHeight)!!.info
                            val group = groupService.groupAtHeight(policy.groupId, txInfo.blockHeight)!!.info
                            val tally = groupService.proposalTallyAtHeight(proposalId, txInfo.blockHeight)?.tally
                            val data = GroupsProposalData(
                                msg.proposersList,
                                msg.metadata,
                                msg.messagesList,
                                msg.exec.name,
                                txInfo.txTimestamp,
                                group.version,
                                policy.version,
                                tally,
                                nodeData?.votingPeriodEnd?.toDateTime()
                            )
                            val status = nodeData?.status ?: ProposalStatus.PROPOSAL_STATUS_ACCEPTED
                            val execResult = nodeData?.executorResult ?: tx.mapEventAttrValues(
                                idx,
                                GroupProposalEvents.GROUP_EXEC.event,
                                listOf("result")
                            )["result"]!!.getGroupsExecutorResult()

                            val proposal = groupService.buildProposal(
                                group.id,
                                policy.address,
                                proposalId,
                                data,
                                nodeData,
                                status,
                                execResult,
                                txInfo
                            )
                            val votes = groupService.buildVotes(
                                group.id,
                                group.version,
                                msg.proposersList,
                                vote {
                                    this.proposalId = proposalId
                                    this.option = Types.VoteOption.VOTE_OPTION_YES
                                    this.metadata = ""
                                },
                                txInfo
                            )
                            txUpdate.apply {
                                this.groupProposals.add(proposal)
                                this.groupVotes.addAll(votes)
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(msg.groupPolicyAddress, txInfo).first)
                            }
                        }
                        GroupGovMsgType.VOTE -> {
                            val msg = pair.second.toMsgVoteGroup()
                            val proposal = groupService.getProposalById(msg.proposalId)!!

                            groupService.buildVotes(
                                proposal.groupId.toLong(),
                                proposal.proposalData.groupVersion,
                                listOf(msg.voter),
                                vote {
                                    this.proposalId = msg.proposalId
                                    this.option = msg.option
                                    this.metadata = msg.metadata
                                },
                                txInfo
                            ).let {
                                txUpdate.apply {
                                    this.groupVotes.addAll(it)
                                    this.policyJoinAlt.add(groupService.buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                                }
                            }

                            val execResult = tx.mapEventAttrValues(
                                idx,
                                GroupProposalEvents.GROUP_EXEC.event,
                                listOf("result")
                            )["result"]?.getGroupsExecutorResult()

                            if (execResult != null)
                                transaction {
                                    proposal.apply {
                                        if (proposal.proposalStatus.getGroupsProposalStatus() != ProposalStatus.PROPOSAL_STATUS_ACCEPTED)
                                            this.proposalStatus = ProposalStatus.PROPOSAL_STATUS_ACCEPTED.name
                                        if (proposal.executorResult.getGroupsExecutorResult() != execResult)
                                            this.executorResult = execResult.name
                                    }
                                }
                        }
                        GroupGovMsgType.EXEC -> {
                            val msg = pair.second.toMsgExecGroup()
                            val proposal = groupService.getProposalById(msg.proposalId)!!

                            txUpdate.apply {
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                            }

                            val execResult = tx.mapEventAttrValues(
                                idx,
                                GroupProposalEvents.GROUP_EXEC.event,
                                listOf("result")
                            )["result"]?.getGroupsExecutorResult()

                            if (execResult != null)
                                transaction {
                                    proposal.apply {
                                        if (proposal.proposalStatus.getGroupsProposalStatus() != ProposalStatus.PROPOSAL_STATUS_ACCEPTED)
                                            this.proposalStatus = ProposalStatus.PROPOSAL_STATUS_ACCEPTED.name
                                        if (proposal.executorResult.getGroupsExecutorResult() != execResult)
                                            this.executorResult = execResult.name
                                    }
                                }
                        }
                        GroupGovMsgType.WITHDRAW -> {
                            val msg = pair.second.toMsgWithdrawProposalGroup()
                            val proposal = groupService.getProposalById(msg.proposalId)!!

                            txUpdate.apply {
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                            }

                            transaction {
                                proposal.apply { this.proposalStatus = ProposalStatus.PROPOSAL_STATUS_WITHDRAWN.name }
                            }
                        }
                    }
                }
        else
            tx.tx.body.messagesList.mapNotNull { it.getAssociatedGroupProposals() }
                .forEachIndexed { _, pair ->
                    when (pair.first) {
                        GroupGovMsgType.PROPOSAL -> {
                            val msg = pair.second.toMsgSubmitProposalGroup()
                            txUpdate.apply {
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(msg.groupPolicyAddress, txInfo).first)
                            }
                        }
                        GroupGovMsgType.VOTE -> {
                            val msg = pair.second.toMsgVoteGroup()
                            val proposal = groupService.getProposalById(msg.proposalId)!!
                            txUpdate.apply {
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                            }
                        }
                        GroupGovMsgType.EXEC -> {
                            val msg = pair.second.toMsgExecGroup()
                            val proposal = groupService.getProposalById(msg.proposalId)!!
                            txUpdate.apply {
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                            }
                        }
                        GroupGovMsgType.WITHDRAW -> {
                            val msg = pair.second.toMsgWithdrawProposalGroup()
                            val proposal = groupService.getProposalById(msg.proposalId)!!
                            txUpdate.apply {
                                this.policyJoinAlt.add(groupService.buildTxGroupPolicy(proposal.policyAddress, txInfo).first)
                            }
                        }
                    }
                }
    }

    private fun saveSignaturesTx(tx: ServiceOuterClass.GetTxResponse, txInfo: TxData, txUpdate: TxUpdate) = transaction {
        val signerEvents = tx.mapTxEventAttrValues(TX_EVENT, TX_ACC_SEQ)

        tx.tx.authInfo.signerInfosList.mapIndexedNotNull { idx, sig ->
            val pubKey = sig.publicKey
                ?: SignatureRecord.findByAddressSingle(signerEvents[idx]!!.getAddrFromAccSeq()).pubkeyObject
            SignatureTxRecord.buildInsert(pubKey, idx, txInfo, sig.sequence.toInt())
        }.let { txUpdate.apply { this.sigs.addAll(it) } }

        AccountRecord.findByAddress(tx.tx.authInfo.fee.granter)
            ?.let { TxFeepayerRecord.buildInsert(txInfo, FeePayer.GRANTER.name, it.id.value, it.accountAddress) }
            ?.let { txUpdate.apply { this.feePayers.add(it) } }

        AccountRecord.findByAddress(tx.tx.authInfo.fee.payer)
            ?.let { TxFeepayerRecord.buildInsert(txInfo, FeePayer.PAYER.name, it.id.value, it.accountAddress) }
            ?.let { txUpdate.apply { this.feePayers.add(it) } }

        AccountRecord.findByAddress(tx.getFirstSigner())
            ?.let { TxFeepayerRecord.buildInsert(txInfo, FeePayer.FIRST_SIGNER.name, it.id.value, it.accountAddress) }
            ?.let { txUpdate.apply { this.feePayers.add(it) } }
    }
}

fun String.getAddressType(activeSet: Int) = when {
    this.isValidatorAddress() ->
        Pair(TxAddressJoinType.OPERATOR.name, ValidatorStateRecord.findByOperator(activeSet, this)?.operatorAddrId)
    this.isStandardAddress() -> Pair(TxAddressJoinType.ACCOUNT.name, AccountRecord.findByAddress(this)?.id?.value)
    else -> logger().debug("Address type is not supported: Addr $this").let { null }
}
