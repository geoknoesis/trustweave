package org.trustweave.credential.requests

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.issue
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.results.CredentialStatusInfo
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import java.time.Duration as JavaDuration

/**
 * The convenience surface a consumer actually reaches for, which had no tests at all.
 *
 * These are the functions in the README and the KDoc examples: the short `issue(...)` overloads,
 * the type helpers, the option presets. They are small, which is exactly why nobody tested them
 * and exactly why a mistake in one is expensive — it is wrong in every caller at once, and the
 * failure surfaces as a credential nobody can verify rather than as an error here.
 */
class PublicRequestApiTest {
    private val issuerDid = Did("did:example:issuer")
    private val subjectDid = Did("did:example:subject")
    private val format = ProofSuiteId.VC_LD

    // ---------------------------------------------------------------- type helpers

    @Test
    fun `credentialTypes always includes VerifiableCredential, first`() {
        assertEquals(
            listOf("VerifiableCredential", "DegreeCredential"),
            credentialTypes("DegreeCredential").map { it.value },
        )
    }

    @Test
    fun `credentialTypes does not duplicate an explicit VerifiableCredential`() {
        assertEquals(
            listOf("VerifiableCredential", "DegreeCredential"),
            credentialTypes("VerifiableCredential", "DegreeCredential").map { it.value },
        )
    }

    @Test
    fun `credentialTypes preserves the order it was given`() {
        assertEquals(
            listOf("VerifiableCredential", "A", "B", "C"),
            credentialTypes("A", "B", "C").map { it.value },
        )
    }

    @Test
    fun `credentialTypes with no arguments still yields VerifiableCredential`() {
        assertEquals(listOf("VerifiableCredential"), credentialTypes().map { it.value })
    }

    // ---------------------------------------------------------------- issuanceRequest overloads

    @Test
    fun `the single-type overload adds VerifiableCredential`() {
        val request = issuanceRequest(format, issuer(), subject(), type = "DegreeCredential")
        assertEquals(listOf("VerifiableCredential", "DegreeCredential"), request.type.map { it.value })
    }

    @Test
    fun `the single-type overload does not duplicate VerifiableCredential`() {
        val request = issuanceRequest(format, issuer(), subject(), type = "VerifiableCredential")
        assertEquals(listOf("VerifiableCredential"), request.type.map { it.value })
    }

    @Test
    fun `the multi-type overload adds VerifiableCredential when absent and keeps it when present`() {
        assertEquals(
            listOf("VerifiableCredential", "A", "B"),
            issuanceRequest(format, issuer(), subject(), types = listOf("A", "B")).type.map { it.value },
        )
        assertEquals(
            listOf("VerifiableCredential", "A"),
            issuanceRequest(format, issuer(), subject(), types = listOf("VerifiableCredential", "A")).type.map { it.value },
        )
    }

    @Test
    fun `optional fields are carried through, and absent ones stay null`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val until = Instant.parse("2026-06-01T00:00:00Z")
        val full =
            issuanceRequest(
                format = format,
                issuer = issuer(),
                credentialSubject = subject(),
                type = "DegreeCredential",
                issuedAt = issued,
                validUntil = until,
            )
        assertEquals(issued, full.issuedAt)
        assertEquals(until, full.validUntil)
        assertEquals(format, full.format)

        val bare = issuanceRequest(format, issuer(), subject(), type = "DegreeCredential")
        assertNull(bare.validUntil)
        assertNull(bare.issuerKeyId)
    }

    @Test
    fun `withExpiration measures from issuedAt rather than from now`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val request =
            issuanceRequest(
                format = format,
                issuer = issuer(),
                credentialSubject = subject(),
                type = "DegreeCredential",
                issuedAt = issued,
            ).withExpiration(JavaDuration.ofDays(30))
        assertEquals(Instant.parse("2026-01-31T00:00:00Z"), request.validUntil)
        assertEquals(issued, request.issuedAt, "the issue time itself must not move")
    }

    @Test
    fun `withExpiration replaces an expiry that was already set`() {
        val issued = Instant.parse("2026-01-01T00:00:00Z")
        val request =
            issuanceRequest(
                format = format,
                issuer = issuer(),
                credentialSubject = subject(),
                type = "DegreeCredential",
                issuedAt = issued,
                validUntil = Instant.parse("2027-01-01T00:00:00Z"),
            ).withExpiration(JavaDuration.ofDays(1))
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), request.validUntil)
    }

    // ---------------------------------------------------------------- option presets

    @Test
    fun `strict checks everything and tolerates the least clock skew`() {
        val strict = VerificationOptionPresets.strict()
        assertTrue(strict.checkRevocation && strict.checkExpiration && strict.checkNotBefore)
        assertTrue(strict.resolveIssuerDid && strict.validateSchema)
        assertTrue(
            strict.clockSkewTolerance < VerificationOptionPresets.standard().clockSkewTolerance,
            "strict must not be more forgiving about time than standard",
        )
    }

    @Test
    fun `loose checks nothing beyond the proof itself`() {
        val loose = VerificationOptionPresets.loose()
        assertTrue(!loose.checkRevocation && !loose.checkExpiration && !loose.checkNotBefore)
        assertTrue(!loose.resolveIssuerDid && !loose.validateSchema)
    }

    @Test
    fun `standard sits between the two, and skips only schema validation`() {
        val standard = VerificationOptionPresets.standard()
        assertTrue(standard.checkRevocation && standard.checkExpiration && standard.checkNotBefore)
        assertTrue(standard.resolveIssuerDid)
        assertTrue(!standard.validateSchema, "schema validation is the expensive one standard drops")
        assertTrue(standard.clockSkewTolerance < VerificationOptionPresets.loose().clockSkewTolerance)
    }

    @Test
    fun `the three presets are genuinely different configurations`() {
        // Otherwise choosing between them would be decoration.
        val all = listOf(VerificationOptionPresets.strict(), VerificationOptionPresets.standard(), VerificationOptionPresets.loose())
        assertEquals(3, all.toSet().size, "presets must differ: $all")
    }

    // ---------------------------------------------------------------- the short issue overloads

    @Test
    fun `the short issue overload builds the request the long form would`() =
        runBlocking<Unit> {
            val service = CapturingCredentialService()
            service.issue(
                format = format,
                issuerDid = issuerDid,
                subjectDid = subjectDid,
                type = "DegreeCredential",
                claims = mapOf("name" to "Ada", "age" to 36, "active" to true),
            )
            val request = requireNotNull(service.captured)
            assertEquals(listOf("VerifiableCredential", "DegreeCredential"), request.type.map { it.value })
            assertEquals(issuerDid.value, request.issuer.id.value)
            assertEquals(subjectDid.value, request.credentialSubject.id?.value)
            assertNull(request.validUntil, "no expiry was asked for")
        }

    @Test
    fun `claims keep their JSON types through the convenience overload`() =
        runBlocking<Unit> {
            val service = CapturingCredentialService()
            service.issue(
                format = format,
                issuerDid = issuerDid,
                subjectDid = subjectDid,
                type = "T",
                claims = mapOf("s" to "text", "n" to 7, "b" to false, "j" to JsonPrimitive("raw")),
            )
            val claims = requireNotNull(service.captured).credentialSubject.claims
            assertEquals("text", claims["s"]?.jsonPrimitive?.content)
            assertEquals("7", claims["n"]?.jsonPrimitive?.content)
            assertEquals("false", claims["b"]?.jsonPrimitive?.content)
            assertEquals("raw", claims["j"]?.jsonPrimitive?.content)
        }

    @Test
    fun `an unrecognised claim type is coerced through toString rather than dropped`() =
        runBlocking<Unit> {
            val service = CapturingCredentialService()
            service.issue(
                format = format,
                issuerDid = issuerDid,
                subjectDid = subjectDid,
                type = "T",
                claims = mapOf("when" to Instant.parse("2026-01-01T00:00:00Z")),
            )
            val claims = requireNotNull(service.captured).credentialSubject.claims
            assertEquals("2026-01-01T00:00:00Z", claims["when"]?.jsonPrimitive?.content)
        }

    @Test
    fun `expiresIn produces an expiry in the future`() =
        runBlocking<Unit> {
            val service = CapturingCredentialService()
            val before = Clock.System.now()
            service.issue(
                format = format,
                issuerDid = issuerDid,
                subjectDid = subjectDid,
                type = "T",
                expiresIn = 30.days,
            )
            val validUntil = requireNotNull(requireNotNull(service.captured).validUntil)
            assertTrue(validUntil > before, "an expiry 30 days out must be after the moment we asked")
        }

    @Test
    fun `the multi-type short overload adds VerifiableCredential too`() =
        runBlocking<Unit> {
            val service = CapturingCredentialService()
            service.issue(
                format = format,
                issuerDid = issuerDid,
                subjectDid = subjectDid,
                types = listOf("A", "B"),
            )
            assertEquals(
                listOf("VerifiableCredential", "A", "B"),
                requireNotNull(service.captured).type.map { it.value },
            )
        }

    @Test
    fun `the short overload returns whatever the service returned`() =
        runBlocking<Unit> {
            val service = CapturingCredentialService()
            val result =
                service.issue(format = format, issuerDid = issuerDid, subjectDid = subjectDid, type = "T")
            assertSame(service.answer, result, "the convenience wrapper must not reinterpret the result")
        }

    // ---------------------------------------------------------------- fixtures

    private fun issuer() = Issuer.fromDid(issuerDid)

    private fun subject() = CredentialSubject(id = Iri(subjectDid.value), claims = emptyMap())

    /** Records the request the convenience overload built, and answers with a fixed result. */
    private class CapturingCredentialService : CredentialService {
        var captured: IssuanceRequest? = null
        val answer: IssuanceResult =
            IssuanceResult.Failure.InvalidRequest(field = "test", reason = "captured, not issued")

        override suspend fun issue(request: IssuanceRequest): IssuanceResult {
            captured = request
            return answer
        }

        private fun unreachable(): Nothing = throw UnsupportedOperationException("not part of this test")

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = unreachable()

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: PresentationRequest,
        ): VerifiablePresentation = unreachable()

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = unreachable()

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: kotlin.time.Duration,
        ): CredentialStatusInfo = unreachable()

        override fun supports(format: ProofSuiteId): Boolean = unreachable()

        override fun supportedFormats(): List<ProofSuiteId> = unreachable()

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: ProofEngineCapabilities.() -> Boolean,
        ): Boolean = unreachable()
    }
}
