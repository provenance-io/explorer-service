# IBC Ledger

Tracking movement of money in and out of ibc channels is not a thing. In order to accurately show the movement of denoms,
we should have a ledger that tracks each in and out on a channel. This would replace the old `/ibc/channels/balances` api.

Needed:
* DB changes to track movement in a ledger style
* Adjust the ingestion to pull movements from events
* Adjust the api return to show roll ups and last tx timestamp per chain/channel/denom
* Add new api to pull all denom/balances in/out on a all/per chain basis

## Data Changes
* Add `ibc_balance_ledger` &#9989;
  * id
  * channel_id
  * denom
  * balance_in
  * balance_out
  * block_height
  * tx_hash_id
  * tx_hash
  * tx_timestamp
  * pass_through_account_id
  * pass_through_account
  * acknowledged
  * ack_success
  * ack_block_height
  * ack_tx_hash_id
  * ack_tx_hash
  * ack_tx_timestamp
    
## Ingestion
* Msg types &#9989;
  * MsgTransfer/MsgAcknowledge
  * MsgRecv
* Pull data from events &#9989;

## API Changes
* Modify `/ibc/balances/channel` to pull data from `ibc_balance_ledger` primarily &#9989;
* Return
  * chain id
  * last tx timestamp
    * channel
    * last tx timestamp
        * denom
        * total in
        * total out
        * last tx timestamp
    
## New API
* `/ibc/balances/denom` -> roll up across all chains &#9989;
* Return 
  * denom
    * total in
    * total out
    * last tx timestamp

* `/ibc/balances/chain` -> roll up across all channels per chain &#9989;
* Return
  * chain id
  * last tx timestamp
  * denom
    * total in
    * total out
    * last tx timestamp
