package io.provenance.explorer.web.v2.utility

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.domain.entities.UnknownTxType
import io.provenance.explorer.service.utility.UtilityService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(
    name = "Utilities",
    description = "This should not be used by the UI"
)
@HiddenApi
class UtilityController(private val us: UtilityService) {

    @Operation(summary = "Adds a new marker record for the given denom. SHOULD ONLY BE USED IN EXTREME CASES")
    @PostMapping("/add/marker")
    fun addValidAsset(@RequestParam denom: String) = ResponseEntity.ok(us.addMarker(denom))

    @Operation(summary = "Updates existing transaction msg types with the given info")
    @PostMapping("/update/txMsgType")
    fun updateTxMessageType(@RequestBody txMsgType: List<UnknownTxType>) =
        ResponseEntity.ok(us.updateTxMsgType(txMsgType))

    @Operation(summary = "Fetches common error types in the data")
    @GetMapping("/errors")
    fun getErrors() = ResponseEntity.ok(us.getErrors())

    @Operation(summary = "For Testing proto types")
    @GetMapping("/test/json")
    fun jsonTest(@RequestParam txHash: String) = ResponseEntity.ok(us.translateMsgAny(txHash))

    @Operation(summary = "Given the accounts, get the balance for the denom")
    @GetMapping("/accounts/denom")
    fun getAccountsWithDenom(
        @RequestParam denom: String,
        @RequestParam accounts: List<String>
    ) = ResponseEntity.ok(us.searchAccountsForDenom(accounts, denom))

    @Operation(summary = "For Testing stringified json")
    @GetMapping("/test/string")
    fun strToJsonTest(@RequestParam str: String) = ResponseEntity.ok(us.stringToJson(str))

    @Operation(summary = "For Testing base64 encoded string")
    @GetMapping("/test/base64")
    fun base64ToStringTest(@RequestParam str: String) = ResponseEntity.ok(us.decodeToString(str))

    @Operation(summary = "For Fetching tx types from DB and formatting data from proto")
    @GetMapping("/txTypes")
    fun getTxTypes() = ResponseEntity.ok(us.getMsgTypeToProto())

    @Operation(summary = "Updates tx fees from a given height - uses procedure to recalc fees")
    @PostMapping("/update/tx_fees")
    fun updateTxFeesFromHeight(@RequestParam height: Int) = ResponseEntity.ok(us.updateTxFeesFromHeight(height))

    @Operation(summary = "Parses raw tx json, formatted as a string. Used for debugging a tx response")
    @PostMapping("/parse/tx_json")
    fun parseTxResponseObject(@RequestBody rawJson: String) = ResponseEntity.ok(us.parseRawTxJson(rawJson))

    @Operation(summary = "Parses and tries to save raw tx json, formatted as a string. Used for debugging a tx response")
    @PostMapping("/parse/tx_json/save")
    fun saveTxResponseObject(@RequestBody rawJson: String) = ResponseEntity.ok(us.saveRawTxJson(rawJson))
}
