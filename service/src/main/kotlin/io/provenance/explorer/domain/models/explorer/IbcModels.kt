package io.provenance.explorer.domain.models.explorer

import io.provenance.explorer.domain.entities.AccountRecord
import io.provenance.explorer.domain.entities.IbcAckType
import io.provenance.explorer.domain.entities.IbcChannelRecord
import org.joda.time.DateTime
import java.math.BigDecimal

data class LedgerInfo(
    var channel: IbcChannelRecord? = null,
    var denom: String = "",
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
