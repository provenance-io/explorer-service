## [v2.3.1](https://github.com/provenance-io/explorer-service/releases/tag/v2.3.1) - 2021-09-10
### Release Name: Ahmad ibn Fadlan

### Improvements
* Added checks to refresh the `current_validator_state` view
    * Should reduce the number of unnecessary refreshes
* Updated Spotlight Cache create to use the second top-most height actually in the database
    * This will prevent a race condition for saving blocks

### Bug Fixes
* Added `tx_message.msg_idx` to actually have message uniqueness
* Fixed the hashing function for tx_message to be deterministic
