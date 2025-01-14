package io.provenance.explorer.web.v2

import io.provenance.explorer.service.ExplorerService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api/v2/blocks"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Block-related endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Blocks"]
)
class BlockController(private val explorerService: ExplorerService) {

    @ApiOperation("Returns the block information at current height")
    @GetMapping("/height")
    fun blockHeight() = explorerService.getBlockAtHeight(null)

    @ApiOperation("Return the block information at the specified height")
    @GetMapping("/height/{height}")
    fun blockHeight(@PathVariable height: Int) = explorerService.getBlockAtHeight(height)

    @ApiOperation("Returns X most recent blocks")
    @Cacheable(value = ["responses"], key = "{#root.methodName, #count, #page}")
    @GetMapping("/recent")
    fun recentBlocks(
        @ApiParam(value = "Record count between 1 and 200", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(200)
        count: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = explorerService.getRecentBlocks(count, page - 1)
}
