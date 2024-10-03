package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.domain.models.HistoricalPrice
import org.joda.time.DateTime

interface HistoricalPriceFetcher {

    fun getSource(): String
    fun fetchHistoricalPrice(fromDate: DateTime?): List<HistoricalPrice>
}
