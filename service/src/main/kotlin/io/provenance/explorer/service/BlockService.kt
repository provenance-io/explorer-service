package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import io.provenance.explorer.client.TendermintClient
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.height
import org.joda.time.DateTime
import org.springframework.stereotype.Service

@Service
class BlockService(
    private val cacheService: CacheService,
    private val pbClient: PbClient,
    private val tendermintClient: TendermintClient
) {

    protected val logger = logger(BlockService::class)

    fun getLatestBlockHeightIndex(): Int = cacheService.getBlockIndex()!!.maxHeightRead!!

    fun getBlockchain(maxHeight: Int) = tendermintClient.getBlockchain(maxHeight)

    fun getLatestBlockHeight(): Int = getStatus().syncInfo.latestBlockHeight.toInt()

    private fun getStatus() = tendermintClient.getStatus().result

    fun getBlock(blockHeight: Int) =
        cacheService.getBlockByHeight(blockHeight) ?: getBlockchain(blockHeight).result.blockMetas
            .firstOrNull { it.height() == blockHeight }
            ?.let { cacheService.addBlockToCache(it.height(), it.numTxs.toInt(), DateTime.parse(it.header.time), it) }

    fun getTotalSupply(denom: String) = pbClient.getSupplyTotalByDenomination(denom)
}
