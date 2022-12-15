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
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
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
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v3/gov"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Governance-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Governance"]
)
class GovControllerV3(private val govService: GovService, private val printer: JsonFormat.Printer) {

    @ApiOperation("Returns paginated list of proposals, proposal ID descending")
    @GetMapping("/proposals")
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

    @ApiOperation("Returns vote tallies and vote records of a proposal")
    @GetMapping("/proposals/{id}/votes")
    fun getProposalVotes(
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
    ) = govService.getProposalVotesPaginated(id, page, count)

    @ApiOperation("Returns paginated list of vote records for an address, proposal ID descending")
    @GetMapping("/votes/{address}")
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

    @ApiOperation("Return list of supported proposal types and associated objects to be used for SubmitProposal content string")
    @GetMapping("/types/supported")
    fun supportedProposalTypes() = govService.getSupportedProposalTypes()

    @ApiOperation(value = "Builds submit proposal transaction for submission to blockchain")
    @PostMapping("/submit/{type}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE])
    fun createSubmitProposal(
        @ApiParam(value = "The supported proposal type") @PathVariable type: ProposalType,
        @ApiParam(value = "Data used to craft the SubmitProposal msg type; `content` is a stringified json object conforming to the matching proposal type.")
        @RequestPart
        request: GovSubmitProposalRequest,
        @ApiParam(value = "A .wasm file type containing the WASM code; required for a StoreCode proposal type")
        @RequestPart(required = false)
        wasmFile: MultipartFile? = null,
        @ApiParam(hidden = true)
        @RequestAttribute(name = X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.submitter) {
            throw IllegalArgumentException("Unable to process create submit proposal; connected wallet does not match request")
        }
        return govService.createSubmitProposal(type, request, wasmFile).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds deposit transaction for submission to blockchain")
    @PostMapping("/deposit")
    fun createDeposit(
        @ApiParam(value = "Data used to craft the Deposit msg type") @RequestBody request: GovDepositRequest,
        @ApiParam(hidden = true)
        @RequestAttribute(name = X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.depositor) {
            throw IllegalArgumentException("Unable to process create deposit; connected wallet does not match request")
        }
        return govService.createDeposit(request).toTxBody().toTxMessageBody(printer)
    }

    @ApiOperation(value = "Builds vote transaction for submission to blockchain")
    @PostMapping("/vote")
    fun createVote(
        @ApiParam(value = "Data used to craft the Vote and WeightedVote msg types")
        @RequestBody
        request: GovVoteRequest,
        @ApiParam(hidden = true)
        @RequestAttribute(name = X_ADDRESS, required = true)
        xAddress: String
    ): TxMessageBody {
        if (xAddress != request.voter) {
            throw IllegalArgumentException("Unable to process create vote; connected wallet does not match request")
        }
        return govService.createVote(request).toTxBody().toTxMessageBody(printer)
    }
}
