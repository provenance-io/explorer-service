package io.provenance.explorer.domain.extensions

import cosmos.base.abci.v1beta1.Abci
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass.Tx
import io.provenance.explorer.config.ExplorerProperties
import io.provenance.explorer.config.ExplorerProperties.Companion.UTILITY_TOKEN
import io.provenance.explorer.domain.entities.TxFeeRecord
import io.provenance.explorer.domain.entities.TxMessageTypeRecord
import io.provenance.explorer.domain.models.explorer.TxFeeData
import io.provenance.explorer.grpc.extensions.denomAmountToPair
import io.provenance.explorer.grpc.extensions.getByDefinedEvent
import io.provenance.explorer.grpc.v1.MsgFeeGrpcClient
import io.provenance.explorer.model.TxFee
import io.provenance.msgfees.v1.eventMsgFees
import io.provenance.msgfees.v1.msgAssessCustomMsgFeeRequest
import net.pearx.kasechange.toTitleCase
import java.math.BigDecimal

fun Abci.TxResponse.getFeeTotalPaid() =
    this.tx.unpack(Tx::class.java).authInfo.fee.amountList.firstOrNull { it.denom == UTILITY_TOKEN }?.amount?.toBigDecimal()
        ?: BigDecimal.ZERO

fun List<TxFeeRecord>.toFees() = this.groupBy { it.feeType }
    .map { (k, v) -> TxFee(k.toTitleCase(), v.map { it.toFeeCoinStr() }) }

fun List<TxFeeRecord>.toFeePaid(altDenom: String) =
    this.sumOf { it.amount }.toCoinStr(this.firstOrNull()?.marker ?: altDenom)

fun getCustomFeeProtoType() = msgAssessCustomMsgFeeRequest { }.getType()
const val CUSTOM_FEE_MSG_TYPE = "custom_fee"
fun getEventMsgFeesType() = eventMsgFees { }.getType()

fun Abci.TxResponse.defaultBaseFees(msgFeeClient: MsgFeeGrpcClient, height: Int) =
    BigDecimal(this.gasWanted * msgFeeClient.getFloorGasPriceOrDefault(height))

// If block lands in bug range and no msg fees, use wanted * gas price param
// If basefee event is present, use that
// If not, if fee event is present, use that
// If not, use the total fee paid (usually used for older txs)
fun Abci.TxResponse.getSuccessTotalBaseFee(msgFeeClient: MsgFeeGrpcClient, height: Int, props: ExplorerProperties, hasMsgFees: Boolean) =
    if (props.inOneElevenBugRange(height) && !hasMsgFees) {
        this.defaultBaseFees(msgFeeClient, height)
    } else {
        this.eventsList
            .firstOrNull { it.type == "tx" && it.attributesList.map { attr -> attr.key.toStringUtf8() }.contains("basefee") }
            ?.attributesList?.first { it.key.toStringUtf8() == "basefee" }
            ?.value?.toStringUtf8()?.denomAmountToPair()?.first?.let {
                try {
                    it.toBigDecimal()
                } catch (e: NumberFormatException) {
                    return BigDecimal.ZERO
                }
            }
            ?: (
                this.eventsList
                    .firstOrNull { it.type == "tx" && it.attributesList.map { attr -> attr.key.toStringUtf8() }.contains("fee") }
                    ?.attributesList?.first { it.key.toStringUtf8() == "fee" }
                    ?.value?.toStringUtf8()?.denomAmountToPair()?.first?.let {
                        try {
                            it.toBigDecimal()
                        } catch (e: NumberFormatException) {
                            return BigDecimal.ZERO
                        }
                    }
                    ?: this.getFeeTotalPaid()
                )
    }

// If min_fee_charged event is present, use that
// if not, if coin_spent event is present, use that
// If not, if <codespace, code> combo is one that will not incur a fee, set to 0
// If not, find the msg fee set at height, and then calc base fee as floor * wanted OR total fee paid (greater of the 2)
// If no msg fee set, use the total fee paid (usually used for older txs)
fun Abci.TxResponse.getFailureTotalBaseFee(msgFeeClient: MsgFeeGrpcClient, height: Int) =
    this.eventsList
        .firstOrNull { it.type == "tx" && it.attributesList.map { attr -> attr.key.toStringUtf8() }.contains("min_fee_charged") }
        ?.attributesList?.first { it.key.toStringUtf8() == "min_fee_charged" }
        ?.value?.toStringUtf8()?.denomAmountToPair()?.first?.let {
            try {
                it.toBigDecimal()
            } catch (e: NumberFormatException) {
                return BigDecimal.ZERO
            }
        }
        ?: (
            this.eventsList
                .firstOrNull { it.type == "coin_spent" && it.attributesList.map { attr -> attr.key.toStringUtf8() }.contains("amount") }
                ?.attributesList?.first { it.key.toStringUtf8() == "amount" }
                ?.value?.toStringUtf8()?.denomAmountToPair()?.first?.let {
                    try {
                        it.toBigDecimal()
                    } catch (e: NumberFormatException) {
                        return BigDecimal.ZERO
                    }
                }
                ?: (
                    sigErrorComboList.firstOrNull { it == Pair(this.codespace, this.code) }?.let { BigDecimal.ZERO }
                        ?: (
                            if (msgFeeClient.getMsgFeesAtHeight(height).isNotEmpty()) {
                                val baseFee = this.defaultBaseFees(msgFeeClient, height)
                                if (baseFee > this.getFeeTotalPaid()) this.getFeeTotalPaid() else baseFee
                            } else {
                                this.getFeeTotalPaid()
                            }
                            )
                    )
            )

val sigErrorComboList = listOf("sdk" to 8, "sdk" to 32)

fun Abci.TxResponse.getTotalBaseFees(msgFeeClient: MsgFeeGrpcClient, height: Int, props: ExplorerProperties, hasMsgFees: Boolean) =
    if (this.code == 0) {
        this.getSuccessTotalBaseFee(msgFeeClient, height, props, hasMsgFees)
    } else {
        this.getFailureTotalBaseFee(msgFeeClient, height)
    }

// Old way to find msg fees, before the events were in place
fun ServiceOuterClass.GetTxResponse.identifyMsgBasedFeesOld(msgFeeClient: MsgFeeGrpcClient, height: Int): List<TxFeeData> {
    val msgToFee = mutableMapOf<String, MutableList<Long>>()
    // get msg fee list
    val msgFees = msgFeeClient.getMsgFeesAtHeight(height).associate { it.msgTypeUrl to it.additionalFee }
        .ifEmpty { return emptyList() }
    // get defined events list
    val definedEvents = getByDefinedEvent()
    // get tx level events list
    this.txResponse.eventsList
        // filter for matching type == defined events
        .filter { definedEvents.keys.contains(it.type) }
        .forEach { event ->
            // for each event obj, count the unique field, multiply by fee, and add to map (type to full amount)
            val match = definedEvents[event.type]!!
            val msgFee = msgFees[match.msg]!!
            val count = event.attributesList.count { it.key.toStringUtf8() == match.uniqueField }
            val amount = msgFee.amount.toLong() * count
            msgToFee[match.msg]?.add(amount) ?: msgToFee.put(match.msg, mutableListOf(amount))
        }
    // from map, build the TxFeeData objects for return
    return msgToFee.map {
        TxFeeData(
            TxMessageTypeRecord.findByProtoType(it.key)!!.type,
            it.value.sum().toBigDecimal(),
            msgFees[it.key]!!.denom,
            null,
            null
        )
    }
}
