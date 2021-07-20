package io.provenance.explorer.web.v2

import io.provenance.explorer.service.GovService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/gov"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Governance controller", produces = "application/json", consumes = "application/json", tags = ["Governance"])
class GovController(private val govService: GovService) {

    @ApiOperation("Returns paginated list of proposals, proposal ID descending")
    @GetMapping("/proposals/all")
    fun getProposalsList(
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(govService.getProposalsList(page, count))

    @ApiOperation("Returns header and timing detail of a proposal")
    @GetMapping("/proposals/{id}")
    fun getProposal(@PathVariable id: Long) = ResponseEntity.ok(govService.getProposalDetail(id))

    @ApiOperation("Returns vote tallies and vote records of a proposal")
    @GetMapping("/proposals/{id}/votes")
    fun getProposalVotes(@PathVariable id: Long) = ResponseEntity.ok(govService.getProposalVotes(id))

    @ApiOperation("Returns paginated list of deposit records of a proposal, block height descending")
    @GetMapping("/proposals/{id}/deposits")
    fun getProposalDeposits(
        @PathVariable id: Long,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(govService.getProposalDeposits(id, page, count))

    @ApiOperation("Returns paginated list of vote records for an address, proposal ID descending")
    @GetMapping("/address/{address}/votes")
    fun getValidatorVotes(
        @PathVariable address: String,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(govService.getAddressVotes(address, page, count))
}
