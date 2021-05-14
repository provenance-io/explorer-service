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
