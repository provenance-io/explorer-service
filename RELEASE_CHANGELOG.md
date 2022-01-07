## [v3.1.0](https://github.com/provenance-io/explorer-service/releases/tag/v3.1.0) - 2022-01-07
### Release Name: Benjamin of Tudela

### Features
* Added API for chain upgrades [#246](https://github.com/provenance-io/explorer-service/issues/246)
    * API under `/api/v2/chain/upgrades`
* Added support for Tx Fee breakdown and Tx Feepayer identification [#252](https://github.com/provenance-io/explorer-service/issues/252)
    * Added `docs/txs/Feepayer.md` to lay out changes
    * Support for ingestion
    * Added support on tx query responses - updated field `fee` and new field `feepayer`
* Added API for chain prefixes [#269](https://github.com/provenance-io/explorer-service/issues/269)
    * API under `/api/v2/chain/prefixes`

### Improvements
* Updated ingestion to use the same TxData object for block-related data
* Increased local DB connection size to 40 to match container connection size
* Added `EXPLORER_HIDDEN_APIS` to allowing hiding of maintenance APIs from Swagger [#271](https://github.com/provenance-io/explorer-service/issues/271)
* Updated the Param set [#270](https://github.com/provenance-io/explorer-service/issues/270)

### Bug Fixes
* Updated `AccountDetail.publicKeys` to be a public key object showing the key and key type [#267](https://github.com/provenance-io/explorer-service/issues/267)
  ```json
  {
    ...
     "publicKeys": {
       "pubKey": "AireALVFyk8k1alZLcJLEdfaDFuyDua9eZsswvYsHUGN",
       "type": "secp256k1"
     },
    ...
  }
  ```

### Data
* Migration 1.39 - Added Tx fee and feepayer tables [#252](https://github.com/provenance-io/explorer-service/issues/252)
    * `tx_fee`, `tx_feepayer` tables
    * Insertion logic to prep the tables
