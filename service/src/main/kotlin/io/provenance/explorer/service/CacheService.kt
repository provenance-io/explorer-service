package io.provenance.explorer.service

import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.domain.BlockMeta
import io.provenance.explorer.domain.GasStatistics
import io.provenance.explorer.domain.PbDelegations
import io.provenance.explorer.domain.PbStakingValidator
import io.provenance.explorer.domain.PbTransaction
import io.provenance.explorer.domain.PbValidatorsResponse
import io.provenance.explorer.domain.Spotlight
import io.provenance.explorer.domain.TxHistory
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.entities.BlockCacheRecord
import io.provenance.explorer.domain.entities.BlockIndexRecord
import io.provenance.explorer.domain.entities.SpotlightCacheRecord
import io.provenance.explorer.domain.entities.StakingValidatorCacheRecord
import io.provenance.explorer.domain.entities.TransactionCacheRecord
import io.provenance.explorer.domain.entities.ValidatorDelegationCacheRecord
import io.provenance.explorer.domain.entities.ValidatorsCacheRecord
import io.provenance.explorer.domain.entities.updateHitCount
import io.provenance.explorer.domain.extensions.isPastDue
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class CacheService(private val explorerProperties: ExplorerProperties) {

    protected val logger = logger(CacheService::class)

    fun getBlockByHeight(blockHeight: Int) = transaction {
        BlockCacheRecord.findById(blockHeight)?.also {
            BlockCacheRecord.updateHitCount(blockHeight)
        }?.block
    }

    fun addBlockToCache(blockHeight: Int, transactionCount: Int, timestamp: DateTime, blockMeta: BlockMeta) =
            BlockCacheRecord.insertIgnore(blockHeight, transactionCount, timestamp, blockMeta)

    fun getValidatorsByHeight(blockHeight: Int) = transaction {
        ValidatorsCacheRecord.findById(blockHeight)?.also {
            ValidatorsCacheRecord.updateHitCount(blockHeight)
        }?.validators
    }

    fun addValidatorsToCache(blockHeight: Int, json: PbValidatorsResponse) = ValidatorsCacheRecord.insertIgnore(blockHeight, json)

    ///// TRANSACTION CACHE //////////

    fun getTransactionByHash(hash: String) = transaction {
        TransactionCacheRecord.findById(hash)?.also {
            TransactionCacheRecord.updateHitCount(hash)
        }?.tx
    }

    fun addTransactionToCache(pbTransaction: PbTransaction) =
        TransactionCacheRecord.insertIgnore(pbTransaction, explorerProperties.provenanceAccountPrefix())
            .let { pbTransaction }

    fun transactionCount() = transaction {
        TransactionCacheRecord.all().count()
    }

    fun transactionCountForHeight(blockHeight: Int) = transaction {
        TransactionCacheRecord.findByHeight(blockHeight).count()
    }

    fun getTransactionsAtHeight(blockHeight: Int) = transaction {
        TransactionCacheRecord.findByHeight(blockHeight).map { it.tx }
    }

    fun getTransactions(count: Int, offset: Int) = transaction {
        TransactionCacheRecord.getAllWithOffset(SortOrder.DESC, count, offset)
            .map { it.tx }
    }

    fun getBlockIndex() = BlockIndexRecord.getIndex()

    fun updateBlockMaxHeightIndex(maxHeightRead: Int) = BlockIndexRecord.save(maxHeightRead, null)

    fun updateBlockMinHeightIndex(minHeightRead: Int) = BlockIndexRecord.save(null, minHeightRead)

    fun getHistoricalDaysBetweenHeights(maxHeightRead: Int, minHeightRead: Int) = transaction {
        val connection = TransactionManager.current().connection
        val query = "SELECT date_trunc('day', block_timestamp) " +
            "FROM block_cache WHERE height <= ? and height >= ? " +
            " GROUP BY 1 ORDER BY 1 DESC"
        val statement = connection.prepareStatement(query)
        statement.setObject(1, maxHeightRead)
        statement.setObject(2, minHeightRead)
        val resultSet = statement.executeQuery()
        val days = mutableListOf<DateTime>()
        while (resultSet.next()) {
            days.add(DateTime(resultSet.getDate(1)))
        }
        days
    }

    fun getTransactionCountsForDates(startDate: String, endDate: String, granularity: String) = transaction {
        val connection = TransactionManager.current().connection
        val query = """select date_trunc(?, block_timestamp), sum(tx_count) from block_cache 
            |where block_timestamp >= ?::timestamp and block_timestamp <=?::timestamp GROUP BY 1 ORDER BY 1 DESC""".trimMargin()
        val statement = connection.prepareStatement(query)
        statement.setObject(1, if (granularity.contains(granularity)) granularity else "day")
        statement.setObject(2, startDate)
        statement.setObject(3, endDate)
        val resultSet = statement.executeQuery()
        val metrics = mutableListOf<TxHistory>()
        while (resultSet.next()) {
            metrics.add(TxHistory(resultSet.getString(1), resultSet.getInt(2)))
        }
        metrics
    }

    fun getLatestBlockCreationIntervals(limit: Int) = transaction {
        val connection = TransactionManager.current().connection
        val query = "SELECT  height, " +
            "extract(epoch from LAG(block_timestamp) OVER(order by height desc) ) - " +
            "extract(epoch from block_timestamp) as block_creation_time " +
            "FROM block_cache limit ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, limit)
        val resultSet = statement.executeQuery()
        val results = mutableListOf<Pair<Int, BigDecimal?>>()
        while (resultSet.next()) {
            results.add(Pair(resultSet.getInt(1), resultSet.getBigDecimal(2)))
        }
        results
    }

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

    fun getStakingValidator(operatorAddress: String) = transaction {
        StakingValidatorCacheRecord.findById(operatorAddress)?.let {
            if (it.lastHit.millis.isPastDue(explorerProperties.stakingValidatorTtlMs())) {
                it.delete()
                null
            } else it.stakingValidator
        }
    }

    fun addStakingValidatorToCache(operatorAddress: String, stakingValidator: PbStakingValidator) =
        StakingValidatorCacheRecord.insertIgnore(operatorAddress, stakingValidator)

    fun getStakingValidatorDelegations(operatorAddress: String) = transaction {
        ValidatorDelegationCacheRecord.findById(operatorAddress)?.let {
            if (it.lastHit.millis.isPastDue(explorerProperties.stakingValidatorDelegationsTtlMs())) {
                it.delete()
                null
            } else it.validatorDelegations
        }
    }

    fun addStakingValidatorDelegations(operatorAddress: String, delegations: PbDelegations) =
        ValidatorDelegationCacheRecord.insertIgnore(operatorAddress, delegations)

}
