## [v2.4.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.4.0) - 2021-09-15
### Release Name: Bjarni Herjulfsson

### Features
* Upgraded versions [#172](https://github.com/provenance-io/explorer-service/issues/172), [#236](https://github.com/provenance-io/explorer-service/issues/236)
    * Provenance: v1.5.0 -> v1.7.0
    * Cosmos SDK: v0.42.6 -> v0.44.0
    * Added IBC Go: ** -> v1.1.0
    * Kotlin: 1.4.30 -> 1.5.30
    * Gradle: 6.8.3 -> 7.2.0
    * SpringBoot: 2.4.3 -> 2.5.4
* With upgraded versions, added in support for new Msg types and pubkey `secp256r1`
* Now pulling `wasmd` protos from `provenance-io/wasmd` instead of `CosmWasm/wasmd` [#238](https://github.com/provenance-io/explorer-service/issues/238)
    * Current version at v0.19.0
    * Pinned CosmWasm/wasmd protos to `v0.17.0` for backwards compatibility

### Improvements
* Translated Smart Contract msg bytes to human-readable json
