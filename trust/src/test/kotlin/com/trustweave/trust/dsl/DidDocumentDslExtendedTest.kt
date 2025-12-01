package com.trustweave.trust.dsl

import com.trustweave.testkit.did.DidKeyMockMethod
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import com.trustweave.testkit.getOrFail
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for extended DID Document DSL.
 */
class DidDocumentDslExtendedTest {

    @Test
    fun `test add capability invocation via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustWeave = trustWeave {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }

        val context = trustWeave.getDslContext()
        val did = context.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val updatedDoc = context.updateDid {
            did(did.value)
            method(DidMethods.KEY)
            addCapabilityInvocation("${did.value}#key-1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test add capability delegation via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustWeave = trustWeave {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }

        val context = trustWeave.getDslContext()
        val did = context.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val updatedDoc = context.updateDid {
            did(did.value)
            method(DidMethods.KEY)
            addCapabilityDelegation("${did.value}#key-1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test set context via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustWeave = trustWeave {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }

        val context = trustWeave.getDslContext()
        val did = context.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val updatedDoc = context.updateDid {
            did(did.value)
            method(DidMethods.KEY)
            context("https://www.w3.org/ns/did/v1", "https://example.com/context/v1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test remove capability invocation via DSL`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustWeave = trustWeave {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }

        val did = trustWeave.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        // First add, then remove
        trustWeave.updateDid {
            did(did.value)
            method(DidMethods.KEY)
            addCapabilityInvocation("${did.value}#key-1")
        }

        val updatedDoc = trustWeave.updateDid {
            did(did.value)
            method(DidMethods.KEY)
            removeCapabilityInvocation("${did.value}#key-1")
        }

        assertNotNull(updatedDoc)
    }

    @Test
    fun `test full DID document update with all new fields`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val trustWeave = trustWeave {
            keys { custom(kms) }
            did { method(DidMethods.KEY) {} }
        }

        val context = trustWeave.getDslContext()
        val did = context.createDid {
            method(DidMethods.KEY)
            algorithm(KeyAlgorithms.ED25519)
        }.getOrFail()

        val updatedDoc = context.updateDid {
            did(did.value)
            method(DidMethods.KEY)
            addKey {
                type("Ed25519VerificationKey2020")
            }
            addCapabilityInvocation("$did#key-1")
            addCapabilityDelegation("${did.value}#key-1")
            context("https://www.w3.org/ns/did/v1")
            addService {
                id("${did.value}#service-1")
                type("LinkedDomains")
                endpoint("https://example.com")
            }
        }

        assertNotNull(updatedDoc)
    }
}


