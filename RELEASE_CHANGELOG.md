## [v2.2.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.2.0) - 2021-07-29
### Release Name: Hyecho

### Features
* Added dedicated Gov Tx API at `/api/v2/txs/module/gov?address={address}&etc...` to fetch txs with specific data [#193](https://github.com/provenance-io/explorer-service/issues/193)
* Added tables for tx events and event attributes that are populated when tx messages are saved [#115](https://github.com/provenance-io/explorer-service/issues/115)

### Improvements
* Updated front-facing docs

### Bug Fixes
* Ensure governance proposal votes take precedence by block height
* Use actual count of validator results for listview page count [#201](https://github.com/provenance-io/explorer-service/issues/201)
* Fixed Refresh of Materialized View for Tx history slow down block saves [#196](https://github.com/provenance-io/explorer-service/issues/196)

### Data
* Added `tx_msg_event`, `tx_msg_event_attr` for tx event caching [#115](https://github.com/provenance-io/explorer-service/issues/115)
* Removed `block_cache_tx_history_day`, `block_cache_tx_history_hour` [#196](https://github.com/provenance-io/explorer-service/issues/196)
* Added `block_cache_hourly_tx_counts` for better caching of tx counts [#196](https://github.com/provenance-io/explorer-service/issues/196)
