package io.provenance.explorer.web.pulse

import io.provenance.explorer.domain.models.explorer.pulse.EntityLedgeredAsset
import io.provenance.explorer.domain.models.explorer.pulse.EntityLedgeredAssetDetail
import io.provenance.explorer.domain.models.explorer.pulse.ExchangeSummary
import io.provenance.explorer.domain.models.explorer.pulse.PulseAssetSummary
import io.provenance.explorer.domain.models.explorer.pulse.TransactionSummary
import io.provenance.explorer.model.base.PagedResults
import io.provenance.explorer.service.PulseMetricService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.jetbrains.exposed.sql.SortOrder
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping(
    path = ["/api/pulse/asset"],
    produces = [MediaType.APPLICATION_JSON_VALUE]
)
@Tag(
    name = "Pulse Assets",
    description = "Pulse asset endpoint"
)
class PulseAssetController(private val pulseMetricService: PulseMetricService) {

    @Operation(summary = "Exchange-traded Asset Summaries")
    @GetMapping("/summary/list")
    fun getPulseAssetSummaries(
        @RequestParam(required = false) search: String?,
                               @RequestParam(required = false) sortOrder: List<SortOrder>?,
                               @RequestParam(required = false) sortColumn: List<String>?
    ): List<PulseAssetSummary> =
        pulseMetricService.pulseAssetSummaries().filter {
            // this is a small list so we can get away with this
            search.isNullOrBlank() ||
                    it.name.contains(search, ignoreCase = true) ||
                    it.symbol.contains(search, ignoreCase = true) ||
                    it.display.contains(search, ignoreCase = true) ||
                    it.base.contains(search, ignoreCase = true) ||
                    it.description.contains(search, ignoreCase = true)
        }.sortedWith(
            Comparator { a, b ->
            if (sortColumn == null || sortOrder == null) return@Comparator 0

            for (i in sortColumn.indices) {
                val column = sortColumn[i]
                val order = sortOrder[i]

                val comparisonResult = when (column) {
                    "name" -> compareValues(
                        a.name.ifEmpty { a.base },
                        b.name.ifEmpty { b.base }
                    )
                    "price" -> compareValues(a.priceTrend?.currentQuantity ?: BigDecimal.ZERO, b.priceTrend?.currentQuantity ?: BigDecimal.ZERO)
                    "marketCap" -> compareValues(a.marketCap, b.marketCap)
                    "volume" -> compareValues(a.volumeTrend?.currentQuantity ?: BigDecimal.ZERO, b.volumeTrend?.currentQuantity ?: BigDecimal.ZERO)
                    else -> throw IllegalArgumentException("Invalid sort column: $column")
                }

                if (comparisonResult != 0) {
                    return@Comparator if (order == SortOrder.ASC) comparisonResult else -comparisonResult
                }
            }
            0
        }
        )

    /**
     * Note that `denom` is a required request param instead of a path variable
     * because denoms like `nft/blahblahblah` exist and are not valid path variables.
     */
    @Operation(summary = "Asset exchange module-based details")
    @GetMapping("/exchange/summary")
    fun getAssetExchangeSummary(@RequestParam(required = true) denom: String): List<ExchangeSummary> =
        pulseMetricService.exchangeSummaries(denom)

    /**
     * See previous comment about `denom` being a request param
     * Note that page is zero indexed (i.e. page 0 is the first page)
     */
    @Operation(summary = "Asset exchange module-based transactions")
    @GetMapping("/transaction/summary")
    fun getAssetTransactionSummary(
        @RequestParam(required = true) denom: String,
                                   @RequestParam(required = false) page: Int = 0,
                                   @RequestParam(required = false) count: Int = 10,
                                   @RequestParam(required = false) sortOrder: List<SortOrder>?,
                                   @RequestParam(required = false) sortColumn: List<String>?
    ): PagedResults<TransactionSummary> =
        pulseMetricService.transactionSummaries(denom, count, page, sortOrder.orEmpty(), sortColumn.orEmpty())

    @Operation(summary = "Ledgered assets by entity")
    @GetMapping("/ledgered/by/entity")
    fun getLedgeredAssetsByEntity(
        @RequestParam(required = false) @Min(1) page: Int = 1,
        @RequestParam(required = false) @Min(1) @Max(200) count: Int = 10,
        @RequestParam(required = false) sortOrder: List<SortOrder>?,
        @RequestParam(required = false) sortColumn: List<String>?
    ): PagedResults<EntityLedgeredAsset> =
        pulseMetricService.ledgeredAssetsByEntity(count, page, sortOrder.orEmpty(), sortColumn.orEmpty())

    @Operation(summary = "Ledgered assets for a given entity")
    @GetMapping("/ledgered/by/entity/{uuid}")
    fun getLedgeredAssetsByEntity(
        @Parameter(description = "The uuid for the entity") @PathVariable uuid: String,
    ): EntityLedgeredAsset =
        pulseMetricService.ledgeredAssetsByEntity(uuid)

    @Operation(summary = "Ledgered asset list by entity")
    @GetMapping("/ledgered/by/entity/{uuid}/list")
    fun getLedgeredAssetListByEntity(
        @Parameter(description = "The uuid for the entity") @PathVariable uuid: String,
        @RequestParam(required = false) @Min(1) page: Int = 1,
        @RequestParam(required = false) @Min(1) @Max(200) count: Int = 10,
        @RequestParam(required = false) sortOrder: List<SortOrder>?,
        @RequestParam(required = false) sortColumn: List<String>?
    ): PagedResults<EntityLedgeredAssetDetail>? =
        pulseMetricService.ledgeredAssetListByEntity(uuid, count, page, sortOrder.orEmpty(), sortColumn.orEmpty())
}
