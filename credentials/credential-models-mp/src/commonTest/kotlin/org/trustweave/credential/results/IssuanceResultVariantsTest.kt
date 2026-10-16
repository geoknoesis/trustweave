package org.trustweave.credential.results

import kotlinx.serialization.json.JsonPrimitive
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class IssuanceResultVariantsTest {
    private val credential =
        VerifiableCredential(
            id = CredentialId("urn:uuid:44444444-4444-4444-4444-444444444444"),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:key:zIssuer")),
            issuanceDate = Instant.parse("2026-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject(id = Iri("did:key:zSubject")),
        )

    @Test
    fun `a success reports its credential and warnings and no errors`() {
        val result = IssuanceResult.Success(credential, warnings = listOf("deprecated context"))
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(credential, result.credentialOrNull)
        assertEquals(emptyList(), result.allErrors)
        assertEquals(listOf("deprecated context"), result.allWarnings)
    }

    @Test
    fun `every failure shape is a failure with no credential and an explanatory error`() {
        val failures: List<IssuanceResult.Failure> =
            listOf(
                IssuanceResult.Failure.UnsupportedFormat(ProofSuiteId.VC_LD),
                IssuanceResult.Failure.UnsupportedFormat(ProofSuiteId.VC_LD, listOf(ProofSuiteId.VC_JWT, ProofSuiteId.SD_JWT_VC)),
                IssuanceResult.Failure.AdapterNotReady(ProofSuiteId.VC_JWT),
                IssuanceResult.Failure.AdapterNotReady(ProofSuiteId.VC_JWT, "no key"),
                IssuanceResult.Failure.InvalidRequest("subject", "required"),
                IssuanceResult.Failure.AdapterError(ProofSuiteId.VC_LD, "boom", IllegalStateException("cause")),
            )
        for (failure in failures) {
            val name = failure::class.simpleName
            assertTrue(failure.isFailure, name)
            assertFalse(failure.isSuccess, name)
            assertNull(failure.credentialOrNull, name)
            assertNull(failure.getOrNull(), name)
            assertEquals(failure.errors, failure.allErrors, name)
            assertTrue(failure.errors.size == 1, name)
            assertEquals(emptyList(), failure.allWarnings, name)
        }
    }

    @Test
    fun `convenience constructors build precise messages`() {
        assertEquals(
            "Proof suite 'vc-ld' is not supported. No proof suites available.",
            IssuanceResult.Failure
                .UnsupportedFormat(ProofSuiteId.VC_LD)
                .errors
                .single(),
        )
        assertEquals(
            "Proof suite 'vc-ld' is not supported. Supported proof suites: vc-jwt, sd-jwt-vc",
            IssuanceResult.Failure
                .UnsupportedFormat(
                    ProofSuiteId.VC_LD,
                    listOf(ProofSuiteId.VC_JWT, ProofSuiteId.SD_JWT_VC),
                ).errors
                .single(),
        )
        assertEquals(
            "Adapter for format 'vc-jwt' is not ready",
            IssuanceResult.Failure
                .AdapterNotReady(ProofSuiteId.VC_JWT)
                .errors
                .single(),
        )
        assertEquals(
            "Adapter for format 'vc-jwt' is not ready: no key",
            IssuanceResult.Failure
                .AdapterNotReady(ProofSuiteId.VC_JWT, "no key")
                .errors
                .single(),
        )
        assertEquals(
            "Invalid request: field 'subject' - required",
            IssuanceResult.Failure
                .InvalidRequest("subject", "required")
                .errors
                .single(),
        )
        val cause = IllegalStateException("cause")
        val adapter = IssuanceResult.Failure.AdapterError(ProofSuiteId.VC_LD, "boom", cause)
        assertEquals("Issuance failed for format 'vc-ld': boom", adapter.errors.single())
        assertSame(cause, adapter.cause)
        assertNull(adapter.formatSpecificError)
    }

    @Test
    fun `adapter errors may carry format specific detail`() {
        val detail = JsonPrimitive("kid not found")
        val error =
            IssuanceResult.Failure.AdapterError(
                format = ProofSuiteId.VC_JWT,
                reason = "sign",
                errors = listOf("sign failed"),
                formatSpecificError = detail,
            )
        assertEquals(detail, error.formatSpecificError)
        assertEquals(listOf("sign failed"), error.allErrors)
    }

    @Test
    fun `multiple failures combine nested errors and warnings without duplicating`() {
        val a = IssuanceResult.Failure.InvalidRequest("a", "bad")
        val b = IssuanceResult.Failure.AdapterNotReady(ProofSuiteId.VC_LD)
        val combined = IssuanceResult.Failure.MultipleFailures(listOf(a, b))
        assertEquals(a.errors + b.errors, combined.errors)
        assertEquals(a.errors + b.errors, combined.allErrors)

        val withExtra = IssuanceResult.Failure.MultipleFailures(listOf(a), errors = a.errors + "extra", warnings = listOf("w"))
        assertEquals(a.errors + "extra", withExtra.allErrors)
        assertEquals(listOf("w"), withExtra.allWarnings)
    }

    @Test
    fun `multiple failures need at least one failure`() {
        assertFailsWith<IllegalArgumentException> { IssuanceResult.Failure.MultipleFailures(emptyList()) }
    }

    @Test
    fun `fold map and getOrThrow agree on success`() {
        val success = IssuanceResult.Success(credential)
        assertEquals("ok", success.fold(onFailure = { "failed" }, onSuccess = { "ok" }))
        val mapped = success.map<Unit> { it.copy(name = "renamed") }
        assertEquals("renamed", (mapped as IssuanceResult.Success).credential.name)
        assertEquals(credential, success.getOrThrow())
    }
}
