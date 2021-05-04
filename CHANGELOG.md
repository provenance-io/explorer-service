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
"API Breaking" for breaking exported APIs used by developers building on SDK.
"State Machine Breaking" for any changes that result in a different AppState given same genesisState and txList.
Ref: https://keepachangelog.com/en/1.0.0/
-->

## Unreleased

### Improvements
* Added name to AccountDetail object; applicable only for ModuleAccount type #83
* Added ModuleAccount name to applicable moniker lists #83
* Added pagination and status search to getAssets() API #78
* Removed `initial supply` as it was causing confusion, and left circulation as the current total on chain #78

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

