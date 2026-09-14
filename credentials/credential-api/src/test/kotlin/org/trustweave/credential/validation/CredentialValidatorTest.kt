package org.trustweave.credential.validation

import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Structure validation, which runs before anything cryptographic and had no tests.
 *
 * Its job is to reject a credential whose shape means later stages would fail confusingly — a
 * missing proof, a claim set to null, an issuer that says it is a DID and is not. Each case is
 * pinned to the code the caller will branch on, because the code is the part that is an API.
 */
class CredentialValidatorTest {
    private val proof =
        CredentialProof.LinkedDataProof(
            type = CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020,
            proofPurpose = CredentialConstants.ProofPurposes.ASSERTION_METHOD,
            verificationMethod = "did:key:test#key-1",
            proofValue = "test-signature",
            created = Clock.System.now(),
        )

    private fun credential(
        types: List<String> = listOf("VerifiableCredential"),
        issuer: String = "did:example:issuer",
        subjectId: String? = "did:example:subject",
        claims: Map<String, kotlinx.serialization.json.JsonElement> = mapOf("name" to JsonPrimitive("Ada")),
        withProof: CredentialProof? = proof,
    ) = VerifiableCredential(
        type = types.map { CredentialType.fromString(it) },
        issuer = Issuer.IriIssuer(Iri(issuer)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = subjectId?.let { Iri(it) }, claims = claims),
        proof = withProof,
    )

    private fun invalid(result: ValidationResult): ValidationResult.Invalid {
        assertTrue(result is ValidationResult.Invalid, "expected Invalid, got $result")
        return result
    }

    // ---------------------------------------------------------------- structure

    @Test
    fun `a well-formed credential validates`() {
        val result = CredentialValidator.validateStructure(credential())
        assertEquals(ValidationResult.Valid, result)
        assertTrue(result.isValid())
        assertNull(result.errorMessage())
    }

    @Test
    fun `a credential missing the VerifiableCredential type is rejected`() {
        val error = invalid(CredentialValidator.validateStructure(credential(types = listOf("DegreeCredential"))))
        assertEquals("MISSING_VERIFIABLE_CREDENTIAL_TYPE", error.code)
        assertEquals("type", error.field)
        assertEquals(listOf("DegreeCredential"), error.value)
        assertFalse(error.isValid())
        assertEquals(error.message, error.errorMessage())
    }

    @Test
    fun `an issuer that claims to be a DID must parse as one`() {
        val error = invalid(CredentialValidator.validateStructure(credential(issuer = "did:example")))
        assertEquals("INVALID_ISSUER_DID", error.code)
        assertEquals("issuer", error.field)
    }

    @Test
    fun `a non-DID issuer IRI is accepted without DID parsing`() {
        assertEquals(
            ValidationResult.Valid,
            CredentialValidator.validateStructure(credential(issuer = "https://issuer.example/1")),
        )
    }

    @Test
    fun `a subject that claims to be a DID must parse as one`() {
        val error = invalid(CredentialValidator.validateStructure(credential(subjectId = "did:example")))
        assertEquals("INVALID_SUBJECT_DID", error.code)
        assertEquals("credentialSubject.id", error.field)
    }

    @Test
    fun `a subject with no id is allowed, per VC 2 point 0`() {
        assertEquals(ValidationResult.Valid, CredentialValidator.validateStructure(credential(subjectId = null)))
    }

    @Test
    fun `a null claim value is rejected and the error names the claim`() {
        val error =
            invalid(
                CredentialValidator.validateStructure(
                    credential(claims = mapOf("name" to JsonPrimitive("Ada"), "nickname" to JsonNull)),
                ),
            )
        assertEquals("NULL_CLAIM_VALUE", error.code)
        assertEquals("credentialSubject.claims.nickname", error.field)
        assertNull(error.value)
    }

    @Test
    fun `an empty claim set is allowed`() {
        assertEquals(ValidationResult.Valid, CredentialValidator.validateStructure(credential(claims = emptyMap())))
    }

    @Test
    fun `a credential with no proof is rejected`() {
        val error = invalid(CredentialValidator.validateStructure(credential(withProof = null)))
        assertEquals("MISSING_PROOF", error.code)
        assertEquals("proof", error.field)
    }

    @Test
    fun `the type check runs before the proof check`() {
        // Both are wrong; the caller sees the first failure, not a list. Worth pinning so the
        // order is a decision rather than an accident of statement order.
        val error =
            invalid(
                CredentialValidator.validateStructure(
                    credential(types = listOf("DegreeCredential"), withProof = null),
                ),
            )
        assertEquals("MISSING_VERIFIABLE_CREDENTIAL_TYPE", error.code)
    }

    // ---------------------------------------------------------------- proof only

    @Test
    fun `validateProof accepts a credential carrying a proof`() {
        assertEquals(ValidationResult.Valid, CredentialValidator.validateProof(credential()))
    }

    @Test
    fun `validateProof rejects a credential with no proof`() {
        val error = invalid(CredentialValidator.validateProof(credential(withProof = null)))
        assertEquals("MISSING_PROOF", error.code)
        assertTrue("must have a proof" in error.message)
    }

    @Test
    fun `validateProof ignores everything structure validation would catch`() {
        // A credential with the wrong type and a null claim still passes validateProof: it asks
        // one question. Callers needing the rest must call validateStructure.
        val result =
            CredentialValidator.validateProof(
                credential(types = listOf("DegreeCredential"), claims = mapOf("x" to JsonNull)),
            )
        assertEquals(ValidationResult.Valid, result)
    }

    @Test
    fun `every proof format resolves to a format id`() {
        // getFormatId is exhaustive over CredentialProof, so no proof shape can reach the
        // MISSING_PROOF_FORMAT branch; recorded so that branch is known to be defensive.
        assertEquals(ValidationResult.Valid, CredentialValidator.validateProof(credential()))
        assertEquals(
            ValidationResult.Valid,
            CredentialValidator.validateProof(
                credential(withProof = CredentialProof.JwtProof(jwt = "a.b.c")),
            ),
        )
    }
}
