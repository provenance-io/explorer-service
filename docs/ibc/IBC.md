# IBC

This document is a work in progress. (Always)

- [IBC](#ibc)
    * [Data changes](#data-changes)
    * [Ingestion](#ingestion)
    * [Listview - IBC Denom](#listview---ibc-denom)
    * [Balances](#balances)
    * [Available Channels](#available-channels)
    * [IBC Denom Detail](#ibc-denom-detail)

IBC (Inter-blockchain communication) is used to communicate between modules on different chains based on the Cosmos 
SDK. Currently it is being used to transfer denominations. These denominations are not always visible, or easy to trace, 
so an API would be most useful.

We would need the following:
* List known IBC denoms in the prov chain
  * should mimic the existing Asset listview
      * Name 
      * Total Supply
      * Last Tx
      * Age
* Get balances on a per channel basis
  * Form the assumed escrow account address, and fetch balances from that address
  * This would show denoms that have been transferred out of Provenance and are currently being circulated off of Provenance. 
    This does not associate repatriated denoms via a different route.
  * Destination chain
      * port/channel Prov origin
      * port/channel chain destination
      * denom
      * cumulative amount
      * last tx timestamp
* List available channels for use
  * Destination chain
    * source port/channel
    * destination port/channel
    * Status
* IBC denom detail
  * currently exists

## Data changes
* Currently ingesting IBC denoms into `marker_cache`.  &#9989;
* Will need to pull existing channels/identified channels into db &#9989;
  * ibc_channel
    * id
    * client id
    * destination chain name
    * source port
    * source channel
    * dest port
    * dest channel
    * status
    * escrow address ?
    * data
    
## Ingestion
Per tx: &#9989;
* identify port/channel
* Update ibc_channel with current data

## Listview - IBC Denom
Data from &#9989;
* `marker_cache` where marker_type = `IBC_DENOM`

Columns: &#9989;
* Denom
* supply
* last tx timestamp

## Balances
Data from &#9989;
* getEscrowAddress() function in `io/provenance/explorer/grpc/extensions/Domain.kt`. This location is not permanent.
* query account balances
* DB info for channel columns
* ~~DB info for denom last tx timestamp~~ leaving off for now

Columns &#9989;
* Destination chain
* port/channel Prov origin
* port/channel chain destination
* denom
* amount
* ~~last tx timestamp~~

## Available Channels
Data from &#9989;
* `/ibc/core/channel/v1beta1/channels/{channel_id}/ports/{port_id}`
* `/ibc/core/channel/v1beta1/channels/{channel_id}/ports/{port_id}/client_state`
* From DB for OPEN `status` only, probably paginated?

Columns &#9989;
* Destination chain
* source port/channel
* destination port/channel
* Status

## IBC Denom Detail
Data from &#9989;
* DB for marker data
* DB for tx data
* grpc for denom trace, metadata

Columns &#9989;
* marker
* supply
* holderCount -> default to 0 until we can get a good way to could holders on a regular denom (non-marker)
* txnCount
* metadata
* trace
