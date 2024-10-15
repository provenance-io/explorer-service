package io.provenance.explorer.grpc.flow

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.core.logger
import io.provlabs.flow.api.NavEvent
import io.provlabs.flow.api.NavEventRequest
import io.provlabs.flow.api.NavEventResponse
import io.provlabs.flow.api.NavServiceGrpc
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class FlowApiGrpcClient(flowApiChannelUri: URI) {

    private val navService: NavServiceGrpc.NavServiceBlockingStub

    init {
        val channel =
            ManagedChannelBuilder.forAddress(flowApiChannelUri.host, flowApiChannelUri.port)
                .also {
                    if (flowApiChannelUri.scheme == "grpcs") {
                        it.useTransportSecurity()
                    } else {
                        it.usePlaintext()
                    }
                }
                .idleTimeout(60, TimeUnit.SECONDS)
                .keepAliveTime(300, TimeUnit.SECONDS)
                .keepAliveTimeout(300, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        navService = NavServiceGrpc.newBlockingStub(channel)
    }

    fun getMarkerNavByPriceDenoms(denom: String, priceDenoms: List<String>, fromDate: DateTime?, limit: Int = 100): List<NavEvent> = runBlocking {
        val fromDateString = fromDate?.toString(DateTimeFormat.forPattern("yyyy-MM-dd")) ?: ""
        val requestBuilder = NavEventRequest.newBuilder()
            .setDenom(denom)
            .setFromDate(fromDateString)
            .setLimit(limit)

        priceDenoms.forEach { requestBuilder.addPriceDenoms(it) }
        val request = requestBuilder.build()
        try {
            logger().debug("getMarkerNavByPriceDenoms $request")
            val response: NavEventResponse = navService.getNavEvents(request)
            return@runBlocking response.navEventsList
        } catch (e: Exception) {
            logger().error("Error fetching Nav Events: ${e.message}", e)
            emptyList()
        }
    }
}
