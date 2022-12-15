package io.provenance.explorer.web.v2

import io.provenance.explorer.service.NameService
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
@RequestMapping(path = ["/api/v2/names"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    description = "Name endpoints",
    produces = MediaType.APPLICATION_JSON_VALUE,
    consumes = MediaType.APPLICATION_JSON_VALUE,
    tags = ["Name"]
)
class NameController(private val nameService: NameService) {

    @ApiOperation("Returns tree of names")
    @GetMapping("/tree")
    fun getNameTree() = nameService.getNameMap()

    @ApiOperation("Returns attribute names owned by the address")
    @GetMapping("/{address}/owned")
    fun getNamesOwnedByAddress(
        @ApiParam(value = "The address, starting with the standard account prefix")
        @PathVariable
        address: String,
        @ApiParam(value = "Record count between 1 and 50", defaultValue = "10", required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @ApiParam(defaultValue = "1", required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = nameService.getNamesOwnedByAddress(address, page, count)
}
