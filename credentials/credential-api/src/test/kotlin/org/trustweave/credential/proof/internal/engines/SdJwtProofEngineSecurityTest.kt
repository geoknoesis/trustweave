package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.internal.DefaultCredentialService
import org.trustweave.credential.internal.PresentationVerification
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.proofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
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
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Security regression tests for [SdJwtProofEngine] and the SD-JWT presentation pipeline.
 *
 * Covers:
 * - Finding 4a: signed `exp`/`nbf` claims are authoritative — stripping or rewriting the
 *   unsigned envelope `expirationDate` must not bypass expiry.
 * - Finding 4b: the signed `iss` claim must match the envelope issuer.
 * - Finding 4c: envelope `credentialSubject` claims must be backed (name AND value) by
 *   verified disclosures or signed claims.
 * - Finding 4d: Key Binding JWT (KB-JWT) verification for SD-JWT presentations —
 *   valid round-trip, wrong nonce/audience, missing/stripped KB-JWT.
 * - Finding 8: holder binding requires exact DID equality of the verification method's
 *   pre-fragment part, not a prefix match.
 */
class SdJwtProofEngineSecurityTest {

    private class TestRig {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
        val holderDocument: DidDocument = runBlocking { didMethod.createDid() }
        val didResolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
        }
        val engine = SdJwtProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms),
                didResolver = didResolver
            )
        )
        val service = DefaultCredentialService(
            engines = mapOf(ProofSuiteId.SD_JWT_VC to engine),
            didResolver = didResolver
        )

        suspend fun issue(
            claims: Map<String, JsonPrimitive> = mapOf(
                "name" to JsonPrimitive("Alice"),
                "email" to JsonPrimitive("alice@example.com")
            ),
            validUntil: Instant? = null
        ): VerifiableCredential = engine.issue(
            IssuanceRequest(
                format = ProofSuiteId.SD_JWT_VC,
                issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                issuerKeyId = issuerDocument.verificationMethod.first().id,
                credentialSubject = CredentialSubject(
                    id = Iri(holderDocument.id.value),
                    claims = claims
                ),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                validUntil = validUntil
            )
        )

        suspend fun present(
            credential: VerifiableCredential,
            challenge: String? = null,
            domain: String? = null
        ) = engine.createPresentation(
            listOf(credential),
            PresentationRequest(
                proofOptions = proofOptions {
                    challenge?.let { this.challenge = it }
                    domain?.let { this.domain = it }
                    verificationMethod = holderDocument.verificationMethod.first().id.value
                }
            )
        )
    }

    private fun assertInvalid(result: VerificationResult, message: String) {
        assertTrue(result is VerificationResult.Invalid, message + " — got ${result::class.simpleName}")
    }

    // --- Happy path ---------------------------------------------------------------------

    @Test
    fun `sd-jwt sign and verify round-trip succeeds`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue(validUntil = Clock.System.now().plus(1.hours))

        val result = rig.engine.verify(credential, VerificationOptions())

        assertTrue(
            result is VerificationResult.Valid,
            "Expected Valid, got ${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    // --- Issuer key purpose: must be authorized under assertionMethod ---------------------

    @Test
    fun `issuer key not listed under assertionMethod is rejected`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue(validUntil = Clock.System.now().plus(1.hours))

        // Remove the issuer key from the assertionMethod relationship: it remains present
        // in verificationMethod (and authentication) but is no longer authorized to sign
        // assertions (credentials).
        rig.didMethod.updateDid(rig.issuerDocument.id) { document ->
            document.copy(assertionMethod = emptyList())
        }

        val result = rig.engine.verify(credential, VerificationOptions())
        assertInvalid(
            result,
            "An issuer key present in the DID document but not referenced under " +
                "assertionMethod must not verify SD-JWT credentials"
        )
    }

    // --- Finding 4a: signed temporal claims are authoritative -----------------------------

    @Test
    fun `stripping envelope expirationDate does not bypass expired signed exp`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue(validUntil = Clock.System.now().minus(2.hours))

        // Attacker strips the unsigned envelope expiry to dodge the format-agnostic check.
        val stripped = credential.copy(expirationDate = null, validUntil = null)

        // The full service pipeline must still reject: the signed 'exp' is authoritative.
        val serviceResult = rig.service.verify(stripped, null, VerificationOptions())
        assertInvalid(serviceResult, "Stripped envelope expiry must not bypass signed exp")

        // And the engine itself rejects it as expired/tampered.
        val engineResult = rig.engine.verify(stripped, VerificationOptions())
        assertInvalid(engineResult, "Engine must reject expired signed exp")
        assertTrue(
            engineResult is VerificationResult.Invalid.Expired,
            "Expected Expired, got ${engineResult::class.simpleName}"
        )
    }

    @Test
    fun `rewriting envelope expirationDate to extend validity is rejected as tampered`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue(validUntil = Clock.System.now().plus(1.hours))

        val extended = credential.copy(expirationDate = Clock.System.now().plus(1000.hours))

        val result = rig.engine.verify(extended, VerificationOptions())
        assertInvalid(result, "Envelope expiry disagreeing with signed exp must be rejected")
    }

    // --- Finding 4b: signed iss must match the envelope issuer ----------------------------

    @Test
    fun `signed iss differing from envelope issuer is rejected`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()

        // Attacker publishes a DID document that embeds the real issuer's public key, then
        // re-attributes the credential envelope to that DID. The signature still verifies
        // against the embedded key — only the signed 'iss' claim exposes the fraud.
        val attackerDocument = rig.didMethod.createDid()
        rig.didMethod.updateDid(attackerDocument.id) { document ->
            document.copy(
                verificationMethod = rig.issuerDocument.verificationMethod,
                assertionMethod = rig.issuerDocument.assertionMethod
            )
        }
        val reAttributed = credential.copy(
            issuer = Issuer.IriIssuer(Iri(attackerDocument.id.value))
        )

        val result = rig.engine.verify(reAttributed, VerificationOptions())
        assertInvalid(result, "Envelope issuer differing from signed iss must be rejected")
        assertTrue(
            result is VerificationResult.Invalid.InvalidIssuer,
            "Expected InvalidIssuer, got ${result::class.simpleName}"
        )
    }

    // --- Finding 4c: envelope claims must be backed by signed/disclosed data --------------

    @Test
    fun `envelope claim value differing from disclosure value is rejected`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()

        val tampered = credential.copy(
            credentialSubject = credential.credentialSubject.copy(
                claims = credential.credentialSubject.claims + ("name" to JsonPrimitive("Mallory"))
            )
        )

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertInvalid(result, "Swapped envelope claim value must be rejected")
    }

    @Test
    fun `envelope claim not backed by any disclosure is rejected`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()

        val tampered = credential.copy(
            credentialSubject = credential.credentialSubject.copy(
                claims = credential.credentialSubject.claims + ("admin" to JsonPrimitive(true))
            )
        )

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertInvalid(result, "Injected envelope claim without a disclosure must be rejected")
    }

    // --- Finding 4d: KB-JWT verification ---------------------------------------------------

    private fun kbOptions(
        challenge: String = "nonce-123",
        domain: String = "verifier.example.com"
    ) = VerificationOptions(
        verifyChallenge = true,
        expectedChallenge = challenge,
        verifyDomain = true,
        expectedDomain = domain,
        enforceHolderBinding = true
    )

    @Test
    fun `kb-jwt presentation round-trip passes full service verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue(validUntil = Clock.System.now().plus(1.hours))
        val presentation = rig.present(
            credential,
            challenge = "nonce-123",
            domain = "verifier.example.com"
        )

        val result = rig.service.verifyPresentation(presentation, null, kbOptions())

        assertTrue(
            result is VerificationResult.Valid,
            "Expected Valid, got ${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    @Test
    fun `kb-jwt with wrong nonce fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        val presentation = rig.present(
            credential,
            challenge = "nonce-123",
            domain = "verifier.example.com"
        )

        val result = rig.service.verifyPresentation(
            presentation, null, kbOptions(challenge = "a-different-nonce")
        )
        assertInvalid(result, "KB-JWT nonce differing from the expected challenge must fail")
    }

    @Test
    fun `kb-jwt with wrong audience fails verification`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        val presentation = rig.present(
            credential,
            challenge = "nonce-123",
            domain = "verifier.example.com"
        )

        val result = rig.service.verifyPresentation(
            presentation, null, kbOptions(domain = "attacker.example.com")
        )
        assertInvalid(result, "KB-JWT audience differing from the expected domain must fail")
    }

    @Test
    fun `presentation without kb-jwt fails when proof verification is required`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        // No challenge => engine does not create a KB-JWT and the presentation has no proof.
        val presentation = rig.present(credential, challenge = null)

        val result = rig.service.verifyPresentation(
            presentation, null, VerificationOptions(verifyPresentationProof = true)
        )
        assertInvalid(result, "Missing presentation proof / KB-JWT must fail when required")
    }

    @Test
    fun `presentation with stripped kb-jwt fails`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        val presentation = rig.present(
            credential,
            challenge = "nonce-123",
            domain = "verifier.example.com"
        )

        val sdProof = presentation.proof as CredentialProof.SdJwtVcProof
        // Strip the KB-JWT (keep the trailing '~' of the compact form).
        val strippedCompact = sdProof.sdJwtVc.substringBeforeLast("~") + "~"
        val stripped = presentation.copy(
            proof = CredentialProof.SdJwtVcProof(
                sdJwtVc = strippedCompact,
                disclosures = sdProof.disclosures
            )
        )

        val result = rig.service.verifyPresentation(stripped, null, kbOptions())
        assertInvalid(result, "A presentation whose KB-JWT was stripped must fail")
    }

    @Test
    fun `kb-jwt bound to different disclosures fails sd_hash check`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        val presentation = rig.present(
            credential,
            challenge = "nonce-123",
            domain = "verifier.example.com"
        )

        val sdProof = presentation.proof as CredentialProof.SdJwtVcProof
        val segments = sdProof.sdJwtVc.split("~")
        // Drop one disclosure from the compact form while keeping the original KB-JWT.
        val tamperedCompact = (listOf(segments.first()) + segments.drop(2)).joinToString("~")
        val tampered = presentation.copy(
            proof = CredentialProof.SdJwtVcProof(
                sdJwtVc = tamperedCompact,
                disclosures = sdProof.disclosures?.drop(1)
            )
        )

        val result = rig.service.verifyPresentation(tampered, null, kbOptions())
        assertInvalid(result, "A KB-JWT whose sd_hash does not match the presented token must fail")
    }

    // --- KB-JWT iat freshness (max age) ----------------------------------------------------

    /**
     * Craft a KB-JWT over [presentedWithoutKb] (the compact SD-JWT ending in `~`) signed
     * by the holder's key, with a caller-controlled `iat` — the engine always stamps
     * `iat = now`, so age-based tests need a hand-built (but otherwise genuine) KB-JWT.
     */
    private suspend fun TestRig.craftKbJwt(
        presentedWithoutKb: String,
        iat: Instant,
        challenge: String = "nonce-123",
        domain: String = "verifier.example.com"
    ): String {
        val verificationMethodId = holderDocument.verificationMethod.first().id.value
        val keyId = verificationMethodId.substringAfter("#")
        val b64 = Base64.getUrlEncoder().withoutPadding()
        val sdHash = b64.encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(presentedWithoutKb.toByteArray(Charsets.UTF_8))
        )
        val header = b64.encodeToString(
            """{"typ":"kb+jwt","alg":"EdDSA","kid":"$verificationMethodId"}"""
                .toByteArray(Charsets.UTF_8)
        )
        val payload = b64.encodeToString(
            """{"iat":${iat.epochSeconds},"nonce":"$challenge","sd_hash":"$sdHash","aud":"$domain"}"""
                .toByteArray(Charsets.UTF_8)
        )
        val signResult = kms.sign(KeyId(keyId), "$header.$payload".toByteArray(Charsets.UTF_8))
        val signature = (signResult as SignResult.Success).signature
        return "$header.$payload.${b64.encodeToString(signature)}"
    }

    private suspend fun TestRig.presentationWithKbJwtAge(age: kotlin.time.Duration): VerifiablePresentation {
        val credential = issue(validUntil = Clock.System.now().plus(2.hours))
        val presentation = present(credential, challenge = "nonce-123", domain = "verifier.example.com")
        val sdProof = presentation.proof as CredentialProof.SdJwtVcProof
        val presentedWithoutKb = sdProof.sdJwtVc.substringBeforeLast("~") + "~"
        val kbJwt = craftKbJwt(presentedWithoutKb, iat = Clock.System.now().minus(age))
        return presentation.copy(
            proof = CredentialProof.SdJwtVcProof(
                sdJwtVc = presentedWithoutKb + kbJwt,
                disclosures = sdProof.disclosures
            )
        )
    }

    @Test
    fun `kb-jwt older than the default max age is rejected`() = runBlocking {
        val rig = TestRig()
        // 1 hour old: well past the 10-minute default max age + 5-minute default skew.
        val stale = rig.presentationWithKbJwtAge(1.hours)

        val result = rig.service.verifyPresentation(stale, null, kbOptions())
        assertInvalid(result, "A KB-JWT with an hour-old iat must be rejected as a replay")
        assertTrue(
            (result as VerificationResult.Invalid).errors.joinToString().contains("max age", ignoreCase = true) ||
                (result as? VerificationResult.Invalid.InvalidProof)?.reason
                    ?.contains("too old", ignoreCase = true) == true,
            "Failure should mention the KB-JWT max age, got: ${result.errors}"
        )
    }

    @Test
    fun `kb-jwt max age is configurable via the kbJwtMaxAge additional option`() = runBlocking {
        val rig = TestRig()
        // 30 minutes old: rejected by the 10-minute default, accepted at a 2-hour max age.
        val aged = rig.presentationWithKbJwtAge(30.minutes)

        val defaultResult = rig.service.verifyPresentation(aged, null, kbOptions())
        assertInvalid(defaultResult, "A 30-minute-old KB-JWT must fail the 10-minute default max age")

        val relaxed = kbOptions().copy(
            additionalOptions = mapOf(PresentationVerification.KB_JWT_MAX_AGE_OPTION to 2.hours)
        )
        val relaxedResult = rig.service.verifyPresentation(aged, null, relaxed)
        assertTrue(
            relaxedResult is VerificationResult.Valid,
            "A 30-minute-old KB-JWT must pass a 2-hour max age (also proves the crafted " +
                "KB-JWT is otherwise genuine), got ${relaxedResult::class.simpleName}: " +
                ((relaxedResult as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    // --- Finding 8: holder binding must use exact DID equality ----------------------------

    @Test
    fun `holder binding rejects prefix-matching verification method DID`() {
        assertFalse(
            PresentationVerification.verificationMethodBelongsToHolder(
                "did:example:abcdef#key-1", "did:example:abc"
            ),
            "'did:example:abcdef#key-1' must NOT satisfy holder 'did:example:abc'"
        )
        assertFalse(
            PresentationVerification.verificationMethodBelongsToHolder(
                "did:example:other#key-1", "did:example:abc"
            ),
            "A different DID must not satisfy the holder"
        )
    }

    @Test
    fun `holder binding accepts exact DID match`() {
        assertTrue(
            PresentationVerification.verificationMethodBelongsToHolder(
                "did:example:abc#key-1", "did:example:abc"
            ),
            "'did:example:abc#key-1' must satisfy holder 'did:example:abc'"
        )
        assertTrue(
            PresentationVerification.verificationMethodBelongsToHolder(
                "did:example:abc", "did:example:abc"
            ),
            "A fragment-less verification method equal to the holder DID must match"
        )
    }
}
