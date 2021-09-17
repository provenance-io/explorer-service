package io.provenance.explorer.grpc.v1

import com.google.protobuf.util.JsonFormat
import cosmos.base.tendermint.v1beta1.Query
import cosmos.base.tendermint.v1beta1.ServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.domain.exceptions.FigmentApiException
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

    fun getBlockAtHeightFromFigment(height: Int): Query.GetBlockByHeightResponse {
        val res = khttp.get(
            url = "${explorerProps.figmentUrl}/apikey/${explorerProps.figmentApikey}/cosmos/base/tendermint/v1beta1/blocks/$height",
            headers = mapOf("Content-Type" to "application/json")
        )

        if (res.statusCode == 200) {
            val builder = Query.GetBlockByHeightResponse.newBuilder()
            protoParser.ignoringUnknownFields().merge(res.jsonObject.toString(), builder)
            return builder.build()
        } else throw FigmentApiException("Error reaching figment: ${res.jsonObject}")
    }
}
