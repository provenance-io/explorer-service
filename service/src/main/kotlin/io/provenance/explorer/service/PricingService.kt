package io.provenance.explorer.service

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.AssetPricingRecord
import io.provenance.explorer.domain.entities.MarkerCacheRecord
import io.provenance.explorer.domain.entities.MarkerCacheTable
import io.provenance.explorer.domain.extensions.toDateTime
import io.provenance.explorer.domain.models.explorer.AssetPricing
import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import io.provenance.explorer.model.base.USD_LOWER
import io.provenance.marker.v1.MarkerStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class PricingService(
    private val props: ExplorerProperties,
    private val tokenService: TokenService,
    private val assetService: AssetService,
    private val flowApiGrpcClient: FlowApiGrpcClient
) {
    protected val logger = logger(PricingService::class)

    private var assetPricinglastRun: OffsetDateTime? = null

    fun updateAssetPricingFromLatestNav() = runBlocking {
        val now = OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC)
        logger.info("Updating asset pricing, last run at: $assetPricinglastRun")

        val latestPrices = flowApiGrpcClient.getLatestNavPrices(
            priceDenom = USD_LOWER,
            includeMarkers = true,
            includeScopes = true,
            fromDate = assetPricinglastRun?.toDateTime(),
            limit = 100000
        )

        latestPrices.forEach { price ->
            if (price.denom != UTILITY_TOKEN) {
                val marker = assetService.getAssetRaw(price.denom)
                insertAssetPricing(
                    marker = marker,
                    markerDenom = price.denom,
                    pricingDenom = price.priceDenom,
                    pricingAmount = BigDecimal(price.priceAmount).setScale(3).divide(BigDecimal(1000)),
                    timestamp = DateTime(price.blockTime * 1000)
                )
            } else {
                // TODO: figure out what this does and finish it in this PR

//                val cmcPrice = tokenService.getTokenLatest()?.quote?.get(USD_UPPER)?.price?.let {
//                    val scale = it.scale()
//                    it.setScale(scale + UTILITY_TOKEN_BASE_DECIMAL_PLACES)
//                        .div(UTILITY_TOKEN_BASE_MULTIPLIER)
//                }
//                val newPriceObj = price.copy(usdPrice = cmcPrice ?: price.usdPrice)
//                val marker = assetService.getAssetRaw(price.markerDenom)
//                insertAssetPricing(
//                    marker = marker,
//                    markerDenom = price.markerDenom,
//                    pricingDenom = price.priceDenom,
//                    pricingAmount = newPriceObj.usdPrice!!,
//                    timestamp = DateTime.now()
//                )
            }
        }
        assetPricinglastRun = now
    }

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

    fun insertAssetPricing(marker: Pair<EntityID<Int>, MarkerCacheRecord>, markerDenom:String, pricingDenom: String, pricingAmount:BigDecimal, timestamp : DateTime) = transaction {
        marker.first.value.let { AssetPricingRecord.upsert(it, markerDenom, pricingDenom, pricingAmount, timestamp) }
    }

    fun getPricingAsync(time: String, comingFrom: String) = runBlocking {
        try {
            KTOR_CLIENT_JAVA.get("${props.pricingUrl}/api/v1/pricing/marker/new") {
                parameter("time", time)
            }.body()
        } catch (e: Exception) {
            return@runBlocking listOf<AssetPricing>()
                .also { logger.error("Error coming from $comingFrom: ${e.message}") }
        }
    }
}
