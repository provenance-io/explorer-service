package io.provenance.explorer.web.v3

import com.google.protobuf.util.JsonFormat
import io.provenance.explorer.config.interceptor.JwtInterceptor.Companion.X_ADDRESS
import io.provenance.explorer.domain.extensions.toTxBody
import io.provenance.explorer.domain.extensions.toTxMessageBody
import io.provenance.explorer.model.GovDepositRequest
import io.provenance.explorer.model.GovSubmitProposalRequest
import io.provenance.explorer.model.GovVoteRequest
import io.provenance.explorer.model.ProposalType
import io.provenance.explorer.model.TxMessageBody
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@Validated
@RestController
@RequestMapping(path = ["/api/v3/gov"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [org.springframework.http.MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Governance",
    description = "Governance-related endpoints"
)
class GovControllerV3(private val govService: GovService, private val printer: JsonFormat.Printer) {

    @Operation(summary = "Returns paginated list of proposals, proposal ID descending")
    @GetMapping("/proposals")
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

    @Operation(summary = "Returns vote tallies and vote records of a proposal")
    @GetMapping("/proposals/{id}/votes")
    fun getProposalVotes(
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
    ) = govService.getProposalVotesPaginated(id, page, count)

    @Operation(summary = "Returns paginated list of vote records for an address, proposal ID descending")
    @GetMapping("/votes/{address}")
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

    @Operation(summary = "Return list of supported proposal types and associated objects to be used for SubmitProposal content string")
    @GetMapping("/types/supported")
    fun supportedProposalTypes() = govService.getSupportedProposalTypes()

    @Operation(summary = "Builds submit proposal transaction for submission to blockchain")
    @PostMapping("/submit/{type}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE])
    fun createSubmitProposal(
        @Parameter(description = "The supported proposal type") @PathVariable type: ProposalType,
        @Parameter(description = "Data used to craft the SubmitProposal msg type; `content` is a stringified json object conforming to the matching proposal type.")
        @RequestPart
        request: GovSubmitProposalRequest,
        @Parameter(description = "A .wasm file type containing the WASM code; required for a StoreCode proposal type")
        @RequestPart(required = false)
        wasmFile: MultipartFile? = null,
        @Parameter(hidden = true)
        @RequestAttribute(name = X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.submitter) {
            throw IllegalArgumentException("Unable to process create submit proposal; connected wallet does not match request")
        }
        return govService.createSubmitProposal(type, request, wasmFile).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds deposit transaction for submission to blockchain")
    @PostMapping("/deposit")
    fun createDeposit(
        @Parameter(description = "Data used to craft the Deposit msg type") @RequestBody request: GovDepositRequest,
        @Parameter(hidden = true)
        @RequestAttribute(name = X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.depositor) {
            throw IllegalArgumentException("Unable to process create deposit; connected wallet does not match request")
        }
        return govService.createDeposit(request).toTxBody().toTxMessageBody(printer)
    }

    @Operation(summary = "Builds vote transaction for submission to blockchain")
    @PostMapping("/vote")
    fun createVote(
        @Parameter(description = "Data used to craft the Vote and WeightedVote msg types")
        @RequestBody
        request: GovVoteRequest,
        @Parameter(hidden = true)
        @RequestAttribute(name = X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.voter) {
            throw IllegalArgumentException("Unable to process create vote; connected wallet does not match request")
        }
        return govService.createVote(request).toTxBody().toTxMessageBody(printer)
    }
}
