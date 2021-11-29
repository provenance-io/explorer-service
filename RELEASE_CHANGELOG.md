## [v3.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v3.0.0) - 2021-11-29
### Release Name: Thorfinn Karlsefni

### Features
* Updated `/cosmos/base/tendermint/v1beta1/blocks/{height}` to come from Figment url instead of local node [#231](https://github.com/provenance-io/explorer-service/issues/231)
    * Defaults to Figment, with local node as backup in case Figment call fails
    * Added new properties `explorer.figment-apikey`, `explorer.figment-url` defaulted to Figure's key and test url
* Added ingestion and API for Smart Contract msg types [#70](https://github.com/provenance-io/explorer-service/issues/70)
    * API under `/api/v2/smart_contract`
    * Proposal monitoring to find when a `StoreCodeProposal` is created
* Generating Kotlin protobuf classes using official Protobuf Kotlin DSL [#254](https://github.com/provenance-io/explorer-service/issues/254)
* Added `/api/v2/validators/missed_blocks` and `/api/v2/validators/missed_blocks/distinct` APIs [#257](https://github.com/provenance-io/explorer-service/issues/257)
    * Used for devops validator monitoring

### Improvements
* Added a block tx retry table to store heights that failed to fetch txs [#232](https://github.com/provenance-io/explorer-service/issues/232)
    * Added async to then retry those blocks
* Decreased time to update proposal status
    * from every day at 12 am to every hour at the 30-minute mark
* Reworked how events were scraped for denoms and addresses
* Reworked how msg type was being derived [#248](https://github.com/provenance-io/explorer-service/issues/248)
    * Now deriving `module`, `type` from `proto.typeUrl` instead of scraping events for possible values
* Reworked account insert/update to use upsert instead
* Adding initial use cases for Kotlin Coroutines [#255](https://github.com/provenance-io/explorer-service/issues/255)
* Improved NFT API with additional data [#253](https://github.com/provenance-io/explorer-service/issues/253)
    * Added APIs for specification json responses

### Bug Fixes
* Added additional MsgConverter lines to handle older tx msg types
* Downgraded Spring Boot Version: 2.5.4 -> 2.4.3
* Downgraded jackson-databind dependency: 2.12.2 -> 2.11.2
* Updated signature finding based on msg type [#249](https://github.com/provenance-io/explorer-service/issues/249)
* Added API response handler for IllegalArgumentExceptions [#245](https://github.com/provenance-io/explorer-service/issues/245)
* Fixed NullPointerException when `session_id_components` was actually null
* Fixed bug in `update_gas_fee_volume()` procedure [#258](https://github.com/provenance-io/explorer-service/issues/258)
* Fixed bug with active validator set not repecting staking param `max_validators` [#261](https://github.com/provenance-io/explorer-service/issues/261)

### Data
* Migration 1.32 - Added `block_tx_retry` table [#232](https://github.com/provenance-io/explorer-service/issues/232)
* Migration 1.33 - Added Smart Contract tables and keys [#70](https://github.com/provenance-io/explorer-service/issues/70)
    * `sm_code`, `tx_sm_code`, `sm_contract`, `tx_sm_contract` tables
    * Updated `account` with column `is_contract`
* Migration 1.34 - Added unique key to `tx_message_type` [#248](https://github.com/provenance-io/explorer-service/issues/248)
    * Upserted records to make records uniform
* Migration 1.35 - Added `proposal_monitor` table [#70](https://github.com/provenance-io/explorer-service/issues/70)
* Migration 1.36 - Updated `update_gas_fee_volume()` procedure [#258](https://github.com/provenance-io/explorer-service/issues/258)
* Migration 1.37 - Added `missed_block_periods()` function to find missed blocks for inputs [#257](https://github.com/provenance-io/explorer-service/issues/257)
* Migration 1.38 - Added `get_validator_list()` function to properly identify state, and gather list for inputs [#261](https://github.com/provenance-io/explorer-service/issues/261)
