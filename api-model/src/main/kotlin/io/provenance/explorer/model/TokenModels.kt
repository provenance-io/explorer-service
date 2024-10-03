package io.provenance.explorer.model

import io.provenance.explorer.model.base.CoinStr
import io.provenance.explorer.model.base.DateTruncGranularity
import io.provenance.explorer.model.base.USD_UPPER
import io.provenance.explorer.model.download.currFormat
import io.provenance.explorer.model.download.customFormat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import java.math.BigDecimal

data class TokenSupply(
    val maxSupply: CoinStr,
    val currentSupply: CoinStr,
    val circulation: CoinStr,
    val communityPool: CoinStr,
    val bonded: CoinStr,
    val burned: CoinStr
)

data class TokenDistributionAmount(
    val denom: String,
    val amount: String
)

data class TokenDistribution(
    val range: String,
    val amount: TokenDistributionAmount,
    val percent: String
)

data class RichAccount(
    val address: String,
    val amount: CoinStr,
    val percentage: String
)

data class CmcHistoricalQuote(
    val time_open: DateTime,
    val time_close: DateTime,
    val time_high: DateTime,
    val time_low: DateTime,
    val quote: Map<String, CmcQuote>,
) {
    fun toCsv(): MutableList<Any> =
        this.quote[USD_UPPER]!!.let {
            mutableListOf(
                it.timestamp.withZone(DateTimeZone.UTC).customFormat(DateTruncGranularity.DAY),
                it.open,
                it.high,
                it.low,
                it.close,
                it.volume.currFormat()
            )
        }
}

data class CmcQuote(
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
    val market_cap: BigDecimal,
    val timestamp: DateTime
)

data class CmcLatestDataAbbrev(
    val last_updated: DateTime,
    val quote: Map<String, CmcLatestQuoteAbbrev>
)

data class CmcLatestQuoteAbbrev(
    val price: BigDecimal,
    val percent_change_24h: BigDecimal,
    val volume_24h: BigDecimal,
    val market_cap_by_total_supply: BigDecimal?,
    val last_updated: DateTime
)
