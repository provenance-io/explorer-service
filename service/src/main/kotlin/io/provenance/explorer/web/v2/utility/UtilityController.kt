package io.provenance.explorer.web.v2.utility

import io.provenance.explorer.domain.annotation.HiddenApi
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
@RequestMapping(path = ["/api/v2/utility"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Utility controller", produces = "application/json", consumes = "application/json", tags = ["Utilities"],
    description = "This should not be used by the UI"
)
@HiddenApi
class UtilityController(private val us: UtilityService) {

    @ApiOperation("Adds a new marker record for the given denom. SHOULD ONLY BE USED IN EXTREME CASES")
    @PostMapping("/add/marker")
    fun addValidAsset(@RequestParam denom: String) = ResponseEntity.ok(us.addMarker(denom))

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

    @ApiOperation("For Fetching tx types from DB and formatting data from proto")
    @GetMapping("/txTypes")
    fun getTxTypes() = ResponseEntity.ok(us.getMsgTypeToProto())

    @ApiOperation("Updates tx fees from a given height - uses procedure to recalc fees")
    @PostMapping("/update/tx_fees")
    fun updateTxFeesFromHeight(@RequestParam height: Int) = ResponseEntity.ok(us.updateTxFeesFromHeight(height))
}
