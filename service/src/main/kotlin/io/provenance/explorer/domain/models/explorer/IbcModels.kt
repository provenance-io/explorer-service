package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import cosmos.base.v1beta1.CoinOuterClass
import org.joda.time.DateTime
import java.math.BigInteger


data class IbcDenomListed(
    val marker: String,
    val supply: String,
    val lastTxTimestamp: String?
)

data class IbcDenomDetail(
    val marker: String,
    val supply: String,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val metadata: ObjectNode,
    val trace: ObjectNode
)

data class IbcChannelBalance(
    val dstChainId: String,
    val channels: List<ChannelBalance>
)

data class ChannelBalance(
    val srcChannel: Channel,
    val dstChannel: Channel,
    val balances: List<CoinStr>
)

data class Channel(
    val port: String,
    val channel: String
)

data class IbcChannelStatus(
    val dstChainId: String,
    val channels: List<ChannelStatus>
)

data class ChannelStatus(
    val srcChannel: Channel,
    val dstChannel: Channel,
    val status: String
)
