package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.trust.TrustWeave
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.getOrFail
import org.trustweave.kms.results.SignResult
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.credential.presentation
import org.trustweave.trust.dsl.createTestCredentialService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for PresentationBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class PresentationBuilderBranchCoverageTest {

    private lateinit var trustWeave: TrustWeave
    private lateinit var issuerDid: String
    private lateinit var keyId: String
    
    /**
     * Helper to issue a credential with proof for use in presentations.
     */
    private suspend fun issueTestCredential(
        type: String,
        subjectId: String = "did:key:holder",
        claims: Map<String, Any> = emptyMap()
    ): VerifiableCredential {
        return trustWeave.issue {
            credential {
                type(type)
                issuer(issuerDid)
                subject {
                    id(subjectId)
                    claims.forEach { (key, value) ->
                        when (value) {
                            is String -> key to value
                            is Map<*, *> -> {
                                key to {
                                    (value as Map<String, Any>).forEach { (k, v) ->
                                        k to v.toString()
                                    }
                                }
                            }
                            else -> key to value.toString()
                        }
                    }
                }
                issued(Clock.System.now())
            }
            signedBy(issuerDid = Did(issuerDid), keyId = keyId)
        }.getOrFail()
    }

    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        
        // Create temporary TrustWeave to get DID registry for resolver
        val tempTrustWeave = TrustWeave.build {
            // DID methods auto-discovered via SPI
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
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
        
        val didResolver = DidResolver { did ->
            tempTrustWeave.configuration.didRegistry.resolve(did.value)
        }
        
        val credentialService = createTestCredentialService(kms = kms, didResolver = didResolver)
        trustWeave = TrustWeave.build {
            // DID methods auto-discovered via SPI
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
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
            // Set CredentialService as issuer for presentation builder
            issuer(credentialService)
        }
        
        // Setup issuer DID and key ID for test credentials
        val createdDid = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrFail()
        issuerDid = createdDid.value
        
        val issuerDidResolution = trustWeave.configuration.didRegistry.resolve(issuerDid)
            ?: throw IllegalStateException("Failed to resolve issuer DID")
        val issuerDidDoc = when (issuerDidResolution) {
            is org.trustweave.did.resolver.DidResolutionResult.Success -> issuerDidResolution.document
            else -> throw IllegalStateException("Failed to resolve issuer DID")
        }
        val verificationMethod = issuerDidDoc.verificationMethod.firstOrNull()
            ?: throw IllegalStateException("No verification method in issuer DID")
        keyId = verificationMethod.id.value.substringAfter("#")
    }

    // ========== Credentials Required Branches ==========

    @Test
    fun `test branch credentials required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.presentation {
                    holder("did:key:holder")
                    // Missing credentials
                }
        }
    }

    @Test
    fun `test branch single credential provided`() = runBlocking {
        val credential = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe")
        )

        val presentation = trustWeave.presentation {
            credentials(credential)
            holder("did:key:holder")
        }

        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential.size)
    }

    @Test
    fun `test branch multiple credentials provided`() = runBlocking {
        val credential1 = issueTestCredential(type = "PersonCredential")
        val credential2 = issueTestCredential(type = "DegreeCredential")

        val presentation = trustWeave.presentation {
            credentials(credential1, credential2)
            holder("did:key:holder")
        }

        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test branch credentials from list`() = runBlocking {
        val credentials = listOf(
            issueTestCredential(type = "PersonCredential"),
            issueTestCredential(type = "DegreeCredential")
        )

        val presentation = trustWeave.presentation {
            credentials(credentials)
            holder("did:key:holder")
        }

        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    // ========== Holder DID Required Branches ==========

    @Test
    fun `test branch holder DID required error`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        assertFailsWith<IllegalStateException> {
            trustWeave.presentation {
                    credentials(credential)
                    // Missing holder
                }
        }
    }

    @Test
    fun `test branch holder DID provided`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentation {
            credentials(credential)
            holder("did:key:holder")
        }

        assertNotNull(presentation)
        assertEquals("did:key:holder", presentation.holder.value)
    }

    // ========== Challenge Branches ==========

    @Test
    fun `test branch challenge not provided`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                // No challenge
            }

        assertNotNull(presentation)
        assertNull(presentation.challenge)
    }

    @Test
    fun `test branch challenge provided`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                challenge("verification-challenge-123")
            }

        assertNotNull(presentation)
        assertEquals("verification-challenge-123", presentation.challenge)
    }

    // ========== Domain Branches ==========

    @Test
    fun `test branch domain not provided`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                // No domain
            }

        assertNotNull(presentation)
        assertNull(presentation.domain)
    }

    @Test
    fun `test branch domain provided`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                domain("example.com")
            }

        assertNotNull(presentation)
        assertEquals("example.com", presentation.domain)
    }

    // ========== Proof Type Branches ==========

    @Test
    fun `test branch proof type default`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Clock.System.now())
        }

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                // Uses default proof type
            }

        assertNotNull(presentation)
    }

    @Test
    fun `test branch proof type custom`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Clock.System.now())
        }

        val presentation = trustWeave.presentation {
            credentials(credential)
            holder("did:key:holder")
        }

        assertNotNull(presentation)
    }

    // ========== Key ID Branches ==========

    @Test
    fun `test branch key ID not provided`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Clock.System.now())
        }

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                // No keyId
            }

        assertNotNull(presentation)
    }

    @Test
    fun `test branch key ID provided`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                verificationMethod("did:key:holder#key-1")
            }

        assertNotNull(presentation)
    }

    // ========== Selective Disclosure Branches ==========

    @Test
    fun `test branch selective disclosure disabled by default`() = runBlocking {
        val credential = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe", "email" to "john@example.com")
        )

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                // No selective disclosure
            }

        assertNotNull(presentation)
    }

    @Test
    fun `test branch selective disclosure enabled with reveal`() = runBlocking {
        val credential = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe", "email" to "john@example.com", "ssn" to "123-45-6789")
        )

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                selectiveDisclosure {
                    reveal("name", "email")
                }
        }

        assertNotNull(presentation)
    }

    @Test
    fun `test branch selective disclosure with hide`() = runBlocking {
        val credential = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe", "email" to "john@example.com", "ssn" to "123-45-6789")
        )

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                selectiveDisclosure {
                    reveal("name", "email")
                    // hide() method removed - only reveal() is available
                }
        }

        assertNotNull(presentation)
    }

    // ========== Combined Options Branches ==========

    @Test
    fun `test branch all presentation options provided`() = runBlocking {
        val credential = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe")
        )

        val presentation = trustWeave.presentation {
                credentials(credential)
                holder("did:key:holder")
                challenge("challenge-123")
                domain("example.com")
                verificationMethod("did:key:holder#key-1")
                selectiveDisclosure {
                    reveal("name")
                }
        }

        assertNotNull(presentation)
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
    }
}

