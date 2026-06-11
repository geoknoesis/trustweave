package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.internal.DefaultCredentialService
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
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Security tests for the SD-JWT `cnf` (RFC 7800) holder-binding chain:
 *
 * - Issuance embeds `cnf.kid = <holder DID>` whenever the credential subject id is a DID.
 * - Presentation verification REQUIRES the KB-JWT to be signed by the cnf-designated DID
 *   and the envelope holder to equal that DID — unconditionally, independent of
 *   [VerificationOptions.enforceHolderBinding].
 * - An attacker who steals a credential and presents it with their OWN KB-JWT and a
 *   rewritten envelope `holder` must be rejected (the pre-fix model verified the KB-JWT
 *   against the attacker-controlled envelope holder and accepted this).
 * - Legacy credentials WITHOUT `cnf` keep the (weaker, documented) envelope-holder
 *   binding behaviour.
 */
class SdJwtCnfHolderBindingSecurityTest {

    private class TestRig {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
        val holderDocument: DidDocument = runBlocking { didMethod.createDid() }
        val attackerDocument: DidDocument = runBlocking { didMethod.createDid() }
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
            validUntil: Instant? = Clock.System.now().plus(2.hours)
        ): VerifiableCredential = engine.issue(
            IssuanceRequest(
                format = ProofSuiteId.SD_JWT_VC,
                issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                issuerKeyId = issuerDocument.verificationMethod.first().id,
                credentialSubject = CredentialSubject(
                    id = Iri(holderDocument.id.value),
                    claims = mapOf(
                        "name" to JsonPrimitive("Alice"),
                        "email" to JsonPrimitive("alice@example.com")
                    )
                ),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                validUntil = validUntil
            )
        )

        suspend fun present(
            credential: VerifiableCredential,
            challenge: String = "nonce-123",
            domain: String = "verifier.example.com"
        ) = engine.createPresentation(
            listOf(credential),
            PresentationRequest(
                proofOptions = proofOptions {
                    this.challenge = challenge
                    this.domain = domain
                    verificationMethod = holderDocument.verificationMethod.first().id.value
                }
            )
        )

        /**
         * Craft a KB-JWT over [presentedWithoutKb] signed by [signerDocument]'s key —
         * structurally identical to what the engine produces, but with an arbitrary signer
         * (the attack vector under test).
         */
        suspend fun craftKbJwt(
            presentedWithoutKb: String,
            signerDocument: DidDocument,
            challenge: String = "nonce-123",
            domain: String = "verifier.example.com"
        ): String {
            val verificationMethodId = signerDocument.verificationMethod.first().id.value
            val keyId = verificationMethodId.substringAfter("#")
            val b64 = Base64.getUrlEncoder().withoutPadding()
            val sdHash = b64.encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(presentedWithoutKb.toByteArray(Charsets.UTF_8))
            )
            val header = b64.encodeToString(
                """{"typ":"kb+jwt","alg":"EdDSA","kid":"$verificationMethodId"}"""
                    .toByteArray(Charsets.UTF_8)
            )
            val payloadJson = """{"iat":${Clock.System.now().epochSeconds},"nonce":"$challenge",""" +
                """"sd_hash":"$sdHash","aud":"$domain"}"""
            val payload = b64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
            val signResult = kms.sign(KeyId(keyId), "$header.$payload".toByteArray(Charsets.UTF_8))
            val signature = (signResult as SignResult.Success).signature
            return "$header.$payload.${b64.encodeToString(signature)}"
        }

        /**
         * Hand-craft a legacy SD-JWT credential WITHOUT a `cnf` claim (as issued by
         * TrustWeave versions predating cnf support), issuer-signed with the real
         * issuer key so it passes credential verification.
         */
        suspend fun issueLegacyWithoutCnf(): VerifiableCredential {
            val b64 = Base64.getUrlEncoder().withoutPadding()
            val claims = mapOf("name" to JsonPrimitive("Alice"))

            val disclosures = mutableListOf<String>()
            val sdHashes = mutableListOf<String>()
            for ((claimName, claimValue) in claims) {
                val salt = b64.encodeToString(ByteArray(16) { it.toByte() })
                val disclosureJson = """["$salt","$claimName",$claimValue]"""
                val discB64 = b64.encodeToString(disclosureJson.toByteArray(Charsets.UTF_8))
                disclosures.add(discB64)
                sdHashes.add(
                    b64.encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(discB64.toByteArray(Charsets.UTF_8))
                    )
                )
            }

            val verificationMethodId = issuerDocument.verificationMethod.first().id.value
            val keyId = verificationMethodId.substringAfter("#")
            val header = b64.encodeToString(
                """{"alg":"EdDSA","kid":"$keyId"}""".toByteArray(Charsets.UTF_8)
            )
            val sdArray = sdHashes.joinToString(",") { "\"$it\"" }
            val payload = b64.encodeToString(
                (
                    """{"iss":"${issuerDocument.id.value}","sub":"${holderDocument.id.value}",""" +
                        """"iat":${Clock.System.now().epochSeconds},"_sd_alg":"sha-256",""" +
                        """"vct":"VerifiableCredential","vc":{""" +
                        """"@context":["https://www.w3.org/2018/credentials/v1"],""" +
                        """"type":["VerifiableCredential"],""" +
                        """"credentialSubject":{"id":"${holderDocument.id.value}","_sd":[$sdArray]}}}"""
                    ).toByteArray(Charsets.UTF_8)
            )
            val signResult = kms.sign(KeyId(keyId), "$header.$payload".toByteArray(Charsets.UTF_8))
            val signature = (signResult as SignResult.Success).signature
            val jwt = "$header.$payload.${b64.encodeToString(signature)}"

            return VerifiableCredential(
                id = CredentialId("urn:uuid:${UUID.randomUUID()}"),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                issuanceDate = Clock.System.now(),
                credentialSubject = CredentialSubject(
                    id = Iri(holderDocument.id.value),
                    claims = claims
                ),
                proof = CredentialProof.SdJwtVcProof(sdJwtVc = jwt, disclosures = disclosures)
            )
        }
    }

    /** Challenge/domain verification only — enforceHolderBinding deliberately left at its default (false). */
    private fun defaultKbOptions() = VerificationOptions(
        verifyChallenge = true,
        expectedChallenge = "nonce-123",
        verifyDomain = true,
        expectedDomain = "verifier.example.com"
    )

    private fun assertInvalid(result: VerificationResult, message: String): VerificationResult.Invalid {
        assertTrue(result is VerificationResult.Invalid, "$message — got ${result::class.simpleName}")
        return result
    }

    private fun assertValid(result: VerificationResult, message: String) {
        assertTrue(
            result is VerificationResult.Valid,
            "$message — got ${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    // --- cnf at issuance --------------------------------------------------------------------

    @Test
    fun `issuance embeds cnf kid bound to the holder DID`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()

        val issuerJwt = SignedJWT.parse(
            (credential.proof as CredentialProof.SdJwtVcProof).sdJwtVc.substringBefore("~")
        )
        val cnf = issuerJwt.jwtClaimsSet.getJSONObjectClaim("cnf")
        assertEquals(
            rig.holderDocument.id.value, cnf?.get("kid"),
            "Issuance must embed cnf.kid = holder DID when the subject id is a DID"
        )
    }

    // --- cnf round-trip ----------------------------------------------------------------------

    @Test
    fun `cnf round-trip presentation verifies without opting into enforceHolderBinding`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        val presentation = rig.present(credential)

        // enforceHolderBinding is NOT set: the cnf/KB-JWT chain must be enforced (and pass)
        // by default.
        val result = rig.service.verifyPresentation(presentation, null, defaultKbOptions())
        assertValid(result, "cnf-bound presentation with a holder-signed KB-JWT must verify")
    }

    // --- attacker re-binding -----------------------------------------------------------------

    @Test
    fun `kb-jwt signed by a different DID than cnf is rejected even when envelope holder matches the attacker`() =
        runBlocking {
            val rig = TestRig()
            val credential = rig.issue()
            val presentation = rig.present(credential)

            // Attacker steals the presented token, strips the holder's KB-JWT, appends
            // their OWN KB-JWT (correct nonce/aud/sd_hash, signed with the attacker key)
            // and rewrites the unsigned envelope holder to their own DID. Pre-fix, the
            // KB-JWT was verified against the envelope holder — this attack succeeded.
            val sdProof = presentation.proof as CredentialProof.SdJwtVcProof
            val withoutKb = sdProof.sdJwtVc.substringBeforeLast("~") + "~"
            val attackerKb = rig.craftKbJwt(withoutKb, signerDocument = rig.attackerDocument)
            val hijacked = presentation.copy(
                holder = Iri(rig.attackerDocument.id.value),
                proof = CredentialProof.SdJwtVcProof(
                    sdJwtVc = withoutKb + attackerKb,
                    disclosures = sdProof.disclosures
                )
            )

            val result = rig.service.verifyPresentation(hijacked, null, defaultKbOptions())
            val invalid = assertInvalid(
                result,
                "A KB-JWT signed by a non-cnf DID must be rejected even when the envelope " +
                    "holder matches the attacker"
            )
            assertTrue(
                (invalid as? VerificationResult.Invalid.InvalidProof)?.reason
                    ?.contains("cnf", ignoreCase = true) == true,
                "Rejection should cite the cnf holder binding, got: " +
                    (invalid as? VerificationResult.Invalid.InvalidProof)?.reason
            )
        }

    @Test
    fun `envelope holder differing from cnf DID is rejected even with a genuine holder kb-jwt`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()
        val presentation = rig.present(credential)

        // KB-JWT is genuine (signed by the real holder) but the unsigned envelope holder
        // is rewritten — the envelope must equal the cnf DID.
        val rebound = presentation.copy(holder = Iri(rig.attackerDocument.id.value))

        val result = rig.service.verifyPresentation(rebound, null, defaultKbOptions())
        assertInvalid(result, "Envelope holder differing from the cnf DID must be rejected")
        return@runBlocking
    }

    // --- token substitution ------------------------------------------------------------------

    @Test
    fun `presentation proof carrying a different issuer-signed jwt than the credential is rejected`() =
        runBlocking {
            val rig = TestRig()
            val cnfCredential = rig.issue()
            val legacyCredential = rig.issueLegacyWithoutCnf()
            // A proof built over a DIFFERENT (here: cnf-less) issuer-signed JWT, with a
            // perfectly valid holder KB-JWT over it, paired with the cnf-bound credential:
            // accepting it would let an attacker substitute a token that evades cnf.
            val legacyPresentation = rig.present(legacyCredential)
            val substituted = legacyPresentation.copy(
                verifiableCredential = listOf(cnfCredential)
            )

            val result = rig.service.verifyPresentation(substituted, null, defaultKbOptions())
            assertInvalid(
                result,
                "The presentation proof must carry the same issuer-signed JWT as the " +
                    "presented credential"
            )
            return@runBlocking
        }

    // --- format-agnostic cnf binding (non-SD-JWT presentation proofs) -------------------------

    @Test
    fun `cnf binding is enforced for any presentation proof format via verifyCnfHolderBinding`() = runBlocking {
        val rig = TestRig()
        val credential = rig.issue()

        // A presentation envelope (as an attacker could build around an LD presentation
        // proof signed with their OWN key) embedding the stolen cnf-bound credential.
        val hijacked = org.trustweave.credential.model.vc.VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri(rig.attackerDocument.id.value),
            verifiableCredential = listOf(credential)
        )
        val rejection = org.trustweave.credential.internal.PresentationVerification
            .verifyCnfHolderBinding(hijacked)
        assertTrue(
            rejection != null && rejection.reason.contains("cnf", ignoreCase = true),
            "A cnf-bound credential presented under a non-cnf holder must be rejected " +
                "regardless of the presentation proof format, got: ${rejection?.reason}"
        )

        // The genuine holder passes, and a legacy cnf-less credential is exempt.
        val genuine = hijacked.copy(holder = Iri(rig.holderDocument.id.value))
        assertEquals(null, org.trustweave.credential.internal.PresentationVerification
            .verifyCnfHolderBinding(genuine))
        val legacy = hijacked.copy(verifiableCredential = listOf(rig.issueLegacyWithoutCnf()))
        assertEquals(null, org.trustweave.credential.internal.PresentationVerification
            .verifyCnfHolderBinding(legacy))
    }

    // --- legacy (cnf-less) fallback ----------------------------------------------------------

    @Test
    fun `legacy credential without cnf keeps envelope-holder binding behaviour`() = runBlocking {
        val rig = TestRig()
        val legacyCredential = rig.issueLegacyWithoutCnf()

        // Sanity: the legacy credential itself verifies and carries no cnf.
        val credentialResult = rig.engine.verify(legacyCredential, VerificationOptions())
        assertValid(credentialResult, "Hand-crafted legacy credential must verify")

        val presentation = rig.present(legacyCredential)
        val result = rig.service.verifyPresentation(presentation, null, defaultKbOptions())
        // Documented weaker binding: without cnf, the KB-JWT is checked against the
        // envelope holder (which here is the genuine holder).
        assertValid(result, "cnf-less legacy presentation must keep verifying (envelope-holder binding)")
    }
}
