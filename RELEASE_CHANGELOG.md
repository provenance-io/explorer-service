## [v5.4.0](https://github.com/provenance-io/explorer-service/releases/tag/v5.4.0) - 2022-12-15
### Release Name: Jean de Béthencourt

### Features
* Add CSV download for Token Historical Pricing [#453](https://github.com/provenance-io/explorer-service/issues/453)
    * GET `/api/v3/utility_token/historical_pricing/download` - Using the given filters, exports the data behind the Hash Price History Chart
* Updated to Provenance version 1.13.0 [#435](https://github.com/provenance-io/explorer-service/issues/435)
    * Added support for groups data
    * Added support for new governance v1 actions
    * Added minimal support for IBC updates, nft module, rewards module
    * TODO: Add in support for rewards module
* Publish API model set [#464](https://github.com/provenance-io/explorer-service/issues/464)
* Publish API client set [#468](https://github.com/provenance-io/explorer-service/issues/468)

### Improvements
* Updated Kotlin version `1.5.31` -> `1.6.21` [#342](https://github.com/provenance-io/explorer-service/issues/342)
    * Gradle `7.3.3` -> `7.4.2`
    * SpringBoot `2.5.6` -> `2.6.6`
    * Kotlin Coroutines `1.5.2` -> `1.6.4`
    * Apache commons-text `1.9` -> `1.10`
    * Apache HttClient `4.5.12` -> `5.2`
    * BouncyCastle `1.69` -> `1.70`
    * Exposed `0.34.1` -> `0.38.2`
    * Jackson `2.12.5` -> `2.13.2`
    * Jackson Protobuf `0.9.12` -> `0.9.13`
    * Ktor `1.5.7` -> `2.1.3`
    * Grpc `1.40.1` -> `1.50.2`
    * Protobuf `3.19.1` -> `3.21.9`
    * NOTE: These updates had minimal impact, but Springfox 3.0.0 and Springboot 2.6.x do not play nicely. Had
      to add a hacky fix; will probably set some time aside to switch to SpringDoc.
* Added API to return only Account flags [#447](https://github.com/provenance-io/explorer-service/issues/447)
    * GET `/api/v3/accounts/{address}/flags` - returns `isContract`, `isVesting` flags on the account
* Added Tx Msg logs to `/api/v2/txs/{hash}/msgs` [#400](https://github.com/provenance-io/explorer-service/issues/400)
* Added Tx-level event logs to `/api/v2/txs/{hash}` [#401](https://github.com/provenance-io/explorer-service/issues/401)
    * Decodes the data to human-readable strings
* Now caching FT/NFT counts per address [#448](https://github.com/provenance-io/explorer-service/issues/448)
    * This is done on the fly, asynchronously
    * The Account detail API pulls from the cached values
* Deprecated a few APIs to align the API sets better [#468](https://github.com/provenance-io/explorer-service/issues/468)
    * `/api/v2/assets/detail/{denom}` -> `/api/v3/assets/{denom}`
    * `/api/v2/assets/detail/ibc/{hash}` -> `/api/v3/assets/ibc/{hash}`
    * `/api/v2/assets/holders` -> `/api/v3/assets/{denom}/holders`
    * `/api/v2/assets/metadata` -> `/api/v3/assets/metadata`
    * `/api/v2/gov/proposals/all` -> `/api/v3/gov/proposals`
    * `/api/v2/gov/address/{address}/votes` -> `/api/v3/gov/votes/{address}`
    * `/api/v2/smart_contract/codes/all` -> `/api/v3/smart_contract/code`
    * `/api/v2/smart_contract/contract/all` -> `/api/v3/smart_contract/contract`
    * `/api/v2/txs/types/tx/{hash}` -> `/api/v3/txs/{hash}/types`
    * `/api/v2/validators/recent` -> `/api/v3/validators/recent`

### Data
* Migration 1.86 - Add `process_queue`, `account_token_counts` [#448](https://github.com/provenance-io/explorer-service/issues/448)
    * Adds both tables, inserts records for all accounts in the `account` table
* Migration 1.87 - 1.13.0 updates [#435](https://github.com/provenance-io/explorer-service/issues/435)
    * Added `owner`, `is_group_policy` fields to `account`
    * Updated `gov_proposal`, `gov_vote` to hold new v1 data objects
    * Groups changes
    * Ingest procedure updates where needed
    * Inserts already-seen groups txs into the retry table to be reprocessed
