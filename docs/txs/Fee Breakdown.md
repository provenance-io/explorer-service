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
* Fetch the set MsgFees from grpc
  * This will match to the defined fees via proposal
* search for msg level event `assess_custom_msg_fee` -> This holds the custom msg fee from SC, with receiver, can be in usd/nhash
  * This will match to the `MsgAssessCustomMsgFeeRequest` fee type from the tx level event

* Recipient can come from the custom msg fee, otherwise null


?? Will the tx level event roll up all fees based on type and recipient
