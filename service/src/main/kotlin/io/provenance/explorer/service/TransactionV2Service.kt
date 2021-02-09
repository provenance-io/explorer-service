package io.provenance.explorer.service

import io.provenance.explorer.client.PbClient
import org.springframework.stereotype.Service

@Service
class TransactionV2Service(
    private val cacheService: CacheService,
    private val pbClient: PbClient
) {

    fun getTxByHash(hash: String) = pbClient.getTxV2(hash)

    fun getSignaturesForTx(hash: String) = getTxByHash(hash).tx.authInfo.signerInfos

}
