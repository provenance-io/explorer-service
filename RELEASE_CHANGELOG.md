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
