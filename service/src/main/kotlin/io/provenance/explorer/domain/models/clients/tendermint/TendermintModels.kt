package io.provenance.explorer.domain.models.clients.tendermint

data class JsonRpc<T>(val jsonrpc: String, val id: Int, val result: T)

//Block Result
data class BlockId(val hash: String, val parts: Parts)

data class Parts(val total: String, val hash: String)

data class BlockHeader(
    val version: Version,
    val chainId: String,
    val height: String,
    val time: String,
    val lastBlockId: BlockId,
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

data class Version(val block: String, val app: String?)

//Blockchain result
data class TendermintBlockchainResponse(val lastHeight: String, val blockMetas: List<BlockMeta>)

data class BlockMeta(val blockId: BlockId, val header: BlockHeader, val blockSize: String, val numTxs: String)
