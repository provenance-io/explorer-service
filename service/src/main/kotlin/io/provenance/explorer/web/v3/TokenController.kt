package io.provenance.explorer.web.v3

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.service.NHASH
import io.provenance.explorer.service.TokenService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
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
@RequestMapping(path = ["/api/v3/utility_token"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Utility Token-related data - statistics surrounding the utility token (nhash)",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Utility Token"]
)
class TokenController(private val tokenService: TokenService) {

    @ApiOperation("Returns token statistics for the chain, ie circulation, community pool")
    @GetMapping("/stats")
    fun getTokenStats() = ResponseEntity.ok(tokenService.getTokenBreakdown())

    @ApiOperation("Runs the distribution update")
    @GetMapping("/run")
    @HiddenApi
    fun runDistribution() = ResponseEntity.ok(tokenService.updateTokenDistributionStats(NHASH))

    @ApiOperation("Returns distribution of hash between sets of accounts = all - nhash marker - zeroSeq - modules - contracts")
    @GetMapping("/distribution")
    fun getDistribution() = ResponseEntity.ok(tokenService.getTokenDistributionStats())

    @ApiOperation("Returns the top X accounts rich in 'nhash' = all - nhash marker - zeroSeq - modules - contracts")
    @GetMapping("/rich_list")
    fun getRichList(@RequestParam(defaultValue = "100") @Min(1) @Max(1000) limit: Int) =
        ResponseEntity.ok(tokenService.richList(limit))

    @ApiOperation("Returns max supply of `nhash` = max")
    @GetMapping("/max_supply")
    fun getMaxSupply() = ResponseEntity.ok(tokenService.maxSupply())

    @ApiOperation("Returns total supply of `nhash` = max - burned ")
    @GetMapping("/total_supply")
    fun getTotalSupply() = ResponseEntity.ok(tokenService.totalSupply())

    @ApiOperation("Returns circulating supply of `nhash` = max - burned - modules - zeroSeq - pool - nonspendable ")
    @GetMapping("/circulating_supply")
    fun getCirculatingSupply() = ResponseEntity.ok(tokenService.circulatingSupply())
}
