package io.provenance.explorer.grpc.v1

import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.grpc.extensions.getPagination
import io.provenance.msgfees.v1.QueryGrpcKt
import io.provenance.msgfees.v1.queryAllMsgFeesRequest
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class MsgFeeGrpcClient(channelUri: URI) {

    private val msgFeeClient: QueryGrpcKt.QueryCoroutineStub

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

        msgFeeClient = QueryGrpcKt.QueryCoroutineStub(channel)
    }

    fun getMsgFees(offset: Int = 0, limit: Int = 100) = runBlocking {
        msgFeeClient.queryAllMsgFees(
            queryAllMsgFeesRequest {
                this.pagination = getPagination(offset, limit)
            }
        )
    }

    suspend fun getMsgFeeParams() = msgFeeClient.params(io.provenance.msgfees.v1.queryParamsRequest { })

    fun getFloorGasPrice() = runBlocking { getMsgFeeParams().params.floorGasPrice.amount.toLong() }
}
