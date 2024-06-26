package io.provenance.explorer.domain.models

import java.math.BigDecimal

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
