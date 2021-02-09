package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.models.explorer.AssetDetail
import io.provenance.explorer.domain.models.explorer.AssetHolder
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
import org.springframework.web.bind.annotation.RestController

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
    fun getMarkerHolders(@PathVariable id: String): ResponseEntity<List<AssetHolder>> =
        ResponseEntity.ok(assetService.getAssetHolders(id))
}
