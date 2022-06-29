# Staking Msgs
* [Delegate](#delegate)
* [Redelegate](#redelegate)
* [Undelegate](#undelegate)
* [Withdraw Rewards](#withdraw-rewards)
* [Withdraw Commission](#withdraw-commission)


## Delegate
To craft a MsgDelegate, use `/api/v3/staking/delegate`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/staking/delegate' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"amount":{"amount":"100","denom":"nhash"},"delegator":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","validator":"pbvaloper1q0xydatnq9pevcjsj7phs4kty98g8430j67u95"}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "amount": {
    "amount": "string",
    "denom": "string"
  },
  "delegator": "string",
  "validator": "string"
}
```
* `delegator` - The same address of the signer
* `validator` - The operating address of a validator
* `amount` - The amount to delegate, in denom `nhash`

## Redelegate
To craft a MsgBeginRedelegate, use `/api/v3/staking/redelegate`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/staking/delegate' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"amount":{"amount":"100","denom":"nhash"},"delegator":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","validatorDst":"pbvaloper1q0xydatnq9pevcjsj7phs4kty98g8430j67u95","validatorDst":"pbvaloper1q0xydatnq9pevcjsj7phs4kty98g8430j67u95"}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "amount": {
    "amount": "string",
    "denom": "string"
  },
  "delegator": "string",
  "validatorDst": "string",
  "validatorSrc": "string"
}
```
* `delegator` - The same address of the signer
* `validatorSrc` - The operating address of a validator the delegator is currently delegated to
* `validatorDst` - The operating address of a validator the delegator wants to move their delegation to; must be different that the source validator
* `amount` - The amount to redelegate, in denom `nhash`

## Undelegate
To craft a MsgUndelegate, use `/api/v3/staking/undelegate`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/staking/delegate' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"amount":{"amount":"100","denom":"nhash"},"delegator":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","validator":"pbvaloper1q0xydatnq9pevcjsj7phs4kty98g8430j67u95"}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "amount": {
    "amount": "string",
    "denom": "string"
  },
  "delegator": "string",
  "validator": "string"
}
```
* `delegator` - The same address of the signer
* `validator` - The operating address of a validator the delegator is currently delegated to
* `amount` - The amount to undelegate, in denom `nhash`

## Withdraw Rewards
To craft a MsgWithdrawDelegatorReward, use `/api/v3/staking/withdraw_rewards`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/staking/withdraw_rewards' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"delegator":"pb1949aeejayfs4srtl8sc672h2m03xarz46unz4q","validator":"pbvaloper1q0xydatnq9pevcjsj7phs4kty98g8430j67u95"}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "delegator": "string",
  "validator": "string"
}
```
* `delegator` - The same address of the signer
* `validator` - The operating address of a validator the delegator is currently delegated to, and wishes to withdraw rewards from

## Withdraw Commission
To craft a MsgWithdrawValidatorCommission, use `/api/v3/staking/withdraw_commission`  
Example CURL request:
```shell
curl -X POST 'http://localhost:8612/api/v3/staking/withdraw_commission' \
  -H 'accept: application/json' \
  -H 'authorization: Bearer eyJhbGciOiJFUzI1NksiLCJ0eXAiOiJKV1QifQ==.eyJzdWIiOiJBMFBMcWdKQ3BaeklXK0xNckFzT003bFpXM2RMejRtaE00YnVWTmk0K2pnaSIsImlzcyI6InByb3ZlbmFuY2UuaW8iLCJpYXQiOjE2NTU0MDg2NjIsImV4cCI6MTY1NTQ5NTA2MiwiYWRkciI6InBiMTk0OWFlZWpheWZzNHNydGw4c2M2NzJoMm0wM3hhcno0NnVuejRxIn0=.Tbt2Qg62qoFhW959UUPL6wJcGc1tERfOaQhPTmn4FgFiG1+WuAZbQzQrFmtgJlqRQbAJZ3QF08UVJ5xiJi5R7A==' \
  -H 'content-type: application/json' \
  --data-raw '{"validator":"pbvaloper1q0xydatnq9pevcjsj7phs4kty98g8430j67u95"}' \
  --compressed
```

For the `request` portion, use the following structure:
```json
{
  "validator": "string"
}
```
* `validator` - The operating address of a validator the signing address owns

