package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.models.HistoricalPrice
import org.joda.time.DateTime

interface HistoricalPriceFetcher {
    fun fetchHistoricalPrice(fromDate: DateTime?): List<HistoricalPrice>
}
