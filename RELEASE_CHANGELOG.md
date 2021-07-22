## [v2.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v2.1.0) - 2021-07-22
### Release Name: Xu Fu

### Features
* Added `msgType={msgType}` to `/api/v2/txs/{hash}/msgs` to allow for filtering based on `msgType` [#146](https://github.com/provenance-io/explorer-service/issues/146)
* Added `/api/v2/txs/types/tx/{hash}` to fetch msg types applicable to a single tx [#146](https://github.com/provenance-io/explorer-service/issues/146)
* Updated `/api/v2/accounts/{address}` to return `TokenCount` object [#140](https://github.com/provenance-io/explorer-service/issues/140)
  * Shows count of fungible and non-fungible tokens
* Added `/api/v2/params` to return parameters from the Grpc clients [#153](https://github.com/provenance-io/explorer-service/issues/153)

### Improvements
* Added `PagedResults.total` to return total record count [#146](https://github.com/provenance-io/explorer-service/issues/146)
* Updated `/api/v2/nft/scope/owner/{address}` to return a listview rather than list of `Scope.uuid` [#140](https://github.com/provenance-io/explorer-service/issues/140)
* Updated `NFTs.md` design doc to match newer design doc layouts
* Updated MissedBlocks insert to accommodate for out-of-sequence blocks (eg service playing catchup) [#143](https://github.com/provenance-io/explorer-service/issues/143)
* Added Kotlin lint check to github actions and fixed incorrectly formatted kotlin code [#61](https://github.com/provenance-io/explorer-service/issues/61)
* Created DB view for tx history data, making he UI chart faster [#117](https://github.com/provenance-io/explorer-service/issues/117)

### Bug Fixes
* Fixed wrong error message being populated on commission when an operator is jailed [#156](https://github.com/provenance-io/explorer-service/issues/156)
* Handled 500 error from jailed operators and used default value of 0 instead [#155](https://github.com/provenance-io/explorer-service/issues/155)
* Tx Msgs MetadataAddress types displayed as Base64 strings in UI [#145](https://github.com/provenance-io/explorer-service/issues/145)
* Processing new protos from v1.5.0 [#175](https://github.com/provenance-io/explorer-service/issues/175)
* Don't try to save missed blocks when current block height is 0 [#167](https://github.com/provenance-io/explorer-service/issues/167)
* Now updating proposal status every day at 12 AM UTC [#168](https://github.com/provenance-io/explorer-service/issues/168)
* Fixed NPE on .toObjectNode() for metadata call [#180](https://github.com/provenance-io/explorer-service/issues/180)
* Fixed bug where a validator does not have any signing info (which is used to populate some fields) [#178](https://github.com/provenance-io/explorer-service/issues/178)
  * Affected the Validator listview, filtered on 'Candidate'
  * Affected the Validator detail for the specific validator

### Data
* Added migration 20 for indices on `block_cache` [#117](https://github.com/provenance-io/explorer-service/issues/117)
* Created `block_cache_tx_history_day` and `block_cache_tx_history_hour` as views for better caching [#117](https://github.com/provenance-io/explorer-service/issues/117)
