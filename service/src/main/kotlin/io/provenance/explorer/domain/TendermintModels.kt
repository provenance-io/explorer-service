package io.provenance.explorer.domain

import com.fasterxml.jackson.annotation.JsonProperty


//Status Api Models
data class StatusResult(val nodeInfo: NodeInfo?, val syncInfo: SyncInfo, val validatorInfo: ValidatorInfo)

data class NodeInfo(val protocolVersion: ProtocolVersion, val id: String, val listenAddr: String, val network: String, val version: String, val channels: String, val moniker: String, val other: Other)

data class Other(val txIndex: String, val rpcAddress: String)

data class ProtocolVersion(val p2p: String, val block: String, val app: String)

data class SyncInfo(val latestBlockHash: String, val latestAppHash: String, val latestBlockHeight: String, val latestBlockTime: String, val earliestBlockHash: String, val earliestAppHash: String, val earliestBlockHeight: String, val earliestBlockTime: String, val catchingUp: Boolean)

data class PubKey(val type: String, val value: String)

data class ValidatorInfo(val address: String, val pubKey: PubKey, val votingPower: String)


//Search Transaction Api Models

data class TXSearchResult(val txs: List<Transaction>, val totalCount: String)

data class Transaction(val hash: String, val height: String, val index: Int, val txResult: TxResult, val tx: String)

//data class TXResult(val data: String?, val log: String, val info: String, @JsonPropertyJsonProperty("gasWanted") val gasWanted: String, @JsonProperty("gasUsed") val gasUsed: String, val codespace: String)
data class TxResult(val code: Int, val data: String?, val log: String, val info: String, @JsonProperty("gasWanted") val gasWanted: String, @JsonProperty("gasUsed") val gasUsed: String, val codespace: String, val events: List<TxEvent>)

data class TxEvent(val type: String, val attributes: List<TxEvenAttribute>)

data class TxEvenAttribute(val key: String, val value: String)

data class TxLog(val msgIndex: Int, val log: String, val events: List<TxEvent>)

//Block Result
data class BlockResponse(val blockId: BlockId, val block: Block)

data class BlockId(val hash: String, val parts: Parts)

data class Parts(val total: String, val hash: String)

data class Block(val header: BlockHeader, val data: BlockData, val evidence: Evidence?, val lastCommit: LastCommit)

data class BlockHeader(val version: Version, val chainId: String, val height: String, val time: String, val lastBlockId: BlockId, val lastCommitHash: String, val dataHash: String, val validatorsHash: String, val nextValidatorsHash: String, val consensusHash: String, val appHash: String, val lastResultsHash: String, val evidenceHash: String, val proposerAddress: String)

data class Version(val block: String, val app: String)

data class BlockData(val txs: List<String>?)

data class LastCommit(val height: String, val round: String, val blockId: BlockId, val signatures: List<Signature>)

data class Signature(val blockIdFlag: Int, val validatorAddress: String, val timestamp: String, val signature: String?)

data class Evidence(val evidence: EvidenceDetail?)

data class EvidenceDetail(val type: String?, val height: Long, val time: Int, val totalVotingPower: Int, val validator: Validator)

//validator
data class ValidatorsResponse(val blockHeight: String, val validators: List<Validator>, val count: String, val total: String)

data class Validator(val address: String, val pubKey: PubKey, val votingPower: String, val proposerPriority: String)

//Blockchain result
data class Blockchain(val lastHeight: String, val blockMetas: List<BlockMeta>)

data class BlockMeta(val blockId: BlockId, val header: BlockHeader, val numTxs: String)
