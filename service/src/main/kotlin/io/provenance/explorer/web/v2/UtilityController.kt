package io.provenance.explorer.web.v2

import com.google.protobuf.ByteString
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.service.utility.UtilityService
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
class UtilityController(private val us: UtilityService) {

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

    @ApiOperation("Given the accounts, get the balance for the denom")
    @GetMapping("/accounts/denom")
    fun getAccountsWithDenom(
        @RequestParam denom: String,
        @RequestParam accounts: List<String>
    ) = ResponseEntity.ok(us.searchAccountsForDenom(accounts, denom))

    @ApiOperation("For Testing stringified json")
    @GetMapping("/test/string")
    fun strToJsonTest(@RequestParam str: String) = ResponseEntity.ok(us.stringToJson(str))

    @ApiOperation("For Testing base64 encoded string")
    @GetMapping("/test/base64")
    fun base64ToStringTest(@RequestParam str: String) = ResponseEntity.ok(us.decodeToString(str))

}
