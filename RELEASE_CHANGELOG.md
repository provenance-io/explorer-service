## [v4.0.0](https://github.com/provenance-io/explorer-service/releases/tag/v4.0.0) - 2022-03-08
### Release Name: Marco Polo

### Features
* Added attributes, UUID to NFT API [#299](https://github.com/provenance-io/explorer-service/issues/299)
* Fully supporting msg based fee breakdown [#299](https://github.com/provenance-io/explorer-service/issues/299)
* Updated Params API to include MsgFee params [#299](https://github.com/provenance-io/explorer-service/issues/299)
* Added Msg-Based Fee API [#299](https://github.com/provenance-io/explorer-service/issues/299)

### Improvements
* Now using provenance-io produced proto jar [#199](https://github.com/provenance-io/explorer-service/issues/199)
* Dependency cleanup
* Updates to event scraping [#299](https://github.com/provenance-io/explorer-service/issues/299)
    * tied to increased Smart Contract usage
* Swagger cleanup

### Data
* Rewrote Migrations 1.49, 1.50 to make more sense [#299](https://github.com/provenance-io/explorer-service/issues/299)
    * Added procedure `update_market_rate()` to update market rate independently
    * Added procedure `update_market_rate_stats()` to update market rate stats tables independently
    * Removed fee updates via migration for now
* Migration 1.51 - 1.8.0 updates [#299](https://github.com/provenance-io/explorer-service/issues/299)
    * Added `msg_type` to `tx_fee` table
    * Updated `add_tx()` procedure
    * Updated `tx_fee_unique_idx`
    * Added `update_tx_fees()` procedure to update tx fees at a specific block height
        * Did it this way so the migration is not dependent on a specific block height, and so future updates would only need
          to update the procedure with additional msg types/events for msg-based fees
