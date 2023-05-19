## [v5.5.0](https://github.com/provenance-io/explorer-service/releases/tag/v5.5.0) - 2023-05-19
### Release Name: John Cabot

### Features
* Validator Delegation Program metrics [#482](https://github.com/provenance-io/explorer-service/issues/482)
    * Added support to calculate and return validator metrics used by the Validator Delegation Program
    * Added client support

### Improvements
* Add date parameters to `/api/v3/txs/heatmap` [#462](https://github.com/provenance-io/explorer-service/issues/462)
    * Added `fromDate`, `toDate`, `timeframe`
    * Defaulting to `FOREVER` to return all data

### Bug Fixes
* Fixed `/api/v3/validators` url
* Now handling no fee amount in the tx
* Now handling Exec'd governance msgs, and properly handling weights from v1.Gov msgs
* Added object mapper and query parameters to `api-client` [#487](https://github.com/provenance-io/explorer-service/issues/487)

### Data
* Migration 1.88 - Add `block_time_spread`, `validator_metrics` [#482](https://github.com/provenance-io/explorer-service/issues/482)
    * Adds view, table to support calculating and storing metrics for the Validator Delegation Program
