## [v3.4.0](https://github.com/provenance-io/explorer-service/releases/tag/v3.4.0) - 2022-02-28
### Release Name: William of Rubruck

### Features
* Added Market Rate APIs [#309](https://github.com/provenance-io/explorer-service/issues/309)
    * `/api/v2/validators/{address}/market_rate/period` - time period of rates per validator
    * `/api/v2/chain/market_rate/period` - time period of rates for chain
    * `api/v2/chain/market_rate` - avg values for chain for block count

### Improvements
* Added generic batch upsert functionality
* Expanded top 5 accounts on token distribution list
* Updated API naming to reflect current state
* Added doc to track all the statistics being presented

### Bug Fixes
* Reworked Tx Fee calcs to align with how market rates are actually determined
    * Added `validator_market_rate`
    * Removed `min_gas_fee` from `block_proposer`
    * Renamed `validator_gas_fee_cache` to `validator_market_rate_stats`
    * Renamed `chain_gas_fee_cache` to `chain_market_rate_stats`

### Data
* Migration 1.49 - Add validator market rate
    * Added `validator_market_rate` table and indices
    * Inserted data into `validator_market_rate`
    * Dropped `block_proposer.min_gas_fee`
    * Renamed `validator_gas_fee_cache` to `validator_market_rate_stats`, accompanying columns
    * Renamed `chain_gas_fee_cache` to `chain_market_rate_stats`, accompanying columns
    * Updated procedure `add_block()`
    * Updated procedure `add_tx()`
    * Updated type `tx_update`
* Migration 1.50 - Update tx fee records
    * Deleted and rebuilt `tx_fee` records
    * Deleted `token_distribution_amounts` records - will be rebuilt by service
