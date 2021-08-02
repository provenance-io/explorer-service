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

### Features
* Added gas fee statistics (min, max, avg, std) per message type on single message transactions [#185](https://github.com/provenance-io/explorer-service/issues/185)

### Improvements

### Bug Fixes

### Data
* Added caching and aggregate tables for txs with single massages only [#185](https://github.com/provenance-io/explorer-service/issues/185)
  * Added `tx_single_message_cache` table
  * Added `tx_single_message_gas_stats_day` table
  * Added `tx_single_message_gas_stats_hour` table
  * Added `update_gas_stats` stored procedure


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

