package io.provenance.explorer.web.v2

import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.IbcService
import io.provenance.marker.v1.MarkerStatus
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
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/assets"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Asset controller", produces = "application/json", consumes = "application/json", tags = ["Assets"])
class AssetController(private val assetService: AssetService, private val ibcService: IbcService) {

    @ApiOperation("Returns paginated list of assets for selected statuses")
    @GetMapping("/all")
    fun getMarkers(
        @RequestParam(required = false, defaultValue = "MARKER_STATUS_ACTIVE") statuses: List<MarkerStatus>,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(200) count: Int
    ) = ResponseEntity.ok(assetService.getAssets(statuses, page, count))

    @ApiOperation("Returns asset detail for denom or address")
    @GetMapping("/detail/{id}")
    fun getMarkerDetail(@PathVariable id: String) = ResponseEntity.ok(assetService.getAssetDetail(id))

    @ApiOperation("Returns asset detail for denom or address")
    @GetMapping("/detail/ibc/{id}")
    fun getIbcDetail(@PathVariable id: String) = ResponseEntity.ok(ibcService.getIbcDenomDetail(id))

    @ApiOperation("Returns asset holders for denom or address")
    @GetMapping("/holders")
    fun getMarkerHolders(
        @RequestParam id: String,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) @Max(200) count: Int
    ) =
        ResponseEntity.ok(assetService.getAssetHolders(id, page, count))

    @ApiOperation("Returns distribution of hash between sets of accounts")
    @GetMapping("/distribution")
    fun getTokenDistributionStats() = ResponseEntity.ok(assetService.getTokenDistributionStats())

    @ApiOperation("Returns metadata for all or specified asset")
    @GetMapping("/metadata")
    fun getMarkerMetadata(@RequestParam(required = false) id: String?) = ResponseEntity.ok(assetService.getMetadata(id))
}
