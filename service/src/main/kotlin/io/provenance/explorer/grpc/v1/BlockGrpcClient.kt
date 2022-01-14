package io.provenance.explorer.grpc.v1

import com.google.protobuf.util.JsonFormat
import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.ktor.client.call.receive
import io.ktor.client.features.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.provenance.explorer.KTOR_CLIENT
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.domain.exceptions.FigmentApiException
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class BlockGrpcClient(
    channelUri: URI,
    private val protoParser: JsonFormat.Parser,
    private val explorerProps: ExplorerProperties
) {

    private val tmClient: ServiceGrpc.ServiceBlockingStub

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

        tmClient = ServiceGrpc.newBlockingStub(channel)
    }

    fun getBlockAtHeight(maxHeight: Int) =
        try {
            tmClient.getBlockByHeight(Query.GetBlockByHeightRequest.newBuilder().setHeight(maxHeight.toLong()).build())
        } catch (e: Exception) {
            null
        }

    fun getLatestBlock() = tmClient.getLatestBlock(Query.GetLatestBlockRequest.getDefaultInstance())

    fun getBlockAtHeightFromFigment(height: Int) = runBlocking {
        val res = try {
            KTOR_CLIENT.get<HttpResponse>("${explorerProps.figmentUrl}/apikey/${explorerProps.figmentApikey}/cosmos/base/tendermint/v1beta1/blocks/$height")
        } catch (e: ResponseException) {
            throw FigmentApiException("Error reaching figment: ${e.response}")
        }

        if (res.status.value in 200..299) {
            val builder = Query.GetBlockByHeightResponse.newBuilder()
            protoParser.ignoringUnknownFields().merge(res.receive<String>(), builder)
            builder.build()
        } else throw FigmentApiException("Error reaching figment: ${res.status.value}")
    }
}
