package io.provenance.explorer.service

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_DECIMAL_PLACES
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN_BASE_MULTIPLIER
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AssetPricingRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.MarkerCacheTable
import io.provenance.explorer.domain.extensions.calculateUsdPricePerUnit
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.models.explorer.AssetPricing
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.marker.v1.MarkerStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class PricingService(
    private val props: ExplorerProperties,
    private val tokenService: TokenService,
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
        AssetPricingRecord.findByDenomList(denoms).associate { it.denom to it.pricing }.toMutableMap()
    }

    fun getPricingInfoSingle(denom: String) = AssetPricingRecord.findByDenom(denom)?.pricing
}
