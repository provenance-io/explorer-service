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
