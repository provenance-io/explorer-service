package io.provenance.explorer.config

import kotlinx.coroutines.sync.Semaphore
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI

@EnableConfigurationProperties(
    value = [ExplorerProperties::class]
)
@Configuration
class GrpcClientConfig(val props: ExplorerProperties) {

    @Bean
    fun channelUri() = URI(props.pbUrl)

    @Bean
    fun semaphore() = Semaphore(System.getenv("GRPC_CONCURRENCY")?.toInt() ?: 20)
}
