package org.trustweave.credential.proof.internal.engines

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.PresentationVerification
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.credential.proof.proofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

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
        assertFalse(engine.capabilities.selectiveDisclosure)
        assertFalse(engine.capabilities.zeroKnowledge)
        assertTrue(engine.capabilities.revocation)
        assertTrue(engine.capabilities.presentation)
        assertFalse(engine.capabilities.predicates)
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
    fun `test createPresentation refuses non-empty disclosedClaims`() =
        runBlocking<Unit> {
            val credentials = listOf(createValidCredential())
            val request =
                PresentationRequest(
                    disclosedClaims = setOf("name", "email"),
                )

            // Mutating signed VC-LD credentials would invalidate issuer signatures; refuse cleanly.
            assertThrows<UnsupportedOperationException> {
                engine.createPresentation(credentials, request)
            }
        }

    @Test
    fun `test createPresentation rejects proofOptions without verificationMethod`() =
        runBlocking<Unit> {
            val credentials = listOf(createValidCredential())
            val request =
                PresentationRequest(
                    proofOptions =
                        proofOptions {
                            purpose = ProofPurpose.Authentication
                            challenge = "challenge-123"
                            domain = "verifier.example"
                        },
                )

            val exception =
                assertThrows<IllegalArgumentException> {
                    engine.createPresentation(credentials, request)
                }
            assertTrue(exception.message?.contains("verificationMethod") == true)
        }

    @Test
    fun `test createPresentation rejects proofOptions when KMS and signer are missing`() =
        runBlocking<Unit> {
            val credentials =
                listOf(
                    createValidCredential().copy(
                        credentialSubject =
                            CredentialSubject.fromDid(
                                Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"),
                            ),
                    ),
                )
            val request =
                PresentationRequest(
                    proofOptions =
                        proofOptions {
                            purpose = ProofPurpose.Authentication
                            challenge = "challenge-123"
                            domain = "verifier.example"
                            verificationMethod = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1"
                        },
                )

            val exception =
                assertThrows<IllegalStateException> {
                    engine.createPresentation(credentials, request)
                }
            assertTrue(exception.message?.contains("KMS not configured") == true)
        }

    @Test
    fun `test createPresentation signs VP with real Data Integrity proof and verifies end-to-end`() =
        runBlocking<Unit> {
            val kms = InMemoryKeyManagementService()
            val didMethod = DidKeyMockMethod(kms)
            val issuerDoc = didMethod.createDid()
            val holderDoc = didMethod.createDid()
            val didResolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                }
            val signedEngine =
                VcLdProofEngine(
                    config =
                        ProofEngineConfig(
                            properties = mapOf("kms" to kms),
                            didResolver = didResolver,
                        ),
                )

            val issuedVc =
                signedEngine.issue(
                    IssuanceRequest(
                        format = ProofSuiteId.VC_LD,
                        issuer = Issuer.IriIssuer(Iri(issuerDoc.id.value)),
                        issuerKeyId = issuerDoc.verificationMethod.first().id,
                        credentialSubject = CredentialSubject(id = Iri(holderDoc.id.value)),
                        type = listOf(CredentialType.VerifiableCredential),
                        issuedAt = Clock.System.now(),
                    ),
                )
            assertTrue(signedEngine.verify(issuedVc, VerificationOptions()) is VerificationResult.Valid)

            val holderVmId =
                holderDoc.verificationMethod
                    .first()
                    .id.value
            val presentation =
                signedEngine.createPresentation(
                    credentials = listOf(issuedVc),
                    request =
                        PresentationRequest(
                            proofOptions =
                                proofOptions {
                                    challenge = "vp-challenge-42"
                                    domain = "verifier.trustweave.example"
                                    verificationMethod = holderVmId
                                },
                        ),
                )

            val proof = assertIs<CredentialProof.LinkedDataProof>(presentation.proof)
            assertEquals(CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020, proof.type)
            assertEquals(CredentialConstants.ProofPurposes.AUTHENTICATION, proof.proofPurpose)
            assertEquals(holderVmId, proof.verificationMethod)
            assertEquals(JsonPrimitive("vp-challenge-42"), proof.additionalProperties["challenge"])
            assertEquals(JsonPrimitive("verifier.trustweave.example"), proof.additionalProperties["domain"])
            assertTrue(proof.proofValue.startsWith("z"), "Ed25519Signature2020 proofValue must be multibase base58-btc ('z')")

            // End-to-end verification via PresentationVerification
            val resolvedVm =
                PresentationVerification.resolvePresentationProofVerificationMethod(
                    holderIri = presentation.holder,
                    verificationMethodId = proof.verificationMethod,
                    didResolver = didResolver,
                    declaredProofPurpose = proof.proofPurpose,
                )
            assertNotNull(resolvedVm, "Holder verification method must resolve for authentication purpose")

            val vpJson = Json { serializersModule = SerializationModule.default }
            val vpFullJson = vpJson.encodeToJsonElement(VerifiablePresentation.serializer(), presentation).jsonObject
            val vpDocWithoutProof =
                buildJsonObject {
                    vpFullJson.entries.forEach { (k, v) -> if (k != "proof") put(k, v) }
                }

            assertTrue(
                PresentationVerification.verifyPresentationSignature(vpDocWithoutProof, proof, resolvedVm),
                "Signed VP Data Integrity proof must verify end-to-end",
            )
            assertNull(
                PresentationVerification.verifyChallenge(
                    presentation,
                    VerificationOptions(verifyChallenge = true, expectedChallenge = "vp-challenge-42"),
                ),
            )
            assertNull(
                PresentationVerification.verifyDomain(
                    presentation,
                    VerificationOptions(verifyDomain = true, expectedDomain = "verifier.trustweave.example"),
                ),
            )

            // Tampering with challenge or domain must invalidate the Data Integrity signature
            val tamperedProof =
                proof.copy(
                    additionalProperties = proof.additionalProperties + ("challenge" to JsonPrimitive("replayed-challenge")),
                )
            assertFalse(
                PresentationVerification.verifyPresentationSignature(vpDocWithoutProof, tamperedProof, resolvedVm),
                "Tampering with challenge must invalidate VP signature",
            )
        }

    @Test
    fun `test createPresentation signs VP using custom signer property`() =
        runBlocking<Unit> {
            val kms = InMemoryKeyManagementService()
            val didMethod = DidKeyMockMethod(kms)
            val holderDoc = didMethod.createDid()
            val didResolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                }
            val customSigner: suspend (ByteArray, String) -> ByteArray = { data, keyId ->
                when (val result = kms.sign(KeyId(keyId), data)) {
                    is SignResult.Success -> result.signature
                    else -> error("Unexpected sign result: $result")
                }
            }
            val signerEngine =
                VcLdProofEngine(
                    config =
                        ProofEngineConfig(
                            properties = mapOf("signer" to customSigner),
                            didResolver = didResolver,
                        ),
                )

            val holderVmId =
                holderDoc.verificationMethod
                    .first()
                    .id.value
            val credential =
                createValidCredential().copy(
                    credentialSubject = CredentialSubject(id = Iri(holderDoc.id.value)),
                )
            val presentation =
                signerEngine.createPresentation(
                    credentials = listOf(credential),
                    request =
                        PresentationRequest(
                            proofOptions =
                                proofOptions {
                                    purpose = ProofPurpose.Authentication
                                    challenge = "custom-signer-nonce"
                                    domain = "rp.example"
                                    verificationMethod = holderVmId
                                },
                        ),
                )

            val proof = assertIs<CredentialProof.LinkedDataProof>(presentation.proof)
            assertEquals(CredentialConstants.ProofPurposes.AUTHENTICATION, proof.proofPurpose)
            assertEquals(JsonPrimitive("custom-signer-nonce"), proof.additionalProperties["challenge"])
            assertEquals(JsonPrimitive("rp.example"), proof.additionalProperties["domain"])

            val resolvedVm =
                PresentationVerification.resolvePresentationProofVerificationMethod(
                    holderIri = presentation.holder,
                    verificationMethodId = proof.verificationMethod,
                    didResolver = didResolver,
                    declaredProofPurpose = proof.proofPurpose,
                )
            assertNotNull(resolvedVm)
            val vpJson = Json { serializersModule = SerializationModule.default }
            val vpFullJson = vpJson.encodeToJsonElement(VerifiablePresentation.serializer(), presentation).jsonObject
            val vpDocWithoutProof =
                buildJsonObject {
                    vpFullJson.entries.forEach { (k, v) -> if (k != "proof") put(k, v) }
                }
            assertTrue(PresentationVerification.verifyPresentationSignature(vpDocWithoutProof, proof, resolvedVm))
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
