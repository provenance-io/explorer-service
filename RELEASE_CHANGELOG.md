## [v5.2.0](https://github.com/provenance-io/explorer-service/releases/tag/v5.2.0) - 2022-10-28
### Release Name: Diogo Afonso

### Features
* Added CSV downloads for the Tx History chart data [#406](https://github.com/provenance-io/explorer-service/issues/406)
    * Deprecated `/api/v2/txs/history` in favor of `/api/v3/txs/history`
    * GET `/api/v3/txs/history/download` - Using the given filters, exports the data behind the Tx History chart
    * GET `/api/v3/accounts/tx_history` - Returns data for the Account Tx History chart
    * GET `/api/v3/accounts/tx_history/download` - Using the given filters, exports the data behind the Account Tx History chart
    * Added `MONTH` as a granularity option
* Added APIs to get account balance for a single denom [#418](https://github.com/provenance-io/explorer-service/issues/418)
    * GET `/api/v3/accounts/{address}/balances/{denom}` - Returns balance for the given account and denom
    * GET `/api/v3/accounts/{address}/balances/utility_token` - Returns balance for the utility token for the given account
    * `/api/v3/accounts/{address}/balances/{height}` -> `/api/v3/accounts/{address}/balances_at_height` - Added `denom` as query param

### Improvements
* Extracted `nhash` as the utility token out into an ENV to be configurable [#399](https://github.com/provenance-io/explorer-service/issues/399)
    * Also extracted the base denom decimal places, voting power multiplier, floor gas price
* Merging DLOB and CMC volumes for more accurate values

### Bug Fixes
* Now using the CMC price for the utility token instead of from PE [#407](https://github.com/provenance-io/explorer-service/issues/407)
* Hotfix - Fixed fee calc on success with msg fees
* Fix error where weighted vote values were being exponentialized [#424](https://github.com/provenance-io/explorer-service/issues/424)

### Data
* Migration 1.74 - Add index on `hash` to `tx_cache`
* Migration 1.77 - Add `tx_timestamp` to `tx_fee` [#406](https://github.com/provenance-io/explorer-service/issues/406)
* Migration 1.78 - Add `tx_timestamp` to `tx_feepayer` [#406](https://github.com/provenance-io/explorer-service/issues/406)
* Migration 1.79 - Add `tx_timestamp` to `tx_message` [#406](https://github.com/provenance-io/explorer-service/issues/406)
* Migration 1.80 - Add `tx_hash_id` to `tx_gas_cache` [#406](https://github.com/provenance-io/explorer-service/issues/406)
* Migration 1.81 - Updating ingest procedures, add materialized views [#406](https://github.com/provenance-io/explorer-service/issues/406)
    * Updated associated ingest procedures
    * Created `tx_history_chart_data_hourly`, `tx_type_data_hourly`, `fee_type_data_hourly` materialized views, with data
