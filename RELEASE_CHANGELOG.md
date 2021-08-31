## [v2.3.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.3.0) - 2021-08-31
### Release Name: Erik the Red

### Features
* Added gas fee statistics (min, max, avg, std) per message type on single message transactions [#185](https://github.com/provenance-io/explorer-service/issues/185)
* Added gas fee volume [#186](https://github.com/provenance-io/explorer-service/issues/186)
* Added heatmap endpoint `/api/v2/txs/heatmap` of when txs are submitted during the day/week [#187](https://github.com/provenance-io/explorer-service/issues/187)
* Added token statistics for circulation supply, community pool and bonded supply [#188](https://github.com/provenance-io/explorer-service/issues/188)
* Updated Validators to return an image URL from Keybase for their profile image [#200](https://github.com/provenance-io/explorer-service/issues/200)
* Added distribution of hash between sets of accounts [#189](https://github.com/provenance-io/explorer-service/issues/189)
* Added validator block latency endpoint `/api/v2/validators/{id}/latency` [#199](https://github.com/provenance-io/explorer-service/issues/199)

### Improvements
* Added global boolean to disregard historical data check once its no longer needed
    * Reduces slowdown between block processing
* Reworked tx history count calcs to be more efficient [#220](https://github.com/provenance-io/explorer-service/issues/220)
* Reworked how validator state was being stored [#222](https://github.com/provenance-io/explorer-service/issues/222)
    * No longer updates, instead append-only to reduce processing time
* Reworked `/api/v2/validators/recent` & `/api/v2/validators/height/{blockHeight}` to be more efficient with data [#222](https://github.com/provenance-io/explorer-service/issues/222)
* Reworked how spotlight data was being stored [#221](https://github.com/provenance-io/explorer-service/issues/221)
    * No longer updates, instead append-only to reduce processing time
    * New record every 5 seconds -> no TTL for now
* Added `isProposer` and `didVote` flags to validators at height api [#183](https://github.com/provenance-io/explorer-service/issues/183)
    * Broke out fetching validators are height from recent validators functions to be cleaner

### Bug Fixes
* Fixed update of the daily gas stats causes hourly data to be missed [#210](https://github.com/provenance-io/explorer-service/issues/210)
* Fixed nullable fields in Migration 26
* Fixed null pointer for bad Keybase identity [#224](https://github.com/provenance-io/explorer-service/issues/224)
* Added image url to BlockSummery object [#224](https://github.com/provenance-io/explorer-service/issues/224)
* Fixed Spotlight data to pull correct block votes vs validator set for block

### Data
* Added caching and aggregate tables for txs with single messages only [#185](https://github.com/provenance-io/explorer-service/issues/185)
    * Added `tx_single_message_cache` table
    * Added `tx_single_message_gas_stats_day` table
    * Added `tx_single_message_gas_stats_hour` table
    * Added `update_gas_fee_stats` stored procedure
* Added aggregate, cache table(s) and stored procedure for gas fee volume [#186](https://github.com/provenance-io/explorer-service/issues/186)
    * Added `tx_gas_cache` table
    * Added `tx_gas_fee_volume_day` table
    * Added `tx_gas_fee_volume_hour` table
    * Added `update_gas_fee_volume` stored procedure
* Added cache table, stored procedure for tx history counts [#220](https://github.com/provenance-io/explorer-service/issues/220)
    * Added `block_tx_count_cache` table
    * Added `update_block_cache_hourly_tx_counts` stored procedure
* Removed unused indices
* Added append-only cache table, materialized view for validator state [#222](https://github.com/provenance-io/explorer-service/issues/222)
    * Added `validator_state` table
    * Added `current_validator_state` materialized view
    * Dropped unused columns from `staking_validator_cache` table
* Updated `block_proposer` to include `block_latency` [#221](https://github.com/provenance-io/explorer-service/issues/221)
    * Added `update_block_latency()` stored procedure
