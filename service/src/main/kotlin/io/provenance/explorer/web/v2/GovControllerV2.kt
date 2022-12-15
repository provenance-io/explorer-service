package io.provenance.explorer.web.v2

import io.provenance.explorer.service.GovService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/gov"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Governance-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Governance"]
)
class GovControllerV2(private val govService: GovService) {

    @ApiOperation("Returns paginated list of proposals, proposal ID descending")
    @GetMapping("/proposals/all")
    @Deprecated("Use /api/v3/gov/proposals")
    @java.lang.Deprecated
    fun getProposalsList(
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 100", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int
    ) = govService.getProposalsList(page, count)

    @ApiOperation("Returns header and timing detail of a proposal")
    @GetMapping("/proposals/{id}")
    fun getProposal(
        @ApiParam(value = "The ID of the proposal") @PathVariable id: Long
    ) = govService.getProposalDetail(id)

    @ApiOperation("Returns paginated list of deposit records of a proposal, block height descending")
    @GetMapping("/proposals/{id}/deposits")
    fun getProposalDeposits(
        @ApiParam(value = "The ID of the proposal") @PathVariable id: Long,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int
    ) = govService.getProposalDeposits(id, page, count)

    @ApiOperation("Returns paginated list of vote records for an address, proposal ID descending")
    @GetMapping("/address/{address}/votes")
    @Deprecated("Use /api/v3/gov/votes/{address}")
    @java.lang.Deprecated
    fun getValidatorVotes(
        @ApiParam(
            value = "The standard address for the chain. If searching for votes for a validator, use the Owner " +
                "Address of the validator"
        ) @PathVariable address: String,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = govService.getAddressVotes(address, page, count)
}
