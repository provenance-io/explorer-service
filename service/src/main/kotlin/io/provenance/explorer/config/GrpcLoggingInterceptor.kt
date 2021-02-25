package io.provenance.explorer.config

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.MethodDescriptor
import io.provenance.explorer.domain.core.logger
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit


@Component
class GrpcLoggingInterceptor : ClientInterceptor {
    private val logger = logger()
    override fun <M, R> interceptCall(
        method: MethodDescriptor<M, R>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<M, R> {
        return object : BackendForwardingClientCall<M, R>(
            method,
            next.newCall(method, callOptions.withDeadlineAfter(10000, TimeUnit.MILLISECONDS))
        ) {
            override fun sendMessage(message: M) {
                logger.info("Requesting external api method: $methodName")
                super.sendMessage(message)
            }
        }
    }

    private open class BackendForwardingClientCall<M, R> constructor(
        method: MethodDescriptor<M, R>,
        delegate: ClientCall<*, *>?
    ) :
        SimpleForwardingClientCall<M, R>(delegate as ClientCall<M, R>?) {
            var methodName: String = method.bareMethodName ?: method.fullMethodName
        }
}
