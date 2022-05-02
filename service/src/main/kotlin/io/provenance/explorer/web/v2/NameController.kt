package io.provenance.explorer.web.v2

import io.provenance.explorer.service.NameService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
    fun getNameTree() = ResponseEntity.ok(nameService.getNameMap())
}
