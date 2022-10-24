package io.provenance.explorer.domain.extensions

import io.provenance.explorer.domain.exceptions.CsvWriteException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

data class CsvData(
    val fileName: String,
    val headers: List<String>,
    val records: List<List<Any>>
)

fun CsvData.writeCsvEntry(): ByteArray = try {
    val baos = ByteArrayOutputStream()
    CSVPrinter(OutputStreamWriter(baos), CSVFormat.DEFAULT)
        .use { csvPrinter ->
            csvPrinter.printRecord(this.headers)
            this.records.forEach { csvPrinter.printRecord(it) }
            csvPrinter.flush()
            return baos.toByteArray()
        }
} catch (e: IOException) {
    throw CsvWriteException("Failed to export data to CSV: " + e.message)
}
