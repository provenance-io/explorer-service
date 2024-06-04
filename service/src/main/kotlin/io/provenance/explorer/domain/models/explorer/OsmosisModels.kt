package io.provenance.explorer.domain.models

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.math.BigDecimal

@JsonDeserialize(using = OsmosisHistoricalPriceDeserializer::class)
data class OsmosisHistoricalPrice(
    val time: DateTime,
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


class OsmosisHistoricalPriceDeserializer : JsonDeserializer<OsmosisHistoricalPrice>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OsmosisHistoricalPrice {
        val node = p.codec.readTree<com.fasterxml.jackson.databind.node.ObjectNode>(p)

        val timeLong = node.get("time").asLong()
        val time = DateTime(timeLong * 1000, DateTimeZone.UTC)  // Convert to DateTime in UTC

        val high = node.get("high").decimalValue()
        val low = node.get("low").decimalValue()
        val close = node.get("close").decimalValue()
        val open = node.get("open").decimalValue()
        val volume = node.get("volume").decimalValue()

        return OsmosisHistoricalPrice(
            time = time,
            high = high,
            low = low,
            close = close,
            open = open,
            volume = volume
        )
    }
}
