package org.trustweave.credential.results

import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Every rejection shape of [VerificationResult] reports its errors and warnings and is classified correctly. */
class VerificationResultVariantsTest {
    private val credential =
        VerifiableCredential(
            id = CredentialId("urn:uuid:22222222-2222-2222-2222-222222222222"),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:key:zIssuer")),
            issuanceDate = Instant.parse("2026-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject(id = Iri("did:key:zSubject")),
        )
    private val at = Instant.parse("2026-06-01T00:00:00Z")

    private fun valid(
        issuer: String = "did:key:zIssuer",
        subject: String? = "did:key:zSubject",
        warnings: List<String> = emptyList(),
    ) = VerificationResult.Valid(
        credential = credential,
        issuerIri = Iri(issuer),
        subjectIri = subject?.let { Iri(it) },
        issuedAt = at,
        expiresAt = null,
        warnings = warnings,
        formatMetadata = mapOf("alg" to JsonPrimitive("EdDSA")),
    )

    private val invalids: List<VerificationResult.Invalid> =
        listOf(
            VerificationResult.Invalid.InvalidProof(credential, "bad signature", errors = listOf("proof")),
            VerificationResult.Invalid.Expired(credential, at, errors = listOf("expired")),
            VerificationResult.Invalid.NotYetValid(credential, at, errors = listOf("early")),
            VerificationResult.Invalid.Revoked(credential, at, errors = listOf("revoked"), revocationReason = "key loss"),
            VerificationResult.Invalid.Suspended(credential, errors = listOf("suspended")),
            VerificationResult.Invalid.InvalidIssuer(credential, Iri("did:key:zIssuer"), "unresolvable", errors = listOf("issuer")),
            VerificationResult.Invalid.UntrustedIssuer(credential, Did("did:key:zIssuer")),
            VerificationResult.Invalid.UnsupportedFormat(credential, ProofSuiteId.VC_LD, errors = listOf("format")),
            VerificationResult.Invalid.AdapterNotReady(credential, "engine down"),
            VerificationResult.Invalid.RevocationCheckFailed(credential, "timeout", errors = listOf("status")),
            VerificationResult.Invalid.SchemaValidationFailed(credential, "schema-1", listOf("name missing"), errors = listOf("schema")),
        )

    @Test
    fun `a valid result has no errors and keeps its warnings`() {
        val result = valid(warnings = listOf("clock skew"))
        assertTrue(result.isValid)
        assertEquals(emptyList(), result.allErrors)
        assertEquals(listOf("clock skew"), result.allWarnings)
        assertEquals(credential, result.credential)
        assertEquals(credential, result.validCredential)
        assertEquals(JsonPrimitive("EdDSA"), result.formatMetadata["alg"])
    }

    @Test
    fun `issuer and subject convert to DIDs only for DID IRIs`() {
        assertEquals("did:key:zIssuer", valid().issuerDid?.value)
        assertEquals("did:key:zSubject", valid().subjectDid?.value)
        assertNull(valid(issuer = "https://issuer.example").issuerDid)
        assertNull(valid(subject = "urn:uuid:subject").subjectDid)
        assertNull(valid(subject = null).subjectDid)
    }

    @Test
    fun `every rejection is invalid, carries the credential and reports its own errors`() {
        for (result in invalids) {
            val name = result::class.simpleName
            assertFalse(result.isValid, name)
            assertEquals(credential, result.credential, name)
            assertEquals(emptyList(), result.allWarnings, name)
            assertNull(result.validCredential, name)
            assertTrue(result.allErrors.isNotEmpty(), "$name should explain itself")
        }
    }

    @Test
    fun `schema failures list both the generic and the schema-specific errors`() {
        val failure = invalids.filterIsInstance<VerificationResult.Invalid.SchemaValidationFailed>().single()
        assertEquals(listOf("schema", "name missing"), failure.allErrors)
    }

    @Test
    fun `the convenience constructors build their own messages`() {
        val untrusted = VerificationResult.Invalid.UntrustedIssuer(credential, Did("did:web:evil.example"))
        assertEquals(listOf("Issuer did:web:evil.example is not trusted according to trust policy"), untrusted.errors)
        assertEquals(listOf("engine down"), VerificationResult.Invalid.AdapterNotReady(credential, "engine down").errors)
        assertTrue(
            "Configure it" in
                VerificationResult.Invalid
                    .AdapterNotReady(credential, null)
                    .errors
                    .single(),
        )
    }

    @Test
    fun `multiple failures flatten their nested errors before their own`() {
        val first = VerificationResult.Invalid.Expired(credential, at, errors = listOf("expired"))
        val second = VerificationResult.Invalid.Revoked(credential, null, errors = listOf("revoked"))
        val combined =
            VerificationResult.Invalid.MultipleFailures(
                credential = credential,
                failures = listOf(first, second),
                errors = listOf("summary"),
                warnings = listOf("w"),
            )
        assertEquals(listOf("expired", "revoked", "summary"), combined.allErrors)
        assertEquals(listOf("w"), combined.allWarnings)
    }

    @Test
    fun `multiple failures reject an empty list and a mismatched credential`() {
        assertFailsWith<IllegalArgumentException> { VerificationResult.Invalid.MultipleFailures(credential, emptyList()) }
        val other = credential.copy(id = CredentialId("urn:uuid:33333333-3333-3333-3333-333333333333"))
        assertFailsWith<IllegalArgumentException> {
            VerificationResult.Invalid.MultipleFailures(credential, listOf(VerificationResult.Invalid.Expired(other, at)))
        }
    }

    @Test
    fun `status predicates identify exactly one rejection`() {
        fun flags(result: VerificationResult) = listOf(result.isExpired, result.isRevoked, result.isSuspended, result.isInvalidProof)
        assertEquals(listOf(false, false, false, true), flags(invalids[0]))
        assertEquals(listOf(true, false, false, false), flags(invalids[1]))
        assertEquals(listOf(false, true, false, false), flags(invalids[3]))
        assertEquals(listOf(false, false, true, false), flags(invalids[4]))
        assertEquals(listOf(false, false, false, false), flags(valid()))
    }

    @Test
    fun `suspended and invalid-proof results may have no credential`() {
        assertNull(VerificationResult.Invalid.Suspended(null, reason = "paused").credential)
        assertNull(VerificationResult.Invalid.InvalidProof(null, "presentation proof").credential)
        assertNull(VerificationResult.Invalid.RevocationCheckFailed(null, "offline").credential)
    }

    @Test
    fun `credential status info defaults describe a plain valid credential`() {
        val info = CredentialStatusInfo(valid = true)
        assertTrue(info.valid)
        assertFalse(info.revoked || info.expired || info.notYetValid)
        assertEquals(emptyMap(), info.statusDetails)
        assertEquals(true, CredentialStatusInfo(valid = false, revoked = true).revoked)
    }
}
