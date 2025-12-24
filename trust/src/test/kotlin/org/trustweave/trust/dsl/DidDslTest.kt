package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.wallet.CredentialOrganization
import org.trustweave.testkit.credential.InMemoryWallet
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.services.TestkitDidMethodFactory
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for DidDsl.kt
 */
class DidDslTest {

    private lateinit var trustWeave: TrustWeaveConfig
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        val kmsInstance = kms
        trustWeave = trustWeave {
            factories(
                didMethodFactory = TestkitDidMethodFactory()
            )
            keys {
                custom(kmsInstance)
                signer { data, keyId ->
                    when (val result = kmsInstance.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
        }
    }

    @Test
    fun `test createDid with method and algorithm`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrFail()

        assertTrue(did.value.startsWith("did:key:"), "DID should start with did:key:")
        assertNotNull(did)
    }

    @Test
    fun `test createDid without method throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.createDid {
                algorithm("Ed25519")
            }
        }
    }

    @Test
    fun `test createDid with unconfigured method throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.createDid {
                method("web")
                algorithm("Ed25519")
            }
        }
    }

    @Test
    fun `test createDid with custom options`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
            option("custom", "value")
        }.getOrFail()

        assertNotNull(did)
        assertTrue(did.value.startsWith("did:key:"))
    }

    @Test
    fun `test createDid via TrustWeaveContext`() = runBlocking {
        val context = trustWeave.getDslContext()
        val did = context.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrFail()

        assertNotNull(did)
        assertTrue(did.value.startsWith("did:key:"))
    }

    @Test
    fun `test createDid with different algorithms`() = runBlocking {
        val did1 = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrFail()

        // Create second DID - even with same algorithm, should produce different DID
        // since it generates a new key
        val did2 = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrFail()

        assertNotNull(did1)
        assertNotNull(did2)
        assertNotEquals(did1, did2, "Different key generations should produce different DIDs")
    }

    @Test
    fun `test createDid extracts DID from document`() = runBlocking {
        val did = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrFail()

        // Verify DID format
        assertTrue(did.value.startsWith("did:key:"))
        assertTrue(did.value.length > 10) // Should have some identifier
    }
}


