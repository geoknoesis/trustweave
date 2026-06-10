package org.trustweave.trust.dsl

import org.trustweave.trust.types.getOrThrowDid
import org.trustweave.credential.results.getOrThrow
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.trust.TrustWeave
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.trust.dsl.credential.credential
import org.trustweave.trust.dsl.credential.presentationResult
import org.trustweave.trust.dsl.createTestCredentialService
import org.trustweave.trust.types.PresentationResult
import org.trustweave.trust.types.getOrThrow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Tests for PresentationBuilder DSL.
 */
class PresentationDslTest {

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
            withTestClaimContexts() // Define ad-hoc test claims in the credential @context
        }.getOrThrow()
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
            credentialService(credentialService)
        }
        
        // Setup issuer DID and key ID for test credentials
        val createdDid = trustWeave.createDid {
            method("key")
            algorithm("Ed25519")
        }.getOrThrowDid()
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

    @Test
    fun `test presentation creation with single credential`() = runBlocking {
        val credential = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe")
        )

        val presentation = trustWeave.presentationResult {
                credentials(credential)
                holder("did:key:holder")
        }.getOrThrow()

        assertNotNull(presentation)
        assertEquals("did:key:holder", presentation.holder.value)
        assertEquals(1, presentation.verifiableCredential.size)
        assertTrue(presentation.type.any { it.value == "VerifiablePresentation" })
    }

    @Test
    fun `test presentation creation with multiple credentials`() = runBlocking {
        val credential1 = issueTestCredential(
            type = "PersonCredential",
            claims = mapOf("name" to "John Doe")
        )

        val credential2 = issueTestCredential(
            type = "DegreeCredential",
            claims = mapOf("degree" to mapOf("type" to "BachelorDegree"))
        )

        val presentation = trustWeave.presentationResult {
                credentials(credential1, credential2)
                holder("did:key:holder")
        }.getOrThrow()

        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test presentation creation with challenge`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentationResult {
                credentials(credential)
                holder("did:key:holder")
                challenge("verification-challenge-123")
        }.getOrThrow()

        assertNotNull(presentation)
        assertEquals("verification-challenge-123", presentation.challenge)
    }

    @Test
    fun `test presentation creation with domain`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = trustWeave.presentationResult {
                credentials(credential)
                holder("did:key:holder")
                domain("example.com")
        }.getOrThrow()

        assertNotNull(presentation)
        assertEquals("example.com", presentation.domain)
    }

    @Test
    fun `test presentation creation with custom proof type`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Clock.System.now())
        }

        val presentation = trustWeave.presentationResult {
                credentials(credential)
                holder("did:key:holder")
        }.getOrThrow()

        assertNotNull(presentation)
        // Proof type is used during presentation creation
    }

    @Test
    fun `holder string must be a did colon prefix`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")
        assertFailsWith<IllegalArgumentException> {
            trustWeave.presentationResult {
                credentials(credential)
                holder("not-a-did")
            }
        }
    }

    @Test
    fun `test presentation creation requires credentials`() = runBlocking {
        val r = trustWeave.presentationResult {
            holder("did:key:holder")
            // Missing credentials
        }
        assertIs<PresentationResult.Failure.InvalidRequest>(r)
    }

    @Test
    fun `test presentation creation requires holder`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
            }
            issued(Clock.System.now())
        }

        val r = trustWeave.presentationResult {
            credentials(credential)
            // Missing holder
        }
        assertIs<PresentationResult.Failure.InvalidRequest>(r)
    }

    @Test
    fun `test presentation with selective disclosure`() = runBlocking {
        val credential = credential {
            type("PersonCredential")
            issuer("did:key:issuer")
            subject {
                id("did:key:holder")
                "name" to "John Doe"
                "email" to "john@example.com"
                "ssn" to "123-45-6789"
            }
            issued(Clock.System.now())
        }

        val presentation = trustWeave.presentationResult {
                credentials(credential)
                holder("did:key:holder")
                selectiveDisclosure {
                    reveal("name", "email")
                }
        }.getOrThrow()

        assertNotNull(presentation)
        // Selective disclosure fields are configured
    }
}


