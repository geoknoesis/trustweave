package org.trustweave.wallet.database

import kotlinx.coroutines.runBlocking
import org.trustweave.wallet.services.WalletCreationOptions
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseWalletEncryptionKeyTest {
    private val url = "jdbc:h2:mem:enc-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"

    @Test
    fun `an encryptionKey is refused instead of silently ignored`() =
        runBlocking<Unit> {
            for (options in listOf(
                WalletCreationOptions(storagePath = url, encryptionKey = "super-secret"),
                WalletCreationOptions(storagePath = url, additionalProperties = mapOf("encryptionKey" to "super-secret")),
            )) {
                val error =
                    assertFailsWith<IllegalArgumentException> {
                        DatabaseWalletFactory().create("database", holderDid = "did:key:h", options = options)
                    }
                assertTrue(error.message!!.contains("encryptionKey"))
                assertTrue(!error.message!!.contains("super-secret"))
            }
        }
}
