## [v5.11.0](https://github.com/provenance-io/explorer-service/releases/tag/v5.11.0) - 2024-08-27

### Improvements

* Updated Prov Protos to 1.19.0 [#527](https://github.com/provenance-io/explorer-service/pull/527)
* Updated gRPC query to use `query` field instead of `events` field [#523](https://github.com/provenance-io/explorer-service/pull/523)
* Fixed transaction details endpoint by handling Base64 and string encoding changes [#525](https://github.com/provenance-io/explorer-service/pull/525)

### Bug Fixes

* Fixed issue with Proto deserialization incorrectly matching `cosmos.upgrade.v1beta1.CancelSoftwareUpgradeProposal` as `cosmos.upgrade.v1beta1.SoftwareUpgradeProposal`, ensuring accurate type URL handling. [#524](https://github.com/provenance-io/explorer-service/pull/524)
* Update osmosis pricing query with new required field `coinMinimalDenom` [#526](https://github.com/provenance-io/explorer-service/pull/526)
* Fix group save tx processing [#526](https://github.com/provenance-io/explorer-service/issues/528), [#526](https://github.com/provenance-io/explorer-service/issues/529)
* Fix validator proposer priority number type [#530](https://github.com/provenance-io/explorer-service/issues/530)