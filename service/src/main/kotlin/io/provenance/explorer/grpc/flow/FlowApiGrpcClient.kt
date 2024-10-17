package io.provenance.explorer.grpc.flow

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.core.logger
import io.provlabs.flow.api.LatestNavEventRequest
import io.provlabs.flow.api.NavEvent
import io.provlabs.flow.api.NavEventRequest
import io.provlabs.flow.api.NavEventResponse
import io.provlabs.flow.api.NavServiceGrpc
import io.provlabs.flow.api.PaginationRequest
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
                .keepAliveTime(15, TimeUnit.SECONDS)
                .keepAliveTimeout(15, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        navService = NavServiceGrpc.newBlockingStub(channel)
    }

    fun getMarkerNavByPriceDenoms(denom: String, priceDenoms: List<String>, fromDate: DateTime?, pageCount: Int = 100, page: Int = 0): List<NavEvent> = runBlocking {
        val fromDateString = fromDate?.toString(DateTimeFormat.forPattern("yyyy-MM-dd")) ?: ""
        val pagination = PaginationRequest.newBuilder().setPage(page).setPageSize(pageCount).build()
        val requestBuilder = NavEventRequest.newBuilder()
            .setDenom(denom)
            .setFromDate(fromDateString)
            .setPagination(pagination)

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

    fun getAllMarkerNavByPriceDenoms(denom: String, priceDenoms: List<String>, fromDate: DateTime?, requestSize: Int = 10000): List<NavEvent> = runBlocking {
        val fromDateString = fromDate?.toString(DateTimeFormat.forPattern("yyyy-MM-dd")) ?: ""
        val allNavEvents = mutableListOf<NavEvent>()
        var currentPage = 0
        var hasMorePages = true

        while (hasMorePages) {
            val pagination = PaginationRequest.newBuilder().setPage(currentPage).setPageSize(requestSize).build()
            val requestBuilder = NavEventRequest.newBuilder()
                .setDenom(denom)
                .setFromDate(fromDateString)
                .setPagination(pagination)

            priceDenoms.forEach { requestBuilder.addPriceDenoms(it) }
            val request = requestBuilder.build()

            try {
                logger().debug("getAllMarkerNavByPriceDenoms $request")
                val response: NavEventResponse = navService.getNavEvents(request)
                allNavEvents.addAll(response.navEventsList)

                if (response.pagination.currentPage >= response.pagination.totalPages) {
                    hasMorePages = false
                } else {
                    currentPage++
                }
            } catch (e: Exception) {
                logger().error("Error fetching Nav Events: ${e.message}", e)
                hasMorePages = false
            }
        }

        return@runBlocking allNavEvents
    }

    fun getLatestNavPrices(priceDenom: String, includeMarkers: Boolean = true, includeScopes: Boolean = true, fromDate: DateTime?, pageCount: Int = 100, page: Int = 0): List<NavEvent> = runBlocking {
        val fromDateString = fromDate?.toString(DateTimeFormat.forPattern("yyyy-MM-dd")) ?: ""
        val pagination = PaginationRequest.newBuilder().setPage(page).setPageSize(pageCount).build()
        val request = LatestNavEventRequest.newBuilder()
            .setPriceDenom(priceDenom)
            .setFromDate(fromDateString)
            .setIncludeMarkers(includeMarkers)
            .setIncludeScope(includeScopes)
            .setPagination(pagination)
            .build()
        try {
            logger().debug("getLatestNavEvents $request")
            val response: NavEventResponse = navService.getLatestNavEvents(request)
            return@runBlocking response.navEventsList
        } catch (e: Exception) {
            logger().error("Error fetching latest Nav Events: ${e.message}", e)
            emptyList()
        }
    }

    fun getAllLatestNavPrices(priceDenom: String, includeMarkers: Boolean = true, includeScopes: Boolean = true, fromDate: DateTime?, requestSize: Int = 10000): List<NavEvent> = runBlocking {
        val fromDateString = fromDate?.toString(DateTimeFormat.forPattern("yyyy-MM-dd")) ?: ""
        var currentPage = 0
        var totalPages = 1
        val allNavEvents = mutableListOf<NavEvent>()

        try {
            while (currentPage < totalPages) {
                val pagination = PaginationRequest.newBuilder().setPage(currentPage).setPageSize(requestSize).build()
                val request = LatestNavEventRequest.newBuilder()
                    .setPriceDenom(priceDenom)
                    .setFromDate(fromDateString)
                    .setIncludeMarkers(includeMarkers)
                    .setIncludeScope(includeScopes)
                    .setPagination(pagination)
                    .build()

                logger().debug("getLatestNavEvents $request")

                val response: NavEventResponse = navService.getLatestNavEvents(request)

                allNavEvents.addAll(response.navEventsList)

                currentPage++
                totalPages = response.pagination.totalPages
            }
            logger().debug("getLatestNavEvents finished total pages requested: $currentPage total navs: ${allNavEvents.size}")
            return@runBlocking allNavEvents
        } catch (e: Exception) {
            logger().error("Error fetching latest Nav Events: ${e.message}", e)
            emptyList()
        }
    }
}
