package io.provenance.explorer.service.pricing.fetchers

import io.provenance.explorer.grpc.flow.FlowApiGrpcClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FetcherConfiguration {

    @Bean
    fun historicalPriceFetcherFactory(flowApiGrpcClient: FlowApiGrpcClient): HistoricalPriceFetcherFactory {
        return HistoricalPriceFetcherFactory(flowApiGrpcClient)
    }
}
