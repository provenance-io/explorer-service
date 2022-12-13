package io.provenance.explorer.model

import com.fasterxml.jackson.databind.node.ObjectNode
import io.provenance.explorer.model.base.CoinStr
import org.joda.time.DateTime
import java.math.BigInteger

data class IbcDenomDetail(
    val marker: String,
    val supply: String,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val metadata: ObjectNode,
    val trace: ObjectNode
)

data class IbcDenomListed(
    val marker: String,
    val supply: String,
    val lastTxTimestamp: String?
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

data class Balance(
    val denom: String,
    val denomTrace: String,
    val balanceIn: CoinStr?,
    val balanceOut: CoinStr?,
    val lastTx: String
)

data class BalancesByChain(
    val dstChainId: String,
    var lastTx: String = "",
    val balances: List<Balance>
)

data class BalancesByChannel(
    val dstChainId: String,
    var lastTx: String = "",
    val channels: List<BalanceByChannel>
)

data class BalanceByChannel(
    val srcChannel: Channel,
    val dstChannel: Channel,
    var lastTx: String = "",
    val balances: List<Balance>
)

data class IbcRelayer(
    val address: String,
    val txCount: Long,
    val lastTimestamp: DateTime
)
