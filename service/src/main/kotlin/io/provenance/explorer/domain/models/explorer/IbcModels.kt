package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import cosmos.base.abci.v1beta1.Abci
import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.IbcAckType
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
    var channel: IbcChannelRecord? = null,
    var denom: String = "",
    var logs: Abci.ABCIMessageLog? = null,
    var denomTrace: String = "",
    var balanceIn: String? = null,
    var balanceOut: String? = null,
    var fromAddress: String = "",
    var toAddress: String = "",
    var passThroughAddress: AccountRecord? = null,
    var ack: Boolean = false,
    var sequence: Int = -1,
    var ackType: IbcAckType? = null,
    var movementIn: Boolean = false, // recv == true, else false
    var changesEffected: Boolean = false,
    var ackSuccess: Boolean = false
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
    val denomTrace: String,
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
    val denomTrace: String,
    val balanceIn: BigDecimal?,
    val balanceOut: BigDecimal?,
    val lastTx: DateTime
)

data class TxIbcData(
    val msgClient: String?,
    val msgSrcPort: String?,
    val msgSrcChannel: String?,
    val event: String,
    val clientAttr: String?,
    val srcPortAttr: String?,
    var srcChannelAttr: String?
)

data class IbcEventData(
    val event: String,
    val attrs: IbcAttrSet
)

data class IbcAttrSet(
    val clientAttr: String?,
    val srcPortAttr: String?,
    var srcChannelAttr: String?
)

data class IbcMsgData(
    val packetEvent: String,
    val srcPortAttr: String,
    val srcChannelAttr: String,
    val eventCheck: String
)

data class IbcRelayer(
    val address: String,
    val txCount: Long,
    val lastTimestamp: DateTime
)
