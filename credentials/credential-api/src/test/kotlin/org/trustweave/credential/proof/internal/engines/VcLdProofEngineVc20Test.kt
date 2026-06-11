package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.exception.SerializationException
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.JsonLdContextLoader
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
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
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * VC Data Model version honesty tests for [VcLdProofEngine] (Finding 19).
 *
 * A request targets VC 2.0 by declaring the `https://www.w3.org/ns/credentials/v2` base
 * context via the [JsonLdDocumentBuilder.CONTEXTS_OPTION] proof option. The engine must
 * then emit the v2 context as the **first** `@context` entry and use `validFrom`/`validUntil`
 * (the v2 vocabulary does not define `issuanceDate`/`expirationDate`). Without the v2
 * context the engine keeps its VC 1.1 behaviour unchanged.
 */
class VcLdProofEngineVc20Test {

    companion object {
        private const val TEST_CONTEXT_URL = "https://trustweave.example/contexts/vc20-claims/v1"

        init {
            JsonLdContextLoader.registerContext(
                TEST_CONTEXT_URL,
                """
                {
                  "@context": {
                    "name": "https://schema.org/name"
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
        val engine = VcLdProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms),
                didResolver = object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                }
            )
        )

        fun issuanceRequest(
            contexts: List<String>,
            issuedAt: Instant = Clock.System.now(),
            validUntil: Instant? = null
        ): IssuanceRequest = IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
            issuerKeyId = issuerDocument.verificationMethod.first().id,
            credentialSubject = CredentialSubject(
                id = Iri("did:example:holder"),
                claims = mapOf("name" to JsonPrimitive("Alice"))
            ),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuedAt = issuedAt,
            validUntil = validUntil,
            proofOptions = proofOptions {
                option(JsonLdDocumentBuilder.CONTEXTS_OPTION, contexts)
            }
        )
    }

    @Test
    fun `issuing with the v2 base context emits a VC 2_0 credential that verifies`() = runBlocking {
        val rig = TestRig()
        val issuedAt = Clock.System.now()
        val validUntil = issuedAt.plus(kotlin.time.Duration.parse("P365D"))

        val credential = rig.engine.issue(
            rig.issuanceRequest(
                contexts = listOf(CredentialConstants.VcContexts.VC_2_0, TEST_CONTEXT_URL),
                issuedAt = issuedAt,
                validUntil = validUntil
            )
        )

        assertEquals(
            CredentialConstants.VcContexts.VC_2_0, credential.context.first(),
            "The VC 2.0 base context must be the first @context entry"
        )
        assertFalse(
            CredentialConstants.VcContexts.VC_1_1 in credential.context,
            "A pure VC 2.0 credential must not also carry the VC 1.1 context"
        )
        assertNull(credential.issuanceDate, "VC 2.0 does not define issuanceDate")
        assertNull(credential.expirationDate, "VC 2.0 does not define expirationDate")
        assertEquals(issuedAt, credential.validFrom, "validFrom must default to issuedAt for VC 2.0")
        assertEquals(validUntil, credential.validUntil, "validUntil must carry the requested expiry")

        val result = rig.engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Valid,
            "VC 2.0 issue -> verify round-trip must succeed, got ${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    @Test
    fun `v2 base context is hoisted to the first context position`() = runBlocking {
        val rig = TestRig()

        // Declared with the claims context first — the base context must still end up first.
        val credential = rig.engine.issue(
            rig.issuanceRequest(contexts = listOf(TEST_CONTEXT_URL, CredentialConstants.VcContexts.VC_2_0))
        )

        assertEquals(CredentialConstants.VcContexts.VC_2_0, credential.context.first())
        assertTrue(TEST_CONTEXT_URL in credential.context, "Declared claim contexts must be preserved")

        val result = rig.engine.verify(credential, VerificationOptions())
        assertTrue(result is VerificationResult.Valid)
    }

    @Test
    fun `default issuance remains VC 1_1 with issuanceDate and expirationDate`() = runBlocking {
        val rig = TestRig()
        val issuedAt = Clock.System.now()
        val validUntil = issuedAt.plus(kotlin.time.Duration.parse("P365D"))

        val credential = rig.engine.issue(
            rig.issuanceRequest(
                contexts = listOf(TEST_CONTEXT_URL),
                issuedAt = issuedAt,
                validUntil = validUntil
            )
        )

        assertEquals(
            CredentialConstants.VcContexts.VC_1_1, credential.context.first(),
            "Without a declared v2 context the credential must remain VC 1.1"
        )
        assertNotNull(credential.issuanceDate, "VC 1.1 credentials carry issuanceDate")
        assertEquals(validUntil, credential.expirationDate, "VC 1.1 expiry maps to expirationDate")
        assertNull(credential.validUntil, "VC 1.1 credentials must not carry the v2 validUntil field")

        val result = rig.engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Valid,
            "VC 1.1 issue -> verify round-trip must stay green, got ${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    // --- Dual-context credentials (both VC 1.1 and VC 2.0 base contexts) ---------------

    @Test
    fun `dual-context credentials are treated as VC 1_1 for field emission`() {
        // Version trichotomy: only a *pure* v2 context list selects the VC 2.0 field
        // mapping; declaring both base contexts keeps the credential on the 1.1 path
        // (issuanceDate/expirationDate), mirroring CredentialValidation's fallback rules.
        assertFalse(
            JsonLdDocumentBuilder.isPureVc2(
                listOf(
                    CredentialConstants.VcContexts.VC_1_1,
                    CredentialConstants.VcContexts.VC_2_0
                )
            ),
            "A credential declaring BOTH base contexts must use the VC 1.1 field mapping"
        )
    }

    @Test
    fun `dual-context issuance fails closed - the official W3C v1 and v2 contexts are incompatible`(): Unit =
        runBlocking {
            // The official W3C context documents both mark their terms @protected, and the
            // two define different (type-scoped) term definitions for VerifiableCredential.
            // A conformant JSON-LD 1.1 processor therefore rejects a document that declares
            // both with PROTECTED_TERM_REDEFINITION — there is no interoperable canonical
            // form for a dual-context credential. Canonicalization must fail closed (no
            // plain-JSON fallback), so issuance throws instead of signing bytes that no
            // conformant verifier could ever reproduce.
            val rig = TestRig()
            val exception = assertFailsWith<SerializationException> {
                rig.engine.issue(
                    rig.issuanceRequest(
                        contexts = listOf(
                            CredentialConstants.VcContexts.VC_1_1,
                            CredentialConstants.VcContexts.VC_2_0,
                            TEST_CONTEXT_URL
                        )
                    )
                )
            }
            assertTrue(
                exception.message?.contains("canonicalization", ignoreCase = true) == true,
                "Failure must be a fail-closed canonicalization error, got: ${exception.message}"
            )
        }

    @Test
    fun `tampering with validUntil on a VC 2_0 credential fails verification`() = runBlocking {
        val rig = TestRig()
        val issuedAt = Clock.System.now()
        val validUntil = issuedAt.plus(kotlin.time.Duration.parse("P30D"))

        val credential = rig.engine.issue(
            rig.issuanceRequest(
                contexts = listOf(CredentialConstants.VcContexts.VC_2_0, TEST_CONTEXT_URL),
                issuedAt = issuedAt,
                validUntil = validUntil
            )
        )

        val tampered = credential.copy(
            validUntil = validUntil.plus(kotlin.time.Duration.parse("P3650D"))
        )

        val result = rig.engine.verify(tampered, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "validUntil must be covered by the signature — extending it must invalidate the proof"
        )
    }
}
