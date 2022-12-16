package io.provenance.explorer.web.v3

import io.provenance.explorer.model.ValidatorState
import io.provenance.explorer.service.ValidatorService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v3/validators"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Validator-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Validators"]
)
class ValidatorControllerV3(private val validatorService: ValidatorService) {

    @ApiOperation("Returns recent validators")
    @GetMapping("/recent")
    fun validators(
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Validator status", defaultValue = "ACTIVE", required = false)
        @RequestParam(defaultValue = "ACTIVE")
        status: ValidatorState
    ) = validatorService.getRecentValidators(count, page, status)
}
