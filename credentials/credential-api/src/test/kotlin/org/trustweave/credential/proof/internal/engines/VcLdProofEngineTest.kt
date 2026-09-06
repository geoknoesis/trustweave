package org.trustweave.credential.proof.internal.engines

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.credential.proof.proofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for VcLdProofEngine.
 */
class VcLdProofEngineTest {
    private val engine = VcLdProofEngine()

    @Test
    fun `test engine properties`() {
        assertEquals(ProofSuiteId.VC_LD, engine.format)
        assertEquals("Verifiable Credentials (Linked Data)", engine.formatName)
        assertEquals("1.1, 2.0", engine.formatVersion)
        assertTrue(engine.capabilities.selectiveDisclosure)
        assertFalse(engine.capabilities.zeroKnowledge)
        assertTrue(engine.capabilities.revocation)
        assertTrue(engine.capabilities.presentation)
        assertTrue(engine.capabilities.predicates)
    }

    @Test
    fun `test engine is ready by default`() {
        assertTrue(engine.isReady())
    }

    @Test
    fun `test initialize and close`() =
        runBlocking<Unit> {
            engine.initialize()
            engine.close()
            // Should not throw
        }

    @Test
    fun `test initialize with config`() =
        runBlocking<Unit> {
            val config = ProofEngineConfig(properties = mapOf("test" to "value"))
            engine.initialize(config)
            // Should not throw
        }

    @Test
    fun `test issue with valid request`() =
        runBlocking<Unit> {
            val request = createValidIssuanceRequest()

            // Note: This will fail because KMS is not configured
            // This is expected - the engine needs actual KMS integration for signing
            val exception =
                assertThrows<IllegalStateException> {
                    engine.issue(request)
                }
            assertTrue(
                exception.message?.contains("KMS not configured") == true || exception.message?.contains("No signer available") == true,
            )
        }

    @Test
    fun `test issue with wrong format`() =
        runBlocking<Unit> {
            val request =
                createValidIssuanceRequest().copy(
                    format = ProofSuiteId.VC_JWT,
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    engine.issue(request)
                }
            assertTrue(exception.message?.contains("does not match engine format") == true)
        }

    @Test
    fun `test issue with proof options`() =
        runBlocking<Unit> {
            val request =
                createValidIssuanceRequest().copy(
                    proofOptions =
                        proofOptions {
                            purpose = ProofPurpose.Authentication
                            challenge = "challenge-123"
                            domain = "example.com"
                        },
                )

            // Note: This will fail because KMS is not configured
            val exception =
                assertThrows<IllegalStateException> {
                    engine.issue(request)
                }
            assertTrue(
                exception.message?.contains("KMS not configured") == true || exception.message?.contains("No signer available") == true,
            )
        }

    @Test
    fun `test verify with valid credential`() =
        runBlocking<Unit> {
            val credential = createValidCredential()
            val options = VerificationOptions()

            // Note: Verification will fail because proof verification is not fully implemented
            // This is expected for a skeleton implementation
            val result = engine.verify(credential, options)

            // Should return InvalidProof or similar since proof verification isn't implemented
            assertTrue(result is VerificationResult.Invalid)
        }

    @Test
    fun `test verify with expired credential`() =
        runBlocking<Unit> {
            val credential =
                createValidCredential().copy(
                    expirationDate = Clock.System.now().minus(kotlin.time.Duration.parse("PT1H")), // Expired 1 hour ago
                )
            val options = VerificationOptions(checkExpiration = true)

            val result = engine.verify(credential, options)

            // Note: VcLdProofEngine verify doesn't check expiration, it goes straight to proof verification
            // So it will return InvalidIssuer or InvalidProof instead of Expired
            assertTrue(result is VerificationResult.Invalid)
        }

    @Test
    fun `test verify with credential missing proof`() =
        runBlocking<Unit> {
            val credential = createValidCredential().copy(proof = null)
            val options = VerificationOptions()

            val result = engine.verify(credential, options)

            assertTrue(result is VerificationResult.Invalid)
        }

    @Test
    fun `test verify with credential missing issuer`() =
        runBlocking<Unit> {
            // Use a valid but unresolvable issuer instead of empty string
            // Empty IRI throws IllegalArgumentException during construction
            val credential =
                createValidCredential().copy(
                    issuer = Issuer.IriIssuer(Iri("did:example:invalid-issuer")),
                )
            val options = VerificationOptions()

            val result = engine.verify(credential, options)

            // Should return InvalidIssuer since issuer can't be resolved
            assertTrue(result is VerificationResult.Invalid)
        }

    @Test
    fun `verify fails closed for a credential whose subject id is an invalid IRI`() =
        runBlocking<Unit> {
            // Security regression: a credentialSubject.id like "urn:has space" is accepted by Iri()
            // (its regex allows the space) but JsonLd.toRdf drops every triple whose subject is not
            // a usable absolute IRI, so the subject's claims would be UNSIGNED. The canonicalization
            // guard now rejects such ids; the verify path must surface that as a fail-CLOSED
            // VerificationResult.Invalid, NEVER an uncaught exception or a Valid result.
            val invalidSubjectCredential =
                createValidCredential().copy(
                    credentialSubject =
                        CredentialSubject.fromIri(
                            "urn:has space",
                            claims = mapOf("name" to JsonPrimitive("Mallory")),
                        ),
                )

            val result = engine.verify(invalidSubjectCredential, VerificationOptions())

            assertTrue(
                result is VerificationResult.Invalid,
                "A credential with a toRdf-droppable subject id must fail closed (Invalid), got: $result",
            )
        }

    @Test
    fun `verify canonicalization input rejects an invalid subject id - signing input guard is in the verify path`() {
        // Prove the guard is on the exact code path verify() uses to build its signing input:
        // DefaultJsonLdCanonicalizationAdapter.canonicalize delegates to
        // JsonLdUtils.canonicalizeDocument, which throws for a subject id toRdf would drop.
        val adapter =
            org.trustweave.credential.internal.infrastructure
                .DefaultJsonLdCanonicalizationAdapter()
        val docWithInvalidSubjectId =
            buildJsonObject {
                put(
                    "@context",
                    buildJsonArray {
                        add("https://www.w3.org/2018/credentials/v1")
                        add(buildJsonObject { put("name", "https://schema.org/name") })
                    },
                )
                put("type", buildJsonArray { add("VerifiableCredential") })
                put("issuer", "did:key:test")
                put(
                    "credentialSubject",
                    buildJsonObject {
                        put("id", "urn:has space")
                        put("name", "Mallory")
                    },
                )
            }

        assertFailsWith<org.trustweave.core.exception.SerializationException> {
            adapter.canonicalize(docWithInvalidSubjectId)
        }
    }

    @Test
    fun `test createPresentation`() =
        runBlocking<Unit> {
            val credentials = listOf(createValidCredential())
            val request = PresentationRequest()

            // VC-LD supports presentations and createPresentation is implemented
            val presentation = engine.createPresentation(credentials, request)

            assertNotNull(presentation)
            assertEquals(credentials.size, presentation.verifiableCredential.size)
        }

    @Test
    fun `test createPresentation with selective disclosure`() =
        runBlocking<Unit> {
            val credentials = listOf(createValidCredential())
            val request =
                PresentationRequest(
                    disclosedClaims = setOf("name", "email"),
                )

            // VC-LD supports presentations and createPresentation is implemented
            val presentation = engine.createPresentation(credentials, request)

            assertNotNull(presentation)
            assertEquals(credentials.size, presentation.verifiableCredential.size)
            // Note: Full selective disclosure filtering may not be implemented, but presentation is created
        }

    @Test
    fun `test createPresentation with empty credentials`() =
        runBlocking<Unit> {
            val request = PresentationRequest()

            val exception =
                assertThrows<IllegalArgumentException> {
                    engine.createPresentation(emptyList(), request)
                }
        }

    // Helper functions

    private fun createValidIssuanceRequest(): IssuanceRequest {
        val issuerDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        val subjectDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

        return IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(issuerDid),
            issuerKeyId = VerificationMethodId.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1"),
            // No additional claims: arbitrary claims now require a declared @context that
            // defines them (fail-closed dropped-claims guard); these tests target the
            // KMS-not-configured signing path, which runs after canonicalization.
            credentialSubject = CredentialSubject.fromDid(subjectDid),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuedAt = Clock.System.now(),
            validUntil = Clock.System.now().plus(kotlin.time.Duration.parse("PT${86400 * 365}S")), // 1 year
        )
    }

    private fun createValidCredential(): VerifiableCredential {
        val issuerDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        val subjectDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

        return VerifiableCredential(
            id = CredentialId("urn:uuid:test-credential-123"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(issuerDid),
            issuanceDate = Clock.System.now(),
            expirationDate = Clock.System.now().plus(kotlin.time.Duration.parse("PT${86400 * 365}S")), // 1 year
            credentialSubject =
                CredentialSubject.fromDid(
                    subjectDid,
                    claims =
                        mapOf(
                            "name" to JsonPrimitive("John Doe"),
                            "email" to JsonPrimitive("john@example.com"),
                        ),
                ),
            proof =
                org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof(
                    type = "Ed25519Signature2020",
                    created = Clock.System.now(),
                    verificationMethod = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1",
                    proofPurpose = "assertionMethod",
                    proofValue = "test-signature-value",
                    additionalProperties = emptyMap(),
                ),
        )
    }
}
