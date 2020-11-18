package io.provenance.explorer.domain;

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun String.dayPart() = this.substring(0, 10)

fun String.asDay() = LocalDate.parse(this.dayPart(), DateTimeFormatter.ISO_DATE)

fun BlockMeta.height() = this.header.height.toInt()

fun BlockMeta.day() = this.header.time.dayPart()

fun List<BlockMeta>.maxHeight() = this.sortedByDescending { it.header.height.toInt() }.first().height()

fun List<BlockMeta>.minHeight() = this.sortedByDescending { it.header.height.toInt() }.last().height()

fun TxResult.fee(minGasPrice: BigDecimal) = this.gasUsed.toBigDecimal().multiply(minGasPrice).setScale(2, RoundingMode.CEILING)

fun BlockResponse.height() = this.block.header.height.toInt()