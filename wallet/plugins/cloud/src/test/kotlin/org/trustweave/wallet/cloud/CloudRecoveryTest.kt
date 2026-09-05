package org.trustweave.wallet.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudRecoveryTest {
    private fun unavailable(error: Exception) =
        object : CloudWallet("id", "did:key:w", "did:key:h", "bucket", "wallet") {
            override suspend fun upload(
                key: String,
                data: ByteArray,
            ) {}

            override suspend fun download(key: String): ByteArray? = throw error

            override suspend fun deleteFromStorage(key: String) = false

            override suspend fun listKeys(prefix: String) = listOf("wallet/credentials/known.json")
        }

    @Test fun `strict listing reports storage failure and recovery identifies the failed record`() =
        runBlocking {
            val wallet = unavailable(IllegalStateException("Storage unavailable"))
            assertFailsWith<IllegalStateException> { wallet.list() }
            assertFailsWith<IllegalStateException> { wallet.getStatistics() }
            val result = wallet.recoverRecords()
            assertFalse(result.complete)
            assertEquals(1, result.failures.size)
            assertTrue(result.records.isEmpty())
        }

    @Test fun `recovery does not swallow cancellation`() =
        runBlocking {
            assertFailsWith<CancellationException> { unavailable(CancellationException("cancel")).recoverRecords() }
        }
}
