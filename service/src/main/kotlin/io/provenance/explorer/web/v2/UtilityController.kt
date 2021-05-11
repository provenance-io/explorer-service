package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.service.utility.UtilityService
import io.provenance.explorer.web.BaseController
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Utility controller", produces = "application/json", consumes = "application/json", tags = ["Utilities"],
    description = "This should not be used by the UI"
)
class UtilityController(private val us: UtilityService) : BaseController() {

    @ApiOperation("Updates existing transaction msg types with the given info")
    @PostMapping("/update/txMsgType")
    fun updateTxMessageType(@RequestBody txMsgType: List<UnknownTxType>) =
        ResponseEntity.ok(us.updateTxMsgType(txMsgType))

    @ApiOperation("Fetches common error types in the data")
    @GetMapping("/errors")
    fun getErrors() = ResponseEntity.ok(us.getErrors())

    @ApiOperation("For Testing proto types")
    @GetMapping("/test/json")
    fun jsonTest(@RequestParam txHash: String) = ResponseEntity.ok(us.translateMsgAny(txHash))

}
