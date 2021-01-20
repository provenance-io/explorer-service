package io.provenance.explorer.web.v2

import io.provenance.explorer.domain.BlockDetail
import io.provenance.explorer.domain.PagedResults
import io.provenance.explorer.domain.RecentBlock
import io.provenance.explorer.service.ExplorerService
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
@RequestMapping(path = ["/api/v2/blocks"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(value = "Block controller", produces = "application/json", consumes = "application/json", tags = ["Blocks"])
class BlockController(private val explorerService: ExplorerService) : BaseController() {

    @ApiOperation("Return block at specified height")
    @GetMapping("/height/{height}")
    fun blockHeight(@PathVariable(required = false) height: Int?):
        ResponseEntity<BlockDetail> = ResponseEntity.ok(explorerService.getBlockAtHeight(height))

    @ApiOperation("Returns most recent blocks")
    @GetMapping("/recent")
    fun recentBlocks(@RequestParam(required = true, defaultValue = "10") @Min(1) count: Int,
                     @RequestParam(required = true, defaultValue = "1") @Min(1) page: Int,
                     @RequestParam(required = false, defaultValue = "desc") sort: String):
        ResponseEntity<PagedResults<RecentBlock>> =
        ResponseEntity.ok(explorerService.getRecentBlocks(count, page - 1, sort))

}
