package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.height
import io.provenance.explorer.domain.models.clients.pb.BlockSingle
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class BlockService(
    private val cacheService: CacheService,
    private val pbClient: PbClient,
    private val tendermintClient: TendermintClient
) {
    protected val logger = logger(BlockService::class)

    protected var chainId: String = ""

    fun getLatestBlockHeightIndex(): Int = cacheService.getBlockIndex()!!.maxHeightRead!!

    fun getBlockchain(maxHeight: Int) = tendermintClient.getBlockchain(maxHeight)

    fun getLatestBlockHeight(): Int = pbClient.getLatestBlock().block.header.height.toInt()

    fun getBlock(blockHeight: Int) =
        cacheService.getBlockByHeight(blockHeight) ?: getBlockchain(blockHeight).result.blockMetas
            .firstOrNull { it.height() == blockHeight }
            ?.let { cacheService.addBlockToCache(it.height(), it.numTxs.toInt(), DateTime.parse(it.header.time), it) }

    fun getTotalSupply(denom: String) = pbClient.getSupplyTotalByDenomination(denom).amount.amount.toBigDecimal()

    fun getChainIdString() =
        if (chainId.isEmpty())
            getBlock(getLatestBlockHeightIndex())!!.header.chainId.also { this.chainId = it }
        else
            this.chainId


    fun getBlockPb(block: String): BlockSingle = pbClient.getBlock(block)
}
