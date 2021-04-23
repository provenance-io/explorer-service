## [v1.0.0](https://github.com/provenance-io/explorer-service/releases/tag/vx.x.x) - 2021-04-23

### Features
* Added a script to pull in protos used in the client #5
* Added a `build.gradle` to allow the protos to be compiled to java on build task #5
* Added support for multisig on transactions, accounts, and validators (limited) #9
* Added Validator-specific APIs #15
* Added Account-specific transaction API #11
* Added working docker-compose script #10
* Dockerize Database #36
* Added Min Gas Fee statistics #30

### Improvements
* Added templates and build workflow
* Updated `/api/v2/validators/height/{blockHeight}` to translate page to offset for proper searching
* Upgraded to gRpc blockchain calls #17
* Upgraded to Kotlin gradle
* Updated the transaction database tables to allow for better searching #15
* Updated block queries to return the same object with updated info #13
* Updated native queries to use exposed query-building instead
* Updated transaction queries to return same objects as other similar queries #14
* Upgraded Exposed library from 0.17.1 to 0.29.1
* Updated the copy_proto script to be more intuitive
* Because we have a docker gha workflow that builds, I removed the branch-> main designation for build gha #34
* Added gradle custom `downloadProtos` task to download protos instead of shell script. 
* Updated Validator.bond_height to come from real numbers
* Added Asset status to the asset objects 
* Added current gas fee to Validator objects
* Initial conversions from nhash to hash #44
* Changed ownership on assets #45
* Improved query performance #53
* Added release workflow #58

### Bug Fixes
* Translated the signatures back to usable addresses
* Fixed `gas/statistics` from previous updates
* Changed out Int to BigInteger in API return objects #34
* Added distinct to Tx query so it actually only brings back tx objects/counts #34
* Added total tx count to Spotlight object #34
* Made Block Hash look like a hash #34
* Updated a couple docker scripts cuz I moved things around in the last commit #34
* Fixed a bug where candidate/jailed validators were being filtered out of results
* Added missing protos #55
* Properly handling Multi Msg txs #56

### Data
* Added a db index on tx_cache.height
* Added additional db indices
* Added migrations for Tx, BlockProposer, TxMessage, and TxMessageType data points #57


