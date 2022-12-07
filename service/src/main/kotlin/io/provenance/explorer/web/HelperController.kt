package io.provenance.explorer.web

import io.provenance.explorer.config.ExplorerProperties.Companion.UNDER_MAINTENANCE
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping(path = ["/api"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Api(
    value = "Helper controller", produces = "application/json", consumes = "application/json", tags = ["Helper"],
    description = "This contains helper apis for infrastructure"
)
@Hidden
class HelperController {

    @ApiOperation("Returns true if node is under maintenance, else false")
    @GetMapping("/maintenance_mode")
    fun getMaintenanceMode() = ResponseEntity.ok(UnderMaintenance(UNDER_MAINTENANCE))
}

data class UnderMaintenance(val isUnderMaintenance: Boolean)
