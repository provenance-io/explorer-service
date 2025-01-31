package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.models.HistoricalPrice
import java.time.LocalDateTime

interface HistoricalPriceFetcher {

    fun getSource(): String
    fun fetchHistoricalPrice(fromDate: LocalDateTime?): List<HistoricalPrice>
}
