package io.provenance.explorer.web.v2

import io.provenance.explorer.service.NameService
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
@RequestMapping(path = ["/api/v2/names"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
    name = "Name",
    description = "Name endpoints",
)
class NameController(private val nameService: NameService) {

    @Operation(summary = "Returns tree of names")
    @GetMapping("/tree")
    fun getNameTree() = nameService.getNameMap()

    @Operation(summary = "Returns attribute names owned by the address")
    @GetMapping("/{address}/owned")
    fun getNamesOwnedByAddress(
        @Parameter(description = "The address, starting with the standard account prefix")
        @PathVariable
        address: String,
        @Parameter(description = "Record count between 1 and 50", schema = Schema(defaultValue = "10"), required = false)
        @RequestParam(defaultValue = "10")
        @Min(1)
        @Max(50)
        count: Int,
        @Parameter(schema = Schema(defaultValue = "1"), required = false)
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int
    ) = nameService.getNamesOwnedByAddress(address, page, count)
}
