## [v5.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v5.1.0) - 2022-10-07
### Release Name: Symon Semeonis

### Features
* Added APIs for CoinMarketCap data [#388](https://github.com/provenance-io/explorer-service/issues/388)
    * GET `/api/v3/utility_token/historical_pricing` - Returns a list of daily historical pricing for the utility token, queryable by date range
    * GET `/api/v3/utility_token/latest_pricing` - Returns the latest pricing for the utility token
* Added APIs for Authz grant and Feegrant data on an address [#265](https://github.com/provenance-io/explorer-service/issues/265)
    * GET `/api/v3/grants/authz/{address}/grantee` - Returns a paginated list of authz grants granted to the address
    * GET `/api/v3/grants/authz/{address}/granter` - Returns a paginated list of authz grants granted by the address
    * GET `/api/v3/grants/feegrant/{address}/grantee` - Returns a paginated list of feegrant allowances granted to the address
    * GET `/api/v3/grants/feegrant/{address}/granter` - Returns a 501 until the supporting gRPC query is applied to the chain
* Added dedicated API for getting Attribute Names by address [#346](https://github.com/provenance-io/explorer-service/issues/346)
    * GET `/api/v2/names/{address}/owned` - Returns attribute names owned by the address; applies to contracts as well

### Improvements
* Removed calls to Figment [#385](https://github.com/provenance-io/explorer-service/issues/385)
* Added API to check account balance at height [#387](https://github.com/provenance-io/explorer-service/issues/387)
* Updated Prov Protos from 1.11.0 to 1.12.0 [#391](https://github.com/provenance-io/explorer-service/issues/391)
* Added proper exception on invalid JWT token [#394](https://github.com/provenance-io/explorer-service/issues/394)
* Added caching on image urls from Keybase [#396](https://github.com/provenance-io/explorer-service/issues/396)
    * Reduces calls to Keybase so it doesnt yell at us
* Updated the MsgTypeSet enum to include an `additionalType` list that can be included in the module-based tx list [#405](https://github.com/provenance-io/explorer-service/issues/405)
    * This allows assets created and traded via smart contract to show those transactions under the asset page
* Add list of associated values to a tx hash [#340](https://github.com/provenance-io/explorer-service/issues/340)

### Bug Fixes
* Swapped `Long` for `BigDecimal` in places that will need it, specifically for large nhash quantities [#380](https://github.com/provenance-io/explorer-service/issues/380)
* Rounding circulating supply, community pool values as those are whole `nhash` values [#381](https://github.com/provenance-io/explorer-service/issues/381)
* Fix Null pointer on reading to EventFee object
* Fixed calcs for tx fees, including failed [#387](https://github.com/provenance-io/explorer-service/issues/387)
* Updated tx hash uniqueness to include block height [#384](https://github.com/provenance-io/explorer-service/issues/384)
    * Added `blockHeight` query param to APIs where tx hash is used as the identifier
    * Updated the tx detail response to include a list of other blocks the hash was found in
* Fixed calc for tx fees, including success, during 1.11 block range [#397](https://github.com/provenance-io/explorer-service/issues/397)
* Fixed error on validator fetch when validator is no longer in state [#395](https://github.com/provenance-io/explorer-service/issues/395)
* Fixed IBC denom ingest when its a known denom instead of `ibc/...`

### Data
* Migration 1.67 - Adding CMC data caching support [#388](https://github.com/provenance-io/explorer-service/issues/388)
    * Added `token_historical_daily` table
    * Inserted `cache_update` record for `utility_token_latest` key
* Migration 1.68 - Fixing `tx_fee` data, again [#387](https://github.com/provenance-io/explorer-service/issues/387)
    * Update `recipient` values that are blank to `null`
    * Delete duplicate fee records due to incorrect unique key
    * Correct the unique key, and update the ingest procedure to use it
* Migration 1.69 - Adding height uniqueness to tx hash keys [#384](https://github.com/provenance-io/explorer-service/issues/384)
    * Addressed `tx_cache`, `gov_deposit`, `tx_address_join`, `tx_feepayer`, `tx_gas_cache`, `tx_marker_join`,
      `tx_nft_join`, `tx_single_message_cache`, `tx_sm_code`, `tx_sm_contract`, `validator_market_rate`, `add_tx()`, `add_tx_debug()`
* Migration 1.70 - Update `tx_fee` records for non-fee failures [#387](https://github.com/provenance-io/explorer-service/issues/387)
    * Updated `BASE_FEE_USED` fee type to `0`
    * Deleted any extra records
* Migration 1.71 - Update rollup functions [#397](https://github.com/provenance-io/explorer-service/issues/397)
    * Updated `update_gas_fee_volume()` function to aggregate fees from `tx_fee` table
    * Updated `insert_tx_gas_cache()` function to reset `processed` to false and update the fee amount
        * This will ensure the rollups will be reaggregated
    * Updated `insert_validator_market_rate()` to update the market rate value on duplicate
    * Updated `insert_tx_fees()` to delete all related fee records first, then insert
    * Inserted new `cache_update` record for 1.11 block range to reprocess those blocks for correct fees
* Migration 1.72 - Update `validator_state` to include new `removed` field [#395](https://github.com/provenance-io/explorer-service/issues/395)
    * Added `removed` column
    * Updated `current_validator_state` view to include the new column
    * Updated `get_validator_list()` function to include new column
    * Updated `get_all_validator_state()` function to include new column
* Migration 1.73 - Add `address_image` table to store image urls against an address [#396](https://github.com/provenance-io/explorer-service/issues/396)
    * Added `address_image` table
    * Updated `current_validator_state` view to include the new table join
    * Updated `get_validator_list()` function to include new column from the view
    * Updated `get_all_validator_state()` function to include new column from the view
* Migration 1.74 - Update known IBC denoms
    * Identifies known IBC denoms (NOT `ibc/...`)
    * Finds the base, updates records as needed, and deletes the errant known IBC denoms
* Migration 1.75 - Add function to fetch associated values to a tx hash [#340](https://github.com/provenance-io/explorer-service/issues/340)
    * Added `get_tx_associated_values()` function
