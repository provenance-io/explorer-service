## [v4.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v4.1.0) - 2022-03-24
### Release Name: Abu Bakr II

### Features
* API for validator uptime statistics [#260](https://github.com/provenance-io/explorer-service/issues/260)
    * `/api/v2/validators/uptime`
* API for chain AUM time series [#291](https://github.com/provenance-io/explorer-service/issues/291)
    * `/api/v2/chain/aum/list`
* API for validator commission history [#300](https://github.com/provenance-io/explorer-service/issues/300)
    * `/api/v2/validators/{address}/commission/history`

### Improvements
* Added uptime back to ValidatorSummary object [#260](https://github.com/provenance-io/explorer-service/issues/260)
* Updated uptime calc for ValidatorDetail [#260](https://github.com/provenance-io/explorer-service/issues/260)
* Removed `.isActive()` extension, which was inaccurate disregarding active set count [#260](https://github.com/provenance-io/explorer-service/issues/260)
* Now inserting `tx_sm_code` record when a SC code is identified and correlated with a governance proposal [#319](https://github.com/provenance-io/explorer-service/issues/319)
* Can now search by any marker's denom unit name [#321](https://github.com/provenance-io/explorer-service/issues/321)
* Using pricing-engine pricing for `nhash`, made calcs uniform for all denoms and prices [#321](https://github.com/provenance-io/explorer-service/issues/321)

### Bug Fixes
* Fixed how the Array exposed set works [#260](https://github.com/provenance-io/explorer-service/issues/260)
* Added single quote replacer function for transcribing objects for DB insertion [#300](https://github.com/provenance-io/explorer-service/issues/300)

### Data
* Migration 1.52 - Creating procedure `get_all_validator_state()` [#260](https://github.com/provenance-io/explorer-service/issues/260)
    * Used to fetch all validators with their current actual state
        * replaces using an inaccurate extension
* Migration 1.53 - Add Chain AUM table [#291](https://github.com/provenance-io/explorer-service/issues/291)
    * Added `chain_aum_hourly` table
    * Inserted data
* Migration 1.54 - Modify `validator_state` [#300](https://github.com/provenance-io/explorer-service/issues/300)
    * Updated `validator_state` to include `commission_rate` column, updated with current data
    * Updated `current_validator_state` materialized view
    * Updated `get_validator_list()`, `get_all_validator_state()` functions
* Migration 1.55 - Add debug procedures [#300](https://github.com/provenance-io/explorer-service/issues/300)
    * Used to debug why a block/tx doesnt get saved correctly; needed for prod
* Migration 1.56 - Adding `tx_hash_id` to governance tables [#319](https://github.com/provenance-io/explorer-service/issues/319)
    * Updated `gov_proposal`, `gov_vote`, `gov_deposit` to include `tx_hash_id`
    * Updated `add_tx()`, `add_tx_debug()` procedures to include the new fields
    * Inserting matching `tx_sm_code` records for SC codes created via governance proposal
* Migration 1.57 - Adding `marker_unit` table [#321](https://github.com/provenance-io/explorer-service/issues/321)
