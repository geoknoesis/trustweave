package org.trustweave.wallet

import kotlinx.coroutines.test.runTest
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.trustweave.did.model.DidDocument
import kotlin.test.Test
import kotlin.test.assertEquals

class WalletDidManagementTest {
    @Test
    fun `DID creation supplies default options when omitted`() =
        runTest {
            val management = RecordingDidManagement()
            assertEquals("did:key:created", management.createDid("key"))
            assertEquals("key" to DidCreationOptions(), management.requests.single())
        }

    @Test
    fun `DID creation DSL forwards the method and configured options`() =
        runTest {
            val management = RecordingDidManagement()
            assertEquals(
                "did:web:created",
                management.createDid("web") {
                    algorithm = KeyAlgorithm.P256
                    forAssertion()
                    property("domain", "example.com")
                },
            )
            val expected =
                DidCreationOptions(
                    algorithm = KeyAlgorithm.P256,
                    purposes = listOf(KeyPurpose.ASSERTION),
                    additionalProperties = mapOf("domain" to "example.com"),
                )
            assertEquals("web" to expected, management.requests.single())
        }

    private class RecordingDidManagement : DidManagement {
        override val walletDid = "did:key:wallet"
        override val holderDid = "did:key:holder"
        val requests = mutableListOf<Pair<String, DidCreationOptions>>()

        override suspend fun createDid(
            method: String,
            options: DidCreationOptions,
        ): String {
            requests += method to options
            return "did:$method:created"
        }

        override suspend fun getDids(): List<String> = listOf(walletDid)

        override suspend fun getPrimaryDid(): String = walletDid

        override suspend fun setPrimaryDid(did: String): Boolean = did == walletDid

        override suspend fun resolveDid(did: String): DidDocument? = null
    }
}
