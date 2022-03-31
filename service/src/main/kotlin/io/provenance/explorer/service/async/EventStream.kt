package io.provenance.explorer.service.async

import io.provenance.eventstream.WsAdapter
import io.provenance.eventstream.decoder.moshiDecoderAdapter
import io.provenance.eventstream.extensions.awaitShutdown
import io.provenance.eventstream.net.defaultOkHttpClient
import io.provenance.eventstream.net.okHttpNetAdapter
import io.provenance.eventstream.stream.nodeEventStream
import io.provenance.eventstream.stream.rpc.response.MessageType
import io.provenance.eventstream.stream.toLiveMetaDataStream
import io.provenance.explorer.config.EventStreamProperties
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component


@Component
class EventStream(props: EventStreamProperties) {

    init {
        val okHttp = defaultOkHttpClient()
        val netAdapter = okHttpNetAdapter(props.webSocketUri, okHttp)
        val decoderAdapter = moshiDecoderAdapter()

        @OptIn(kotlin.time.ExperimentalTime::class)
        runBlocking {
            nodeEventStream<MessageType.NewBlock>(netAdapter, decoderAdapter)
                .toLiveMetaDataStream()
                .collect { println("newBlock:\n$it") }

            okHttp.awaitShutdown()
        }
    }
}
