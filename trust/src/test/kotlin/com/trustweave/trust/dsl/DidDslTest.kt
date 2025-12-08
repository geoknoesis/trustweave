package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.revocation.InMemoryStatusListManager
import com.trustweave.credential.revocation.StatusListManager
import com.trustweave.credential.revocation.StatusPurpose
import com.trustweave.wallet.CredentialOrganization
import com.trustweave.testkit.credential.InMemoryWallet
import com.trustweave.did.DidDocument
import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.testkit.getOrFail
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
                signer { data, keyId -> kmsInstance.sign(com.trustweave.core.identifiers.KeyId(keyId), data) }
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


