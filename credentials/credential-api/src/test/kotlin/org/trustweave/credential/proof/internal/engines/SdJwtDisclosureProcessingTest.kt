package org.trustweave.credential.proof.internal.engines

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.security.MessageDigest
import java.util.Base64

/**
 * SD-JWT disclosure processing (SD-JWT §7.1) and status-check failure policy for
 * [SdJwtProofEngine]. Issuer JWTs are hand-built but genuinely signed by the issuer key so each
 * test isolates exactly one processing rule.
 */
class SdJwtDisclosureProcessingTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)
    private val issuer: DidDocument = runBlocking { didMethod.createDid() }
    private val holder: DidDocument = runBlocking { didMethod.createDid() }
    private val resolver =
        object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
        }
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    private fun engine(checker: CredentialStatusChecker? = null) =
        SdJwtProofEngine(
            ProofEngineConfig(
                properties =
                    buildMap {
                        put("kms", kms)
                        checker?.let { put("statusChecker", it) }
                    },
                didResolver = resolver,
            ),
        )

    private fun disclosure(vararg parts: JsonElement): String =
        b64.encodeToString(JsonArray(listOf(JsonPrimitive("salt-${parts.hashCode()}")) + parts).toString().toByteArray())

    private fun named(
        name: String,
        value: JsonElement,
    ) = disclosure(JsonPrimitive(name), value)

    private fun digest(disc: String): String = b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(disc.toByteArray()))

    /** Builds a credential whose issuer-signed `vc.credentialSubject` is [subject]. */
    private suspend fun craft(
        subject: JsonObject,
        disclosures: List<String>,
        envelopeClaims: Map<String, JsonElement>,
        sdAlg: String? = "sha-256",
    ): VerifiableCredential {
        val vmId =
            issuer.verificationMethod
                .first()
                .id.value
        val keyId = vmId.substringAfter("#")
        val payload =
            buildJsonObject {
                put("iss", issuer.id.value)
                put("sub", holder.id.value)
                put("iat", System.currentTimeMillis() / 1000)
                sdAlg?.let { put("_sd_alg", it) }
                put("vct", "TestCredential")
                put(
                    "vc",
                    buildJsonObject {
                        put("type", buildJsonArray { add(JsonPrimitive("VerifiableCredential")) })
                        put("credentialSubject", JsonObject(mapOf("id" to JsonPrimitive(holder.id.value)) + subject))
                    },
                )
            }
        val header = b64.encodeToString("""{"typ":"dc+sd-jwt","alg":"EdDSA","kid":"$keyId"}""".toByteArray())
        val body = b64.encodeToString(payload.toString().toByteArray())
        val sig = (kms.sign(KeyId(keyId), "$header.$body".toByteArray()) as SignResult.Success).signature
        return VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri(issuer.id.value)),
            issuanceDate =
                kotlinx.datetime.Clock.System
                    .now(),
            credentialSubject = CredentialSubject(id = Iri(holder.id.value), claims = envelopeClaims),
            proof = CredentialProof.SdJwtVcProof(sdJwtVc = "$header.$body.${b64.encodeToString(sig)}", disclosures = disclosures),
        )
    }

    private suspend fun verify(credential: VerifiableCredential) = engine().verify(credential, VerificationOptions())

    private fun sdOf(vararg discs: String) = "_sd" to JsonArray(discs.map { JsonPrimitive(digest(it)) })

    @Test
    fun `flat disclosures verify (baseline)`() =
        runBlocking<Unit> {
            val name = named("name", JsonPrimitive("Alice"))
            val cred = craft(JsonObject(mapOf(sdOf(name))), listOf(name), mapOf("name" to JsonPrimitive("Alice")))
            verify(cred).shouldBeInstanceOf<VerificationResult.Valid>()
        }

    @Test
    fun `unknown _sd_alg is rejected`() =
        runBlocking<Unit> {
            val name = named("name", JsonPrimitive("Alice"))
            val cred = craft(JsonObject(mapOf(sdOf(name))), listOf(name), mapOf("name" to JsonPrimitive("Alice")), sdAlg = "sha-512")
            val result = verify(cred)
            result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
            result.reason shouldContain "_sd_alg"
        }

    @Test
    fun `absent _sd_alg defaults to sha-256`() =
        runBlocking<Unit> {
            val name = named("name", JsonPrimitive("Alice"))
            val cred = craft(JsonObject(mapOf(sdOf(name))), listOf(name), mapOf("name" to JsonPrimitive("Alice")), sdAlg = null)
            verify(cred).shouldBeInstanceOf<VerificationResult.Valid>()
        }

    @Test
    fun `the same disclosure presented twice is rejected`() =
        runBlocking<Unit> {
            val name = named("name", JsonPrimitive("Alice"))
            val cred = craft(JsonObject(mapOf(sdOf(name))), listOf(name, name), mapOf("name" to JsonPrimitive("Alice")))
            val result = verify(cred)
            result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
            result.reason shouldContain "Duplicate disclosure"
        }

    @Test
    fun `two disclosures for the same claim name are rejected`() =
        runBlocking<Unit> {
            val a = named("role", JsonPrimitive("user"))
            val b = named("role", JsonPrimitive("admin"))
            val cred = craft(JsonObject(mapOf(sdOf(a, b))), listOf(a, b), mapOf("role" to JsonPrimitive("admin")))
            val result = verify(cred)
            result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
            result.reason shouldContain "collides"
        }

    @Test
    fun `disclosure shadowing a plainly signed claim is rejected`() =
        runBlocking<Unit> {
            val shadow = named("role", JsonPrimitive("admin"))
            val subject = JsonObject(mapOf("role" to JsonPrimitive("user"), sdOf(shadow)))
            val cred = craft(subject, listOf(shadow), mapOf("role" to JsonPrimitive("admin")))
            verify(cred).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    @Test
    fun `duplicate digest in the signed payload is rejected`() =
        runBlocking<Unit> {
            val name = named("name", JsonPrimitive("Alice"))
            val sd = JsonArray(listOf(JsonPrimitive(digest(name)), JsonPrimitive(digest(name))))
            val cred = craft(JsonObject(mapOf("_sd" to sd)), listOf(name), mapOf("name" to JsonPrimitive("Alice")))
            verify(cred).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    @Test
    fun `disclosure not referenced anywhere is rejected`() =
        runBlocking<Unit> {
            val name = named("name", JsonPrimitive("Alice"))
            val stray = named("admin", JsonPrimitive(true))
            val cred = craft(JsonObject(mapOf(sdOf(name))), listOf(name, stray), mapOf("name" to JsonPrimitive("Alice")))
            verify(cred).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    @Test
    fun `nested _sd inside a disclosed object is resolved`() =
        runBlocking<Unit> {
            val street = named("street", JsonPrimitive("Main St"))
            val address = named("address", JsonObject(mapOf("country" to JsonPrimitive("DE"), sdOf(street))))
            val expectedAddress = JsonObject(mapOf("country" to JsonPrimitive("DE"), "street" to JsonPrimitive("Main St")))
            val cred = craft(JsonObject(mapOf(sdOf(address))), listOf(address, street), mapOf("address" to expectedAddress))
            verify(cred).shouldBeInstanceOf<VerificationResult.Valid>()

            // Withholding the nested disclosure removes the claim; an envelope that still shows it is rejected.
            val withheld = craft(JsonObject(mapOf(sdOf(address))), listOf(address), mapOf("address" to expectedAddress))
            verify(withheld).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    @Test
    fun `array element digests are resolved and undisclosed elements removed`() =
        runBlocking<Unit> {
            val de = disclosure(JsonPrimitive("DE"))
            val fr = disclosure(JsonPrimitive("FR"))
            val arr = JsonArray(listOf(de, fr).map { JsonObject(mapOf("..." to JsonPrimitive(digest(it)))) })
            val subject = JsonObject(mapOf("nationalities" to arr))

            val onlyDe = craft(subject, listOf(de), mapOf("nationalities" to JsonArray(listOf(JsonPrimitive("DE")))))
            verify(onlyDe).shouldBeInstanceOf<VerificationResult.Valid>()

            val forgedFr =
                craft(subject, listOf(de), mapOf("nationalities" to JsonArray(listOf(JsonPrimitive("DE"), JsonPrimitive("FR")))))
            verify(forgedFr).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    @Test
    fun `array-element disclosure referenced from an object _sd is rejected`() =
        runBlocking<Unit> {
            val bare = disclosure(JsonPrimitive("x"))
            val cred = craft(JsonObject(mapOf(sdOf(bare))), listOf(bare), emptyMap())
            verify(cred).shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        }

    // ---- status-check failure policy (finding: CheckFailed was silently ignored) -----------

    private suspend fun issuedWithStatus(engine: SdJwtProofEngine): VerifiableCredential =
        engine.issue(
            IssuanceRequest(
                format = ProofSuiteId.SD_JWT_VC,
                issuer = Issuer.IriIssuer(Iri(issuer.id.value)),
                issuerKeyId = issuer.verificationMethod.first().id,
                credentialSubject = CredentialSubject(id = Iri(holder.id.value), claims = mapOf("name" to JsonPrimitive("Alice"))),
                type = listOf(CredentialType.fromString("VerifiableCredential")),
                credentialStatus =
                    CredentialStatus(
                        id = StatusListId("https://status.example/1#5"),
                        type = "BitstringStatusListEntry",
                        statusPurpose = StatusPurpose.REVOCATION,
                        statusListIndex = "5",
                        statusListCredential = StatusListId("https://status.example/1"),
                    ),
            ),
        )

    private val failingChecker =
        object : CredentialStatusChecker {
            override suspend fun checkStatus(credential: VerifiableCredential) =
                CredentialStatusCheckResult.CheckFailed("status list unreachable")
        }

    @Test
    fun `status CheckFailed fails closed by default`() =
        runBlocking<Unit> {
            val engine = engine(failingChecker)
            val result = engine.verify(issuedWithStatus(engine), VerificationOptions())
            result.shouldBeInstanceOf<VerificationResult.Invalid.RevocationCheckFailed>()
        }

    @Test
    fun `status CheckFailed with FAIL_WITH_WARNING is valid with a warning`() =
        runBlocking<Unit> {
            val engine = engine(failingChecker)
            val result =
                engine.verify(
                    issuedWithStatus(engine),
                    VerificationOptions(revocationFailurePolicy = RevocationFailurePolicy.FAIL_WITH_WARNING),
                )
            result.shouldBeInstanceOf<VerificationResult.Valid>()
            result.warnings.any { it.contains("status list unreachable") } shouldBe true
        }

    @Test
    fun `status checker exception follows the policy too`() =
        runBlocking<Unit> {
            val throwing =
                object : CredentialStatusChecker {
                    override suspend fun checkStatus(credential: VerifiableCredential): CredentialStatusCheckResult = error("boom")
                }
            val engine = engine(throwing)
            engine
                .verify(issuedWithStatus(engine), VerificationOptions())
                .shouldBeInstanceOf<VerificationResult.Invalid.RevocationCheckFailed>()
            val open =
                engine.verify(issuedWithStatus(engine), VerificationOptions(revocationFailurePolicy = RevocationFailurePolicy.FAIL_OPEN))
            open.shouldBeInstanceOf<VerificationResult.Valid>()
        }
}
