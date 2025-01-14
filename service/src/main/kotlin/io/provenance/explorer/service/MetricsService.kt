package io.provenance.explorer.service

import io.provenance.explorer.config.ResourceNotFoundException
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.BlockTxCountsCacheRecord
import io.provenance.explorer.domain.entities.ValidatorMetricsRecord
import io.provenance.explorer.domain.extensions.monthToQuarter
import io.provenance.explorer.domain.extensions.validatorMissedBlocksSpecific
import io.provenance.explorer.domain.models.explorer.BlockTimeSpread
import io.provenance.explorer.domain.models.explorer.CurrentValidatorState
import io.provenance.explorer.domain.models.explorer.download.ValidatorMetricsRequest
import io.provenance.explorer.model.MetricPeriod
import io.provenance.explorer.model.ValidatorMetrics
import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.model.base.CountTotal
import jakarta.servlet.ServletOutputStream
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.springframework.stereotype.Service
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class MetricsService(
    private val valService: ValidatorService,
    private val govService: GovService
) {

    fun getQuarters(address: String) = transaction {
        valService.getValidatorOperatorAddress(address)?.let { vali ->
            val (year, quarter) = DateTime.now().let { it.year to it.monthOfYear.monthToQuarter() }
            ValidatorMetricsRecord.findByOperAddr(vali.operatorAddrId)
                .map {
                    val label = it.toMetricPeriodLabel(it.year == year && it.quarter == quarter)
                    MetricPeriod(label, it.year, it.quarter)
                }
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")
    }

    fun getAllQuarters() = transaction {
        val (year, quarter) = DateTime.now().let { it.year to it.monthOfYear.monthToQuarter() }
        ValidatorMetricsRecord.findDistinctPeriods(year, quarter)
    }

    fun getValidatorMetrics(address: String, year: Int, quarter: Int) = transaction {
        valService.getValidatorOperatorAddress(address)?.let {
            ValidatorMetricsRecord.findByOperAddrForPeriod(it.operatorAddrId, year, quarter)?.data
                ?: (
                    BlockTxCountsCacheRecord.getBlockTimeSpread(year, quarter)
                        ?.let { spread -> processMetricsForValObjectAndSpread(it, spread) }
                        ?: throw ResourceNotFoundException("No data found for quarter $year Q$quarter")
                    )
        } ?: throw ResourceNotFoundException("Invalid validator address: '$address'")
    }

    fun processMetricsForValObjectAndSpread(vali: CurrentValidatorState, spread: BlockTimeSpread) = transaction {
        val account = AccountRecord.findByAddress(vali.accountAddr)
            ?: throw ResourceNotFoundException("Invalid account address: '${vali.accountAddr}'")
        ValidatorMetrics(
            vali.operatorAddress,
            vali.moniker,
            spread.year,
            spread.quarter,
            vali.currentState == ValidatorState.ACTIVE,
            valService.isVerified(vali.accountAddr),
            govService.getVotesPerProposalsMetrics(
                account.id.value,
                spread.minTimestamp,
                spread.maxTimestamp
            ).toCountTotal(),
            vali.consensusAddr.validatorMissedBlocksSpecific(spread.minHeight, spread.maxHeight)
                .uptimeToCountTotal(spread.totalBlocks)
        )
    }

    fun downloadQuarterMetrics(filters: ValidatorMetricsRequest, resp: ServletOutputStream): ZipOutputStream {
        val file = filters.getFile()
        val zos = ZipOutputStream(resp)
        zos.putNextEntry(ZipEntry("${filters.getFilenameBase()} - ${file.fileName}.csv"))
        zos.write(file.writeCsvEntry())
        zos.closeEntry()
        zos.close()
        return zos
    }
}

fun Pair<Int, Int>.toCountTotal() = CountTotal(this.first.toBigInteger(), this.second.toBigInteger())
fun Int.uptimeToCountTotal(total: Int) = CountTotal((total - this).toBigInteger(), total.toBigInteger())
