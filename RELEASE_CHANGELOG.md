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
