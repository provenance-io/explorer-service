## [v6.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v6.0.0) - 2024-11-15

### Features
* Remove flow dependency and collect on-chain navs in local database [#574](https://github.com/provenance-io/explorer-service/pull/574)
* Add hash price support for nav values from on chain events [#543](https://github.com/provenance-io/explorer-service/pull/543)
* Integrate on-chain NAV data into historical hash price calculations [#555](https://github.com/provenance-io/explorer-service/pull/555)
* Use on-chain NAV data for latest asset pricing on markers [#556](https://github.com/provenance-io/explorer-service/pull/556)

### Improvements

* Add limit of 1 to missing block queries [#544](https://github.com/provenance-io/explorer-service/pull/544)
* Removes the logic for calculating `running_count` and `total_count` in the `missed_blocks` [#548](https://github.com/provenance-io/explorer-service/pull/548)
* Remove update block latency procedure call [#550](https://github.com/provenance-io/explorer-service/pull/550)
* Docker images at tag `latest` for main branch merges [#551](https://github.com/provenance-io/explorer-service/pull/551)
* Refactor account processing implementation to be more efficient [#552](https://github.com/provenance-io/explorer-service/issues/552)
* Remove spotlight from caching to the database [#532](https://github.com/provenance-io/explorer-service/issues/532)
* Update keep alive times for flow api grpc calls [#558](https://github.com/provenance-io/explorer-service/pull/558)
* Updates to asset pricing table will set data column to null [#562](https://github.com/provenance-io/explorer-service/pull/562)
* Remove `running_count` and `total_count` columns from the `missed_blocks` table [#549](https://github.com/provenance-io/explorer-service/issues/549)
* Remove `spotlight_cache` table [#559](https://github.com/provenance-io/explorer-service/pull/559)
* Remove the `data` column from the `asset_pricing` table [#563](https://github.com/provenance-io/explorer-service/issues/563)
* Updated Prov Protos to 1.20.1 [#575](https://github.com/provenance-io/explorer-service/pull/575)

### Bug Fixes

* Fix pagination off by one calls to flow grpc [#561](https://github.com/provenance-io/explorer-service/pull/561) 
