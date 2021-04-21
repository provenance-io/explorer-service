package io.provenance.explorer.domain.extensions

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode


const val NHASH = "nhash"
const val HASH = "hash"

fun BigInteger.toHash(denom: String) =
    (if (denom == NHASH)
        Pair(this.toBigDecimal().divide(BigDecimal.valueOf(1000000000), 9, RoundingMode.UNNECESSARY), HASH)
    else
        Pair(this.toBigDecimal(), denom)
    ).let { Pair(it.first.stripTrailingZeros().toPlainString(), it.second) }

fun String.toHash(denom: String) =
    (if (denom == NHASH)
        Pair(this.toBigDecimal().divide(BigDecimal.valueOf(1000000000), 9, RoundingMode.UNNECESSARY), HASH)
    else
        Pair(this.toBigDecimal(), denom)
        ).let { Pair(it.first.stripTrailingZeros().toPlainString(), it.second) }

fun BigDecimal.toHash(denom: String) =
    (if (denom == NHASH)
        Pair(this.divide(BigDecimal.valueOf(1000000000), 9, RoundingMode.FLOOR), HASH)
    else
        Pair(this, denom)
        ).let { Pair(it.first.stripTrailingZeros().toPlainString(), it.second) }

fun String.toDecCoin() = BigDecimal(this.toBigInteger(), 18)
