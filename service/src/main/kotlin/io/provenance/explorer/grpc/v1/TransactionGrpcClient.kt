package io.provenance.explorer.grpc.v1

import cosmos.base.abci.v1beta1.Abci
import cosmos.tx.v1beta1.ServiceGrpc
import cosmos.tx.v1beta1.ServiceOuterClass
import io.grpc.ManagedChannelBuilder
import io.provenance.explorer.config.GrpcLoggingInterceptor
import io.provenance.explorer.domain.core.logger
import io.provenance.explorer.domain.exceptions.TendermintApiException
import io.provenance.explorer.grpc.extensions.getPaginationBuilder
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class TransactionGrpcClient(channelUri: URI) {

    private val txClient: ServiceGrpc.ServiceBlockingStub
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
                .build()

        txClient = ServiceGrpc.newBlockingStub(channel)
    }

    fun getTxByHash(hash: String) = txClient.getTx(ServiceOuterClass.GetTxRequest.newBuilder().setHash(hash).build())

    fun getTxsByHeight(height: Int, total: Int): MutableList<Abci.TxResponse> {
        var offset = 0
        val limit = 100

        val results = txClient.getTxsEvent(
            ServiceOuterClass.GetTxsEventRequest.newBuilder()
                .addEvents("tx.height=$height")
                .setPagination(getPaginationBuilder(offset, limit))
                .build())

        val txResps = results.txResponsesList.toMutableList()

        if (txResps.count() == 0)
            throw TendermintApiException("Blockchain failed to retrieve txs for height $height. Expected $total, " +
                "Returned 0. This is not normal.")

        while (txResps.count() < total) {
            offset += limit
            txClient.getTxsEvent(
                ServiceOuterClass.GetTxsEventRequest.newBuilder()
                    .addEvents("tx.height=$height")
                    .setPagination(getPaginationBuilder(offset, limit))
                    .build())
                .let { txResps.addAll(it.txResponsesList) }
        }

        return txResps
    }

}
