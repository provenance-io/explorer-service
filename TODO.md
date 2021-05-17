ACCOUNTS:
* List Scopes where account == value owner or owner (getOwnership())

SCOPE LISTVIEW:
* scopeaddr
* scope spec addr
* session count
* record count
* FILTERS: by scope spec addr

SCOPE SPEC LISTVIEW:
* scope spec addr
* name
* contract spec count
* FILTERS: by contract spec addr

CONTRACT SPEC LISTVIEW:
* contract spec addr
* name
* record spec count

SCOPE DETAIL:
* scope addr
* spec addr -> link to spec page/popup, whatever
* description (from spec)
* owner list w/roles
* data access
* value owner

SCOPE SUBLISTS:
* Records
  * Detail: record Addr
    * From Spec: record spec addr -> link to spec page/popup, whatever
    * Last Modified: Date (from session) From session addr -> link to session page/popup, whatever
    * name
    * process
    * inputs
    * outputs
* Sessions
  * Detail: session addr 
    * From Spec: contract spec addr -> link to spec page/popup, whatever
    * Last Modified: Date By account address -> link to account page
    * name
    * parties w/roles
    * Records: list of record addr -> link to record page/popup, whatever OR details as above
* Txs
  * same tx list as always. filtered on txs connected to this scope addr
  
SCOPE SPEC DETAIL:
* spec addr
* description
* owners
* parties_involved
* number of scopes using this spec -> link to listview with scope spec filter

SCOPE SPEC SUBLISTS:
* Contract Specs List:
  * contract spec addr  -> link to spec page/popup, whatever
  * name
  * Record Specs: list of record specs -> link to spec page/popup, whatever OR details as below
* Record Specs List:
  * contract spec addr -> link to spec page/popup, whatever
  * record spec addr -> link to spec page/popup, whatever
  * name
  * responsible parties
* Txs
  * same tx list as always. filtered on txs connected to this scope spec addr

CONTRACT SPEC DETAIL:
* spec addr
* description
* owners
* parties_involved
* class name
* hash
* number of scope spec using this spec  -> link to listview with contract spec filter

CONTRACT SPEC SUBLISTS:
* Record Specs List:
  * contract spec addr -> link to spec page/popup, whatever
  * record spec addr -> link to spec page/popup, whatever
  * name
  * responsible parties
* Txs
  * same tx list as always. filtered on txs connected to this contract spec addr
