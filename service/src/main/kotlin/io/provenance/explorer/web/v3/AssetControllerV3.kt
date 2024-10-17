package io.provenance.explorer.web.v3

import io.provenance.explorer.service.AssetService
import io.provenance.explorer.service.IbcService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import javax.validation.constraints.Max
import javax.validation.constraints.Min

@Validated
@RestController
@RequestMapping(path = ["/api/v3/assets"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Asset-related endpoints - data for markers and basic denoms on chain",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Assets"]
)
class AssetControllerV3(private val assetService: AssetService, private val ibcService: IbcService) {

    @ApiOperation("Returns asset detail for the denom")
    @GetMapping("/{denom}")
    fun getMarkerDetail(
        @ApiParam(value = "Use any of the denom units to search, ie `nhash` or `hash`") @PathVariable denom: String
    ) = assetService.getAssetDetail(denom)

    @Deprecated("Temporary fix for redirecting NFT scope. Will be updated soon.")
    @ApiOperation("Redirects to the NFT scope page")
    @GetMapping("/nft/{scope}")
    fun redirectToNftScopePage(
        @ApiParam(value = "The scope ID to search for the NFT") @PathVariable scope: String
    ): ResponseEntity<Void> {
        val redirectUrl = "/nft/$scope"
        val headers = HttpHeaders().apply { location = URI(redirectUrl) }
        return ResponseEntity.status(HttpStatus.FOUND).headers(headers).build()
    }


    @ApiOperation("Returns asset detail for an ibc denom")
    @GetMapping("/ibc/{hash}")
    fun getIbcDetail(
        @ApiParam(
            value = "For an IBC denom, the standard format is `ibc/{hash}`. This parameter is solely the {hash}" +
                " portion of the denom."
        ) @PathVariable hash: String
    ) = ibcService.getIbcDenomDetail(hash)

    @ApiOperation("Returns asset holders for the denom")
    @GetMapping("/{denom}/holders")
    fun getMarkerHolders(
        @ApiParam(value = "Use any of the denom units to search, ie `nhash` or `hash`") @PathVariable denom: String,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = assetService.getAssetHolders(denom, page, count)

    @ApiOperation("Returns denom metadata for all or the specified asset")
    @GetMapping("/metadata")
    fun getMarkerMetadata(
        @ApiParam(value = "Use any of the denom units to search, ie `nhash` or `hash`", required = false)
        @RequestParam(required = false)
        denom: String?
    ) = assetService.getMetadata(denom)
}
