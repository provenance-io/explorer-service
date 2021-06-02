package io.provenance.explorer.web.v2

import io.provenance.explorer.service.IbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/ibc"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "IBC controller", produces = "application/json", consumes = "application/json", tags = ["IBC"])
class IbcController(private val ibcService: IbcService) {

    @ApiOperation("Returns paginated list of ibc denoms")
    @GetMapping("/all")
    fun getIbcList(
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) = ResponseEntity.ok(ibcService.getIbcDenoms(page, count))


}
