# Missed Blocks

- [Missed Blocks](#missed-blocks)
  * [DB Changes](#db-changes)
  * [Ingestion](#ingestion)
  * [Query Updates](#query-updates)

Currently we are pulling the `missed blocks` count from signing infos. Unfortunately, it is not a running count. This 
means we need to be tracking the missed blocks per validator per block in the database.

Do the following:
* DB table to hold running list of missed blocks
  * `missed_blocks`
    * height
    * timestamp
    * val_cons_address
    * running_total (?)
* Update ingestion to record missed blocks
* Update Validator queries to pull from new table

## DB Changes
* Add `missed_blocks` &#9989;
    * height
    * timestamp
    * val_cons_address
    * running_total (?)
    
## Ingestion
* For each block, record the missing validators from the previous block &#9989;
    
## Query Updates
* ValidatorSummary &#9989;
  * `uptime` -> Replace existing calc with query from DB
* ValidatorDetail &#9989;
    * `uptime` -> Replace existing calc with query from DB
    * `blocks` -> Replace existing calc with query from DB
