package io.provenance.explorer.client

import feign.Headers
import feign.Param
import feign.RequestLine
import ibc.core.channel.v1.ChannelOuterClass
import io.provenance.explorer.client.BaseRoutes.PAGE_PARAMETERS
import io.provenance.explorer.model.Balance
import io.provenance.explorer.model.BalancesByChain
import io.provenance.explorer.model.BalancesByChannel
import io.provenance.explorer.model.IbcChannelStatus
import io.provenance.explorer.model.IbcDenomListed
import io.provenance.explorer.model.IbcRelayer
import io.provenance.explorer.model.base.PagedResults

object IbcRoutes {
    const val IBC_V2 = "${BaseRoutes.V2_BASE}/ibc"
    const val ALL = "$IBC_V2/denoms"
    const val CHANNELS = "$IBC_V2/channels/status"
    const val BALANCES_BY_DENOM = "$IBC_V2/balances/denom"
    const val BALANCES_BY_CHAIN = "$IBC_V2/balances/chain"
    const val BALANCES_BY_CHANNEL = "$IBC_V2/balances/channel"
    const val RELAYERS_BY_CHANNEL = "$IBC_V2/channels/src_port/{srcPort}/src_channel/{srcChannel}/relayers"
}

@Headers(BaseClient.CT_JSON)
interface IbcClient : BaseClient {

    @RequestLine("GET ${IbcRoutes.ALL}?$PAGE_PARAMETERS")
    fun allDenoms(
        @Param("count") count: Int = 10,
        @Param("page") page: Int = 1): PagedResults<IbcDenomListed>

    @RequestLine("GET ${IbcRoutes.CHANNELS}?status={status}")
    fun channelsByStatus(
        @Param("status") status: ChannelOuterClass.State = ChannelOuterClass.State.STATE_OPEN
    ): List<IbcChannelStatus>

    @RequestLine("GET ${IbcRoutes.BALANCES_BY_DENOM}")
    fun balancesByDenom(): List<Balance>

    @RequestLine("GET ${IbcRoutes.BALANCES_BY_CHAIN}")
    fun balancesByChain(): List<BalancesByChain>

    @RequestLine("GET ${IbcRoutes.BALANCES_BY_CHANNEL}?srcPort={srcPort}&srcChannel={srcChannel}")
    fun balancesByChannel(
        @Param("srcPort") srcPort: String? = null,
        @Param("srcChannel") srcChannel: String? = null
    ): List<BalancesByChannel>

    @RequestLine("GET ${IbcRoutes.RELAYERS_BY_CHANNEL}")
    fun relayersByChannel(
        @Param("srcPort") srcPort: String,
        @Param("srcChannel") srcChannel: String
    ): List<IbcRelayer>
}
