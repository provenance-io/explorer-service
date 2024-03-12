package io.provenance.explorer.service

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.protobuf.Any
import com.google.protobuf.util.JsonFormat
import cosmos.gov.v1.Gov
import cosmos.gov.v1.Gov.VoteOption
import cosmos.gov.v1.msgDeposit
import cosmos.gov.v1.msgExecLegacyContent
import cosmos.gov.v1.msgSubmitProposal
import cosmos.gov.v1.msgVote
import cosmos.gov.v1.msgVoteWeighted
import cosmos.gov.v1.weightedVoteOption
import cosmos.gov.v1beta1.textProposal
import cosmos.params.v1beta1.paramChange
import cosmos.params.v1beta1.parameterChangeProposal
import cosmos.upgrade.v1beta1.cancelSoftwareUpgradeProposal
import cosmos.upgrade.v1beta1.plan
import cosmos.upgrade.v1beta1.softwareUpgradeProposal
import cosmwasm.wasm.v1.accessConfig
import cosmwasm.wasm.v1.instantiateContractProposal
import cosmwasm.wasm.v1.storeCodeProposal
import cosmwasm.wasm.v1beta1.Proposal
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.OBJECT_MAPPER
import io.provenance.explorer.VANILLA_MAPPER
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.DepositType
import io.provenance.explorer.domain.entities.GovDepositRecord
import io.provenance.explorer.domain.entities.GovProposalRecord
import io.provenance.explorer.domain.entities.GovVoteRecord
import io.provenance.explorer.domain.entities.MonitorProposalType
import io.provenance.explorer.domain.entities.ProposalMonitorRecord
import io.provenance.explorer.domain.entities.SmCodeRecord
import io.provenance.explorer.domain.entities.TxSmCodeRecord
import io.provenance.explorer.domain.entities.ValidatorStateRecord
import io.provenance.explorer.domain.exceptions.InvalidArgumentException
import io.provenance.explorer.domain.exceptions.requireNotNullToMessage
import io.provenance.explorer.domain.exceptions.requireToMessage
import io.provenance.explorer.domain.exceptions.validate
import io.provenance.explorer.domain.extensions.formattedString
import io.provenance.explorer.domain.extensions.getType
import io.provenance.explorer.domain.extensions.mapToProtoCoin
import io.provenance.explorer.domain.extensions.mhashToNhash
import io.provenance.explorer.domain.extensions.pack
import io.provenance.explorer.domain.extensions.padToDecStringNew
import io.provenance.explorer.domain.extensions.pageCountOfResults
import io.provenance.explorer.domain.extensions.to256Hash
import io.provenance.explorer.domain.extensions.toBase64
import io.provenance.explorer.domain.extensions.toByteString
import io.provenance.explorer.domain.extensions.toCoinStr
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.extensions.toDecimalString
import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.GovContentV1List
import io.provenance.explorer.domain.models.explorer.GovMsgDetail
import io.provenance.explorer.domain.models.explorer.GovParamType
import io.provenance.explorer.domain.models.explorer.GovProposalMetadata
import io.provenance.explorer.domain.models.explorer.GovVoteMetadata
import io.provenance.explorer.domain.models.explorer.ProposalParamHeights
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.VoteDbRecord
import io.provenance.explorer.domain.models.explorer.VoteDbRecordAgg
import io.provenance.explorer.domain.models.explorer.toCoinStr
import io.provenance.explorer.grpc.extensions.isStandardAddress
import io.provenance.explorer.grpc.extensions.toModuleAccount
import io.provenance.explorer.grpc.extensions.toMsgDeposit
import io.provenance.explorer.grpc.extensions.toMsgDepositOld
import io.provenance.explorer.grpc.extensions.toMsgExecLegacyContent
import io.provenance.explorer.grpc.extensions.toMsgSoftwareUpgrade
import io.provenance.explorer.grpc.extensions.toMsgStoreCode
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposal
import io.provenance.explorer.grpc.extensions.toMsgSubmitProposalOld
import io.provenance.explorer.grpc.extensions.toMsgVote
import io.provenance.explorer.grpc.extensions.toMsgVoteOld
import io.provenance.explorer.grpc.extensions.toMsgVoteWeighted
import io.provenance.explorer.grpc.extensions.toMsgVoteWeightedOld
import io.provenance.explorer.grpc.extensions.toShortType
import io.provenance.explorer.grpc.extensions.toSoftwareUpgradeProposal
import io.provenance.explorer.grpc.v1.GovGrpcClient
import io.provenance.explorer.model.DepositPercentage
import io.provenance.explorer.model.DepositRecord
import io.provenance.explorer.model.GovAddress
import io.provenance.explorer.model.GovDepositRequest
import io.provenance.explorer.model.GovProposalDetail
import io.provenance.explorer.model.GovSubmitProposalRequest
import io.provenance.explorer.model.GovTimeFrame
import io.provenance.explorer.model.GovVoteRequest
import io.provenance.explorer.model.InstantiateContractData
import io.provenance.explorer.model.ParameterChangeData
import io.provenance.explorer.model.ProposalHeader
import io.provenance.explorer.model.ProposalTimings
import io.provenance.explorer.model.ProposalType
import io.provenance.explorer.model.ProposalType.CANCEL_UPGRADE
import io.provenance.explorer.model.ProposalType.INSTANTIATE_CONTRACT
import io.provenance.explorer.model.ProposalType.PARAMETER_CHANGE
import io.provenance.explorer.model.ProposalType.SOFTWARE_UPGRADE
import io.provenance.explorer.model.ProposalType.STORE_CODE
import io.provenance.explorer.model.ProposalType.TEXT
import io.provenance.explorer.model.SoftwareUpgradeData
import io.provenance.explorer.model.StoreCodeData
import io.provenance.explorer.model.Tally
import io.provenance.explorer.model.TallyParams
import io.provenance.explorer.model.VoteRecord
import io.provenance.explorer.model.VotesTally
import io.provenance.explorer.model.VotingDetails
import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.model.base.stringfy
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@Service
class GovService(
    private val govClient: GovGrpcClient,
    private val protoPrinter: JsonFormat.Printer,
    private val valService: ValidatorService,
    private val smService: SmartContractService,
    private val cacheService: CacheService,
    private val accountService: AccountService,
    private val assetService: AssetService
) {
    protected val logger = logger(GovService::class)

    fun buildProposal(proposalId: Long, txInfo: TxData, addr: String, isSubmit: Boolean) = runBlocking {
        govClient.getProposal(proposalId)?.let {
            GovProposalRecord.buildInsert(
                it.proposal,
                protoPrinter,
                txInfo,
                accountService.getAddressDetails(addr),
                isSubmit,
                getParamHeights(it.proposal)
            )
        }
    }

    fun getParamHeights(proposal: Gov.Proposal) =
        proposal.let { prop ->
            val depositTime = if (prop.votingStartTime.toString() == "0001-01-01T00:00:00Z") {
                prop.depositEndTime
            } else {
                prop.votingStartTime
            }
            val votingTime = if (prop.votingEndTime.toString() == "0001-01-01T00:00:00Z") null else prop.votingEndTime

            ProposalParamHeights(
                BlockCacheRecord.getLastBlockBeforeTime(depositTime.toDateTime()),
                BlockCacheRecord.getLastBlockBeforeTime(votingTime?.toDateTime())
            )
        }

    fun updateProposal(record: GovProposalRecord) = transaction {
        runBlocking {
            govClient.getProposal(record.proposalId)?.let { res ->
                if (res.proposal.status.name != record.status) {
                    val paramHeights = getParamHeights(res.proposal)
                    record.apply {
                        this.status = res.proposal.status.name
                        this.dataV1 = res.proposal
                        this.depositParamCheckHeight = paramHeights.depositCheckHeight
                        this.votingParamCheckHeight = paramHeights.votingCheckHeight
                    }
                    ProposalMonitorRecord.getByProposalId(record.proposalId)?.apply {
                        this.votingEndTime = res.proposal.votingEndTime.toDateTime()
                        this.proposedCompletionHeight =
                            calcProposedCompletionHeight(
                                res.proposal.votingEndTime.toDateTime(),
                                record.txTimestamp,
                                this.submittedHeight
                            )
                    }
                }
            } // Assumes it is gone due to no deposit
                ?: record.apply { this.status = Gov.ProposalStatus.PROPOSAL_STATUS_REJECTED.name }
        }
    }

    private fun calcProposedCompletionHeight(
        votingEndTime: DateTime,
        submitTimestamp: DateTime,
        submitHeight: Int
    ): Int {
        val avgBlockTime = cacheService.getAvgBlockTime().multiply(BigDecimal(1000)).toLong()
        val blocksUntilCompletion = (votingEndTime.millis - submitTimestamp.millis).floorDiv(avgBlockTime).toInt()
        return submitHeight + blocksUntilCompletion
    }

    fun buildProposalMonitor(txMsg: Any, proposalId: Long, txInfo: TxData) = runBlocking {
        val contentList = when {
            txMsg.typeUrl.endsWith("gov.v1beta1.MsgSubmitProposal") ->
                listOf(txMsg.toMsgSubmitProposalOld().content)
            txMsg.typeUrl.endsWith("gov.v1.MsgSubmitProposal") ->
                txMsg.toMsgSubmitProposal().messagesList
            else -> return@runBlocking listOf()
        }

        val proposal = govClient.getProposal(proposalId) ?: return@runBlocking listOf()
        contentList.mapNotNull { content ->
            val (proposalType, dataHash) = when {
                content.typeUrl.endsWith("v1beta1.StoreCodeProposal") ->
                    MonitorProposalType.STORE_CODE to
                        // base64(sha256(gzipUncompress(wasmByteCode))) == base64(storedCode.data_hash)
                        content.unpack(Proposal.StoreCodeProposal::class.java)
                            .wasmByteCode.gzipUncompress().to256Hash()
                content.typeUrl.endsWith("v1.StoreCodeProposal") ->
                    MonitorProposalType.STORE_CODE to
                        // base64(sha256(gzipUncompress(wasmByteCode))) == base64(storedCode.data_hash)
                        content.unpack(cosmwasm.wasm.v1.Proposal.StoreCodeProposal::class.java)
                            .wasmByteCode.gzipUncompress().to256Hash()
                content.typeUrl.endsWith("v1.MsgStoreCode") ->
                    MonitorProposalType.STORE_CODE to content.toMsgStoreCode().wasmByteCode.gzipUncompress().to256Hash()
                else -> return@mapNotNull null
            }
            val votingEndTime = proposal.proposal.votingEndTime.toDateTime()

            ProposalMonitorRecord.buildInsert(
                proposalId,
                txInfo.blockHeight,
                calcProposedCompletionHeight(votingEndTime, txInfo.txTimestamp, txInfo.blockHeight),
                votingEndTime,
                proposalType,
                dataHash
            )
        }
    }

    fun processProposal(proposalMon: ProposalMonitorRecord) = transaction {
        when (MonitorProposalType.valueOf(proposalMon.proposalType)) {
            MonitorProposalType.STORE_CODE -> {
                val creationHeight = BlockCacheRecord.getLastBlockBeforeTime(proposalMon.votingEndTime) + 1
                val records = SmCodeRecord.all().sortedByDescending { it.id.value }
                val matching = records.firstOrNull { proposalMon.dataHash == it.dataHash }
                // find existing record and update, else search for next code id only.
                matching?.apply { this.creationHeight = creationHeight }
                    ?.also {
                        proposalMon.apply { this.processed = true }
                        // Insert matching tx join record
                        GovProposalRecord.findByProposalId(proposalMon.proposalId)!!.let { prop ->
                            TxSmCodeRecord.insertIgnore(
                                TxData(prop.blockHeight, prop.txHashId.id.value, prop.txHash, prop.txTimestamp),
                                it.id.value
                            )
                        }
                    }
                    ?: records.first().id.value.let { start ->
                        smService.getSmCodeFromNode(start.toLong() + 1)?.let {
                            if (it.codeInfo.dataHash.toBase64() == proposalMon.dataHash) {
                                SmCodeRecord.getOrInsert(start + 1, it, creationHeight)
                                proposalMon.apply { this.processed = true }
                                // Insert matching tx join record
                                GovProposalRecord.findByProposalId(proposalMon.proposalId)!!.let { prop ->
                                    TxSmCodeRecord.insertIgnore(
                                        TxData(
                                            prop.blockHeight,
                                            prop.txHashId.id.value,
                                            prop.txHash,
                                            prop.txTimestamp
                                        ),
                                        start + 1
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }

    fun buildDeposit(proposalId: Long, txInfo: TxData, deposit: Any?, initial: Any?) =
        transaction {
            val (depositor, depositList) = when {
                initial != null && initial.typeUrl.endsWith("gov.v1beta1.MsgSubmitProposal") ->
                    initial.toMsgSubmitProposalOld().let { it.proposer to it.initialDepositList.toList() }
                initial != null && initial.typeUrl.endsWith("gov.v1.MsgSubmitProposal") ->
                    initial.toMsgSubmitProposal().let { it.proposer to it.initialDepositList.toList() }
                deposit != null && deposit.typeUrl.endsWith("gov.v1beta1.MsgDeposit") ->
                    deposit.toMsgDepositOld().let { it.depositor to it.amountList.toList() }
                deposit != null && deposit.typeUrl.endsWith("gov.v1.MsgDeposit") ->
                    deposit.toMsgDeposit().let { it.depositor to it.amountList.toList() }
                else -> return@transaction null
            }
            val addrInfo = accountService.getAddressDetails(depositor)
            val depositType = if (deposit != null) DepositType.DEPOSIT else DepositType.INITIAL_DEPOSIT
            GovDepositRecord.buildInserts(txInfo, proposalId, depositType, depositList, addrInfo)
        }

    fun buildVote(
        txInfo: TxData,
        votes: List<Gov.WeightedVoteOption>,
        voter: String,
        proposalId: Long,
        justification: String?
    ) =
        GovVoteRecord.buildInsert(txInfo, votes, accountService.getAddressDetails(voter), proposalId, justification)

    private fun getParamsAtHeight(param: GovParamType, height: Int) =
        runBlocking { govClient.getParamsAtHeight(param, height) }

    private fun getParams(param: GovParamType) = runBlocking { govClient.getParams(param) }

    private fun getDepositPercentage(proposalId: Long, depositParamHeight: Int) = transaction {
        val (initial, current) = GovDepositRecord.findByProposalId(proposalId).filter { it.denom == UTILITY_TOKEN }
            .let { list ->
                val current = list.sumOf { it.amount }
                val initial =
                    list.firstOrNull { it.depositType == DepositType.INITIAL_DEPOSIT.name }?.amount ?: BigDecimal.ZERO
                initial to current
            }
        val needed = (
            getParamsAtHeight(GovParamType.deposit, depositParamHeight)
                ?: getParams(GovParamType.deposit)
            ).depositParams.minDepositList.first { it.denom == UTILITY_TOKEN }.amount
        DepositPercentage(initial.stringfy(), current.stringfy(), needed, UTILITY_TOKEN)
    }

    private fun getVotingDetails(proposalId: Long) = transaction {
        val proposal = GovProposalRecord.findByProposalId(proposalId)
            ?: throw ResourceNotFoundException("Invalid proposal id: '$proposalId'")

        val params =
            (
                getParamsAtHeight(GovParamType.tallying, proposal.votingParamCheckHeight)
                    ?: getParams(GovParamType.tallying)
                )
                .tallyParams.let { param ->
                    val eligibleAmount =
                        (
                            if (proposal.votingParamCheckHeight == -1) {
                                cacheService.getSpotlight()!!.latestBlock.height
                            } else {
                                proposal.votingParamCheckHeight
                            }
                            )
                            .let { valService.getValidatorsByHeight(it + 1) }
                            .validatorsList
                            .sumOf { it.votingPower.toBigDecimal() }
                            // Voting power is in mhash, not hash. So pad up to nhash for FE UI conversion
                            .mhashToNhash()

                    TallyParams(
                        CoinStr(eligibleAmount.toPlainString(), UTILITY_TOKEN),
                        param.quorum.toDecimalString(),
                        param.threshold.toDecimalString(),
                        param.vetoThreshold.toDecimalString()
                    )
                }
        val dbRecords = GovVoteRecord.findByProposalId(proposalId)
        val indTallies = dbRecords.groupingBy { it.vote }.eachCount()
        val tallies = runBlocking { govClient.getTally(proposalId)?.tally }
        val zeroStr = 0.toString()
        val tally = VotesTally(
            Tally(
                indTallies.getOrDefault(VoteOption.VOTE_OPTION_YES.name, 0),
                CoinStr(tallies?.yesCount ?: zeroStr, UTILITY_TOKEN)
            ),
            Tally(
                indTallies.getOrDefault(VoteOption.VOTE_OPTION_NO.name, 0),
                CoinStr(tallies?.noCount ?: zeroStr, UTILITY_TOKEN)
            ),
            Tally(
                indTallies.getOrDefault(VoteOption.VOTE_OPTION_NO_WITH_VETO.name, 0),
                CoinStr(tallies?.noWithVetoCount ?: zeroStr, UTILITY_TOKEN)
            ),
            Tally(
                indTallies.getOrDefault(VoteOption.VOTE_OPTION_ABSTAIN.name, 0),
                CoinStr(tallies?.abstainCount ?: zeroStr, UTILITY_TOKEN)
            ),
            Tally(dbRecords.count(), CoinStr(tallies?.sum()?.stringfy() ?: zeroStr, UTILITY_TOKEN))
        )
        VotingDetails(params, tally)
    }

    private fun getAddressObj(addr: String, isValidator: Boolean) = transaction {
        val (operatorAddress, moniker) =
            if (isValidator) {
                ValidatorStateRecord.findByAccount(valService.getActiveSet(), addr)!!
                    .let { it.operatorAddress to it.moniker }
            } else {
                null to null
            }
        GovAddress(addr, operatorAddress, moniker)
    }

    private fun mapProposalRecord(record: GovProposalRecord) = GovProposalDetail(
        ProposalHeader(
            record.proposalId,
            record.status,
            getAddressObj(record.address, record.isValidator),
            record.proposalType,
            record.title,
            record.description,
            record.getContentToPrint(protoPrinter)
        ),
        ProposalTimings(
            getDepositPercentage(record.proposalId, record.depositParamCheckHeight),
            getVotingDetails(record.proposalId),
            record.getDataTimings().submitTime.formattedString(),
            record.getDataTimings().depositEndTime.formattedString(),
            GovTimeFrame(record.getDataTimings().voteStart.formattedString(), record.getDataTimings().voteEnd.formattedString())
        )
    )

    fun getUpgradeProtoType() = softwareUpgradeProposal { }.getType()

    fun getProposalsList(page: Int, count: Int) =
        GovProposalRecord.getAllPaginated(page.toOffset(count), count)
            .let { list ->
                runBlocking {
                    val filtered = list.filter { it.dataV1 == null }
                    if (filtered.isNotEmpty()) {
                        filtered.asFlow().collect { prop ->
                            govClient.getProposal(prop.proposalId).let {
                                transaction {
                                    prop.apply {
                                        this.dataV1 = it?.proposal
                                        this.contentV1 = it?.proposal?.toProposalContent()
                                    }
                                }
                            }
                        }
                        GovProposalRecord.getAllPaginated(page.toOffset(count), count)
                    } else {
                        list
                    }
                }
            }.map {
                mapProposalRecord(it)
            }
            .let {
                val total = GovProposalRecord.getAllCount()
                PagedResults(total.pageCountOfResults(count), it, total)
            }

    fun getProposalDetail(proposalId: Long) = transaction {
        GovProposalRecord.findByProposalId(proposalId)?.let { mapProposalRecord(it) }
            ?: throw ResourceNotFoundException("Invalid proposal id: '$proposalId'")
    }

    fun getProposalVotesPaginated(proposalId: Long, page: Int, count: Int) = transaction {
        GovVoteRecord.findByProposalIdPaginated(proposalId, count, page.toOffset(count))
            .map { mapVoteRecordFromAgg(it) }
            .let {
                val total = GovVoteRecord.findByProposalIdCount(proposalId)
                PagedResults(total.pageCountOfResults(count), it, total)
            }
    }

    fun Gov.TallyResult.sum() =
        this.yesCount.toBigDecimal().plus(this.noCount.toBigDecimal()).plus(this.noWithVetoCount.toBigDecimal())
            .plus(this.abstainCount.toBigDecimal())

    private fun mapVoteRecord(voter: String, records: List<VoteDbRecord>) =
        records[0].let { voteData ->
            VoteRecord(
                getAddressObj(voter, voteData.isValidator),
                records.associate { it.vote to it.weight }.fillVoteSet(),
                voteData.blockHeight,
                voteData.txHash,
                voteData.txTimestamp.toString(),
                voteData.proposalId,
                voteData.proposalTitle,
                voteData.proposalStatus,
                voteData.justification
            )
        }

    private fun mapVoteRecordFromAgg(record: VoteDbRecordAgg) =
        VoteRecord(
            getAddressObj(record.voter, record.isValidator),
            record.voteWeight.associate { it.vote to it.weight }.fillVoteSet(),
            record.blockHeight,
            record.txHash,
            record.txTimestamp.toString(),
            record.proposalId,
            record.proposalTitle,
            record.proposalStatus,
            record.justification
        )

    private fun Map<String, Double>.fillVoteSet() =
        (VoteOption.values().toList() - VoteOption.VOTE_OPTION_UNSPECIFIED - VoteOption.UNRECOGNIZED)
            .associate { it.name to null } + this

    fun getProposalDeposits(proposalId: Long, page: Int, count: Int) = transaction {
        GovDepositRecord.getByProposalIdPaginated(proposalId, count, page.toOffset(count)).map {
            DepositRecord(
                getAddressObj(it.address, it.isValidator),
                it.depositType,
                CoinStr(it.amount.stringfy(), it.denom),
                it.blockHeight,
                it.txHash,
                it.txTimestamp.toString()
            )
        }.let {
            val total = GovDepositRecord.getByProposalIdCount(proposalId)
            PagedResults(total.pageCountOfResults(count), it, total)
        }
    }

    fun getAddressVotes(address: String, page: Int, count: Int) = transaction {
        val addr = AccountRecord.findByAddress(address)
            ?: throw ResourceNotFoundException("Invalid account address: '$address'")
        GovVoteRecord.getByAddrIdPaginated(addr.id.value, count, page.toOffset(count))
            .groupBy { it.proposalId }
            .map { (_, v) ->
                mapVoteRecord(address, v)
            }.let {
                val total = GovVoteRecord.getByAddrIdCount(addr.id.value)
                PagedResults(total.toLong().pageCountOfResults(count), it, total.toLong())
            }
    }

    private fun validateProposal(proposalId: Long, status: List<Gov.ProposalStatus>) =
        GovProposalRecord.findByProposalId(proposalId).let { rec ->
            listOf(
                requireNotNullToMessage(rec) { "Proposal ID $proposalId does not exist." },
                requireToMessage(
                    status.map { it.name }
                        .contains(rec.status)
                ) { "Proposal ID $proposalId is not in the correct status for this action." }
            )
        }

    fun createVote(request: GovVoteRequest): Any {
        validate(
            *validateProposal(
                request.proposalId,
                listOf(Gov.ProposalStatus.PROPOSAL_STATUS_VOTING_PERIOD)
            ).toTypedArray(),
            accountService.validateAddress(request.voter),
            requireToMessage(request.votes.sumOf { it.weight } == 100) { "The sum of all submitted votes must be 100" }
        )
        return when (request.votes.size) {
            0 -> throw InvalidArgumentException("A vote option must be included in the request")
            1 -> msgVote {
                proposalId = request.proposalId
                voter = request.voter
                option = request.votes.first().option
            }.pack()
            else -> msgVoteWeighted {
                proposalId = request.proposalId
                voter = request.voter
                options.addAll(
                    request.votes.filter { it.weight > 0 }.map {
                        weightedVoteOption {
                            option = it.option
                            weight = it.weight.padToDecStringNew()
                        }
                    }
                )
            }.pack()
        }
    }

    fun createDeposit(request: GovDepositRequest): Any {
        validate(
            *validateProposal(
                request.proposalId,
                listOf(
                    Gov.ProposalStatus.PROPOSAL_STATUS_DEPOSIT_PERIOD,
                    Gov.ProposalStatus.PROPOSAL_STATUS_VOTING_PERIOD
                )
            ).toTypedArray(),
            accountService.validateAddress(request.depositor),
            *request.deposit.map { assetService.validateDenom(it.denom) }.toTypedArray(),
            requireToMessage(request.deposit.none { it.amount.toLong() == 0L }) { "At least one deposit must have an amount greater than zero." }
        )
        return msgDeposit {
            proposalId = request.proposalId
            depositor = request.depositor
            amount.addAll(request.deposit.mapToProtoCoin())
        }.pack()
    }

    fun getSupportedProposalTypes() = ProposalType.values().associate { it.name to it.example }

    fun createSubmitProposal(type: ProposalType, request: GovSubmitProposalRequest, wasmFile: MultipartFile?) =
        transaction {
            val prevalidates = mutableListOf(
                accountService.validateAddress(request.submitter),
                *request.initialDeposit.map { assetService.validateDenom(it.denom) }.toTypedArray()
            )
            when (type) {
                TEXT -> textProposal {
                    title = request.title
                    description = request.description
                }.pack()
                CANCEL_UPGRADE -> cancelSoftwareUpgradeProposal {
                    title = request.title
                    description = request.description
                }.pack()
                PARAMETER_CHANGE ->
                    VANILLA_MAPPER.readValue<ParameterChangeData>(request.content).let { content ->
                        parameterChangeProposal {
                            title = request.title
                            description = request.description
                            changes.addAll(
                                content.changes.map {
                                    paramChange {
                                        subspace = it.subspace
                                        key = it.key
                                        value = it.value
                                    }
                                }
                            )
                        }.pack()
                    }
                SOFTWARE_UPGRADE ->
                    VANILLA_MAPPER.readValue<SoftwareUpgradeData>(request.content).let { content ->
                        softwareUpgradeProposal {
                            title = request.title
                            description = request.description
                            plan = plan {
                                name = content.name
                                height = content.height
                                info = content.info
                            }
                        }.pack()
                    }
                STORE_CODE -> {
                    prevalidates.addAll(
                        listOf(
                            requireNotNullToMessage(wasmFile) { "Must have a WASM file submitted for a StoreCode proposal" },
                            requireToMessage(wasmFile.bytes.isWASM()) { "Must have a .wasm file type for a StoreCode proposal" }
                        )
                    )
                    VANILLA_MAPPER.readValue<StoreCodeData>(request.content).let { content ->
                        prevalidates.addAll(
                            listOf(
                                requireToMessage(content.runAs.isStandardAddress()) { "runAs must be a standard address format" },
                                requireToMessage(content.accessConfig?.address?.isStandardAddress() ?: true) { "accessConfig.address must be a standard address format" }
                            )
                        )
                        storeCodeProposal {
                            title = request.title
                            description = request.description
                            runAs = content.runAs
                            wasmByteCode = wasmFile.bytes.gzipCompress().toByteString()
                            content.accessConfig?.let { config ->
                                instantiatePermission = accessConfig {
                                    config.address?.let { this.address = it }
                                    permission = config.type
                                }
                            }
                        }.pack()
                    }
                }
                INSTANTIATE_CONTRACT ->
                    VANILLA_MAPPER.readValue<InstantiateContractData>(request.content).let { content ->
                        prevalidates.addAll(content.funds.map { assetService.validateDenom(it.denom) })
                        prevalidates.addAll(
                            listOf(
                                requireToMessage(content.runAs.isStandardAddress()) { "runAs must be a standard address format" },
                                requireToMessage(content.admin?.isStandardAddress() ?: true) { "admin must be a standard address format" },
                                requireNotNullToMessage(smService.getSmCodeFromNode(content.codeId.toLong())) { "codeId is not valid for instantiation" }
                            )
                        )
                        instantiateContractProposal {
                            title = request.title
                            description = request.description
                            runAs = content.runAs
                            content.admin?.let { this.admin = it }
                            codeId = content.codeId.toLong()
                            content.label?.let { this.label = it }
                            msg = content.msg.toByteArray().toByteString()
                            content.funds.mapToProtoCoin().let { if (it.isNotEmpty()) funds.addAll(it) }
                        }.pack()
                    }
            }.let { msg ->
                validate(*prevalidates.toTypedArray())
                msgSubmitProposal {
                    messages.add(
                        msgExecLegacyContent {
                            content = msg
                            authority = runBlocking { govClient.getGovModuleAccount().account.toModuleAccount().baseAccount.address }
                        }.pack()
                    )
                    proposer = request.submitter
                    request.initialDeposit.mapToProtoCoin().let { if (it.isNotEmpty()) initialDeposit.addAll(it) }
                }.pack()
            }
        }

    fun getVotesPerProposalsMetrics(addrId: Int, startDate: DateTime, endDate: DateTime) = transaction {
        val props = GovProposalRecord.getCompletedProposalsForPeriod(startDate, endDate).map { it.proposalId }
        val votes = GovVoteRecord.getAddressVotesForProposalList(addrId, props)
        votes.size to props.size
    }
}

fun Any.getGovMsgDetail(txHash: String) =
    when {
        typeUrl.endsWith("gov.v1beta1.MsgSubmitProposal")
            || typeUrl.endsWith("gov.v1.MsgSubmitProposal") ->
            transaction {
                val proposalId = GovProposalRecord.findByTxHash(txHash)!!.proposalId
                val deposit = GovDepositRecord.findByTxHash(txHash)!!
                GovMsgDetail(deposit.amount.toCoinStr(deposit.denom), "", proposalId, "")
            }
        typeUrl.endsWith("gov.v1beta1.MsgVote") ->
            this.toMsgVoteOld().let { GovMsgDetail(null, "", it.proposalId, "") }
        typeUrl.endsWith("gov.v1.MsgVote") ->
            this.toMsgVote().let { GovMsgDetail(null, "", it.proposalId, "") }
        typeUrl.endsWith("gov.v1beta1.MsgVoteWeighted") ->
            this.toMsgVoteWeightedOld().let { GovMsgDetail(null, "", it.proposalId, "") }
        typeUrl.endsWith("gov.v1.MsgVoteWeighted") ->
            this.toMsgVoteWeighted().let { GovMsgDetail(null, "", it.proposalId, "") }
        typeUrl.endsWith("gov.v1beta1.MsgDeposit") ->
            this.toMsgDepositOld().let { GovMsgDetail(it.amountList.first().toCoinStr(), "", it.proposalId, "") }
        typeUrl.endsWith("gov.v1.MsgDeposit") ->
            this.toMsgDeposit().let { GovMsgDetail(it.amountList.first().toCoinStr(), "", it.proposalId, "") }
        else -> null.also { logger().debug("This typeUrl is not a governance-based msg: $typeUrl") }
    }?.let { detail ->
        transaction {
            GovProposalRecord.findByProposalId(detail.proposalId)!!.let {
                detail.apply {
                    this.proposalType = it.proposalType
                    this.proposalTitle = it.title
                }
            }
        }
    }

fun Gov.Proposal.toProposalTitleAndDescription(protoPrinter: JsonFormat.Printer) =
    this.messagesList.firstOrNull { it.typeUrl.contains("gov.v1.MsgExecLegacyContent") }
        ?.toMsgExecLegacyContent()?.content?.let { cont ->
            OBJECT_MAPPER.readValue(protoPrinter.print(cont), ObjectNode::class.java)
                .let { it.get("title").asText() to it.get("description").asText() }
        } ?: (
        this.metadata.getProposalMetadata("parsing-title-description")?.let { it.title to it.summary }
            ?: ("Proposal ${this.id}" to "No Description")
        )

fun String.getProposalMetadata(comingFrom: String): GovProposalMetadata? = runBlocking {
    try {
        if (this@getProposalMetadata.isBlank()) return@runBlocking null
        KTOR_CLIENT_JAVA.get(this@getProposalMetadata).body()
    } catch (e: Exception) {
        return@runBlocking null.also { logger().error("Error coming from $comingFrom: ${e.message}") }
    }
}

fun Gov.Proposal.toProposalContent() = GovContentV1List(this.messagesList)

fun Any.getProposalType() = when {
    this.typeUrl.contains("gov.v1.MsgExecLegacyContent") ->
        this.toMsgExecLegacyContent().content.typeUrl.getProposalTypeLegacy()
    else -> this.typeUrl.toShortType()
}

fun String.getProposalTypeLegacy() = this.split(".").last().removeSuffix("Proposal")

fun List<Any>.getProposalTypeList() = this.joinToString(", ") { msg -> msg.getProposalType() }

fun String.toProposalTypeList() = this.split(", ")

fun GovProposalRecord?.getUpgradePlan() =
    this?.contentV1?.list
        ?.mapNotNull { msg ->
            when {
                msg.typeUrl.contains("MsgSoftwareUpgrade") -> msg.toMsgSoftwareUpgrade().plan
                msg.typeUrl.contains("gov.v1.MsgExecLegacyContent") ->
                    msg.toMsgExecLegacyContent().content.let {
                        if (it.typeUrl.contains("SoftwareUpgradeProposal")) {
                            it.toSoftwareUpgradeProposal().plan
                        } else {
                            null
                        }
                    }

                else -> null
            }
        }?.first()
        ?: this?.dataV1beta1?.content?.toSoftwareUpgradeProposal()?.plan

fun String.toVoteMetadata() =
    if (this.isBlank()) {
        null
    } else {
        OBJECT_MAPPER.readValue<GovVoteMetadata>(this).justification
    }

fun Any.toWeightedVoteList() =
    when {
        this.typeUrl.endsWith("gov.v1beta1.MsgVote") ->
            listOf(
                weightedVoteOption {
                    this.weight = "1"
                    this.option = VoteOption.valueOf(this@toWeightedVoteList.toMsgVoteOld().option.name)
                }
            )
        this.typeUrl.endsWith("gov.v1.MsgVote") ->
            listOf(
                weightedVoteOption {
                    this.weight = "1"
                    this.option = this@toWeightedVoteList.toMsgVote().option
                }
            )
        this.typeUrl.endsWith("gov.v1beta1.MsgVoteWeighted") ->
            this.toMsgVoteWeightedOld().optionsList.map {
                weightedVoteOption {
                    this.weight = it.weight
                    this.option = VoteOption.valueOf(it.option.name)
                }
            }.toList()
        this.typeUrl.endsWith("gov.v1.MsgVoteWeighted") -> this.toMsgVoteWeighted().optionsList
        else -> throw InvalidArgumentException("Invalid gov vote msg type: ${this.typeUrl}")
    }
