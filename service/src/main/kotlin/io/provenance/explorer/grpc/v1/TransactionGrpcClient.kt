package io.provenance.explorer.grpc.v1

import cosmos.tx.v1beta1.ServiceGrpcKt
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.getTxRequest
import cosmos.tx.v1beta1.getTxResponse
import cosmos.tx.v1beta1.getTxsEventRequest
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.interceptor.GrpcLoggingInterceptor
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.exceptions.TendermintApiException
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class TransactionGrpcClient(channelUri: URI) {

    private val txClient: ServiceGrpcKt.ServiceCoroutineStub
    protected val logger = logger(TransactionGrpcClient::class)

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
                .maxInboundMessageSize((25 * 1e6).toInt())
                .build()

        txClient = ServiceGrpcKt.ServiceCoroutineStub(channel)
    }

    fun getTxByHash(hash: String) = runBlocking { txClient.getTx(getTxRequest { this.hash = hash }) }

    suspend fun getTxsByHeight(height: Int, total: Int): List<ServiceOuterClass.GetTxResponse> {
        var page = 1
        val limit = 10

        val txResps = mutableListOf<ServiceOuterClass.GetTxsEventResponse>()
        var txRespCount = 0

        do {
            txClient.getTxsEvent(
                getTxsEventRequest {
                    this.events.add("tx.height=$height")
                    this.limit = limit.toLong()
                }
            ).let {
                if (it.txResponsesList.isEmpty())
                    throw TendermintApiException(
                        "Blockchain failed to retrieve txs for height $height. Expected $total, " +
                            "Returned 0. This happens sometimes. The block will retry."
                    )
                txResps.add(it)
                txRespCount += it.txResponsesList.size
            }
            page++
        } while (txRespCount < total)

        return txResps.flatMap {
            it.txsList.zip(it.txResponsesList) { tx, res ->
                getTxResponse {
                    this.tx = tx
                    this.txResponse = res
                }
            }
        }
    }
}
