# NFTs

- [NFTs](#nfts)
  * [Data Changes](#data-changes)
  * [Ingestion](#ingestion)
  * [Scope Detail](#scope-detail)
    + [Header Detail](#header-detail)
    + [Records Detail](#records-detail)
  * [NFT Tx listview Detail](#nft-tx-listview-detail)
  * [Scopes listview](#scopes-listview)
  * [Asset - NFT count](#asset---nft-count)
  * [Account - NFT count](#account---nft-count)

NFTs (Non-Fungible Tokens) are a chain representation of an off-chain asset, whatever that may be. In Provenance land, 
our primary user (Figure) stores NFTs called Scopes on chain. This encompasses Scope, ScopeSepc, Session, ContractSpec, 
Record and RecordSpec. For this first iteration, NFTs essentially equal Scopes.

One major downside to storing Scope info on chain is Scopes and everything underneath can be huge, making direct calls to
chain painfully slow, and sometimes downright unreachable. Due to these circumstances, this first iteration should
limit calls to the chain to individual records, or, if a list, a list of Strings at best.

To accommodate this, we should add the following:
* Fetch Scope Detail by addr (or uuid)
  * scope addr
  * spec addr -> link to spec page/popup, whatever -> spec name links to address
  * description (from spec)
  * owner list w/roles
  * value owner
* Fetch Records under a Scope by Scope addr/uuid
  * Detail: record name
    * From Spec: record spec addr
    * Last Modified: Date (from session) From session addr -> link to session page/popup, whatever
    * record addr
    * parties that updated w/roles
  * NOTE: will need to show unfilled records
* Fetch txs attached to Scope by Scope addr/uuid
  * same tx list as always. filtered on txs connected to this scope addr
* Fetch paginated list of scopes by account ownership, either as owner or value owner
  * scopeaddr
  * spec name or scope spec addr
  * Last updated from tx
* Add NFT count to Asset Detail
* Add NFT count to Account Detail

## Data Changes
* Will need tables to hold NFT Scope, ScopeSpec and ContractSpec basic data &#9989;
  * nft_scope
  * nft_scope_spec
  * nft_contract_spec
* Will need tx join table for NFTs &#9989;
  * tx_nft_join
  
## Ingestion
Per tx: &#9989;
* Scrape metadata addresses from msgs
* Scrape metadata addresses from event logs
* Save addresses into appropriate table
* Save tx join

## Scope Detail
### Header Detail
Data from &#9989;
* `provenance/metadata/v1/scope/{scope_addr}`

Header Detail: &#9989;
* Scope addr
* scope name -> from scope spec
* scope spec addr
* description -> from scope spec
  * name
  * description
  * website
  * icon url
* owners list w/roles
* value owner

### Records Detail
The idea is for the records under a scope to  show whether they fill a spec-required record, and if there are any 
unfilled record specs existing. Also shows if an existing record doesnt match up to any specs listed, thus being kind of 
an orphan.

Data from  &#9989;
* scope -> `provenance/metadata/v1/scope/{scope_addr}`
* scope spec -> `provenance/metadata/v1/scopespec/{specification_id}`
* record specs -> `/provenance/metadata/v1/contractspec/{specification_id}/recordspecs`

Record Detail: &#9989;
* Detail -> record name
  * status
  * record spec list
    * contract spec addr
    * record spec addr
    * responsible parties
  * record detail
    * addr
    * spec addr
    * last modified
    * responsible parties
  
## NFT Tx listview Detail
NFT Txs Data from &#9989;
* db tables
  * txs from `tx_nft_join`
* paginated for FE
* url : `/api/v2/txs/nft/{nftAddr}?page={page}&count={count}&msgType={msgType}&txStatus={txStatus}&fromDate={fromDate}&toDate={toDate}`

NFT Txs Detail: &#9989;
* same as the other tx responses, but different columns (?)

## Scopes listview
Data: &#9989;
* scope ids from `/provenance/metadata/v1/ownership/{address}`
* paginated

Columns: &#9989;
* scopeaddr
* name -> from scope spec
* scope spec addr
* Last updated from tx

## Asset - NFT count
* fetch count of NFTs owned by asset holding address  &#9989;
  * covers both owner and value owner

Changes: &#9989;
* Add `TokenCount` to AssetDetail
  * count of regular denoms
  * count of NFTs as owned

## Account - NFT count
* fetch count of NFTs owned by account address  &#9989;
  * covers both owner and value owner

Changes: &#9989;
* Add `TokenCount` to AccountDetail
  * count of regular denoms
  * count of NFTs as owned

