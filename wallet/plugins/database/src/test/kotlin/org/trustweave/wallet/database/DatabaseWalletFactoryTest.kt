package org.trustweave.wallet.database

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.exception.WalletException
import org.trustweave.wallet.services.WalletCreationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [DatabaseWalletFactory], focused on the P1 finding that every
 * `create()` call built a HikariCP pool nothing could ever close.
 */
class DatabaseWalletFactoryTest {

    private fun h2Url(): String =
        "jdbc:h2:mem:factory-test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"

    private fun credential(id: String): VerifiableCredential =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did("did:key:z6MkTestIssuer")),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkTestSubject"),
            issuanceDate = Clock.System.now(),
            proof = null
        )

    @Test
    fun `factory-created wallet owns its pool and close shuts it down`() {
        runBlocking {
            val factory = DatabaseWalletFactory()
            val wallet = factory.create(
                providerName = "database",
                walletId = "factory-wallet",
                walletDid = null,
                holderDid = "did:key:z6MkHolder",
                options = WalletCreationOptions(storagePath = h2Url())
            )

            // Schema is initialized and the wallet is fully usable before close()
            val id = wallet.store(credential("cred-1"))
            assertEquals("cred-1", id)

            wallet.close()

            // After close() the wallet-owned pool is gone: operations fail fast
            assertFailsWith<WalletException.StorageError> {
                wallet.store(credential("cred-2"))
            }
        }
    }

    @Test
    fun `factory-created wallet works with use block`() {
        runBlocking {
            val factory = DatabaseWalletFactory()
            factory.create(
                providerName = "database",
                walletId = null,
                walletDid = null,
                holderDid = "did:key:z6MkHolder",
                options = WalletCreationOptions(storagePath = h2Url())
            ).use { wallet ->
                wallet.store(credential("cred-in-use"))
                assertEquals("cred-in-use", wallet.get("cred-in-use")?.id?.value)
            }
        }
    }

    @Test
    fun `factory rejects unknown provider names`() {
        runBlocking {
            val factory = DatabaseWalletFactory()
            val exception = assertFailsWith<IllegalArgumentException> {
                factory.create(
                    providerName = "not-database",
                    walletId = null,
                    walletDid = null,
                    holderDid = "did:key:z6MkHolder",
                    options = WalletCreationOptions(storagePath = h2Url())
                )
            }
            assertTrue(exception.message!!.contains("database"))
        }
    }
}
