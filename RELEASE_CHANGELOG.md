## [v4.3.0](https://github.com/provenance-io/explorer-service/releases/tag/v4.3.0) - 2022-06-23
### Release Name: Wang Dayuan

### Features
* Separated out Proposal votes into a paginated API [#362](https://github.com/provenance-io/explorer-service/issues/362)
    * Deprecated `/api/v2/gov/proposals/{id}/votes` in favor of `/api/v3/gov/proposals/{id}/votes`
    * Moved Voting params and counts into the proposal detail / proposal listview responses
* Add Name APIs [#134](https://github.com/provenance-io/explorer-service/issues/134)
    * `/api/v2/names/tree` - tree map of names on chain, including restriction and owner

### Improvements
* Now saving applicable block heights to pull governance param sets [#341](https://github.com/provenance-io/explorer-service/issues/341)
    * This is used to populate past proposals/votes/deposits based on the contemporary parameters on chain
* Now have the ability to fetch data at height from the chain
* Added Exposed functionality for Arrays and ArrayAgg Postgres function

### Bug Fixes
* Fixed the Validator missed block count
* Added else case for IBC Recvs where the effected Recv is in the same tx as an uneffected Recv, which makes the block error out
* Added VotWeighted msg type to `getGovMsgDetail()` function
* Fixed how addresses were being associated with txs

### Data
* Migration 1.64 - Updates for gov params at height [#341](https://github.com/provenance-io/explorer-service/issues/341)
    * Updated `gov_proposal` with `deposit_param_check_height`, `voting_param_check_height`, inserted data
    * Updated `insert_gov_proposal()` procedure
    * Created `get_last_block_before_timestamp()` function
* Migration 1.65 - Add naming tree [#134](https://github.com/provenance-io/explorer-service/issues/134)
    * Created `name` table, and inserted records
