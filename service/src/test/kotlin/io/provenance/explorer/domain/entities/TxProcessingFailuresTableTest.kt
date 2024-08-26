import io.provenance.explorer.domain.entities.TxProcessingFailureRecord
import io.provenance.explorer.domain.entities.TxProcessingFailuresTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Test

class TxProcessingFailureRecordTest {

    @Test
    fun testInsertOrUpdate_newRecord() {
        Database.connect("jdbc:h2:mem:test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
        transaction {
            val sql = this::class.java.getResource("/db/migration/V1_91__Create_tx_processing_failures_table.sql")!!.readText()
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

            val record = TxProcessingFailureRecord.find {
                (TxProcessingFailuresTable.blockHeight eq 100) and
                        (TxProcessingFailuresTable.txHash eq "testHash") and
                        (TxProcessingFailuresTable.processType eq "testProcess")
            }.firstOrNull()
        }
    }

//    @Test
//    fun testInsertOrUpdate_updateRecord() {
//        transaction {
//            TxProcessingFailureRecord.insertOrUpdate(
//                blockHeight = 100,
//                txHash = "testHash",
//                processType = "testProcess",
//                errorMessage = "testError",
//                success = false
//            )
//
//            TxProcessingFailureRecord.insertOrUpdate(
//                blockHeight = 100,
//                txHash = "testHash",
//                processType = "testProcess",
//                errorMessage = "updatedError",
//                success = true
//            )
//
//            val record = TxProcessingFailureRecord.find {
//                (TxProcessingFailuresTable.blockHeight eq 100) and
//                        (TxProcessingFailuresTable.txHash eq "testHash") and
//                        (TxProcessingFailuresTable.processType eq "testProcess")
//            }.firstOrNull()
//
//        }
//    }
}
