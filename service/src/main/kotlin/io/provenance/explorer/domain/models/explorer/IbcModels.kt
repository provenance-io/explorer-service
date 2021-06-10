package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import cosmos.base.abci.v1beta1.Abci
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.IbcChannelRecord
import org.joda.time.DateTime
import java.math.BigDecimal
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

data class LedgerInfo(
    val channel: IbcChannelRecord,
    var denom: String = "",
    var logs: Abci.ABCIMessageLog,
    var denomTrace: String = "",
    var balanceIn: String? = null,
    var balanceOut: String? = null,
    var fromAddress: String = "",
    var toAddress: String = "",
    var passThroughAddress: AccountRecord? = null,
    var ack: Boolean = false
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

data class Balance(
    val denom: String,
    val balanceIn: CoinStr?,
    val balanceOut: CoinStr?,
    val lastTx: String
)

// Used for by denom, by chain, and by channel
data class LedgerBySliceRes(
    val dstChainName: String?,
    val srcPort: String?,
    val srcChannel: String?,
    val dstPort: String?,
    val dstChannel: String?,
    val denom: String,
    val balanceIn: BigDecimal?,
    val balanceOut: BigDecimal?,
    val lastTx: DateTime
)
