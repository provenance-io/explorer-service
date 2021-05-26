package io.provenance.explorer.domain.models.explorer

import com.fasterxml.jackson.databind.node.ObjectNode
import cosmos.base.v1beta1.CoinOuterClass
import org.joda.time.DateTime
import java.math.BigInteger


data class IbcListed(
    val marker: String,
    val supply: String,
    val lastTxTimestamp: String?
)

data class IbcDetail(
    val marker: String,
    val supply: String,
    val holderCount: Int,
    val txnCount: BigInteger?,
    val metadata: ObjectNode,
    val trace: ObjectNode
)

