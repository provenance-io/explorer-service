package io.provenance.explorer.domain;

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun String.dayPart() = this.substring(0, 10)

fun String.asDay() = LocalDate.parse(this.dayPart(), DateTimeFormatter.ISO_DATE)

fun BlockMeta.height() = this.header.height.toInt()

fun BlockMeta.day() = this.header.time.dayPart()

fun List<BlockMeta>.maxHeight() = this.sortedByDescending { it.header.height.toInt() }.first().height()

fun List<BlockMeta>.minHeight() = this.sortedByDescending { it.header.height.toInt() }.last().height()
