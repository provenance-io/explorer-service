package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetListed
import io.provenance.explorer.service.AssetService
import io.provenance.explorer.web.BaseController
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
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v2/assets"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Asset controller", produces = "application/json", consumes = "application/json", tags = ["Assets"])
class AssetController(private val assetService: AssetService) : BaseController() {

    @ApiOperation("Returns all assets")
    @GetMapping("/all")
    fun getMarkers(): ResponseEntity<List<AssetListed>> = ResponseEntity.ok(assetService.getAllAssets())

    @ApiOperation("Returns asset detail for denom or address")
    @GetMapping("/{id}/detail")
    fun getMarkerDetail(@PathVariable id: String): ResponseEntity<AssetDetail> =
        ResponseEntity.ok(assetService.getAssetDetail(id))

    @ApiOperation("Returns asset holders for denom or address")
    @GetMapping("/{id}/holders")
    fun getMarkerHolders(
        @PathVariable id: String,
        @RequestParam(required = false, defaultValue = "1") @Min(1) page: Int,
        @RequestParam(required = false, defaultValue = "10") @Min(1) count: Int
    ) =
        ResponseEntity.ok(assetService.getAssetHolders(id, page, count))

    @ApiOperation("Returns metadata for asset")
    @GetMapping("/{id}/metadata")
    fun getMarkerMetadata(@PathVariable id: String) = ResponseEntity.ok(assetService.getMetaData(id))
}
