# Governance Msgs
* [Submit Proposal](#submit-proposal)
    + [Supported Proposal Types](#supported-proposal-types)
      + TEXT
      * PARAMETER_CHANGE
      * SOFTWARE_UPGRADE
      * CANCEL_SOFTWARE
      * STORE_CODE
      * INSTANTIATE_CONTRACT
    + [Submit Proposal Crafting Request](#submit-proposal-crafting-request)
* [Vote and WeightedVote](#vote-and-weightedvote)
* [Deposit](#deposit)


## Submit Proposal
### Supported Proposal Types
There is an API to fetch the supported types and their content object structure: `/api/v3/gov/types/supported`  
This will give you the enum value mapped to the content structure to be used when submitting a proposal msg creation
request.
```json
{
  "TEXT": {},
  "PARAMETER_CHANGE": {
    "changes": [
      {
        "subspace": "param_space",
        "key": "param_key",
        "value": "param_value"
      },
      {
        "subspace": "attribute",
        "key": "not_a_real_key",
        "value": "blah"
      }
    ]
  },
  "SOFTWARE_UPGRADE": {
    "name": "Test Name",
    "height": 1000087,
    "info": "This is info for the upgrade."
  },
  "CANCEL_UPGRADE": {},
  "STORE_CODE": {
    "runAs": "run as address",
    "accessConfig": {
      "type": "ACCESS_TYPE_EVERYBODY",
      "address": "Only set to an address if ACCESS_TYPE_ONLY_ADDRESS, else null"
    }
  },
  "INSTANTIATE_CONTRACT": {
    "runAs": "run as address",
    "admin": "admin address or null",
    "codeId": 140,
    "label": "Unique label for easy identification",
    "msg": "stringified JSON object for msg data to be used by the contract",
    "funds": [
      {
        "amount": "100",
        "denom": "some_denom"
      }
    ]
  }
}
```

### Submit Proposal Crafting Request
To craft a MsgSubmitProposal, use `/api/v3/gov/submit/{type}`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/gov/submit/{PROPOSAL_TYPE}' \
  -H 'accept: application/json' \
  -H 'authorization: {JWT_TOKEN}' \
  -H 'content-type: multipart/form-data' \
  -F 'request={ "content": {THIS IS THE CORRESPONDING OBJECT STRUCTURE STRINGIFIED}, "description": "Test", "initialDeposit": [ { "amount": "1000", "denom": "nhash" } ], "submitter": "THIS IS YOUR SUBMITTING ADDRESS", "title": "Test proposal" };type=application/json' \
  -F 'wasmFile=@{THIS IS THE PATH TO YOUR .wasm FILE};type=application/octet-stream' \ <------------ EXCLUDE IF NOT SUBMITTING A .wasm FILE
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "content": "string",
  "description": "string",
  "initialDeposit": [
    {
      "amount": "string",
      "denom": "string"
    }
  ],
  "submitter": "string",
  "title": "string"
}
```
* `content` - This is a stringified JSON object as mapped to the PROPOSAL_TYPE enum values coming from the [Supported Types API](#supported-proposal-types)
* `description` - The description for the proposal
* `initialDeposit` - Can be an empty list if no deposit is attached to the proposal request; Should be in denom `nhash`
* `submitter` - The same address of the signer
* `title` - The title of the proposal

Ensure the `request` is typed to `type=application/json`.

For the `wasmFile` portion, ensure you have the path to the .wasm file, and it is typed to `type=application/octet-stream`.


## Vote and WeightedVote
To craft a MsgVote or MsgWeightedVote, use `/api/v3/gov/vote`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/gov/vote' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"proposalId":36,"voter":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","votes":[{"option":"VOTE_OPTION_YES","weight":100}]}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "proposalId": 0,
  "voter": "string",
  "votes": [
    {
      "option": "UNRECOGNIZED",
      "weight": 0
    }
  ]
}
```
* `proposalId` - A valid proposal ID; must be in Voting Period
* `voter` - The same address of the signer
* `votes` - A list of votes with weights; The weights must add up to 100
    * Supported vote options come from the Gov.VoteOption proto enum
        * VOTE_OPTION_YES
        * VOTE_OPTION_ABSTAIN
        * VOTE_OPTION_NO
        * VOTE_OPTION_NO_WITH_VETO

If there is a single vote in the `votes` array, a MsgVote will be crafted. Otherwise, a MsgWeightedVote will be crafted.

## Deposit
To craft a MsgDeposit, use `/api/v3/gov/deposit`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/gov/deposit' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"deposit":[{"amount":"10000","denom":"nhash"}],"depositor":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","proposalId":36}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "deposit": [
    {
      "amount": "string",
      "denom": "string"
    }
  ],
  "depositor": "string",
  "proposalId": 0
}
```
* `proposalId` - A valid proposal ID; must be in Deposit Period or Voting Period
* `depositor` - The same address of the signer
* `deposit` - A list of amounts to be used as a deposit against the proposal; typically the denom is `nhash`, with at 
least one amount greater than zero






