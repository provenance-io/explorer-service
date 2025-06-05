package io.provenance.explorer.domain.models

import java.math.BigDecimal

data class HistoricalPrice(
    val time: Long,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val open: BigDecimal,
    val volume: BigDecimal,
    val source: String,
    val priceChangePercentage24h: BigDecimal? = null,
)

fun HistoricalPrice.toCsv(): List<String> = listOf(
    "$time",
    "$open",
    "$high",
    "$low",
    "$close",
    "$volume"
)

data class OsmosisHistoricalPrice(
    val time: Long,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val open: BigDecimal,
    val volume: BigDecimal
)

data class OsmosisApiResponse(
    val result: OsmosisResult
)

data class OsmosisResult(
    val data: OsmosisData
)

data class OsmosisData(
    val json: List<OsmosisHistoricalPrice>
)

data class CoinGeckoMarket(
    val current_price: BigDecimal,
    val high_24h: BigDecimal,
    val low_24h: BigDecimal,
    val total_volume: BigDecimal,
    val price_change_24h: BigDecimal,
    val price_change_percentage_24h: BigDecimal,
    val last_updated: String,
)

data class CoinGeckoHistoricalChart(
    val prices: List<List<BigDecimal>>,
    val total_volumes: List<List<BigDecimal>>
)

data class CoinGeckoOHLC(
    val timestamp: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal
)

data class CoinGeckoTotalVolume(
    val timestamp: Long,
    val totalVolume: BigDecimal
)
