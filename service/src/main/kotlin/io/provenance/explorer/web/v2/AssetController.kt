package io.provenance.explorer.web.v2

import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.IbcService
import io.provenance.marker.v1.MarkerStatus
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/assets"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Asset-related endpoints - data for markers and basic denoms on chain",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Assets"]
)
class AssetController(private val assetService: AssetService, private val ibcService: IbcService) {

    @ApiOperation("Returns a paginated list of assets for selected statuses")
    @GetMapping("/all")
    fun getMarkers(
        @RequestParam(defaultValue = "MARKER_STATUS_ACTIVE") statuses: List<MarkerStatus>,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(200) count: Int
    ) = ResponseEntity.ok(assetService.getAssets(statuses, page, count))

    @ApiOperation("Returns asset detail for the denom")
    @GetMapping("/detail/{denom}")
    fun getMarkerDetail(
        @ApiParam(value = "Use any of the denom units to search, ie `nhash` or `hash`") @PathVariable denom: String
    ) = ResponseEntity.ok(assetService.getAssetDetail(denom))

    @ApiOperation("Returns asset detail for an ibc denom")
    @GetMapping("/detail/ibc/{hash}")
    fun getIbcDetail(
        @ApiParam(
            value = "For an IBC denom, the standard format is `ibc/{hash}`. This parameter is solely the {hash}" +
                " portion of the denom."
        ) @PathVariable hash: String
    ) = ResponseEntity.ok(ibcService.getIbcDenomDetail(hash))

    @ApiOperation("Returns asset holders for the denom")
    @GetMapping("/holders")
    fun getMarkerHolders(
        @ApiParam(value = "Use the base denom name, ie `nhash` instead of `hash`") @RequestParam id: String,
        @ApiParam(defaultValue = "1", required = false) @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10") @Min(1) @Max(200) count: Int
    ) = ResponseEntity.ok(assetService.getAssetHolders(id, page, count))

    @ApiOperation("Returns distribution of hash between sets of accounts")
    @GetMapping("/distribution")
    fun getTokenDistributionStats() = ResponseEntity.ok(assetService.getTokenDistributionStats())

    @ApiOperation("Returns denom metadata for all or the specified asset")
    @GetMapping("/metadata")
    fun getMarkerMetadata(
        @ApiParam(value = "Use the base denom name, ie `nhash` instead of `hash`", required = false)
        @RequestParam(required = false) id: String?
    ) = ResponseEntity.ok(assetService.getMetadata(id))
}
