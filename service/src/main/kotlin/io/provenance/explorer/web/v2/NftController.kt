package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.service.NftService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.http.MediaType
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
@RequestMapping(path = ["/api/v2/nft"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "NFT-related endpoints - data for Scopes, Records, and Specifications",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["NFTs"]
)
class NftController(private val nftService: NftService) {

    @ApiOperation("Returns NFT detail for address")
    @GetMapping("/scope/{addr}")
    fun getNftDetail(@PathVariable addr: String) = nftService.getScopeDetail(addr)

    @ApiOperation("Returns NFTs for the owning address, includes address as value owner or owner")
    @GetMapping("/scope/owner/{address}")
    fun getNftsByOwningAddress(
        @ApiParam(value = "The standard account address for the chain.") @PathVariable address: String,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = nftService.getScopesForOwningAddress(address, page, count)

    @ApiOperation("Returns MetadataAddress obj for bech32 addr")
    @GetMapping("/address/{addr}")
    @HiddenApi
    fun getMetadataAddress(@PathVariable addr: String) = nftService.translateAddress(addr)

    @ApiOperation("Returns records for the scope address or UUID")
    @GetMapping("/scope/{addr}/records")
    fun getNftRecords(
        @ApiParam(value = "A scope address (prefixed with `scope`) or a scope UUID") @PathVariable addr: String
    ) = nftService.getRecordsForScope(addr)

    @ApiOperation("Returns the scope specification as a JSON object")
    @GetMapping("/scopeSpec/{scopeSpec}/json")
    fun getScopeSpecJson(
        @ApiParam(value = "A scope specification address (prefixed with `scopespec`) or a scope specification UUID")
        @PathVariable
        scopeSpec: String
    ) = nftService.getScopeSpecJson(scopeSpec)

    @ApiOperation("Returns the contract specification as a JSON object")
    @GetMapping("/contractSpec/{contractSpec}/json")
    fun getContractSpecJson(
        @ApiParam(value = "A contract specification address (prefixed with `contractspec`) or a contract specification UUID")
        @PathVariable
        contractSpec: String
    ) = nftService.getContractSpecJson(contractSpec)

    @ApiOperation("Returns the record specification as a JSON object")
    @GetMapping("/recordSpec/{recordSpec}/json")
    fun getRecordSpecJson(
        @ApiParam(value = "A record specification address (prefixed with `recordspec`) or a record specification UUID")
        @PathVariable
        recordSpec: String
    ) = nftService.getRecordSpecJson(recordSpec)
}
