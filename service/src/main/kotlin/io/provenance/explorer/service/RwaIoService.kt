package io.provenance.explorer.service

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.provenance.explorer.config.rwa.RwaIoProperties
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.models.explorer.pulse.MetricRangeType
import io.provenance.explorer.domain.models.explorer.pulse.ProjectInfo
import io.provenance.explorer.domain.models.explorer.pulse.TimeSeriesData
import io.provenance.explorer.domain.models.explorer.pulse.TimeSeriesRecord
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Service handler for third party rwa.io
 */
@Service
class RwaIoService(
    private val pulseMetricService: PulseMetricService,
    private val rwaIoProperties: RwaIoProperties,
    @Qualifier("rwaIoHttpClient") private val rwaIoHttpClient: HttpClient,
) {
    protected val logger = logger(RwaIoService::class)

    fun addProjectTimeSeriesData() = runBlocking {
        val url = "${rwaIoProperties.baseUrl}/project-time-series/data/add?slug=${rwaIoProperties.slug}"
        ProjectInfo.entries.filter { it.metric != null }.forEach { projectMetric ->
            logger.info("Adding project time series from $projectMetric")
            val timeSeriesData = TimeSeriesData(
                tsId = projectMetric.id,
                records = listOf(
                    TimeSeriesRecord(
                        timestamp = System.currentTimeMillis(),
                        value = pulseMetricService.pulseMetric(
                            MetricRangeType.DAY, projectMetric.metric!!
                        ).amount.toString(),
                    )
                )
            )
            sendData(url, timeSeriesData)
        }

        logger.info("Finished adding project time series")
    }

    fun addTokenizedAssetSeriesData() = runBlocking {
        logger.info("Adding tokenized asset time series")
        val assetId = "ASSET_ID_FROM_DASHBOARD"
        val url = "${rwaIoProperties.baseUrl}/tokenized-asset-time-series/data/add?assetId=$assetId"
        val timeSeriesData = TimeSeriesData("", emptyList())
        sendData(url, timeSeriesData)
        logger.info("Finished adding tokenized asset time series")
    }

    private fun sendData(url: String, data: TimeSeriesData) = runBlocking {
        rwaIoHttpClient.post(url) {
            headers {
                append("x-api-key", rwaIoProperties.apiKey)
                append("Accept", "application/json")
            }

            setBody(data)
        }
    }
}
/*
// info for hash or all prov assets?
https://api.rwa.io/project-time-series/info?slug=provenance-blockchn
// adding info for hash or all prov assets
https://api.rwa.io/project-time-series/data/add?slug=provenance-blockchn

// fetch info for specified asset
https://api.rwa.io/tokenized-asset-time-series/info?assetId=123456789
// adding data to a specific asset on prov
https://api.rwa.io/tokenized-asset-time-series/data/add?assetId=123456789
 */
