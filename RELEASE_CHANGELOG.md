## [v2.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.0.0) - 2021-06-14
### Release Name: Leif Eriksson

### Features
* IBC denom API [#97](https://github.com/provenance-io/explorer-service/issues/97)
    * `/ibc/all`
* Add get escrow account address method for ibc [#119](https://github.com/provenance-io/explorer-service/issues/119)
* Gov Proposal API [#64](https://github.com/provenance-io/explorer-service/issues/64)
    * `/gov/proposals/all?page={page}&count={count}`
    * `/gov/proposals/{id}`
    * `/gov/proposals/{id}/votes`
    * `/gov/proposals/{id}/deposits?page={page}&count={count}`
    * `/gov/address/{address}/votes?page={page}&count={count}`
* IBC Channel API [#122](https://github.com/provenance-io/explorer-service/issues/122)
    * ~~`/ibc/channels/balances`~~
    * `/ibc/channels/status?status={status]`
* IBC Balance API [#132](https://github.com/provenance-io/explorer-service/issues/132)
    * `/ibc/channels/balances` -> `/ibc/balances/channel` -> Balances broken down by chain/channel/denom
    * `/ibc/balances/chain` -> Balances broken down by chain/denom
    * `/ibc/balances/denom` -> Balances broken down by denom
* Address-owned Names API [#92](https://github.com/provenance-io/explorer-service/issues/92)
    * `/accounts/{address}/attributes/owned`

### Improvements
* Removed hash conversion [#66](https://github.com/provenance-io/explorer-service/issues/66)
    * This will now be done on the frontend
* Ingesting denoms and addresses from IBC Txs [#97](https://github.com/provenance-io/explorer-service/issues/97)
* Improved failure statuses for APIs [#120](https://github.com/provenance-io/explorer-service/issues/120)
    * Now returning 404s, no message -> Typically due to no record from the db
        * `/api/v2/accounts/{address}` -> checks for a valid account address
        * `/api/v2/assets/detail/{id}`
        * `/api/v2/assets/detail/ibc/{id}`
        * `/api/v2/txs/{hash}`
        * `/api/v2/txs/{hash}/json`
        * `/api/v2/validators/height/{blockHeight}`
        * `/api/v2/validators/{id}`
        * `/api/v2/validators/{id}/commission`
    * Now returning 404s, with message -> This is due to the error coming from chain queries
        * `/api/v2/accounts/{address}/balances`
        * `/api/v2/accounts/{address}/delegations`
        * `/api/v2/accounts/{address}/unbonding`
        * `/api/v2/accounts/{address}/redelegations`
        * `/api/v2/accounts/{address}/rewards`
        * `/api/v2/assets/holders?id={id}`
        * `/api/v2/assets/metadata?id={id}`
        * `/api/v2/blocks/height/{height}`
        * `/api/v2/nft/scope/{addr}`
        * `/api/v2/nft/scope/owner/{addr}` -> returns 404 if invalid address
        * `/api/v2/nft/scope/{addr}/records`
        * `/api/v2/nft/validators/{id}/delegations/bonded`
        * `/api/v2/nft/validators/{id}/delegations/unbonding`
* Created a `docs` folder to store design docs
* Ingesting proposals, votes, deposits for Gov tx msgs [#64](https://github.com/provenance-io/explorer-service/issues/64)
* Updated protos - Provenance to v1.4.1, cosmos sdk to 0.42.5 [#128](https://github.com/provenance-io/explorer-service/issues/128)
* Ingesting IBC channels for IBC tx msgs [#122](https://github.com/provenance-io/explorer-service/issues/122)
* Added attributes assigned to an address, formatted to make sense [#92](https://github.com/provenance-io/explorer-service/issues/92)
* Updated missed_blocks count to pull from DB [#136](https://github.com/provenance-io/explorer-service/issues/136)

### Bug Fixes
* Properly sorting Validator listview [#112](https://github.com/provenance-io/explorer-service/issues/112)
* Removing `nft/scope/all` due to massive performance issues [#118](https://github.com/provenance-io/explorer-service/issues/118)
* Filtering out blanks when associating addresses to txs
* Fixed boolean check on NFT delete messages
* Added an address check on unknown accounts being requested -> checking for proper address prefix
* Adding blank check to NFT uuids
* Added `markerType` to asset listview response [#131](https://github.com/provenance-io/explorer-service/issues/131)
* Updated `AssetDetail.supply` from String to CoinStr [#137](https://github.com/provenance-io/explorer-service/issues/137)
* Updated `AssetList.supply` from String to CoinStr [#137](https://github.com/provenance-io/explorer-service/issues/137)
* Updated `AssetHolder.balance` to include `denom` [#137](https://github.com/provenance-io/explorer-service/issues/137)
* Updated MsgConverter `typeUrl.contains()` to `typeUrl.endsWith()` due to some msgs containing names of other msgs

## Client Breaking
* Account balances are now paginated [#102](https://github.com/provenance-io/explorer-service/issues/102)
    * `/{address}/balances?page={page}&count={count}`
    * Removed from Account Detail API
* Now handling IBC denoms in search [#103](https://github.com/provenance-io/explorer-service/issues/103)
    * `/assets/detail/{id}`, `/assets/detail/ibc/{id}` -> as used by FE, these should resolve naturally
    * `/assets/{id}/holders` -> `/assets/holders?id={denom}`
    * `/assets/{id}/metadata` -> `/assets/metadata?id={denom}` with `id` optional, returning full list of metadata

### Data
* Added migration 14 for token count to staking_validator_cache [#112](https://github.com/provenance-io/explorer-service/issues/112)
* Added migration 15 for increasing denom length, marker_cache holding non-marker denoms [#103](https://github.com/provenance-io/explorer-service/issues/103)
* Added migration 16 for storing Gov data [#64](https://github.com/provenance-io/explorer-service/issues/64)
* Added migration 17 for storing IBC Channel data [#122](https://github.com/provenance-io/explorer-service/issues/122)
* Added migration 18 for storing IBC Balance Ledger data [#132](https://github.com/provenance-io/explorer-service/issues/132)
* Added migration 19 for storing missed blocks data [#136](https://github.com/provenance-io/explorer-service/issues/136)
