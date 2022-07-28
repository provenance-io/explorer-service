## [v5.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v4.3.1) - 2022-07-28
### Release Name: Odoric of Pordenone

### Features
* Updated Provenance proto set to 1.11.0 [#371](https://github.com/provenance-io/explorer-service/issues/371)
* Add Support for Tx Msg building [#355](https://github.com/provenance-io/explorer-service/issues/355)
    * Now supports building Tx msgs through API
        * POST `/api/v3/gov/types/supported` - Gives list of proposal types supported for governance msgs
        * POST `/api/v3/gov/submit/{type}` - Crafts a Submit Proposal msg from the given data
        * POST `/api/v3/gov/deposit` - Crafts a Deposit msg
        * POST `/api/v3/gov/vote` - Crafts a Vote msg
        * POST `/api/v3/staking/delegate` - Crafts a Delegate msg
        * POST `/api/v3/staking/redelegate` - Crafts a Redelegate msg
        * POST `/api/v3/staking/undelegate` - Crafts an Undelegate msg
        * POST `/api/v3/staking/withdraw_rewards` - Crafts a Withdraw Rewards msg
        * POST `/api/v3/staking/withdraw_commission` - Crafts a Withdraw Commission msg
        * POST `/api/v2/accounts/send` - Crafts a Send msg
    * Validations included to allow for better support
    * Docs included to help with usage
* Add Support for Vesting Accounts [#366](https://github.com/provenance-io/explorer-service/issues/366)
    * GET `/api/v3/accounts/{address}/balances` - Returns paginated balances, broken down by spendable vs locked
    * GET `/api/v3/accounts/{address}/vesting` - Returns Vesting detail and vesting schedule
    * Now ingests properly
* Add Support for Custom Msg Fees [#367](https://github.com/provenance-io/explorer-service/issues/367)
    * Updated fee ingestion to accommodate custom msg fees
    * Also saves the original fee amount and denom for historical purposes
* Add APIs for CoinMarketCap [#375](https://github.com/provenance-io/explorer-service/issues/375)
    * GET `/api/v3/utility_token/stats` - Moved from `/api/v2/token/stats`, returns breakdown of nhash allocation on a broader scale
    * GET `/api/v3/utility_token/distribution` - Moved from `/api/v2/assets/distribution`, returns distribution of nhash across valid accounts
        * all accounts - nhash marker - zeroSeq - modules - contracts
    * GET `/api/v3/utility_token/rich_list` - Returns the top X accounts rich in nhash
        * all accounts - nhash marker - zeroSeq - modules - contracts
    * GET `/api/v3/utility_token/max_supply` - Returns the maximum supply ever of nhash on chain
    * GET `/api/v3/utility_token/total_supply` - Returns the total current supply of nhash
        * max - burned
    * GET `/api/v3/utility_token/circulating_supply` - Returns the current circulating supply of nhash
        * max - burned - modules - zeroSeq - pool - nonspendable
    * Updated `nhash` listview and detail supply values to use new `total_supply`
    * Added `burned` amount, `max_supply` amount to token stats chart data
    * Updated Chain Value AUM to use new `total_supply` for `nhash`

### Improvements
* Added Validation message collection to allow for multiple validations at once [#355](https://github.com/provenance-io/explorer-service/issues/355)
* Updated Tx Type lists with new types
