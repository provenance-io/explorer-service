## [v4.2.0](https://github.com/provenance-io/explorer-service/releases/tag/v4.2.0) - 2022-05-24
### Release Name: Odoric of Pordenone

### Features
* Add notification/announcement support [#328](https://github.com/provenance-io/explorer-service/issues/328)
    * `/api/v2/notifications/proposals` - List of NON-UPGRADE OPEN proposals, List of UPGRADE OPEN proposals
    * `/api/v2/notifications/upgrades` - List of expected, scheduled upgrades
    * `/api/v2/notifications/announcement` - PUT an announcement to be displayed in Explorer
    * `/api/v2/notifications/announcement/all` - Paginated list of announcements, can filter by `fromDate`
    * `/api/v2/notifications/announcement/{id}` - GET an announcement by ID
    * `/api/v2/notifications/announcement/{id}` - DELETE an existing announcement
* Add new IBC APIs [#336](https://github.com/provenance-io/explorer-service/issues/336)
    * `/api/v2/txs/ibc/chain/{ibcChain}` - txs per IBC chain id, query params supporting narrowing by channel
    * `/api/v2/ibc/channels/src_port/{srcPort}/src_channel/{srcChannel}/relayers` - relayers by channel

### Improvements
* Update vote ingestion to include Weighted Votes [#323](https://github.com/provenance-io/explorer-service/issues/323)
* Updated the Dockerfile to include support for Vault secrets
* IBC ingestion supports actual IBC flow [#336](https://github.com/provenance-io/explorer-service/issues/336)
* Increased frequency for asset-price polling from every 30 minutes to every 15 minutes
* Removing 0-sequenced account balances from circulation totals [#335](https://github.com/provenance-io/explorer-service/issues/335)
* Updated existing Smart Contract Code and Contract queries with new query params [#339](https://github.com/provenance-io/explorer-service/issues/339)
    * `/api/v2/smart_contract/codes/all` - added filters on `creator`, `has_contracts`
    * `/api/v2/smart_contract/contract/all` - added filters on `creator`, `admin`, `label`
    * `/api/v2/smart_contract/code/{id}/contracts` - added filters on `creator`, `admin`
* Added API to fetch list of unique non-UUID contract labels [#339](https://github.com/provenance-io/explorer-service/issues/339)
    * `/api/v2/smart_contract/contract/labels`
* Updated the Pricing Engine API pathing
* Added @jarrydallison as a CODEOWNER

### Bug Fixes
* Updated how the service runs on an empty DB
* Fixed pagination on Validators at Height API [#326](https://github.com/provenance-io/explorer-service/issues/326)
* Fixed how gov proposals were being processed if they hadn't gotten out of the deposit period [#330](https://github.com/provenance-io/explorer-service/issues/330)
* Fixed processing of IBC msgs [#331](https://github.com/provenance-io/explorer-service/issues/331)
    * Related to incorrect value mapping
    * Added sort to block retry so they process in height order
* Fixed how the weighted vote percentage was being ingested
* Fixed chain version fetch from Github [#349](https://github.com/provenance-io/explorer-service/issues/349)
    * Due to a limit of count on records from the Github API
* Fixed Validators' missed block count, bond height values [#352](https://github.com/provenance-io/explorer-service/issues/352)
    * Missed blocks -> incorrect values being calculated
    * Bond Height -> query for signing infos was being limited, so not returning all records, thus missing some

### Data
* Migration 1.58 - Add weight to proposal votes [#323](https://github.com/provenance-io/explorer-service/issues/323)
    * Updated `gov_vote` to add `weight` column
    * Split out tx ingestion pieces into their own routines for easier updating
    * Inserting existing weighted votes
* Migration 1.59 - Add `cache_update` records for spotlight processing, default avg block time
    * Used to ensure values are present for some initial data processing
* Migration 1.60 - Add `announcements` table [#328](https://github.com/provenance-io/explorer-service/issues/328)
* Migration 1.61 - IBC Ledger updates [#336](https://github.com/provenance-io/explorer-service/issues/336)
    * Modifying `ibc_ledger` - adding `sequence`, `unique_hash`, removing acknowledgement columns
    * Inserting into new columns, updating unique key
    * Created `ibc_ledger_ack` table to hold all corresponding response txs to a ledger record, and inserted records
    * Created `tx_ibc` table to hold all IBC-related txs, and inserted prelim records
    * Created `ibc_relayer` table to hold relayer addresses associated with a channel, and inserted records
* Migration 1.62 - Ingestion procedure updates [#336](https://github.com/provenance-io/explorer-service/issues/336)
    * Updated `insert_ibc_ledger()` procedure, created ingest procedures for new tables
    * Updated `add_tx_debug()`, `add_tx()` procedures with new ingestions
* Migration 1.63 - Add function for `uuid_or_null()` [#339](https://github.com/provenance-io/explorer-service/issues/339)
    * Added function to determine if a string is a UUID, and if not, return null
