#Fee Breakdown

Fee types:
* Used
* Overage
* Msg Based
* Custom

On tx success
```text
Actual values
feeAmount = ServiceOuterClass.GetTxResponse.tx.authInfo.fee.amountList.first { it.denom == NHASH }.amount.toLong()
msgFees = additional fees paid on top of the gas fee
gasWanted = ServiceOuterClass.GetTxResponse.txResponse.gasWanted
gasUsed = ServiceOuterClass.GetTxResponse.txResponse.gasUsed

Derived values
baseTotalPaid = feeAmount - msgFees
marketRate = baseTotalPaid / gasWanted --------------->>> Saved to `validator_market_rate` table
baseFeeUsed = marketRate * gasUsed
baseFeeOverage = baseTotalPaid - baseFeeUsed
msgFees are handled separately

Saved to DB as:
BASE_FEE_USED = baseFeeUsed
BASE_FEE_OVERAGE = baseFeeOverage
MSG_BASED_FEE = msgFees, as broken down by msg type
```

On tx failure due to insufficient gas
```text
Actual values
feeAmount = ServiceOuterClass.GetTxResponse.tx.authInfo.fee.amountList.first { it.denom == NHASH }.amount.toLong()
msgFees = additional fees paid on top of the gas fee
gasWanted = ServiceOuterClass.GetTxResponse.txResponse.gasWanted

Derived values
baseTotalPaid = feeAmount - msgFees
marketRate = baseTotalPaid / gasWanted --------------->>> Saved to `validator_market_rate` table
baseFeeUsed = baseTotalPaid
msgFees are handled separately

Saved to DB as:
BASE_FEE_USED = baseFeeUsed
```

On tx failure due to other reasons
```text
Actual values
feeAmount = ServiceOuterClass.GetTxResponse.tx.authInfo.fee.amountList.first { it.denom == NHASH }.amount.toLong()
msgFees = additional fees paid on top of the gas fee
gasWanted = ServiceOuterClass.GetTxResponse.txResponse.gasWanted
gasUsed = ServiceOuterClass.GetTxResponse.txResponse.gasUsed

Derived values
baseTotalPaid = feeAmount - msgFees
marketRate = baseTotalPaid / gasWanted --------------->>> Saved to `validator_market_rate` table
baseFeeUsed = marketRate * gasUsed
baseFeeOverage = baseTotalPaid - baseFeeUsed

Saved to DB as:
BASE_FEE_USED = baseFeeUsed
BASE_FEE_OVERAGE = baseFeeOverage
```


Fee processing
* search for tx level event `EventMsgFees` -> this holds all additional fees assessed, in `nhash`
  * This should cover all the known msg fees 
  * Will include recipient
* **Fallback:** if no EventMsgFees (e.g. fee-grant / granter flows or flat-fee model), use tx-level attribute `additionalfee` when present so that the sum of tx_fee rows equals the actual total charged (base + additional). The chain may emit `tx` events with `basefee`, `additionalfee`, and `total` instead of or in addition to EventMsgFees.

Backfill (flat-fee era txs ingested before the fallback):
* Procedure `backfill_additionalfee_fees(from_height, to_height)` inserts missing MSG_BASED_FEE rows by reading `additionalfee` from `tx_v2.tx_response.events` (see migration V1_112). Call from SQL: `CALL backfill_additionalfee_fees(from_height, to_height);` After backfill, consider marking affected `tx_gas_cache` as unprocessed and running `update_gas_fee_volume()` so daily/hourly fee aggregates include the new rows.
* Fetch the set MsgFees from grpc
  * This will match to the defined fees via proposal
* search for msg level event `assess_custom_msg_fee` -> This holds the custom msg fee from SC, with receiver, can be in usd/nhash
  * This will match to the `MsgAssessCustomMsgFeeRequest` fee type from the tx level event

* Recipient can come from the custom msg fee, otherwise null



Total Base fee designation
* on success, use tx -> basefee event for total base fee paid (total - msg fees)
  * If doesnt exist, fallback to tx -> fee event
  * If doesnt exist, fallback to basic calc used now
* on failure, use coin_spent -> amount for total base fee paid
  * If doesnt exist, fallback to basic calc used now 
* on failure no fees paid, if coin_spent is null, set to 0 as base fee paid

https://github.com/cosmos/cosmos-sdk/blob/35ae2c4c72d4aeb33447d5a7af23ca47f786606e/x/auth/ante/sigverify.go#L198
