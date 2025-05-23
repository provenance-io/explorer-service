package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AssetPricingRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.MarkerCacheTable
import io.provenance.marker.v1.MarkerStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PricingService(
    private val tokenService: TokenService,
    private val nftService: NftService,
) {
    protected val logger = logger(PricingService::class)

    fun getTotalAum() = runBlocking {
        val baseMap = transaction {
            MarkerCacheRecord.find {
                (MarkerCacheTable.status eq MarkerStatus.MARKER_STATUS_ACTIVE.name) and
                    (MarkerCacheTable.supply greater BigDecimal.ZERO)
            }.filterNot {
                // excluding portfolio manager pools in favor of scope navs
                // we may be able to remove this after scope data migration
                it.denom.startsWith("pm.")
            }.associate { it.denom to (if (it.denom != UTILITY_TOKEN) it.supply else tokenService.totalSupply()) }
        }

        val assetPricing = baseMap.keys.toList().chunked(100) { getPricingInfo(it, "totalAUM") }.flatMap { it.toList() }
            .toMap()
            .toMutableMap()

        val assetsTotalAum = baseMap.map { (k, v) -> (assetPricing[k] ?: BigDecimal.ZERO).multiply(v) }.sumOf { it }
        val nftsTotalAum = nftService.getScopeTotalForNavEvents()

        assetsTotalAum.add(nftsTotalAum)
    }

    fun getAumForList(denoms: Map<String, String>, comingFrom: String): BigDecimal {
        val pricing =
            denoms.keys.toList().chunked(100) { getPricingInfo(it, comingFrom) }.flatMap { it.toList() }.toMap()
                .toMutableMap()
        return denoms.map { (k, v) -> (pricing[k] ?: BigDecimal.ZERO).multiply(v.toBigDecimal()) }.sumOf { it }
    }

    fun getPricingInfoIn(denoms: List<String>, comingFrom: String) = getPricingInfo(denoms, comingFrom)

    fun getPricingInfo(denoms: List<String>, comingFrom: String): MutableMap<String, BigDecimal?> = runBlocking {
        if (denoms.isEmpty()) return@runBlocking mutableMapOf<String, BigDecimal?>()
        AssetPricingRecord.findByDenomList(denoms).associate { it.denom to it.pricing }.toMutableMap()
    }

    fun getPricingInfoSingle(denom: String) = AssetPricingRecord.findByDenom(denom)?.pricing
}
