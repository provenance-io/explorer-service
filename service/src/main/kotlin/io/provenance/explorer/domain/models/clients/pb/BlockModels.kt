package io.provenance.explorer.domain.models.clients.pb


data class BlockSingle(val blockId: BlockIdV2, val block: Block)

data class BlockIdV2(val hash: String, val partSetHeader: BlockPartSetHeader)

data class BlockPartSetHeader(val total: Int, val hash: String)

data class Block(
    val header: BlockHeaderV2,
    val data: BlockData,
    val evidence: BlockEvidence,
    val lastCommit: BlockLastCommit
)

data class BlockHeaderV2(
    val version: BlockHeaderVersion,
    val chainId: String,
    val height: String,
    val time: String,
    val lastBlockId: BlockIdV2,
    val lastCommitHash: String,
    val dataHash: String,
    val validatorsHash: String,
    val nextValidatorsHash: String,
    val consensusHash: String,
    val appHash: String,
    val lastResultsHash: String,
    val evidenceHash: String,
    val proposerAddress: String
)

data class BlockHeaderVersion(val block: String, val app: String)

data class BlockData(val txs: List<String>)

data class BlockEvidence(val evidence: List<String>)

data class BlockLastCommit(
    val height: String,
    val round: Int,
    val blockId: BlockIdV2,
    val signatures: List<BlockLastCommitSignature>
)

data class BlockLastCommitSignature(
    val blockIdFlag: String,
    val validatorAddress: String,
    val timestamp: String,
    val signature: String
)
