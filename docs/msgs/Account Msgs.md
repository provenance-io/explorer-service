# Account Msgs
* [Delegate](#delegate)
* [Redelegate](#redelegate)
* [Undelegate](#undelegate)
* [Withdraw Rewards](#withdraw-rewards)
* [Withdraw Commission](#withdraw-commission)


## Send
To craft a MsgDelegate, use `/api/v3/accounts/send`  
Example CURL request:
```shell
curl -X POST "http://localhost:8612/api/v2/accounts/send" \
  -H "accept: application/json" \
  -H "Content-Type: application/json"  \
  -H "authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==" \
  --data-raw '{"from":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","funds":[{"amount":"100","denom":"nhash"}],"to":"pb1mcyukv73v57jm2cq48p6y666kqjed8suyphshq"}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "from": "string",
  "funds": [
    {
      "amount": "string",
      "denom": "string"
    }
  ],
  "to": "string"
}
```
* `from` - The same address of the signer
* `to` - A valid standard address; must be different that the `from` address
* `amount` - A list of funds to send, in valid denoms
