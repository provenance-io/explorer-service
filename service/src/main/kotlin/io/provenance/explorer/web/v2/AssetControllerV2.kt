package io.provenance.explorer.web.v2

import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.IbcService
import io.provenance.explorer.service.TokenService
import io.provenance.marker.v1.MarkerStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/assets"], produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Assets",
    description = "Asset-related endpoints - data for markers and basic denoms on chain",
)
class AssetControllerV2(
    private val assetService: AssetService,
    private val ibcService: IbcService,
    private val tokenService: TokenService
) {

    @Operation(summary = "Returns a paginated list of assets for selected statuses")
    @GetMapping("/all")
    fun getMarkers(
        @RequestParam(defaultValue = "MARKER_STATUS_ACTIVE") statuses: List<MarkerStatus>,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = assetService.getAssets(statuses, page, count)

    @Operation(summary = "Returns asset detail for the denom")
    @GetMapping("/detail/{denom}")
    @Deprecated("Use /api/v3/assets/{denom}")
    @java.lang.Deprecated
    fun getMarkerDetail(
        @Parameter(description = "Use any of the denom units to search, ie `nhash` or `hash`") @PathVariable denom: String
    ) = ResponseEntity.ok(assetService.getAssetDetail(denom))

    @Operation(summary = "Returns asset detail for an ibc denom")
    @GetMapping("/detail/ibc/{hash}")
    @Deprecated("Use /api/v3/assets/ibc/{hash}")
    @java.lang.Deprecated
    fun getIbcDetail(
        @Parameter(
            description = "For an IBC denom, the standard format is `ibc/{hash}`. This parameter is solely the {hash}" +
                " portion of the denom."
        ) @PathVariable hash: String
    ) = ResponseEntity.ok(ibcService.getIbcDenomDetail(hash))

    @Operation(summary = "Returns asset holders for the denom")
    @GetMapping("/holders")
    @Deprecated("Use /api/v3/assets/{denom}/holders")
    @java.lang.Deprecated
    fun getMarkerHoldersOld(
        @Parameter(description = "Use any of the denom units to search, ie `nhash` or `hash`") @RequestParam id: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = ResponseEntity.ok(assetService.getAssetHolders(id, page, count))

    @Operation(summary = "Returns distribution of hash between sets of accounts")
    @GetMapping("/distribution")
    @Deprecated("Use /api/v3/utility_token/distribution")
    @java.lang.Deprecated
    fun getTokenDistributionStats() = ResponseEntity.ok(tokenService.getTokenDistributionStats())

    @Operation(summary = "Returns denom metadata for all or the specified asset")
    @GetMapping("/metadata")
    @Deprecated("Use /api/v3/assets/metadata")
    @java.lang.Deprecated
    fun getMarkerMetadata(
        @Parameter(description = "Use the base denom name, ie `nhash` instead of `hash`", required = false)
        @RequestParam(required = false)
        id: String?
    ) = ResponseEntity.ok(assetService.getMetadata(id))
}
