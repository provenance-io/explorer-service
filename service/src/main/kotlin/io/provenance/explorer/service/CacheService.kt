package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.SpotlightCacheRecord
import io.provenance.explorer.domain.extensions.isPastDue
import io.provenance.explorer.domain.models.explorer.GasStatistics
import io.provenance.explorer.domain.models.explorer.Spotlight
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service

@Service
class CacheService(private val explorerProperties: ExplorerProperties) {

    protected val logger = logger(CacheService::class)

    fun getGasStatistics(startDate: String, endDate: String, granularity: String) = transaction {
        val connection = TransactionManager.current().connection
        val query = "SELECT date_trunc(?, tx_timestamp), tx_type, min(gas_used), max(gas_used), avg(gas_used) " +
            "FROM transaction_cache where tx_timestamp >= ?::timestamp and tx_timestamp <=?::timestamp " +
            "GROUP BY 1, 2 ORDER BY 1 DESC"
        val statement = connection.prepareStatement(query)
        statement.setObject(1, if (granularity.contains(granularity)) granularity else "day")
        statement.setObject(2, startDate)
        statement.setObject(3, endDate)
        val resultSet = statement.executeQuery()
        val results = mutableListOf<GasStatistics>()
        while (resultSet.next()) {
            results.add(
                GasStatistics(
                    resultSet.getString(1),
                    resultSet.getString(2),
                    resultSet.getLong(3),
                    resultSet.getLong(4),
                    resultSet.getBigDecimal(5)))
        }
        results
    }

    fun addSpotlightToCache(spotlightResponse: Spotlight) = SpotlightCacheRecord.insertIgnore(spotlightResponse).spotlight

    fun getSpotlight() = transaction {
        SpotlightCacheRecord.getIndex()?.let {
            if (it.lastHit.millis.isPastDue(explorerProperties.spotlightTtlMs())) {
                it.delete()
                null
            } else it.spotlight
        }
    }

}
