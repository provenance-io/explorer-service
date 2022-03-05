# Statistics Breakdown
This document breaks down what each of the statistic measurements does.

- [Gas Volume - `tx_gas_cache`](#gas-volume----tx-gas-cache-)
  + [Processing](#processing)
  * [Daily Rollup - `tx_gas_fee_volume_day`](#daily-rollup----tx-gas-fee-volume-day-)
  * [Hourly Rollup - `tx_gas_fee_volume_hour`](#hourly-rollup----tx-gas-fee-volume-hour-)
  * [API Accessibility](#api-accessibility)
- [Single-Msg Gas Statistics - `tx_single_message_cache`](#single-msg-gas-statistics----tx-single-message-cache-)
  + [Processing](#processing-1)
  * [Daily Rollup - `tx_single_message_gas_stats_day`](#daily-rollup----tx-single-message-gas-stats-day-)
  * [Hourly Rollup - `tx_single_message_gas_stats_hour`](#hourly-rollup----tx-single-message-gas-stats-hour-)
  * [API Accessibility](#api-accessibility-1)
- [Market Rate - `validator_market_rate`](#market-rate----validator-market-rate-)
  + [Processing](#processing-2)
  * [Validator Rollup - `validator_market_rate_stats`](#validator-rollup----validator-market-rate-stats-)
  * [Chain Rollup - `chain_market_rate_stats`](#chain-rollup----chain-market-rate-stats-)
  * [API Accessibility](#api-accessibility-2)
- [Token Statistics](#token-statistics)
  + [Processing](#processing-3)
  * [API Accessibility](#api-accessibility-3)
- [Token Distribution - `token_distribution_amounts`](#token-distribution----token-distribution-amounts-)
  + [Processing](#processing-4)
  * [API Accessibility](#api-accessibility-4)
- [Tx Heatmap](#tx-heatmap)
  + [Processing](#processing-5)
  * [API Accessibility](#api-accessibility-5)
- [Chain AUM - `chain_aum_hourly`](#chain-aum----chain-aum-hourly-)
  + [Processing](#processing-6)
  * [API Accessibility](#api-accessibility-6)

## Gas Volume - `tx_gas_cache`
This table records the gas and fee stats per transaction.
* `gas_wanted`
  * The amount of gas specified by the tx signer
  * This is the max gas to be used in processing the tx
  * This is the base gas amount used to estimate the fee to be sent with the tx
* `gas_used`
  * The amount of gas actually consumed in processing the tx
  * This is the gas amount used to calculate the actual fee spent on processing
* `fee_amount`
  * The fee amount paid by the tx signer
  * This fee contains the gas fee and any additional msg fees
    * If the tx was successful, the full fee is paid
    * If the tx failed, the full fee minus the additional msg fees is paid

#### Processing
* Processing of the rollup tables occurs through procedure 
  * `update_gas_fee_volume()`
* This occurs at the start of every hour server time.
  * `@Scheduled(cron = "0 0 0/1 * * ?")`

### Daily Rollup - `tx_gas_fee_volume_day`
This table is a rollup of the above fields at a daily level.

### Hourly Rollup - `tx_gas_fee_volume_hour`
This table is a rollup of the above fields at an hourly level.

### API Accessibility
* `/api/v2/gas/volume?fromDate={fromDate}&toDate={toDate}&granularity={DAY,HOUR}`

----

## Single-Msg Gas Statistics - `tx_single_message_cache`
This table records the gas used by a single-msg tx.
* `gas_used`
    * The amount of gas actually consumed in processing the tx
    * This is the gas amount used to calculate the actual fee spent on processing

#### Processing
* Processing of the rollup tables occurs through procedure 
  * `update_gas_fee_stats()`
* This occurs at the start of every hour server time.
    * `@Scheduled(cron = "0 0 0/1 * * ?")`

### Daily Rollup - `tx_single_message_gas_stats_day`
This table contains calcs from of the above field at a daily level.
* `min_gas_used`
  * This is the absolute minimum gas used to process the msg type
* `max_gas_used`
    * This is the absolute maximum gas used to process the msg type
* `avg_gas_used`
    * This is the average gas used to process the msg type
* `stddev_gas_used`
    * This is the standard deviation of all `gas_used` values for the msg type
    * Calc'd by postgres as `coalesce(round(stddev_samp(gas_used)), 0)`

### Hourly Rollup - `tx_single_message_gas_stats_hour`
This table contains calcs from of the above field at an hourly level.
Same fields as `tx_single_message_gas_stats_day`.

### API Accessibility
* `/api/v2/gas/stats?fromDate={fromDate}&toDate={toDate}&granularity={DAY,HOUR}`

----

## Market Rate - `validator_market_rate`
This table contains the market rate at which a validator accepted the tx. 
This table is populated whether the tx succeeded or failed.
* `market_rate`
  * This is the rate per unit of gas the validator accepted to process the tx
  * This value is used to calculate the actual fee amount to be paid for a tx to process
    * `gas_wanted * market_rate = fee_amount`
    * This does not include additional msg fees

#### Processing
* Processing of the rollup tables occurs through an async task
  * `io.provenance.explorer.service.async.AsyncService#updateMarketRateStats()` 
* This occurs daily at 1 AM server time.
  * `@Scheduled(cron = "0 0 1 * * ?")`

### Validator Rollup - `validator_market_rate_stats`
This table holds the min/max/avg market rate of a validator at a daily level.
* `min_market_rate`
    * This is the absolute minimum market rate a validator accepted to process a tx
* `max_market_rate`
    * This is the absolute maximum market rate a validator accepted to process a tx
* `avg_market_rate`
    * This is the average market rate a validator accepted to process a tx

### Chain Rollup - `chain_market_rate_stats`
This table holds the min/max/avg market rate across the chain at a daily level.
Same fields as `validator_market_rate_stats`

### API Accessibility
* `/api/v2/chain/market_rate?fromDate={fromDate}&toDate={toDate}&dayCount={dayCount}`
* `/api/v2/validators/{id}/market_rate?fromDate={fromDate}&toDate={toDate}&dayCount={dayCount}`

----

## Token Statistics
Not held in a table, these are returned on the fly.
* `currentSupply`
  * The total supply of nhash as seen on the chain
  * Retrieved via `/cosmos/bank/v1beta1/supply/nhash` gRPC query
* `communityPool`
  * The amount of nhash held in the community pool account
  * Retrieved via `/cosmos/distribution/v1beta1/community_pool#nhash` gRPC query
* `bonded`
  * The total amount of nhash that is bonded to validators
  * Retrieved via `/cosmos/staking/v1beta1/pool#bonded_tokens` gRPC query
* `circulation`
  * The amount of nhash in circulation (not locked in accounts)
  * Calc'd as `currentSupply - communityPool - nhash_marker_address_balance - (bond + not_bonded)`

#### Processing
* Calc of the token stats through function 
  * `io.provenance.explorer.service.TokenSupplyService#getTokenStats()`

### API Accessibility
* `/api/v2/token/stats`

----

## Token Distribution - `token_distribution_amounts`
This table holds the distribution of hash across ranges of accounts sorted on nhash balances.
* `range`
  * The number of accounts that hold the corresponding portion of hash
* `data`
  * An object holding the range's amount of nhash and the percentage of the total supply the range encompasses 

#### Processing
* Processing of the table occurs through an async task
  * `io.provenance.explorer.service.async.AsyncService#updateTokenDistributionAmounts()`
* This occurs daily at 1 AM server time.
  * `@Scheduled(cron = "0 0 1 * * ?")`

### API Accessibility
* `/api/v2/assets/distribution`

----

## Tx Heatmap
Not held in a table, these are returned on the fly.
* `heatmap`
  * The actual data for the entire heatmap. Holds the day of week and hourly counts for that day.
* `dailyTotal`
  * Tx count totals for the day of week.
* `hourlyTotal`
  * Tx count totals for hour of day.

#### Processing
* Calc of the heatmap through function
  * `io.provenance.explorer.domain.entities.BlockCacheHourlyTxCountsRecord#getTxHeatmap()`

### API Accessibility
* `/api/v2/txs/heatmap`

----

## Chain AUM - `chain_aum_hourly`
This table holds the chain AUM calculated each hour.

#### Processing
* Processing of the table occurs through an async task
  * `io.provenance.explorer.service.async.AsyncService#saveChainAum()`
* This occurs every hour at server time.
  * `@Scheduled(cron = "0 0 0/1 * * ?")`

### API Accessibility
* `/api/v2/chain/aum/list`
