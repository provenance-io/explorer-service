# Crafting Msgs for Txs

Explorer service now supports crafting specific msgs through APIs. This does not submit the msg, only provides validation 
and formatting/packing.

* [Governance Msgs](Gov%20Msgs.md)
* [Staking Msgs](Staking%20Msgs.md)
* [Account Msgs](Account%20Msgs.md)

After validating and building the basic msg, the API will pack the msg into the `TxBody` object type, and return the following:
```json
{
  "json": {
    "messages": [
      {
        "@type": "/cosmos.gov.v1beta1.MsgVote",
        "proposalId": "36",
        "voter": "pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q",
        "option": "VOTE_OPTION_YES"
      }
    ]
  },
  "base64": [
    "ChsvY29zbW9zLmdvdi52MWJldGExLk1zZ1ZvdGUSLwgkEilwYjE5NDlhZWVqYXlmczRzcnRsOHNjNjcyaDJtMDN4YXJ6NDZ1bno0cRgB"
  ]
}
```
* `json` - A list of readable msgs to show what was built
* `base64` - The list of built msgs encoded into base64 for proper Tx usage

The `json` is used to display to the user prior to signing, so they see what was built exactly. The `base64` is used to 
build the Tx properly for submission.


