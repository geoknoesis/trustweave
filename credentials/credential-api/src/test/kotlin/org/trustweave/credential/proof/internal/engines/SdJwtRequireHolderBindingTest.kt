package org.trustweave.credential.proof.internal.engines

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
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
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Opt-in `requireHolderBinding`: by default a cnf-less SD-JWT credential is accepted as a bearer credential
 * (documented design); with the option set it is rejected, standalone and when presented.
 */
class SdJwtRequireHolderBindingTest {
    private class TestRig {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
        val holderDocument: DidDocument = runBlocking { didMethod.createDid() }
        val attackerDocument: DidDocument = runBlocking { didMethod.createDid() }
        val didResolver =
            object : DidResolver {
                override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
            }
        val engine =
            SdJwtProofEngine(
                config =
                    ProofEngineConfig(
                        properties = mapOf("kms" to kms),
                        didResolver = didResolver,
                    ),
            )
        val service =
            DefaultCredentialService(
                engines = mapOf(ProofSuiteId.SD_JWT_VC to engine),
                didResolver = didResolver,
            )

        suspend fun issue(validUntil: Instant? = Clock.System.now().plus(2.hours)): VerifiableCredential =
            engine.issue(
                IssuanceRequest(
                    format = ProofSuiteId.SD_JWT_VC,
                    issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                    issuerKeyId = issuerDocument.verificationMethod.first().id,
                    credentialSubject =
                        CredentialSubject(
                            id = Iri(holderDocument.id.value),
                            claims =
                                mapOf(
                                    "name" to JsonPrimitive("Alice"),
                                    "email" to JsonPrimitive("alice@example.com"),
                                ),
                        ),
                    type = listOf(CredentialType.fromString("VerifiableCredential")),
                    validUntil = validUntil,
                ),
            )

        suspend fun present(
            credential: VerifiableCredential,
            challenge: String = "nonce-123",
            domain: String = "verifier.example.com",
        ) = engine.createPresentation(
            listOf(credential),
            PresentationRequest(
                proofOptions =
                    proofOptions {
                        this.challenge = challenge
                        this.domain = domain
                        verificationMethod =
                            holderDocument.verificationMethod
                                .first()
                                .id.value
                    },
            ),
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
            domain: String = "verifier.example.com",
        ): String {
            val verificationMethodId =
                signerDocument.verificationMethod
                    .first()
                    .id.value
            val keyId = verificationMethodId.substringAfter("#")
            val b64 = Base64.getUrlEncoder().withoutPadding()
            val sdHash =
                b64.encodeToString(
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(presentedWithoutKb.toByteArray(Charsets.UTF_8)),
                )
            val header =
                b64.encodeToString(
                    """{"typ":"kb+jwt","alg":"EdDSA","kid":"$verificationMethodId"}"""
                        .toByteArray(Charsets.UTF_8),
                )
            val payloadJson =
                """{"iat":${Clock.System.now().epochSeconds},"nonce":"$challenge",""" +
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
                        MessageDigest.getInstance("SHA-256").digest(discB64.toByteArray(Charsets.UTF_8)),
                    ),
                )
            }

            val verificationMethodId =
                issuerDocument.verificationMethod
                    .first()
                    .id.value
            val keyId = verificationMethodId.substringAfter("#")
            val header =
                b64.encodeToString(
                    """{"typ":"dc+sd-jwt","alg":"EdDSA","kid":"$keyId"}""".toByteArray(Charsets.UTF_8),
                )
            val sdArray = sdHashes.joinToString(",") { "\"$it\"" }
            val payload =
                b64.encodeToString(
                    (
                        """{"iss":"${issuerDocument.id.value}","sub":"${holderDocument.id.value}",""" +
                            """"iat":${Clock.System.now().epochSeconds},"_sd_alg":"sha-256",""" +
                            """"vct":"VerifiableCredential","vc":{""" +
                            """"@context":["https://www.w3.org/2018/credentials/v1"],""" +
                            """"type":["VerifiableCredential"],""" +
                            """"credentialSubject":{"id":"${holderDocument.id.value}","_sd":[$sdArray]}}}"""
                    ).toByteArray(Charsets.UTF_8),
                )
            val signResult = kms.sign(KeyId(keyId), "$header.$payload".toByteArray(Charsets.UTF_8))
            val signature = (signResult as SignResult.Success).signature
            val jwt = "$header.$payload.${b64.encodeToString(signature)}"

            return VerifiableCredential(
                id = CredentialId("urn:uuid:${UUID.randomUUID()}"),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                issuanceDate = Clock.System.now(),
                credentialSubject =
                    CredentialSubject(
                        id = Iri(holderDocument.id.value),
                        claims = claims,
                    ),
                proof = CredentialProof.SdJwtVcProof(sdJwtVc = jwt, disclosures = disclosures),
            )
        }
    }

    private fun presentationOptions(require: Boolean) =
        VerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "nonce-123",
            verifyDomain = true,
            expectedDomain = "verifier.example.com",
            requireHolderBinding = require,
        )

    @Test
    fun `option defaults to false`() {
        assertEquals(false, VerificationOptions().requireHolderBinding)
    }

    @Test
    fun `cnf-less credential is accepted as a bearer credential by default`() =
        runBlocking<Unit> {
            val rig = TestRig()
            val result = rig.service.verify(rig.issueLegacyWithoutCnf(), null, VerificationOptions(checkRevocation = false))
            assertTrue(result is VerificationResult.Valid, "got $result")
        }

    @Test
    fun `requireHolderBinding rejects a cnf-less credential`() =
        runBlocking<Unit> {
            val rig = TestRig()
            val result =
                rig.service.verify(
                    rig.issueLegacyWithoutCnf(),
                    null,
                    VerificationOptions(checkRevocation = false, requireHolderBinding = true),
                )
            val invalid = result as? VerificationResult.Invalid.InvalidProof
            assertTrue(invalid != null, "got $result")
            assertTrue(invalid.reason.contains("cnf"), invalid.reason)
        }

    @Test
    fun `requireHolderBinding accepts a cnf-bound credential`() =
        runBlocking<Unit> {
            val rig = TestRig()
            val result =
                rig.service.verify(rig.issue(), null, VerificationOptions(checkRevocation = false, requireHolderBinding = true))
            assertTrue(result is VerificationResult.Valid, "got $result")
        }

    @Test
    fun `requireHolderBinding leaves a tampered credential rejected for its own reason first`() =
        runBlocking<Unit> {
            val rig = TestRig()
            val credential = rig.issueLegacyWithoutCnf()
            val proof = credential.proof as CredentialProof.SdJwtVcProof
            val tampered = credential.copy(proof = proof.copy(sdJwtVc = proof.sdJwtVc.dropLast(4) + "AAAA"))
            val result =
                rig.service.verify(tampered, null, VerificationOptions(checkRevocation = false, requireHolderBinding = true))
            assertTrue(result is VerificationResult.Invalid.InvalidProof, "got $result")
            assertTrue(!(result as VerificationResult.Invalid.InvalidProof).reason.contains("requireHolderBinding"))
        }

    @Test
    fun `presentation of a cnf-less credential is accepted by default and rejected when binding is required`() =
        runBlocking<Unit> {
            val rig = TestRig()
            val presentation = rig.present(rig.issueLegacyWithoutCnf())

            val lenient = rig.service.verifyPresentation(presentation, null, presentationOptions(false).copy(checkRevocation = false))
            assertTrue(lenient is VerificationResult.Valid, "got $lenient")

            val strict = rig.service.verifyPresentation(presentation, null, presentationOptions(true).copy(checkRevocation = false))
            assertTrue(strict is VerificationResult.Invalid, "got $strict")
        }

    @Test
    fun `presentation of a cnf-bound credential still verifies when binding is required`() =
        runBlocking<Unit> {
            val rig = TestRig()
            val presentation = rig.present(rig.issue())
            val result = rig.service.verifyPresentation(presentation, null, presentationOptions(true).copy(checkRevocation = false))
            assertTrue(result is VerificationResult.Valid, "got $result")
        }
}
