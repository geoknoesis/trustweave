package org.trustweave.credential.proof.internal.engines

import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.internal.DefaultCredentialService
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.InMemoryPresentationNonceStore
import org.trustweave.credential.proof.PresentationNonceStore
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
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** SD-JWT issuer-JWT `typ` enforcement and the opt-in single-use KB-JWT nonce. */
class SdJwtTypAndNonceTest {
    private class Rig {
        val kms: KeyManagementService = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument: DidDocument = runBlocking { didMethod.createDid() }
        val holderDocument: DidDocument = runBlocking { didMethod.createDid() }
        val didResolver =
            object : DidResolver {
                override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
            }
        val engine =
            SdJwtProofEngine(ProofEngineConfig(properties = mapOf("kms" to kms), didResolver = didResolver))
        val service =
            DefaultCredentialService(engines = mapOf(ProofSuiteId.SD_JWT_VC to engine), didResolver = didResolver)

        suspend fun issue(): VerifiableCredential =
            engine.issue(
                IssuanceRequest(
                    format = ProofSuiteId.SD_JWT_VC,
                    issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                    issuerKeyId = issuerDocument.verificationMethod.first().id,
                    credentialSubject =
                        CredentialSubject(
                            id = Iri(holderDocument.id.value),
                            claims = mapOf("name" to JsonPrimitive("Alice")),
                        ),
                    type = listOf(CredentialType.fromString("VerifiableCredential")),
                    validUntil = Clock.System.now().plus(2.hours),
                ),
            )

        suspend fun present(
            credential: VerifiableCredential,
            challenge: String,
        ) = engine.createPresentation(
            listOf(credential),
            PresentationRequest(
                proofOptions =
                    proofOptions {
                        this.challenge = challenge
                        domain = "verifier.example.com"
                        verificationMethod =
                            holderDocument.verificationMethod
                                .first()
                                .id.value
                    },
            ),
        )

        /** Re-signs the issuer JWT of [credential] with the issuer's real key under a different header. */
        suspend fun withIssuerHeader(
            credential: VerifiableCredential,
            transformHeaderJson: (String) -> String,
        ): VerifiableCredential {
            val proof = credential.proof as CredentialProof.SdJwtVcProof
            val parts = proof.sdJwtVc.split("~")
            val jwt = SignedJWT.parse(parts[0])
            val b64 = Base64.getUrlEncoder().withoutPadding()
            val header = b64.encodeToString(transformHeaderJson(jwt.header.toString()).toByteArray(Charsets.UTF_8))
            val payload = jwt.payload.toBase64URL().toString()
            val keyId =
                issuerDocument.verificationMethod
                    .first()
                    .id.value
                    .substringAfter("#")
            val signature = (kms.sign(KeyId(keyId), "$header.$payload".toByteArray(Charsets.UTF_8)) as SignResult.Success).signature
            val newJwt = "$header.$payload.${b64.encodeToString(signature)}"
            return credential.copy(proof = proof.copy(sdJwtVc = (listOf(newJwt) + parts.drop(1)).joinToString("~")))
        }
    }

    private fun typeOf(credential: VerifiableCredential): String? =
        SignedJWT
            .parse((credential.proof as CredentialProof.SdJwtVcProof).sdJwtVc.substringBefore("~"))
            .header.type
            ?.toString()

    // ---------------------------------------------------------------- typ

    @Test
    fun `issuance stamps the dc+sd-jwt type and it verifies`() =
        runBlocking<Unit> {
            val rig = Rig()
            val credential = rig.issue()
            assertEquals("dc+sd-jwt", typeOf(credential))
            assertTrue(rig.engine.verify(credential, VerificationOptions()) is VerificationResult.Valid)
        }

    @Test
    fun `the legacy vc+sd-jwt type is accepted`() =
        runBlocking<Unit> {
            val rig = Rig()
            val credential = rig.withIssuerHeader(rig.issue()) { it.replace("dc+sd-jwt", "vc+sd-jwt") }
            assertEquals("vc+sd-jwt", typeOf(credential))
            assertTrue(rig.engine.verify(credential, VerificationOptions()) is VerificationResult.Valid)
        }

    @Test
    fun `a validly signed issuer jwt with a foreign typ is rejected`() =
        runBlocking<Unit> {
            val rig = Rig()
            val credential = rig.withIssuerHeader(rig.issue()) { it.replace("dc+sd-jwt", "JWT") }
            val result = rig.engine.verify(credential, VerificationOptions())
            assertTrue(result is VerificationResult.Invalid.InvalidProof, "got $result")
            assertTrue(result.reason.contains("typ"), result.reason)
        }

    @Test
    fun `a validly signed issuer jwt without typ is rejected by default`() =
        runBlocking<Unit> {
            val rig = Rig()
            val credential = rig.withIssuerHeader(rig.issue()) { it.replace(Regex("\"typ\":\"[^\"]*\",?"), "").replace(",}", "}") }
            assertEquals(null, typeOf(credential))
            val result = rig.engine.verify(credential, VerificationOptions())
            assertTrue(result is VerificationResult.Invalid.InvalidProof, "got $result")
            assertTrue(result.reason.contains("no 'typ'"), result.reason)
        }

    @Test
    fun `the legacy untyped token is accepted only with the explicit option`() =
        runBlocking<Unit> {
            val rig = Rig()
            val credential = rig.withIssuerHeader(rig.issue()) { it.replace(Regex("\"typ\":\"[^\"]*\",?"), "").replace(",}", "}") }
            val optIn = VerificationOptions(additionalOptions = mapOf(SdJwtProofEngine.ALLOW_LEGACY_TYP_OPTION to true))
            assertTrue(rig.engine.verify(credential, optIn) is VerificationResult.Valid)
            val notABoolean = VerificationOptions(additionalOptions = mapOf(SdJwtProofEngine.ALLOW_LEGACY_TYP_OPTION to "yes"))
            assertTrue(rig.engine.verify(credential, notABoolean) is VerificationResult.Invalid)
        }

    @Test
    fun `a foreign typ is refused even with the legacy option`() =
        runBlocking<Unit> {
            val rig = Rig()
            val credential = rig.withIssuerHeader(rig.issue()) { it.replace("dc+sd-jwt", "at+jwt") }
            val optIn = VerificationOptions(additionalOptions = mapOf(SdJwtProofEngine.ALLOW_LEGACY_TYP_OPTION to true))
            assertTrue(rig.engine.verify(credential, optIn) is VerificationResult.Invalid)
        }

    // ---------------------------------------------------------------- nonce store

    private fun options(
        store: Any?,
        challenge: String = "nonce-1",
        verifyProof: Boolean = true,
    ) = VerificationOptions(
        verifyPresentationProof = verifyProof,
        expectedChallenge = challenge,
        expectedDomain = "verifier.example.com",
        additionalOptions = if (store == null) emptyMap() else mapOf(PresentationNonceStore.OPTION_KEY to store),
    )

    @Test
    fun `without a store the same presentation verifies repeatedly`() =
        runBlocking<Unit> {
            val rig = Rig()
            val presentation = rig.present(rig.issue(), "nonce-1")
            repeat(2) {
                assertTrue(rig.service.verifyPresentation(presentation, null, options(null)) is VerificationResult.Valid)
            }
        }

    @Test
    fun `with a store a presentation verifies once and its replay is rejected`() =
        runBlocking<Unit> {
            val rig = Rig()
            val store = InMemoryPresentationNonceStore()
            val presentation = rig.present(rig.issue(), "nonce-1")
            assertTrue(rig.service.verifyPresentation(presentation, null, options(store)) is VerificationResult.Valid)
            val replay = rig.service.verifyPresentation(presentation, null, options(store))
            assertTrue(replay is VerificationResult.Invalid.InvalidProof, "got $replay")
            assertTrue(replay.reason.contains("already been used"), replay.reason)
        }

    @Test
    fun `a presentation rejected for another reason does not consume the nonce`() =
        runBlocking<Unit> {
            val rig = Rig()
            val store = InMemoryPresentationNonceStore()
            val presentation = rig.present(rig.issue(), "nonce-1")
            val wrongDomain = options(store).copy(expectedDomain = "attacker.example")
            assertTrue(rig.service.verifyPresentation(presentation, null, wrongDomain) is VerificationResult.Invalid)
            assertTrue(rig.service.verifyPresentation(presentation, null, options(store)) is VerificationResult.Valid)
        }

    @Test
    fun `a store requires presentation proof verification`() =
        runBlocking<Unit> {
            val rig = Rig()
            val presentation = rig.present(rig.issue(), "nonce-1")
            val result =
                rig.service.verifyPresentation(presentation, null, options(InMemoryPresentationNonceStore(), verifyProof = false))
            assertTrue(result is VerificationResult.Invalid, "got $result")
        }

    @Test
    fun `a nonce store option of the wrong type fails closed`() =
        runBlocking<Unit> {
            val rig = Rig()
            val presentation = rig.present(rig.issue(), "nonce-1")
            val result = rig.service.verifyPresentation(presentation, null, options("not-a-store"))
            assertTrue(result is VerificationResult.Invalid.InvalidProof, "got $result")
        }

    @Test
    fun `the in-memory store is single-use, expires, and refuses rather than evicts when full`() =
        runBlocking<Unit> {
            var now = Instant.fromEpochSeconds(1_000)
            val clock =
                object : kotlinx.datetime.Clock {
                    override fun now() = now
                }
            val store = InMemoryPresentationNonceStore(ttl = 10.minutes, capacity = 2, clock = clock)
            assertTrue(store.consume("a"))
            assertFalse(store.consume("a"))
            assertTrue(store.consume("b"))
            assertFailsWith<IllegalStateException> { store.consume("c") }
            assertFalse(store.consume("a"), "a full store must not evict a live nonce")
            now = now.plus(11.minutes)
            assertTrue(store.consume("c"))
            assertTrue(store.consume("a"), "expired nonces are forgotten")
        }
}
