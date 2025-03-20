## [v7.0.5](https://github.com/provenance-io/explorer-service/releases/tag/v7.0.5) - 2025-03-20

### Bug Fixes

Version 7.0.5 of the Explorer Service fixes a Postgresql sequence integer overflow
issue in the tx_msg_event_attr table. The number of rows exceeded 2.17B, the max
integer value. Table primary key and sequence were updated to BIGINT.
