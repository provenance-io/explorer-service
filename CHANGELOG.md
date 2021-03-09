<!--
Guiding Principles:

Changelogs are for humans, not machines.
There should be an entry for every single version.
The same types of changes should be grouped.
Versions and sections should be linkable.
The latest version comes first.
The release date of each version is displayed.
Mention whether you follow Semantic Versioning.

Usage:

Change log entries are to be added to the Unreleased section under the
appropriate stanza (see below). Each entry should ideally include a tag and
the Github issue reference in the following format:

* (<tag>) \#<issue-number> message

The issue numbers will later be link-ified during the release process so you do
not have to worry about including a link manually, but you can if you wish.

Types of changes (Stanzas):

"Features" for new features.
"Improvements" for changes in existing functionality.
"Deprecated" for soon-to-be removed features.
"Bug Fixes" for any bug fixes.
"Client Breaking" for breaking CLI commands and REST routes used by end-users.
"API Breaking" for breaking exported APIs used by developers building on SDK.
"State Machine Breaking" for any changes that result in a different AppState given same genesisState and txList.
Ref: https://keepachangelog.com/en/1.0.0/
-->

## Unreleased

### Features
* Added a script to pull in protos used in the client #5
* Added a `build.gradle` to allow the protos to be compiled to java on build task #5
* Added support for multisig on transactions, accounts, and validators (limited) #9
* Added Validator-specific APIs #15
* Added Account-specific transaction API #11

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

### Bug Fixes
* Translated the signatures back to usable addresses
* Fixed `gas/statistics` from previous updates

## PRE-HISTORY

