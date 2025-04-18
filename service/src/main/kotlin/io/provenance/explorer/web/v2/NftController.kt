package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.annotation.HiddenApi
import io.provenance.explorer.service.NftService
import io.provenance.metadata.v1.PartyType
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
@RequestMapping(path = ["/api/v2/nft"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "NFTs",
    description = "NFT-related endpoints - data for Scopes, Records, and Specifications"
)
class NftController(private val nftService: NftService) {

    @Operation(summary = "Returns NFT detail for address")
    @GetMapping("/scope/{addr}")
    fun getNftDetail(@PathVariable addr: String) = nftService.getScopeDetail(addr)

    @Operation(summary = "Returns basic NFT data for scope id")
    @GetMapping("/scope/basic/{scopeId}")
    fun getNftBasic(@PathVariable scopeId: String) = nftService.getScopeBasic(scopeId)

    @Operation(summary = "Returns NFTs for the owning address, includes address as value owner or owner")
    @GetMapping("/scope/owner/{address}")
    fun getNftsByOwningAddress(
        @Parameter(description = "The standard account address for the chain.") @PathVariable address: String,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = nftService.getScopeDetailsForOwningAddress(address, page, count)

    @Operation(summary = "Returns NFTs for the owning address and type e.g. PARTY_TYPE_ORIGINATOR")
    @GetMapping("/scope/owner/{address}/{type}")
    fun getNftsByOwningAddressAndType(
        @Parameter(description = "The standard account address for the chain.") @PathVariable address: String,
        @Parameter(description = "The party type") @PathVariable type: PartyType,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @Parameter(description = "Record count between 1 and 200", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int
    ) = nftService.getScopeDetailsForOwnerAndType(address, type, page, count)

    @Operation(summary = "Returns MetadataAddress obj for bech32 addr")
    @GetMapping("/address/{addr}")
    @HiddenApi
    fun getMetadataAddress(@PathVariable addr: String) = nftService.translateAddress(addr)

    @Operation(summary = "Returns records for the scope address or UUID")
    @GetMapping("/scope/{addr}/records")
    fun getNftRecords(
        @Parameter(description = "A scope address (prefixed with `scope`) or a scope UUID") @PathVariable addr: String
    ) = nftService.getRecordsForScope(addr)

    @Operation(summary = "Returns the scope specification as a JSON object")
    @GetMapping("/scopeSpec/{scopeSpec}/json")
    fun getScopeSpecJson(
        @Parameter(description = "A scope specification address (prefixed with `scopespec`) or a scope specification UUID")
        @PathVariable
        scopeSpec: String
    ) = nftService.getScopeSpecJson(scopeSpec)

    @Operation(summary = "Returns the contract specification as a JSON object")
    @GetMapping("/contractSpec/{contractSpec}/json")
    fun getContractSpecJson(
        @Parameter(description = "A contract specification address (prefixed with `contractspec`) or a contract specification UUID")
        @PathVariable
        contractSpec: String
    ) = nftService.getContractSpecJson(contractSpec)

    @Operation(summary = "Returns the record specification as a JSON object")
    @GetMapping("/recordSpec/{recordSpec}/json")
    fun getRecordSpecJson(
        @Parameter(description = "A record specification address (prefixed with `recordspec`) or a record specification UUID")
        @PathVariable
        recordSpec: String
    ) = nftService.getRecordSpecJson(recordSpec)

    @Operation(summary = "Returns the owner addresses for a scope for a given type e.g. PARTY_TYPE_ORIGINATOR")
    @GetMapping("/scope/{scopeId}/owners/{type}")
    fun getNftOwnersByType(
        @Parameter(description = "The uuid or address for the scope") @PathVariable scopeId: String,
        @PathVariable type: PartyType
    ) = nftService.getScopeOwnersByPartyType(scopeId, type)

    @Operation(summary = "Returns basic nft data for the owning address, includes address as value owner or owner")
    @GetMapping("/scope/owner/{address}/all")
    fun getNftsbyOwnerAll(
        @Parameter(description = "The standard account address for the chain.") @PathVariable address: String,
        @RequestParam(name = "valueOwnerOnly", required = false) valueOwnerOnly: Boolean?
    ) = nftService.getScopesForOwningAddress(address, valueOwnerOnly)

    @Operation(summary = "Returns basic nft data for the owning address and type e.g. PARTY_TYPE_ORIGINATOR")
    @GetMapping("/scope/owner/{address}/{type}/all")
    fun getNftsbyOwnerAndTypeAll(
        @Parameter(description = "The standard account address for the chain.") @PathVariable address: String,
        @Parameter(description = "The party type") @PathVariable type: PartyType
    ) = nftService.getScopesForOwnerAndType(address, type)
}
