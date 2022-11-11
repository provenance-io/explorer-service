package io.provenance.explorer.domain.models.explorer

import org.joda.time.DateTime
import java.math.BigDecimal

data class TokenSupply(
    val maxSupply: CoinStr,
    val currentSupply: CoinStr,
    val circulation: CoinStr,
    val communityPool: CoinStr,
    val bonded: CoinStr,
    val burned: CoinStr
)

data class TokenDistributionPaginatedResults(
    val ownerAddress: String,
    val data: CountStrTotal
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

data class CmcStatus(
    val timestamp: DateTime,
    val error_code: Int,
    val error_message: String?,
    val elapsed: Long,
    val credit_count: Int,
    val notice: String?
)

data class CmcHistoricalResponse(
    val status: CmcStatus,
    val data: CmcHistoricalData
)

data class CmcHistoricalData(
    val id: Int,
    val name: String,
    val symbol: String,
    val quotes: List<CmcHistoricalQuote>
)

data class CmcHistoricalQuote(
    val time_open: DateTime,
    val time_close: DateTime,
    val time_high: DateTime,
    val time_low: DateTime,
    val quote: Map<String, CmcQuote>
)

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

data class DlobHistBase(
    val buy: List<DlobHistorical>
)

data class DlobHistorical(
    val trade_id: Long,
    val price: BigDecimal,
    val base_volume: Long,
    val target_volume: BigDecimal,
    val trade_timestamp: Long,
    val type: String
)
