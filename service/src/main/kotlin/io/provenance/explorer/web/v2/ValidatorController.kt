package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.extensions.toOffset
import io.provenance.explorer.domain.models.explorer.PagedResults
import io.provenance.explorer.domain.models.explorer.ValidatorDetails
import io.provenance.explorer.domain.models.explorer.ValidatorSummary
import io.provenance.explorer.service.ExplorerService
import io.provenance.explorer.web.BaseController
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
@RequestMapping(path = ["/api/v2/validators"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Validator controller",
    produces = "application/json",
    consumes = "application/json",
    tags = ["Validators"])
class ValidatorController(private val explorerService: ExplorerService) : BaseController() {

    @ApiOperation("Returns recent validators")
    @GetMapping("/recent")
    fun validatorsV2(
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "desc") sort: String,
        @RequestParam(required = false, defaultValue = "BOND_STATUS_BONDED") status: String
    ):
        ResponseEntity<PagedResults<ValidatorSummary>> =
        ResponseEntity.ok(explorerService.getRecentValidators(count, page, sort, status))

    @ApiOperation("Returns validator by address id")
    @GetMapping("/{id}")
    fun validator(@PathVariable id: String): ResponseEntity<ValidatorDetails?> =
        ResponseEntity.ok(explorerService.getValidator(id))

    @ApiOperation("Returns set of validators at block height")
    @GetMapping("/height/{blockHeight}")
    fun validatorsAtHeight(
        @PathVariable blockHeight: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "desc") sort: String
    ):
        ResponseEntity<PagedResults<ValidatorSummary>> =
        ResponseEntity.ok(
            explorerService.getValidatorsAtHeight(blockHeight, count, page.toOffset(count), sort, "BOND_STATUS_BONDED"))

}
