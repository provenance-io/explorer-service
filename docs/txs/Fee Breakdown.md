#Fee Breakdown

Fee types:
* Used
* Overage
* Msg Based

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
