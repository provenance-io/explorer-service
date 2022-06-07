package io.provenance.explorer.web.v3

import io.provenance.explorer.service.GovService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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
@RequestMapping(path = ["/api/v3/gov"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Governance-related endpoints - V3",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Governance"]
)
class GovControllerV3(private val govService: GovService) {

    @ApiOperation("Returns vote tallies and vote records of a proposal")
    @GetMapping("/proposals/{id}/votes")
    fun getProposalVotes(
        @ApiParam(value = "The ID of the proposal") @PathVariable id: Long,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) count: Int
    ) = ResponseEntity.ok(govService.getProposalVotesPaginated(id, page, count))
}
