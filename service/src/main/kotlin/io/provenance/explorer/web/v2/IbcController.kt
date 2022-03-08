package io.provenance.explorer.web.v2

import ibc.core.channel.v1.ChannelOuterClass
import io.provenance.explorer.service.IbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/ibc"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "IBC-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["IBC"]
)
class IbcController(private val ibcService: IbcService) {

    @ApiOperation("Returns a paginated list of IBC denoms")
    @GetMapping("/denoms/all")
    fun getIbcDenomList(
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int
    ) = ResponseEntity.ok(ibcService.getIbcDenomList(page, count))

    @ApiOperation("Returns list of all available channels with status, grouped by destination chain")
    @GetMapping("/channels/status")
    fun getIbcChannelStatusList(
        @ApiParam(defaultValue = "STATE_OPEN", required = false)
        @RequestParam(defaultValue = "STATE_OPEN") status: ChannelOuterClass.State
    ) = ResponseEntity.ok(ibcService.getChannelsByStatus(status))

    @ApiOperation("Returns list of balances, grouped by denom")
    @GetMapping("/balances/denom")
    fun getIbcBalancesListByDenom() = ResponseEntity.ok(ibcService.getBalanceListByDenom())

    @ApiOperation("Returns list of balances, grouped by chain")
    @GetMapping("/balances/chain")
    fun getIbcBalancesListByChain() = ResponseEntity.ok(ibcService.getBalanceListByChain())

    @ApiOperation("Returns list of balances, grouped by chain/channel")
    @GetMapping("/balances/channel")
    fun getIbcBalancesListByChannel() = ResponseEntity.ok(ibcService.getBalanceListByChannel())
}
