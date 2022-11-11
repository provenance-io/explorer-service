## [v5.3.0](https://github.com/provenance-io/explorer-service/releases/tag/v5.3.0) - 2022-11-11
### Release Name: Hong Bao

### Improvements
* Redesign of signature ingestion [#416](https://github.com/provenance-io/explorer-service/issues/416)
    * Now tracks idx, sequence of sigs on txs
    * Now properly tracks base signature, idx for multisig keys
    * Returns more informative signature objects on txs, accounts
* Now recursively searching within `MsgExec`, `MsgGrant`, `MsgRevoke` for associated info [#417](https://github.com/provenance-io/explorer-service/issues/417)
* Saving tx subtypes for specific msg types [#417](https://github.com/provenance-io/explorer-service/issues/417)
    * Allows for searching within those subtypes as well
    * Async task to update older msgs with the newly identified subtypes
* Swapped CMC pricing out in favor of DLOB pricing [#436](https://github.com/provenance-io/explorer-service/issues/436)

### Bug Fixes
* Fixed unending do-while loop on getting account balance for denom [#433](https://github.com/provenance-io/explorer-service/issues/433)
* Fixed Proposal `eligibileAmount` for quorum percentages [#432](https://github.com/provenance-io/explorer-service/issues/432)
* Fixed `/api/v3/accounts/tx_history` not returning any data [#431](https://github.com/provenance-io/explorer-service/issues/431)
* Removed Account attribute parsing
    * Now returning the object as seen on chain

### Data
* Migration 1.83 - Update `signature`, redo join tables [#416](https://github.com/provenance-io/explorer-service/issues/416)
    * Dropped `signature.multi_sig_object`, Added `signature.address` columns
    * Updated `signature` with new data
    * Added `signature_multi_join`, `signature_tx` tables, updated with data
* Migration 1.84 - Update ingest procedure with new signature info [#416](https://github.com/provenance-io/explorer-service/issues/416)
    * Updated the ingest procedures
    * Dropped `signature_join` table
* Migration 1.85 - Add msg subtypes [#417](https://github.com/provenance-io/explorer-service/issues/417)
    * Created table `tx_msg_type_subtype`, and populated with existing types
    * Updated views and ingest procedures
    * Dropped column `tx_message_type_id` from `tx_message`
    * Created table `tx_msg_type_query` strictly for query purposes
        * Gets inserted on trigger from `tx_msg_type_subtype`, flatmapping the primary/secondary types for better querying purposes
