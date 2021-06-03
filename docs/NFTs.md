ACCOUNTS:
* List Scopes where account == value owner or owner (getOwnership())
* List Specs where account == owner

// FIRST
SCOPE LISTVIEW: ##DONE
* scopeaddr
* spec name or scope spec addr
* Last updated from tx

SCOPE DETAIL: ##DONE
* scope addr
* spec addr -> link to spec page/popup, whatever -> spec name links to address
* description (from spec)
* owner list w/roles
* value owner

SCOPE SUBLISTS: ##DONE
* Records ##DONE
  * Detail: record name
    * From Spec: record spec addr
    * Last Modified: Date (from session) From session addr -> link to session page/popup, whatever
    * record addr
    * parties that updated w/roles
  * NOTE: will need to show unfilled records
* Txs ##DONE
  * same tx list as always. filtered on txs connected to this scope addr
  

// DO THIS LAST
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
