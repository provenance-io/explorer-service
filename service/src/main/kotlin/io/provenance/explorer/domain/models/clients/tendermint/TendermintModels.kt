package io.provenance.explorer.domain.models.clients.tendermint

import io.provenance.explorer.domain.models.clients.PubKey

data class JsonRpc<T>(val jsonrpc: String, val id: Int, val result: T)

//Status Api Models
data class StatusResult(val nodeInfo: NodeInfo?, val syncInfo: SyncInfo, val validatorInfo: ValidatorInfo)

data class NodeInfo(
    val protocolVersion: ProtocolVersion,
    val id: String,
    val listenAddr: String,
    val network: String,
    val version: String,
    val channels: String,
    val moniker: String,
    val other: Other
)

data class Other(val txIndex: String, val rpcAddress: String)

data class ProtocolVersion(val p2p: String, val block: String, val app: String)

data class SyncInfo(
    val latestBlockHash: String,
    val latestAppHash: String,
    val latestBlockHeight: String,
    val latestBlockTime: String,
    val earliestBlockHash: String,
    val earliestAppHash: String,
    val earliestBlockHeight: String,
    val earliestBlockTime: String,
    val catchingUp: Boolean
)

data class ValidatorInfo(val address: String, val pubKey: PubKey, val votingPower: String)


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
