<!--
Guiding Principles:

Changelogs are for humans, not machines.
There should be an entry for every single version.
The same types of changes should be grouped.
Versions and sections should be linkable.
The latest version comes first.
The release date of each version is displayed.
Mention whether you follow Semantic Versioning.

Usage:

Change log entries are to be added to the Unreleased section under the
appropriate stanza (see below). Each entry should ideally include a tag and
the Github issue reference in the following format:

* (<tag>) \#<issue-number> message

The issue numbers will later be link-ified during the release process so you do
not have to worry about including a link manually, but you can if you wish.

Types of changes (Stanzas):

"Features" for new features.
"Improvements" for changes in existing functionality.
"Deprecated" for soon-to-be removed features.
"Bug Fixes" for any bug fixes.
"Client Breaking" for breaking CLI commands and REST routes used by end-users.
"Data" for any data changes.
Ref: https://keepachangelog.com/en/1.0.0/
-->

## Unreleased

## [v3.3.2](https://github.com/provenance-io/explorer-service/releases/tag/v3.3.2) - 2022-02-16
### Release Name: Friar Julian

### Bug Fixes
* Updated Migration 1.42 and 1.43 to work on a new database
* Fixed upgrade versions pulling releases that shouldnt be known

## [v3.3.1](https://github.com/provenance-io/explorer-service/releases/tag/v3.3.1) - 2022-02-16
### Release Name: Rabban Bar Sauma

### Improvements
* Added sort to TxMessage response to order by msg idx as seen in the Tx object

### Bug Fixes
* Fixed `proposal_monitor` buildInsert() to use correct value

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

## [v3.2.0](https://github.com/provenance-io/explorer-service/releases/tag/v3.2.0) - 2022-01-18
### Release Name: John Carpini

### Features
* Added pricing-engine call for Assets [#276](https://github.com/provenance-io/explorer-service/issues/276)
  * Added per token / total price values on asset listview and asset detail
  * Added total AUM value to Spotlight record
  * Added per token / total price values to account balances
  * Added account AUM value
  * Added per token / total price values to account delegation rewards response
  * Updated returned prices to be null for unknown pricing [#285](https://github.com/provenance-io/explorer-service/issues/285)

### Improvements
* Updated Gradle to 7.3.3
* Removed Jcenter ~~, replacing dependent build with JitPack for now~~
* Replaced `khttp` with `ktor client` [#281](https://github.com/provenance-io/explorer-service/issues/281)
* Added `api/v2/validators/recent/abbrev` so FE doesn't have to call the main validator query for all validators [#282](https://github.com/provenance-io/explorer-service/issues/282)

## [v3.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v3.1.0) - 2022-01-07
### Release Name: Benjamin of Tudela

### Features
* Added API for chain upgrades [#246](https://github.com/provenance-io/explorer-service/issues/246)
  * API under `/api/v2/chain/upgrades`
* Added support for Tx Fee breakdown and Tx Feepayer identification [#252](https://github.com/provenance-io/explorer-service/issues/252)
  * Added `docs/txs/Feepayer.md` to lay out changes
  * Support for ingestion
  * Added support on tx query responses - updated field `fee` and new field `feepayer`
* Added API for chain prefixes [#269](https://github.com/provenance-io/explorer-service/issues/269)
  * API under `/api/v2/chain/prefixes`

### Improvements
* Updated ingestion to use the same TxData object for block-related data
* Increased local DB connection size to 40 to match container connection size
* Added `EXPLORER_HIDDEN_APIS` to allowing hiding of maintenance APIs from Swagger [#271](https://github.com/provenance-io/explorer-service/issues/271)
* Updated the Param set [#270](https://github.com/provenance-io/explorer-service/issues/270)

### Bug Fixes
* Updated `AccountDetail.publicKeys` to be a public key object showing the key and key type [#267](https://github.com/provenance-io/explorer-service/issues/267)
  ```json
  {
    ...
     "publicKeys": {
       "pubKey": "AireALVFyk8k1alZLcJLEdfaDFuyDua9eZsswvYsHUGN",
       "type": "secp256k1"
     },
    ...
  }
  ```

### Data
* Migration 1.39 - Added Tx fee and feepayer tables [#252](https://github.com/provenance-io/explorer-service/issues/252)
  * `tx_fee`, `tx_feepayer` tables
  * Insertion logic to prep the tables

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
* Fixed bug with active validator set not respecting staking param `max_validators` [#261](https://github.com/provenance-io/explorer-service/issues/261)

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

## [v2.4.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.4.0) - 2021-09-15
### Release Name: Bjarni Herjulfsson

### Features
* Upgraded versions [#172](https://github.com/provenance-io/explorer-service/issues/172), [#236](https://github.com/provenance-io/explorer-service/issues/236)
  * Provenance: v1.5.0 -> v1.7.0
  * Cosmos SDK: v0.42.6 -> v0.44.0
  * Added IBC Go: ** -> v1.1.0
  * Kotlin: 1.4.30 -> 1.5.30
  * Gradle: 6.8.3 -> 7.2.0
  * SpringBoot: 2.4.3 -> 2.5.4
* With upgraded versions, added in support for new Msg types and pubkey `secp256r1`
* Now pulling `wasmd` protos from `provenance-io/wasmd` instead of `CosmWasm/wasmd` [#238](https://github.com/provenance-io/explorer-service/issues/238)
  * Current version at v0.19.0
  * Pinned CosmWasm/wasmd protos to `v0.17.0` for backwards compatibility

### Improvements
* Translated Smart Contract msg bytes to human-readable json

## [v2.3.1](https://github.com/provenance-io/explorer-service/releases/tag/v2.3.1) - 2021-09-10
### Release Name: Ahmad ibn Fadlan

### Improvements
* Added checks to refresh the `current_validator_state` view
  * Should reduce the number of unnecessary refreshes
* Updated Spotlight Cache create to use the second top-most height actually in the database
  * This will prevent a race condition for saving blocks

### Bug Fixes
* Added `tx_message.msg_idx` to actually have message uniqueness
* Fixed the hashing function for tx_message to be deterministic

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

## [v2.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.1.0) - 2021-07-22
### Release Name: Xu Fu

### Features
* Added `msgType={msgType}` to `/api/v2/txs/{hash}/msgs` to allow for filtering based on `msgType` [#146](https://github.com/provenance-io/explorer-service/issues/146)
* Added `/api/v2/txs/types/tx/{hash}` to fetch msg types applicable to a single tx [#146](https://github.com/provenance-io/explorer-service/issues/146)
* Updated `/api/v2/accounts/{address}` to return `TokenCount` object [#140](https://github.com/provenance-io/explorer-service/issues/140)
  * Shows count of fungible and non-fungible tokens
* Added `/api/v2/params` to return parameters from the Grpc clients [#153](https://github.com/provenance-io/explorer-service/issues/153)

### Improvements
* Added `PagedResults.total` to return total record count [#146](https://github.com/provenance-io/explorer-service/issues/146)
* Updated `/api/v2/nft/scope/owner/{address}` to return a listview rather than list of `Scope.uuid` [#140](https://github.com/provenance-io/explorer-service/issues/140)
* Updated `NFTs.md` design doc to match newer design doc layouts
* Updated MissedBlocks insert to accommodate for out-of-sequence blocks (eg service playing catchup) [#143](https://github.com/provenance-io/explorer-service/issues/143)
* Added Kotlin lint check to github actions and fixed incorrectly formatted kotlin code [#61](https://github.com/provenance-io/explorer-service/issues/61)
* Created DB view for tx history data, making he UI chart faster [#117](https://github.com/provenance-io/explorer-service/issues/117)

### Bug Fixes
* Fixed wrong error message being populated on commission when an operator is jailed [#156](https://github.com/provenance-io/explorer-service/issues/156)
* Handled 500 error from jailed operators and used default value of 0 instead [#155](https://github.com/provenance-io/explorer-service/issues/155)
* Tx Msgs MetadataAddress types displayed as Base64 strings in UI [#145](https://github.com/provenance-io/explorer-service/issues/145)
* Processing new protos from v1.5.0 [#175](https://github.com/provenance-io/explorer-service/issues/175)
* Don't try to save missed blocks when current block height is 0 [#167](https://github.com/provenance-io/explorer-service/issues/167)
* Now updating proposal status every day at 12 AM UTC [#168](https://github.com/provenance-io/explorer-service/issues/168)
* Fixed NPE on .toObjectNode() for metadata call [#180](https://github.com/provenance-io/explorer-service/issues/180)
* Fixed bug where a validator does not have any signing info (which is used to populate some fields) [#178](https://github.com/provenance-io/explorer-service/issues/178)
  * Affected the Validator listview, filtered on 'Candidate'
  * Affected the Validator detail for the specific validator

### Data
* Added migration 20 for indices on `block_cache` [#117](https://github.com/provenance-io/explorer-service/issues/117)
* Created `block_cache_tx_history_day` and `block_cache_tx_history_hour` as views for better caching [#117](https://github.com/provenance-io/explorer-service/issues/117)

## [v2.0.1](https://github.com/provenance-io/explorer-service/releases/tag/v2.0.1) - 2021-06-16
### Release Name: Nehsi

### Bug Fixes
* HOTFIX: Removed tx msgs from listview and detail responses into a paginated API [#142](https://github.com/provenance-io/explorer-service/issues/142)
  * `/api/v2/txs/{hash}/msgs?page={page}&count={count}`

## [v2.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.0.0) - 2021-06-14
### Release Name: Leif Eriksson

### Features
* IBC denom API [#97](https://github.com/provenance-io/explorer-service/issues/97)
  * `/ibc/all`
* Add get escrow account address method for ibc [#119](https://github.com/provenance-io/explorer-service/issues/119)
* Gov Proposal API [#64](https://github.com/provenance-io/explorer-service/issues/64)
  * `/gov/proposals/all?page={page}&count={count}`
  * `/gov/proposals/{id}`
  * `/gov/proposals/{id}/votes`
  * `/gov/proposals/{id}/deposits?page={page}&count={count}`
  * `/gov/address/{address}/votes?page={page}&count={count}`
* IBC Channel API [#122](https://github.com/provenance-io/explorer-service/issues/122)
  * ~~`/ibc/channels/balances`~~
  * `/ibc/channels/status?status={status]`
* IBC Balance API [#132](https://github.com/provenance-io/explorer-service/issues/132)
  * `/ibc/channels/balances` -> `/ibc/balances/channel` -> Balances broken down by chain/channel/denom
  * `/ibc/balances/chain` -> Balances broken down by chain/denom
  * `/ibc/balances/denom` -> Balances broken down by denom
* Address-owned Names API [#92](https://github.com/provenance-io/explorer-service/issues/92)
  * `/accounts/{address}/attributes/owned`

### Improvements
* Removed hash conversion [#66](https://github.com/provenance-io/explorer-service/issues/66)
  * This will now be done on the frontend
* Ingesting denoms and addresses from IBC Txs [#97](https://github.com/provenance-io/explorer-service/issues/97)
* Improved failure statuses for APIs [#120](https://github.com/provenance-io/explorer-service/issues/120)
  * Now returning 404s, no message -> Typically due to no record from the db
    * `/api/v2/accounts/{address}` -> checks for a valid account address
    * `/api/v2/assets/detail/{id}`
    * `/api/v2/assets/detail/ibc/{id}`
    * `/api/v2/txs/{hash}`
    * `/api/v2/txs/{hash}/json`
    * `/api/v2/validators/height/{blockHeight}`
    * `/api/v2/validators/{id}`
    * `/api/v2/validators/{id}/commission`
  * Now returning 404s, with message -> This is due to the error coming from chain queries
    * `/api/v2/accounts/{address}/balances`
    * `/api/v2/accounts/{address}/delegations`
    * `/api/v2/accounts/{address}/unbonding`
    * `/api/v2/accounts/{address}/redelegations`
    * `/api/v2/accounts/{address}/rewards`
    * `/api/v2/assets/holders?id={id}`
    * `/api/v2/assets/metadata?id={id}`
    * `/api/v2/blocks/height/{height}`
    * `/api/v2/nft/scope/{addr}`
    * `/api/v2/nft/scope/owner/{addr}` -> returns 404 if invalid address
    * `/api/v2/nft/scope/{addr}/records`
    * `/api/v2/nft/validators/{id}/delegations/bonded`
    * `/api/v2/nft/validators/{id}/delegations/unbonding`
* Created a `docs` folder to store design docs
* Ingesting proposals, votes, deposits for Gov tx msgs [#64](https://github.com/provenance-io/explorer-service/issues/64)
* Updated protos - Provenance to v1.4.1, cosmos sdk to 0.42.5 [#128](https://github.com/provenance-io/explorer-service/issues/128)
* Ingesting IBC channels for IBC tx msgs [#122](https://github.com/provenance-io/explorer-service/issues/122)
* Added attributes assigned to an address, formatted to make sense [#92](https://github.com/provenance-io/explorer-service/issues/92)
* Updated missed_blocks count to pull from DB [#136](https://github.com/provenance-io/explorer-service/issues/136)

### Bug Fixes
* Properly sorting Validator listview [#112](https://github.com/provenance-io/explorer-service/issues/112)
* Removing `nft/scope/all` due to massive performance issues [#118](https://github.com/provenance-io/explorer-service/issues/118)
* Filtering out blanks when associating addresses to txs
* Fixed boolean check on NFT delete messages
* Added an address check on unknown accounts being requested -> checking for proper address prefix
* Adding blank check to NFT uuids
* Added `markerType` to asset listview response [#131](https://github.com/provenance-io/explorer-service/issues/131)
* Updated `AssetDetail.supply` from String to CoinStr [#137](https://github.com/provenance-io/explorer-service/issues/137)
* Updated `AssetList.supply` from String to CoinStr [#137](https://github.com/provenance-io/explorer-service/issues/137)
* Updated `AssetHolder.balance` to include `denom` [#137](https://github.com/provenance-io/explorer-service/issues/137)
* Updated MsgConverter `typeUrl.contains()` to `typeUrl.endsWith()` due to some msgs containing names of other msgs

## Client Breaking
* Account balances are now paginated [#102](https://github.com/provenance-io/explorer-service/issues/102)
  * `/{address}/balances?page={page}&count={count}`
  * Removed from Account Detail API
* Now handling IBC denoms in search [#103](https://github.com/provenance-io/explorer-service/issues/103)
  * `/assets/detail/{id}`, `/assets/detail/ibc/{id}` -> as used by FE, these should resolve naturally
  * `/assets/{id}/holders` -> `/assets/holders?id={denom}`
  * `/assets/{id}/metadata` -> `/assets/metadata?id={denom}` with `id` optional, returning full list of metadata

### Data
* Added migration 14 for token count to staking_validator_cache [#112](https://github.com/provenance-io/explorer-service/issues/112)
* Added migration 15 for increasing denom length, marker_cache holding non-marker denoms [#103](https://github.com/provenance-io/explorer-service/issues/103)
* Added migration 16 for storing Gov data [#64](https://github.com/provenance-io/explorer-service/issues/64)
* Added migration 17 for storing IBC Channel data [#122](https://github.com/provenance-io/explorer-service/issues/122)
* Added migration 18 for storing IBC Balance Ledger data [#132](https://github.com/provenance-io/explorer-service/issues/132)
* Added migration 19 for storing missed blocks data [#136](https://github.com/provenance-io/explorer-service/issues/136)

## [v1.5.0](https://github.com/provenance-io/explorer-service/releases/tag/v1.5.0) - 2021-05-21
### Release Name: Dicuil

### Features
* Ingesting Scope transactions [#29](https://github.com/provenance-io/explorer-service/issues/29)
* Queries for getAllScopes, getScope, and getScopeRecords [#29](https://github.com/provenance-io/explorer-service/issues/29)

### Improvements
* Added MetadataAddress conversion class from Provenance repo [#29](https://github.com/provenance-io/explorer-service/issues/29)
* Updated tx type listings, now sorted on module/type
* Casting grpc lists to mutable lists
* Added utility to search if accounts have a designated denom

### Bug Fixes
* Casting fees to BigInteger now, as the number is too big for Int
* Asset detail now pulling supply from correct source [#105](https://github.com/provenance-io/explorer-service/issues/105)
* Tx query with filter no applying distinct to get correct number of records [#106](https://github.com/provenance-io/explorer-service/issues/106)
* Accounts now update with all info [#107](https://github.com/provenance-io/explorer-service/issues/107)
* Handling account delegation error if no delegations for account [#108](https://github.com/provenance-io/explorer-service/issues/108)

### Data
* Added tables for NFT data and tx joins [#29](https://github.com/provenance-io/explorer-service/issues/29)
* Added migration to fix account records that have missing data [#107](https://github.com/provenance-io/explorer-service/issues/107)

## [v1.4.0](https://github.com/provenance-io/explorer-service/releases/tag/v1.4.0) - 2021-05-14
### Release Name: Posidonius

### Features
* Accounts can now be queried for delegations, unbonding delegations, redelegations, and rewards [#96](https://github.com/provenance-io/explorer-service/issues/96)

### Improvements
* Updated tx type listing to group IBC calls together
* Updated Provenance protos to v1.3.0 [#94](https://github.com/provenance-io/explorer-service/issues/94)

### Bug Fixes
* Handle unknown accounts now [#85](https://github.com/provenance-io/explorer-service/issues/85)
* Fixed where code was looking for non-existent denoms
* Fixed where a block's validators list was incorrect [#91](https://github.com/provenance-io/explorer-service/issues/91)
* Added missing explicit IBC.header proto [#93](https://github.com/provenance-io/explorer-service/issues/93)

### Client Breaking
* ValidatorDelegation object changes [#96](https://github.com/provenance-io/explorer-service/issues/96)
  * address -> delegatorAddr
  * ADDED validatorSrcAddr, validatorDstAddr, initialBal -> Should not be breaking
* Removed queryDenom from CoinStr object [#66](https://github.com/provenance-io/explorer-service/issues/66)

## [v1.3.0](https://github.com/provenance-io/explorer-service/releases/tag/v1.3.0) - 2021-05-06
### Release Name: Flóki-Vilgerðarson

### Improvements
* Added name to AccountDetail object; applicable only for ModuleAccount type [#83](https://github.com/provenance-io/explorer-service/issues/83)
* Added ModuleAccount name to applicable moniker lists [#83](https://github.com/provenance-io/explorer-service/issues/83)
* Added pagination and status search to getAssets() API [#78](https://github.com/provenance-io/explorer-service/issues/78)
* Removed `initial supply` as it was causing confusion, and left circulation as the current total on chain [#78](https://github.com/provenance-io/explorer-service/issues/78)
* Updated gradle task `downloadProtos` to include CosmWasm/wasmd proto set, Cosmos ibc protos [#62](https://github.com/provenance-io/explorer-service/issues/62)

### Bug Fixes
* Fixed bug where Tx message types were being overwritten with "unknown"

## [v1.2.0](https://github.com/provenance-io/explorer-service/releases/tag/v1.2.0) - 2021-04-30

### Improvements
* Updated Provenance protos to 1.2.0 [#80](https://github.com/provenance-io/explorer-service/issues/80)

### Bug Fixes
* Now handling no rewards on a validator [#76](https://github.com/provenance-io/explorer-service/issues/76)
* Fixed no transaction on address lookup for txs [#76](https://github.com/provenance-io/explorer-service/issues/76)
* Fixed erroring asset holder count [#79](https://github.com/provenance-io/explorer-service/issues/79)

## [v1.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v1.1.0) - 2021-04-29

### Improvements
* On asset list and asset detail, the supply object now contains `initial` and `circulation` rather than `circulation` and `total` [#52](https://github.com/provenance-io/explorer-service/issues/52)
* All coin objects (with a balance and a denom) now have a `queryDenom` value to be used for querying based on that denom [#52](https://github.com/provenance-io/explorer-service/issues/52)
* `/api/v2/assets/{id}/holders` now returns a paginated list [#52](https://github.com/provenance-io/explorer-service/issues/52)
* Asset's current supply now comes from bank module rather than the marker object [#52](https://github.com/provenance-io/explorer-service/issues/52)
* Added tx/msg look up to block/tx queries to fill in missing info under the hood [#71](https://github.com/provenance-io/explorer-service/issues/71)

### Bug Fixes
* Now updating account signatures when updating accounts [#63](https://github.com/provenance-io/explorer-service/issues/63)

### Data
* Added more indices for more complex joins [#71](https://github.com/provenance-io/explorer-service/issues/71)

## [v1.0.0](https://github.com/provenance-io/explorer-service/releases/tag/vx.x.x) - 2021-04-23

### Features
* Added a script to pull in protos used in the client [#5](https://github.com/provenance-io/explorer-service/issues/5)
* Added a `build.gradle` to allow the protos to be compiled to java on build task [#5](https://github.com/provenance-io/explorer-service/issues/5)
* Added support for multisig on transactions, accounts, and validators (limited) [#9](https://github.com/provenance-io/explorer-service/issues/9)
* Added Validator-specific APIs [#15](https://github.com/provenance-io/explorer-service/issues/15)
* Added Account-specific transaction API [#11](https://github.com/provenance-io/explorer-service/issues/11)
* Added working docker-compose script [#10](https://github.com/provenance-io/explorer-service/issues/10)
* Dockerize Database [#36](https://github.com/provenance-io/explorer-service/issues/36)
* Added Min Gas Fee statistics [#30](https://github.com/provenance-io/explorer-service/issues/30)

### Improvements
* Added templates and build workflow
* Updated `/api/v2/validators/height/{blockHeight}` to translate page to offset for proper searching
* Upgraded to gRpc blockchain calls [#17](https://github.com/provenance-io/explorer-service/issues/17)
* Upgraded to Kotlin gradle
* Updated the transaction database tables to allow for better searching [#15](https://github.com/provenance-io/explorer-service/issues/15)
* Updated block queries to return the same object with updated info [#13](https://github.com/provenance-io/explorer-service/issues/13)
* Updated native queries to use exposed query-building instead
* Updated transaction queries to return same objects as other similar queries [#14](https://github.com/provenance-io/explorer-service/issues/14)
* Upgraded Exposed library from 0.17.1 to 0.29.1
* Updated the copy_proto script to be more intuitive
* Because we have a docker gha workflow that builds, I removed the branch-> main designation for build gha [#34](https://github.com/provenance-io/explorer-service/issues/34)
* Added gradle custom `downloadProtos` task to download protos instead of shell script.
* Updated Validator.bond_height to come from real numbers
* Added Asset status to the asset objects
* Added current gas fee to Validator objects
* Initial conversions from nhash to hash [#44](https://github.com/provenance-io/explorer-service/issues/44)
* Changed ownership on assets [#45](https://github.com/provenance-io/explorer-service/issues/45)
* Improved query performance [#53](https://github.com/provenance-io/explorer-service/issues/53)
* Added release workflow [#58](https://github.com/provenance-io/explorer-service/issues/58)

### Bug Fixes
* Translated the signatures back to usable addresses
* Fixed `gas/statistics` from previous updates
* Changed out Int to BigInteger in API return objects [#34](https://github.com/provenance-io/explorer-service/issues/34)
* Added distinct to Tx query so it actually only brings back tx objects/counts [#34](https://github.com/provenance-io/explorer-service/issues/34)
* Added total tx count to Spotlight object [#34](https://github.com/provenance-io/explorer-service/issues/34)
* Made Block Hash look like a hash [#34](https://github.com/provenance-io/explorer-service/issues/34)
* Updated a couple docker scripts cuz I moved things around in the last commit [#34](https://github.com/provenance-io/explorer-service/issues/34)
* Fixed a bug where candidate/jailed validators were being filtered out of results
* Added missing protos [#55](https://github.com/provenance-io/explorer-service/issues/55)
* Properly handling Multi Msg txs [#56](https://github.com/provenance-io/explorer-service/issues/56)

### Data
* Added a db index on tx_cache.height
* Added additional db indices
* Added migrations for Tx, BlockProposer, TxMessage, and TxMessageType data points [#57](https://github.com/provenance-io/explorer-service/issues/57)

## PRE-HISTORY

