package io.provenance.explorer.service

import io.ktor.client.features.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AssetPricingRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.MarkerCacheTable
import io.provenance.explorer.domain.models.explorer.AssetPricing
import io.provenance.marker.v1.MarkerStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class PricingService(
    private val props: ExplorerProperties,
    private val tokenService: TokenService
) {
    protected val logger = logger(PricingService::class)

    fun getTotalAum() = runBlocking {
        val baseMap = transaction {
            MarkerCacheRecord.find {
                (MarkerCacheTable.status eq MarkerStatus.MARKER_STATUS_ACTIVE.name) and
                    (MarkerCacheTable.supply greater BigDecimal.ZERO)
            }.associate { it.denom to (if (it.denom != UTILITY_TOKEN) it.supply else tokenService.totalSupply()) }
        }
        val pricing = baseMap.keys.toList().chunked(100) { getPricingInfo(it, "totalAUM") }.flatMap { it.toList() }
            .toMap()
            .toMutableMap()
        baseMap.map { (k, v) -> (pricing[k] ?: BigDecimal.ZERO).multiply(v) }.sumOf { it }
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
        AssetPricingRecord.findByDenomList(denoms).associate { it.denom to it.pricing.toBigDecimal() }.toMutableMap()
    }

    fun getPricingInfoSingle(denom: String) = AssetPricingRecord.findByDenom(denom)?.pricing?.toBigDecimal()

    fun insertAssetPricing(marker: Pair<EntityID<Int>, MarkerCacheRecord>, data: AssetPricing) = transaction {
        marker.first.value.let { AssetPricingRecord.upsert(it, data) }
    }

    fun getPricingAsync(time: String, comingFrom: String) = runBlocking {
        try {
            KTOR_CLIENT_JAVA.get("${props.pricingUrl}/api/v1/pricing/marker/new") {
                parameter("time", time)
            }
        } catch (e: ResponseException) {
            return@runBlocking listOf<AssetPricing>()
                .also { logger.error("Error coming from $comingFrom: ${e.response}") }
        }
    }
}
