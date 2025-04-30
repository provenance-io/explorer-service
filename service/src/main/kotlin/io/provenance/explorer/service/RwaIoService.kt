package io.provenance.explorer.service

import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.provenance.explorer.KTOR_CLIENT_JAVA
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.rwaio.RwaIoProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.extensions.startOfDay
import io.provenance.explorer.domain.models.explorer.rwaio.ProjectInfo
import io.provenance.explorer.domain.models.explorer.rwaio.TimeSeriesData
import io.provenance.explorer.domain.models.explorer.rwaio.TimeSeriesInterval
import io.provenance.explorer.domain.models.explorer.rwaio.TimeSeriesRecord
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Service handler for third party rwa.io
 */
@Service
class RwaIoService(
    private val explorerProperties: ExplorerProperties,
    private val pulseMetricService: PulseMetricService,
    private val rwaIoProperties: RwaIoProperties
) {
    protected val logger = logger(RwaIoService::class)

    fun addProjectTimeSeriesData(interval: TimeSeriesInterval) = runBlocking {
        val localDateTimeNow = LocalDateTime.now(ZoneOffset.UTC)
        val (pulseDate, timestamp) = when (interval) {
            // using previous day for daily interval calcs
            TimeSeriesInterval.DAILY -> localDateTimeNow.minusDays(1).let {
                it.toLocalDate() to it.startOfDay().atOffset(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
            else -> localDateTimeNow.toLocalDate() to Instant.now().truncatedTo(ChronoUnit.HOURS).toEpochMilli()
        }

        ProjectInfo.entries.filter { it.interval == interval }.forEach { projectMetric ->
            logger.info("Adding project time series for $projectMetric")
            val pulseMetric = pulseMetricService.fromPulseMetricCache(pulseDate, projectMetric.metric)
            when (projectMetric) {
                ProjectInfo.DAILY_TRANSACTIONS_COUNT -> pulseMetric?.trend?.changeQuantity
                else -> pulseMetric?.amount
            }?.let { pulseValue ->
                val tsId = when (explorerProperties.mainnet.toBooleanStrictOrNull()) {
                    true -> projectMetric.mainnetId
                    else -> projectMetric.testnetId
                }

                val timeSeriesData = TimeSeriesData(
                    tsId = tsId,
                    records = listOf(
                        TimeSeriesRecord(
                            timestamp = timestamp,
                            value = pulseValue.toString(),
                        )
                    )
                )

                val result = sendData("project-time-series/data/add?slug=provenance-blockchn", timeSeriesData)
                if (result.status == HttpStatusCode.OK)
                    logger.info("Finished adding project time series metric: $projectMetric")
                else
                    logger.error("Unable to add project time series metric $projectMetric; ${result.bodyAsText()}")
            }
        }
    }

    /*
    // TODO manage list of tokenized asset ids and push time series data for each
    fun addTokenizedAssetSeriesData() = runBlocking {
        val assetId = ""
        val timeSeriesData = TimeSeriesData("", emptyList())
        sendData("tokenized-asset-time-series/data/add?assetId=$assetId", timeSeriesData)
    }
     */

    private fun sendData(path: String, data: TimeSeriesData) = runBlocking {
        KTOR_CLIENT_JAVA.post("https://api.rwa.io/$path") {
            headers {
                append("x-api-key", rwaIoProperties.apiKey)
                append("Accept", "application/json")
            }
            contentType(ContentType.Application.Json)
            setBody(data)
        }.body<HttpResponse>()
    }
}
