package io.provenance.explorer.grpc.v1

import cosmos.base.reflection.v1beta1.Reflection
import cosmos.base.reflection.v1beta1.ReflectionServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit


@Component
class CosmosReflectionGrpcClient(channelUri: URI) {

    private val reflectionClient: ReflectionServiceGrpc.ReflectionServiceBlockingStub

    init {
        val channel =
            ManagedChannelBuilder.forAddress(channelUri.host, channelUri.port)
                .also {
                    if (channelUri.scheme == "grpcs") {
                        it.useTransportSecurity()
                    } else {
                        it.usePlaintext()
                    }
                }
                .idleTimeout(60, TimeUnit.SECONDS)
                .keepAliveTime(10, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .intercept(GrpcLoggingInterceptor())
                .build()

        reflectionClient = ReflectionServiceGrpc.newBlockingStub(channel)
    }

    fun listInterfaces(): Reflection.ListAllInterfacesResponse =
        reflectionClient.listAllInterfaces(Reflection.ListAllInterfacesRequest.getDefaultInstance())

    fun listImplementations() =
        listInterfaces().interfaceNamesList.map {
            it to reflectionClient.listImplementations(
                Reflection
                    .ListImplementationsRequest.newBuilder().setInterfaceName(it).build()
            ).implementationMessageNamesList.toList()
        }.toMap()
}
