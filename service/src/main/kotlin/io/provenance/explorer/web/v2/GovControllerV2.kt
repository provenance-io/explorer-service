package io.provenance.explorer.web.v2

import io.provenance.explorer.service.GovService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/gov"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Governance",
    description = "Governance-related endpoints",
)
class GovControllerV2(private val govService: GovService) {

    @Operation(summary = "Returns paginated list of proposals, proposal ID descending")
    @GetMapping("/proposals/all")
    @Deprecated("Use /api/v3/gov/proposals")
    @java.lang.Deprecated
    fun getProposalsList(
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 100", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(100)
        count: Int
    ) = govService.getProposalsList(page, count)

    @Operation(summary = "Returns header and timing detail of a proposal")
    @GetMapping("/proposals/{id}")
    fun getProposal(
        @Parameter(description = "The ID of the proposal") @PathVariable id: Long
    ) = govService.getProposalDetail(id)

    @Operation(summary = "Returns paginated list of deposit records of a proposal, block height descending")
    @GetMapping("/proposals/{id}/deposits")
    fun getProposalDeposits(
        @Parameter(description = "The ID of the proposal") @PathVariable id: Long,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 50", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int
    ) = govService.getProposalDeposits(id, page, count)

    @Operation(summary = "Returns paginated list of vote records for an address, proposal ID descending")
    @GetMapping("/address/{address}/votes")
    @Deprecated("Use /api/v3/gov/votes/{address}")
    @java.lang.Deprecated
    fun getValidatorVotes(
        @Parameter(
            description = "The standard address for the chain. If searching for votes for a validator, use the Owner " +
                "Address of the validator"
        ) @PathVariable address: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = govService.getAddressVotes(address, page, count)
}
