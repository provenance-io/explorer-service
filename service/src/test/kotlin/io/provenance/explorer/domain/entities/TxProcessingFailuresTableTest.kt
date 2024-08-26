import io.provenance.explorer.domain.entities.TxProcessingFailureRecord
import io.provenance.explorer.domain.entities.TxProcessingFailureTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

class TxProcessingFailureRecordTest {

    @Test
    fun `test tx_processing_failures table insertOrUpdate`() {
        Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")

        transaction {
            val sql = this::class.java.getResource("/db/migration/V1_91__Create_tx_processing_failures_table.sql")!!
                .readText()
            exec(sql)
        }

        transaction {
            TxProcessingFailureRecord.insertOrUpdate(
                blockHeight = 100,
                txHash = "testHash",
                processType = "testProcess",
                errorMessage = "testError",
                success = false
            )

            var record = TxProcessingFailureRecord.find {
                (TxProcessingFailureTable.blockHeight eq 100) and
                    (TxProcessingFailureTable.txHash eq "testHash") and
                    (TxProcessingFailureTable.processType eq "testProcess")
            }.firstOrNull()

            assertNotNull(record, "Record should not be null")
            assertEquals(100, record?.blockHeight)
            assertEquals("testHash", record?.txHash)
            assertEquals("testProcess", record?.processType)
            assertEquals("testError", record?.errorMessage)
            assertEquals(false, record?.success)
        }

        transaction {
            TxProcessingFailureRecord.insertOrUpdate(
                blockHeight = 100,
                txHash = "testHash",
                processType = "testProcess",
                errorMessage = "updatedError",
                success = true
            )

            val record = TxProcessingFailureRecord.find {
                (TxProcessingFailureTable.blockHeight eq 100) and
                    (TxProcessingFailureTable.txHash eq "testHash") and
                    (TxProcessingFailureTable.processType eq "testProcess")
            }.firstOrNull()

            assertNotNull(record, "Record should not be null")
            assertEquals(100, record?.blockHeight)
            assertEquals("testHash", record?.txHash)
            assertEquals("testProcess", record?.processType)
            assertEquals("updatedError", record?.errorMessage)
            assertEquals(true, record?.success)
        }
    }
}
