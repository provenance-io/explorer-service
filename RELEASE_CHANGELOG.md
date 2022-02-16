## [v3.3.0](https://github.com/provenance-io/explorer-service/releases/tag/v3.3.0) - 2022-02-15
### Release Name: Chang Chun

### Features
* Added `24 Hr Power Change` to the Validator listview response [#277](https://github.com/provenance-io/explorer-service/issues/277)

### Improvements
* Reworked block/transaction ingestion [#274](https://github.com/provenance-io/explorer-service/issues/274)
    * Created procedures to save all block/ tx data at once
        * Should eliminate rogue data insertion
* Updated params to represent percentage values better
* Added value rollups to delegation/redelegation/unbonding APIS for both Accounts and Validators [#290](https://github.com/provenance-io/explorer-service/issues/290)
    * Generic map to add different values for different PagedResults responses as needed
* General cleanup of APIs
* Added `denom_trace` to IBC balance objects
* Added error logging to pricing engine calls
* Updated pricing engine calls to be async, pulling new values and saving [#296](https://github.com/provenance-io/explorer-service/issues/296)
* Updated NHASH pricing to pull from gecko API
* Updated Version Upgrade data to pull from GitHub API
* Removed unused fields from Validator listviews
* Updated param fields to use the correct param names
* Added caps to paginated APIs - set to 200 max count

### Bug Fixes
* Updated token stats to use correct Bonded value
* Removing bad denoms [#289](https://github.com/provenance-io/explorer-service/issues/289)
    * For unknown denom types (NOT IBC), set status to `MARKER_STATUS_UNSPECIFIED` so they don't show up in listviews, but are still saved to the database for reference
* Updated Migration 1.41 to include exception handling in procedures
* Updated BlockRetry to use the main block insertion method rather than the intermediary, added exception handling
* Fixed logic to correctly show validator votes in a block
* Fixed the ingestion procedure re: `signature_join` data

### Data
* Migration 1.40 - Data clean up [#274](https://github.com/provenance-io/explorer-service/issues/274)
    * Removed duplicate data for `tx_message`, `tx_msg_event`, `tx_msg_event_attr`
* Migration 1.41 - Update `tx_msg_event_attr` table wth fields [#274](https://github.com/provenance-io/explorer-service/issues/274)
    * Added `attr_idx`, `attr_hash` to `tx_msg_event_attr`
* Migration 1.42 - Update `tx_msg_event_attr` Part 1 [#274](https://github.com/provenance-io/explorer-service/issues/274)
    * Updated `attr_idx` with data
* Migration 1.43 - Update `tx_msg_event_attr` Part 2 [#274](https://github.com/provenance-io/explorer-service/issues/274)
    * Updated `attr_hash` with data
* Migration 1.44 - Added ingestion procedures [#274](https://github.com/provenance-io/explorer-service/issues/274)
    * Added unique indices for all tx-related tables
    * Added types `tx_event`, `tx_msg`, `tx_update`, `block_update`
    * Added procedures `add_tx(tx_update, integer, timestamp)`, `add_block(block_update)`
* Migration 1.45 - Invalid denom cleanup [#289](https://github.com/provenance-io/explorer-service/issues/289)
    * Updated `marker_cache.status` to `MARKER_STATUS_UNSPECIFIED` for all `marker_cache.marker_type = 'DENOM'`
* Migration 1.46 - Update Ingestion procedure
    * Fixed signature_join insertion to use the correct join key value
* Migration 1.47 - Add asset pricing tables [#296](https://github.com/provenance-io/explorer-service/issues/296)
    * Added tables `asset_pricing` and `cache_update`
    * Inserted initial record for `pricing_update` cache record
* Migration 1.48 - Add cache update for chain versions
    * Inserted initial record for `chain_releases` cache record
