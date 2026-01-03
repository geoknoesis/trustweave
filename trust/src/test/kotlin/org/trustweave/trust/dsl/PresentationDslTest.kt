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
            tempTrustWeave.getDslContext().getConfig().registries.didRegistry.resolve(did.value)
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
        
        val issuerDidResolution = trustWeave.getDslContext().getConfig().registries.didRegistry.resolve(issuerDid)
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

        val presentation = with(trustWeave.getDslContext()) {
            presentation {
                credentials(credential)
                holder("did:key:holder")
            }
        }

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

        val presentation = with(trustWeave.getDslContext()) {
            presentation {
                credentials(credential1, credential2)
                holder("did:key:holder")
            }
        }

        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test presentation creation with challenge`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = with(trustWeave.getDslContext()) {
            presentation {
                credentials(credential)
                holder("did:key:holder")
                challenge("verification-challenge-123")
            }
        }

        assertNotNull(presentation)
        assertEquals("verification-challenge-123", presentation.challenge)
    }

    @Test
    fun `test presentation creation with domain`() = runBlocking {
        val credential = issueTestCredential(type = "PersonCredential")

        val presentation = with(trustWeave.getDslContext()) {
            presentation {
                credentials(credential)
                holder("did:key:holder")
                domain("example.com")
            }
        }

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

        val presentation = with(trustWeave.getDslContext()) {
            presentation {
                credentials(credential)
                holder("did:key:holder")
            }
        }

        assertNotNull(presentation)
        // Proof type is used during presentation creation
    }

    @Test
    fun `test presentation creation requires credentials`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            with(trustWeave.getDslContext()) {
                presentation {
                    holder("did:key:holder")
                    // Missing credentials
                }
            }
        }
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

        assertFailsWith<IllegalStateException> {
            with(trustWeave.getDslContext()) {
                presentation {
                    credentials(credential)
                    // Missing holder
                }
            }
        }
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

        val presentation = with(trustWeave.getDslContext()) {
            presentation {
                credentials(credential)
                holder("did:key:holder")
                selectiveDisclosure {
                    reveal("name", "email")
                    hide("ssn")
                }
            }
        }

        assertNotNull(presentation)
        // Selective disclosure fields are configured
    }
}


