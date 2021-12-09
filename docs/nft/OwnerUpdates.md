# Owner / Valueowner / Data Access changes
* track when the values change
* use to count how many scopes the address owns / value owns

### Owner changes
* Can only be updated through a WriteScopeRequest or {Add|Delete}ScopeOwnerRequest
* can be 1 or more entries

### ValueOwner changes
* Can only be updated through a WriteScopeRequest
* only 1 entry

### Data Access changes
* Can be updated by P8eMemorializeContract - Adds only
* Can be updated through a WriteScopeRequest or {Add|Delete}ScopeDataAccess
* Can be 0 or more entries


## Owner Changes
* `nft_scope_owner`
    * id           SERIAL PRIMARY KEY,
    * scope_id     INT          NOT NULL,
    * scope_addr   VARCHAR(128) NOT NULL,
    * tx_id        INT          NOT NULL,
    * block_height INT          NOT NULL,
    * tx_hash      VARCHAR(64)  NOT NULL,
    * owners_data  JSONB        NOT NULL,
    * owners_data_hash TEXT     NOT NULL

* owners_data object
    * list
        * owner
        * role - list

* on insert, check for latest data, and compare hashes
    * to ensure compatibility, order owners alphabetically, then save/compare/hash

* on fetch, gather list of objects, sorted by height
    * first record, all adds
    * second record,
        * if new address, add
        * if address doesnt exist, removal


## Value Owner Changes
* `nft_scope_value_owner`
    * id           SERIAL PRIMARY KEY,
    * scope_id     INT          NOT NULL,
    * scope_addr   VARCHAR(128) NOT NULL,
    * tx_id        INT          NOT NULL,
    * block_height INT          NOT NULL,
    * tx_hash      VARCHAR(64)  NOT NULL,
    * value_owner  VARCHAR(256) NOT NULL

* on insert, check for latest data, and compare value_owner

* on fetch, gather list of objects, sorted by height


## Data Access Changes
* `nft_scope_owner`
    * id           SERIAL PRIMARY KEY,
    * scope_id     INT          NOT NULL,
    * scope_addr   VARCHAR(128) NOT NULL,
    * tx_id        INT          NOT NULL,
    * block_height INT          NOT NULL,
    * tx_hash      VARCHAR(64)  NOT NULL,
    * access_data  JSONB        NOT NULL,
    * access_data_hash TEXT     NOT NULL

* access_data object
    * address - list

* on insert, check for latest data, and compare hashes
    * to ensure compatibility, order addresses alphabetically, then save/compare/hash

* on fetch, gather list of objects, sorted by height
    * first record, all adds
    * second record,
        * if new address, add
        * if address doesnt exist, removal


## Scraping
* ensure its a successful tx before writing records

* p8e memorialize
    * will need to scrape for the fields
    * will need to use the encryption stuff to decipher
```
  p8EData.Scope.Owners = contractRecitalParties
  p8EData.Scope.DataAccess = partyAddresses(contractRecitalParties)
  p8EData.Scope.ValueOwnerAddress, err = getValueOwner(msg.Contract.Invoker, msg.Contract.Recitals)
  if err != nil {
  return p8EData, err
  }
```

* Write Scope
    * scrape for the fields

* {Add|Delete}Scope{Owner|DataAccess}
    * scrape for fields
    * take last set of data, update and insert into table

## API
* Fetch value owner changes
* Fetch owner changes
* Fetch data access changes

