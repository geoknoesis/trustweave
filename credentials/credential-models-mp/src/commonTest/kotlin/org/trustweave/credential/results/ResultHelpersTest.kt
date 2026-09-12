package org.trustweave.credential.results

import kotlinx.datetime.Instant
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The fluent helpers over [IssuanceResult] and [VerificationResult] are how callers branch on
 * whether a credential was produced or accepted, so a helper that reports a failure as a success
 * — or that throws a message naming no cause — is a correctness problem rather than a cosmetic one.
 */
class ResultHelpersTest {
    private val credential =
        VerifiableCredential(
            id = CredentialId("urn:uuid:11111111-1111-1111-1111-111111111111"),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:key:zIssuer")),
            issuanceDate = Instant.parse("2026-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject(id = Iri("did:key:zSubject")),
        )

    private val success = IssuanceResult.Success(credential)

    private fun failure(): IssuanceResult.Failure = IssuanceResult.Failure.InvalidRequest("issuer", "must be a DID")

    // -- IssuanceResult --------------------------------------------------------------------

    @Test
    fun `onSuccess runs only for a success and returns the same result for chaining`() {
        var seen: VerifiableCredential? = null
        assertSame(success, success.onSuccess<Unit> { seen = it })
        assertEquals(credential, seen)

        seen = null
        val failed = failure()
        assertSame(failed, failed.onSuccess<Unit> { seen = it })
        assertNull(seen)
    }

    @Test
    fun `onFailure runs only for a failure and returns the same result for chaining`() {
        var seen: IssuanceResult.Failure? = null
        val failed = failure()
        assertSame(failed, failed.onFailure<Unit> { seen = it })
        assertSame(failed, seen)

        seen = null
        assertSame(success, success.onFailure<Unit> { seen = it })
        assertNull(seen)
    }

    @Test
    fun `getOrThrow returns the credential on success`() {
        assertEquals(credential, success.getOrThrow())
    }

    @Test
    fun `getOrThrow names the cause for every failure shape`() {
        val cases =
            listOf<Pair<IssuanceResult.Failure, String>>(
                IssuanceResult.Failure.UnsupportedFormat(ProofSuiteId.VC_LD) to "No credential formats are available",
                IssuanceResult.Failure.UnsupportedFormat(
                    ProofSuiteId.VC_LD,
                    supportedFormats = listOf(ProofSuiteId.VC_JWT),
                ) to "supported formats",
                IssuanceResult.Failure.AdapterNotReady(
                    format = ProofSuiteId.VC_LD,
                    reason = "engine missing",
                ) to "engine missing",
                IssuanceResult.Failure.InvalidRequest(field = "issuer", reason = "must be a DID") to "issuer",
                IssuanceResult.Failure.AdapterError(
                    format = ProofSuiteId.VC_LD,
                    reason = "signing refused",
                ) to "signing refused",
                IssuanceResult.Failure.MultipleFailures(
                    failures = listOf(IssuanceResult.Failure.InvalidRequest(field = "a", reason = "b")),
                    errors = listOf("first", "second"),
                ) to "first; second",
            )
        for ((result, expected) in cases) {
            val thrown = assertFailsWith<IllegalStateException>(result::class.simpleName) { result.getOrThrow() }
            assertTrue(expected in (thrown.message ?: ""), "${result::class.simpleName}: ${thrown.message}")
        }
    }

    @Test
    fun `an unsupported format with no alternatives points at configuration rather than a bare failure`() {
        val thrown =
            assertFailsWith<IllegalStateException> {
                IssuanceResult.Failure.UnsupportedFormat(ProofSuiteId.VC_LD).getOrThrow()
            }
        assertTrue("TrustWeave.build" in (thrown.message ?: ""), thrown.message ?: "")
    }

    @Test
    fun `getOrNull distinguishes success from failure without throwing`() {
        assertEquals(credential, success.getOrNull())
        assertNull(failure().getOrNull())
    }

    @Test
    fun `map transforms a success and leaves a failure untouched`() {
        val renamed = credential.copy(name = "mapped")
        val mapped = success.map<VerifiableCredential> { renamed }
        assertEquals(IssuanceResult.Success(renamed), mapped)

        val failed = failure()
        assertSame(failed, failed.map<VerifiableCredential> { renamed })
    }

    @Test
    fun `fold picks exactly one branch`() {
        assertEquals("ok", success.fold(onFailure = { "failed" }, onSuccess = { "ok" }))
        assertEquals("failed", failure().fold(onFailure = { "failed" }, onSuccess = { "ok" }))
    }

    // -- VerificationResult ----------------------------------------------------------------

    private val valid =
        VerificationResult.Valid(
            credential = credential,
            issuerIri = Iri("did:key:zIssuer"),
            subjectIri = Iri("did:key:zSubject"),
            issuedAt = Instant.parse("2026-01-01T00:00:00Z"),
            expiresAt = null,
        )

    private fun invalid(): VerificationResult.Invalid =
        VerificationResult.Invalid.InvalidProof(credential = credential, reason = "signature mismatch")

    @Test
    fun `ifValid and ifInvalid each fire for exactly one side`() {
        assertEquals("yes", valid.ifValid { "yes" })
        assertNull(valid.ifInvalid { "no" })

        val rejected = invalid()
        assertNull(rejected.ifValid { "yes" })
        assertEquals("no", rejected.ifInvalid { "no" })
    }

    @Test
    fun `mapCredential rewrites the credential on both sides`() {
        val renamed = credential.copy(name = "mapped")
        val mappedValid = valid.mapCredential { renamed }
        assertEquals(renamed, (mappedValid as VerificationResult.Valid).credential)

        val mappedInvalid = invalid().mapCredential { renamed }
        assertTrue(mappedInvalid is VerificationResult.Invalid)
    }

    @Test
    fun `verification fold picks exactly one branch`() {
        assertEquals("valid", valid.fold(onInvalid = { "invalid" }, onValid = { "valid" }))
        assertEquals("invalid", invalid().fold(onInvalid = { "invalid" }, onValid = { "valid" }))
    }

    @Test
    fun `recover only rewrites the rejections its predicate selects`() {
        val recovered = invalid().recover(predicate = { true }, transform = { valid })
        assertTrue(recovered is VerificationResult.Valid)

        // A predicate that declines leaves the rejection exactly as it was.
        val rejected = invalid()
        assertSame(rejected, rejected.recover(predicate = { false }, transform = { valid }))

        // An acceptance is never passed to the predicate at all.
        assertSame(valid, valid.recover(predicate = { error("must not be consulted") }, transform = { valid }))
    }

    @Test
    fun `a valid result exposes issuer and subject as DIDs only when they are DIDs`() {
        assertEquals("did:key:zIssuer", valid.issuerDid?.value)
        assertEquals("did:key:zSubject", valid.subjectDid?.value)

        val httpIssuer = valid.copy(issuerIri = Iri("https://issuer.example"), subjectIri = null)
        assertNull(httpIssuer.issuerDid)
        assertNull(httpIssuer.subjectDid)
    }
}
