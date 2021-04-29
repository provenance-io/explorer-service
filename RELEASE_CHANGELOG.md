## [v1.1.0](https://github.com/provenance-io/provenance/releases/tag/v1.1.0) - 2021-04-29

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
