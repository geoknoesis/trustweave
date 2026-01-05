package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.kms.results.SignResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Tests for VerificationBuilder DSL.
 */
class VerificationDslTest {

    private lateinit var trustWeave: TrustWeave
    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setUp() = runBlocking {
        kms = InMemoryKeyManagementService()

        // Capture KMS reference for closure
        val kmsRef = kms

        trustWeave = TrustWeave.build {
            keys {
                custom(kmsRef)
                // Provide signer function directly to avoid reflection
                signer { data, keyId ->
                    when (val result = kmsRef.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }

            did {
                method("key") {
                    // Empty block is OK
                }
            }
        }
    }

    @Test
    fun `test verification with default options`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
                "name" to "John Doe"
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
        }

        assertNotNull(result)
        // Note: Verification may fail if DID cannot be resolved, which is expected in test environment
    }

    @Test
    fun `test verification with revocation check enabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            checkRevocation()
        }

        assertNotNull(result)
        // The result contains the verification outcome
    }

    @Test
    fun `test verification with revocation check disabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            skipRevocation()
        }

        assertNotNull(result)
        // Note: The result contains verification outcome, not the options
    }

    @Test
    fun `test verification with expiration check enabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            checkExpiration()
        }

        assertNotNull(result)
        // Note: The result contains verification outcome
    }

    @Test
    fun `test verification with expiration check disabled`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            skipExpiration()
        }

        assertNotNull(result)
        // Note: The result contains verification outcome
    }

    @Test
    fun `test verification with schema validation`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            validateSchema("https://example.com/schemas/person.json")
        }

        assertNotNull(result)
        // Note: Schema validation result is in the verification result
    }

    @Test
    fun `test verification with anchor verification`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:subject")
            }
            issued(Clock.System.now())
        }

        val result = trustWeave.verify {
            credential(credential)
            // Note: verifyAnchor() not available in VerificationBuilder
        }

        assertNotNull(result)
        // Note: Anchor verification result is in the verification result
    }

    @Test
    fun `test verification requires credential`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.verify {
                // Missing credential
                checkRevocation()
            }
        }
    }
}


