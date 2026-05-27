package io.provenance.explorer.web.v3

import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.IbcService
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
@RequestMapping(path = ["/api/v3/assets"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Assets",
    description = "Asset-related endpoints - data for markers and basic denoms on chain"
)
class AssetControllerV3(private val assetService: AssetService, private val ibcService: IbcService) {

    @Operation(summary = "Returns asset detail for the denom")
    @GetMapping("/{denom}")
    fun getMarkerDetail(
        @Parameter(description = "Use any of the denom units to search, ie `nhash` or `hash`") @PathVariable denom: String
    ) = assetService.getAssetDetail(denom)

    @Operation(summary = "Returns asset detail for an ibc denom")
    @GetMapping("/ibc/{hash}")
    fun getIbcDetail(
        @Parameter(
            description = "For an IBC denom, the standard format is `ibc/{hash}`. This parameter is solely the {hash}" +
                " portion of the denom."
        ) @PathVariable hash: String
    ) = ibcService.getIbcDenomDetail(hash)

    @Operation(summary = "Returns asset holders for the denom")
    @GetMapping("/{denom}/holders")
    fun getMarkerHolders(
        @Parameter(description = "Use any of the denom units to search, ie `nhash` or `hash`") @PathVariable denom: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = assetService.getAssetHolders(denom, page, count)

    @Operation(summary = "Returns denom metadata for all or the specified asset")
    @GetMapping("/metadata")
    fun getMarkerMetadata(
        @Parameter(description = "Use any of the denom units to search, ie `nhash` or `hash`", required = false)
        @RequestParam(required = false)
        denom: String?
    ) = assetService.getMetadata(denom)

    @Operation(summary = "Returns assets by role and address")
    @GetMapping("/by-role/{role}/{address}")
    fun getAssetsByRole(
        @Parameter(description = "The role to filter by (e.g., ISSUER, ADMIN)") @PathVariable role: String,
        @Parameter(description = "The address to filter by") @PathVariable address: String
    ) = assetService.getAssetsByRole(role, address)
}
