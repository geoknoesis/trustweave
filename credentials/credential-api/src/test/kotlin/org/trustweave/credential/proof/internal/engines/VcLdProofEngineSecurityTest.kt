package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.exception.SerializationException
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.JsonLdContextLoader
import org.trustweave.credential.internal.JsonLdUtils
import org.trustweave.credential.internal.PresentationVerification
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.proofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Security regression tests for [VcLdProofEngine] and the JSON-LD proof pipeline.
 *
 * Covers:
 * - Data Integrity proof options coverage: tampering with `challenge`/`domain` (or any
 *   proof option) after signing must invalidate the signature (VP replay protection).
 * - Dropped-claims fail-closed: claims whose terms are undefined in the credential's
 *   `@context` are rejected at issuance instead of being silently left unsigned.
 * - Tampered `credentialSubject` claims must invalidate the signature (claims are covered
 *   because the declared `@context` is preserved).
 * - Proof purpose enforcement: a proof with the wrong `proofPurpose`, or a verification
 *   method not authorized for `assertionMethod`, is rejected.
 */
class VcLdProofEngineSecurityTest {

    companion object {
        private const val TEST_CONTEXT_URL = "https://trustweave.example/contexts/person-claims/v1"

        init {
            // Register a claim vocabulary so "name"/"email" are defined JSON-LD terms.
            JsonLdContextLoader.registerContext(
                TEST_CONTEXT_URL,
                """
                {
                  "@context": {
                    "name": "https://schema.org/name",
                    "email": "https://schema.org/email"
                  }
                }
                """.trimIndent()
            )
        }
    }

    private class TestRig {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
        val holderDocument: DidDocument = runBlocking { didMethod.createDid() }
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
        }
        val engine = VcLdProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms),
                didResolver = didResolver
            )
        )

        fun issuanceRequest(
            claims: Map<String, JsonPrimitive> = mapOf(
                "name" to JsonPrimitive("Alice"),
                "email" to JsonPrimitive("alice@example.com")
            ),
            contexts: List<String>? = listOf(TEST_CONTEXT_URL),
            challenge: String? = "challenge-123",
            domain: String? = "verifier.example.com",
            proofType: String? = null
        ): IssuanceRequest {
            return IssuanceRequest(
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                issuerKeyId = issuerDocument.verificationMethod.first().id,
                credentialSubject = CredentialSubject(
                    id = Iri(holderDocument.id.value),
                    claims = claims
                ),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                issuedAt = Clock.System.now(),
                proofOptions = proofOptions {
                    challenge?.let { this.challenge = it }
                    domain?.let { this.domain = it }
                    contexts?.let { option(JsonLdDocumentBuilder.CONTEXTS_OPTION, it) }
                    proofType?.let { option("proofType", it) }
                }
            )
        }
    }

    private fun VerifiableCredential.withProof(
        transform: (CredentialProof.LinkedDataProof) -> CredentialProof.LinkedDataProof
    ): VerifiableCredential {
        val ldProof = proof as CredentialProof.LinkedDataProof
        return copy(proof = transform(ldProof))
    }

    // --- Happy path -------------------------------------------------------------------

    @Test
    fun `sign and verify round-trip succeeds with proof options covered`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest())

        val result = rig.engine.verify(credential, VerificationOptions())

        assertTrue(
            result is VerificationResult.Valid,
            "Expected Valid, got ${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    @Test
    fun `JsonWebSignature2020 sign and verify round-trip succeeds`() = runBlocking {
        // EdDSA detached-JWS verification is implemented with the Java Security API
        // (java.security.Signature "Ed25519"), NOT Nimbus' Ed25519Verifier, which would
        // throw NoClassDefFoundError because the optional com.google.crypto.tink
        // dependency of nimbus-jose-jwt is not on this module's classpath.
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest(proofType = "JsonWebSignature2020"))

        val proof = credential.proof as CredentialProof.LinkedDataProof
        assertTrue(proof.type == "JsonWebSignature2020")
        assertTrue(
            proof.proofValue.matches(Regex("^[A-Za-z0-9_-]+\\.\\.[A-Za-z0-9_-]+$")),
            "proofValue must be a detached JWS (header..signature), got: ${proof.proofValue.take(40)}"
        )
        assertTrue(
            CredentialConstants.SecuritySuites.JSON_WEB_SIGNATURE_2020_V1 in credential.context,
            "Credential @context must include the jws-2020 suite context"
        )

        val result = rig.engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Valid,
            "JsonWebSignature2020 verification round-trip must succeed, got " +
                "${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    @Test
    fun `JsonWebSignature2020 tampered credentialSubject claim fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest(proofType = "JsonWebSignature2020"))

        val tampered = credential.copy(
            credentialSubject = credential.credentialSubject.copy(
                claims = credential.credentialSubject.claims + ("name" to JsonPrimitive("Mallory"))
            )
        )

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "A tampered claim under a JsonWebSignature2020 proof must invalidate the signature"
        )
    }

    // --- Finding 1: proof options must be covered by the signature ---------------------

    @Test
    fun `tampering with proof challenge after signing fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest(challenge = "original-challenge"))

        val tampered = credential.withProof { proof ->
            proof.copy(
                additionalProperties = proof.additionalProperties +
                    ("challenge" to JsonPrimitive("attacker-challenge"))
            )
        }

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "A rewritten proof.challenge must invalidate the signature (VP replay protection)"
        )
    }

    @Test
    fun `tampering with proof domain after signing fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest(domain = "original.example.com"))

        val tampered = credential.withProof { proof ->
            proof.copy(
                additionalProperties = proof.additionalProperties +
                    ("domain" to JsonPrimitive("attacker.example.com"))
            )
        }

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "A rewritten proof.domain must invalidate the signature"
        )
    }

    @Test
    fun `tampering with proof created timestamp after signing fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest())

        val tampered = credential.withProof { proof ->
            proof.copy(created = proof.created.plus(kotlin.time.Duration.parse("PT1H")))
        }

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(result is VerificationResult.Invalid, "A rewritten proof.created must invalidate the signature")
    }

    // --- Finding 2: credentialSubject claims must be signed -----------------------------

    @Test
    fun `tampering with a credentialSubject claim after signing fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest())

        val tampered = credential.copy(
            credentialSubject = credential.credentialSubject.copy(
                claims = credential.credentialSubject.claims + ("name" to JsonPrimitive("Mallory"))
            )
        )

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "A tampered credentialSubject claim must invalidate the signature " +
                "(claims must be covered by the canonical form)"
        )
    }

    @Test
    fun `issuing a credential with claims undefined in its context fails closed`(): Unit = runBlocking {
        val rig = TestRig()
        // No claim context declared: "favoriteColor" is an undefined JSON-LD term and
        // would be silently dropped from the canonical form (left unsigned).
        val request = rig.issuanceRequest(
            claims = mapOf("favoriteColor" to JsonPrimitive("green")),
            contexts = null
        )

        val exception = assertFailsWith<SerializationException> {
            rig.engine.issue(request)
        }
        assertTrue(
            exception.message?.contains("@context") == true,
            "Error must tell the caller to declare a proper @context, got: ${exception.message}"
        )
    }

    // --- Finding 9: proof purpose enforcement -------------------------------------------

    @Test
    fun `proof with keyAgreement proofPurpose is rejected`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest())

        val tampered = credential.withProof { proof ->
            proof.copy(proofPurpose = "keyAgreement")
        }

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(result is VerificationResult.Invalid, "keyAgreement proofPurpose must be rejected")
        val reason = (result as? VerificationResult.Invalid.InvalidProof)?.reason ?: ""
        assertTrue(
            reason.contains("purpose", ignoreCase = true),
            "Failure should clearly mention the proof purpose, got: $reason"
        )
    }

    @Test
    fun `verification method not listed under assertionMethod is rejected`() = runBlocking {
        val rig = TestRig()
        val credential = rig.engine.issue(rig.issuanceRequest())

        // Remove the key from the issuer's assertionMethod relationship (it remains in
        // verificationMethod and authentication) — it must no longer verify assertions.
        rig.didMethod.updateDid(rig.issuerDocument.id) { document ->
            document.copy(assertionMethod = emptyList())
        }

        val result = rig.engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "A key not referenced under assertionMethod must not verify credential proofs"
        )
    }

    // --- Presentation proof path ---------------------------------------------------------

    @Test
    fun `presentation signature covers proof options - tampered challenge fails`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val holderDocument = didMethod.createDid()
        val verificationMethod = holderDocument.verificationMethod.first()
        val keyId = verificationMethod.id.value.substringAfter("#")

        val vpDocument = buildJsonObject {
            put("@context", buildJsonArray {
                add(CredentialConstants.VcContexts.VC_1_1)
                add(CredentialConstants.SecuritySuites.ED25519_2020_V1)
            })
            put("type", buildJsonArray { add("VerifiablePresentation") })
            put("holder", holderDocument.id.value)
        }

        val created = Clock.System.now()
        val challenge = "vp-challenge-123"
        val proofOptionsDocument = ProofEngineUtils.buildProofOptionsDocument(
            context = listOf(
                CredentialConstants.VcContexts.VC_1_1,
                CredentialConstants.SecuritySuites.ED25519_2020_V1
            ),
            proofType = CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020,
            created = created.toString(),
            verificationMethod = verificationMethod.id.value,
            proofPurpose = CredentialConstants.ProofPurposes.AUTHENTICATION,
            additionalProperties = mapOf("challenge" to JsonPrimitive(challenge))
        )

        val payload = ProofEngineUtils.composeDataIntegrityPayload(
            JsonLdUtils.canonicalizeDocument(proofOptionsDocument),
            JsonLdUtils.canonicalizeDocument(vpDocument)
        )
        val signResult = kms.sign(KeyId(keyId), payload)
        val signatureBytes = (signResult as SignResult.Success).signature
        val proofValue = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)

        val proof = CredentialProof.LinkedDataProof(
            type = CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020,
            created = created,
            verificationMethod = verificationMethod.id.value,
            proofPurpose = CredentialConstants.ProofPurposes.AUTHENTICATION,
            proofValue = proofValue,
            additionalProperties = mapOf("challenge" to JsonPrimitive(challenge))
        )

        // Genuine proof verifies
        assertTrue(
            PresentationVerification.verifyPresentationSignature(vpDocument, proof, verificationMethod),
            "Presentation proof with covered proof options should verify"
        )

        // Replayed proof with a rewritten challenge must fail
        val replayed = proof.copy(
            additionalProperties = mapOf("challenge" to JsonPrimitive("attacker-challenge"))
        )
        assertFalse(
            PresentationVerification.verifyPresentationSignature(vpDocument, replayed, verificationMethod),
            "A rewritten proof.challenge on a captured presentation must fail verification"
        )
    }
}
