package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.trust.TrustPolicy
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Comprehensive tests for CredentialValidation utility.
 */
class CredentialValidationTest {
    
    @Test
    fun `test validateContext with valid VC 1_1 context`() {
        val credential = createCredentialWithContext(listOf(CredentialConstants.VcContexts.VC_1_1))
        val result = CredentialValidation.validateContext(credential)
        assertNull(result, "VC 1.1 context should be valid")
    }
    
    @Test
    fun `test validateContext with valid VC 2_0 context`() {
        val credential = createCredentialWithContext(listOf(CredentialConstants.VcContexts.VC_2_0))
        val result = CredentialValidation.validateContext(credential)
        assertNull(result, "VC 2.0 context should be valid")
    }
    
    @Test
    fun `test validateContext with both VC contexts`() {
        val credential = createCredentialWithContext(
            listOf(CredentialConstants.VcContexts.VC_1_1, CredentialConstants.VcContexts.VC_2_0)
        )
        val result = CredentialValidation.validateContext(credential)
        assertNull(result, "Both VC contexts should be valid")
    }
    
    @Test
    fun `test validateContext with invalid context`() {
        val credential = createCredentialWithContext(listOf("https://invalid-context.com/v1"))
        val result = CredentialValidation.validateContext(credential)
        assertNotNull(result, "Invalid context should return error")
        assertTrue(result is VerificationResult.Invalid.InvalidProof)
        assertTrue(result.errors.first().contains("Credential context must include"))
    }
    
    @Test
    fun `test validateContext with empty context`() {
        val credential = createCredentialWithContext(emptyList())
        val result = CredentialValidation.validateContext(credential)
        assertNotNull(result, "Empty context should return error")
    }
    
    @Test
    fun `test validateProofExists with proof present`() {
        val proof = CredentialProof.LinkedDataProof(
            type = CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020,
            proofPurpose = CredentialConstants.ProofPurposes.ASSERTION_METHOD,
            verificationMethod = "did:key:test#key-1",
            proofValue = "test-signature",
            created = Clock.System.now()
        )
        val credential = createCredentialWithProof(proof)
        val result = CredentialValidation.validateProofExists(credential)
        assertNull(result, "Credential with proof should be valid")
    }
    
    @Test
    fun `test validateProofExists with no proof`() {
        val credential = createCredentialWithProof(null)
        val result = CredentialValidation.validateProofExists(credential)
        assertNotNull(result, "Credential without proof should return error")
        assertTrue(result is VerificationResult.Invalid.InvalidProof)
        assertTrue(result.errors.first().contains("must have a proof property"))
    }
    
    @Test
    fun `test validateNotBefore with valid credential`() {
        val now = Clock.System.now()
        val validFrom = now - kotlin.time.Duration.parse("PT1H")
        val credential = createCredentialWithValidFrom(validFrom)
        val options = VerificationOptions(
            checkNotBefore = true,
            clockSkewTolerance = java.time.Duration.ofMinutes(5)
        )
        val result = CredentialValidation.validateNotBefore(credential, options, now)
        assertNull(result, "Credential with validFrom in the past should be valid")
    }
    
    @Test
    fun `test validateNotBefore with future validFrom`() {
        val now = Clock.System.now()
        val validFrom = now + kotlin.time.Duration.parse("PT1H")
        val credential = createCredentialWithValidFrom(validFrom)
        val options = VerificationOptions(
            checkNotBefore = true,
            clockSkewTolerance = java.time.Duration.ofMinutes(5)
        )
        val result = CredentialValidation.validateNotBefore(credential, options, now)
        assertNotNull(result, "Credential with future validFrom should return error")
        assertTrue(result is VerificationResult.Invalid.NotYetValid)
        assertTrue(result.errors.first().contains("not yet valid"))
    }
    
    @Test
    fun `test validateNotBefore with clock skew tolerance`() {
        val now = Clock.System.now()
        val validFrom = now + kotlin.time.Duration.parse("PT3M") // Within 5 minute tolerance
        val credential = createCredentialWithValidFrom(validFrom)
        val options = VerificationOptions(
            checkNotBefore = true,
            clockSkewTolerance = java.time.Duration.ofMinutes(5)
        )
        val result = CredentialValidation.validateNotBefore(credential, options, now)
        assertNull(result, "Credential within clock skew tolerance should be valid")
    }
    
    @Test
    fun `test validateNotBefore when checkNotBefore is false`() {
        val now = Clock.System.now()
        val validFrom = now + kotlin.time.Duration.parse("PT1H")
        val credential = createCredentialWithValidFrom(validFrom)
        val options = VerificationOptions(checkNotBefore = false)
        val result = CredentialValidation.validateNotBefore(credential, options, now)
        assertNull(result, "When checkNotBefore is false, should not validate")
    }
    
    @Test
    fun `test validateNotBefore when validFrom is null`() {
        val credential = createCredentialWithValidFrom(null)
        val options = VerificationOptions(checkNotBefore = true)
        val result = CredentialValidation.validateNotBefore(credential, options)
        assertNull(result, "When validFrom is null, should not validate")
    }
    
    @Test
    fun `test validateExpiration with valid credential`() {
        val now = Clock.System.now()
        val expirationDate = now + kotlin.time.Duration.parse("P1D")
        val credential = createCredentialWithExpiration(expirationDate)
        val options = VerificationOptions(
            checkExpiration = true,
            clockSkewTolerance = java.time.Duration.ofMinutes(5)
        )
        val result = CredentialValidation.validateExpiration(credential, options, now)
        assertNull(result, "Credential with future expiration should be valid")
    }
    
    @Test
    fun `test validateExpiration with expired credential`() {
        val now = Clock.System.now()
        val expirationDate = now - kotlin.time.Duration.parse("P1D")
        val credential = createCredentialWithExpiration(expirationDate)
        val options = VerificationOptions(
            checkExpiration = true,
            clockSkewTolerance = java.time.Duration.ofMinutes(5)
        )
        val result = CredentialValidation.validateExpiration(credential, options, now)
        assertNotNull(result, "Expired credential should return error")
        assertTrue(result is VerificationResult.Invalid.Expired)
        assertTrue(result.errors.first().contains("expired"))
    }
    
    @Test
    fun `test validateExpiration with clock skew tolerance`() {
        val now = Clock.System.now()
        val expirationDate = now - kotlin.time.Duration.parse("PT3M") // Within 5 minute tolerance
        val credential = createCredentialWithExpiration(expirationDate)
        val options = VerificationOptions(
            checkExpiration = true,
            clockSkewTolerance = java.time.Duration.ofMinutes(5)
        )
        val result = CredentialValidation.validateExpiration(credential, options, now)
        assertNull(result, "Credential within clock skew tolerance should be valid")
    }
    
    @Test
    fun `test validateExpiration when checkExpiration is false`() {
        val now = Clock.System.now()
        val expirationDate = now - kotlin.time.Duration.parse("P1D")
        val credential = createCredentialWithExpiration(expirationDate)
        val options = VerificationOptions(checkExpiration = false)
        val result = CredentialValidation.validateExpiration(credential, options, now)
        assertNull(result, "When checkExpiration is false, should not validate")
    }
    
    @Test
    fun `test validateExpiration when expirationDate is null`() {
        val credential = createCredentialWithExpiration(null)
        val options = VerificationOptions(checkExpiration = true)
        val result = CredentialValidation.validateExpiration(credential, options)
        assertNull(result, "When expirationDate is null, should not validate")
    }
    
    @Test
    fun `test validateTrust with no trust policy`() = runBlocking {
        val credential = createTestCredential()
        val result = CredentialValidation.validateTrust(credential, null)
        assertNull(result, "When no trust policy, should not validate")
    }
    
    @Test
    fun `test validateTrust with trusted issuer`() = runBlocking {
        val issuerDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        val credential = createTestCredential(issuer = Issuer.IriIssuer(issuerDid))
        val trustPolicy = TrustPolicy.allowlist(setOf(issuerDid))
        val result = CredentialValidation.validateTrust(credential, trustPolicy)
        assertNull(result, "Trusted issuer should pass validation")
    }
    
    @Test
    fun `test validateTrust with untrusted issuer`() = runBlocking {
        val issuerDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        val untrustedDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK2")
        val credential = createTestCredential(issuer = Issuer.IriIssuer(untrustedDid))
        val trustPolicy = TrustPolicy.allowlist(setOf(issuerDid))
        val result = CredentialValidation.validateTrust(credential, trustPolicy)
        assertNotNull(result, "Untrusted issuer should return error")
        assertTrue(result is VerificationResult.Invalid.UntrustedIssuer)
    }
    
    @Test
    fun `test validateTrust with non-DID issuer`() = runBlocking {
        val issuerIri = Iri("https://example.com/issuer")
        val credential = createTestCredential(issuer = Issuer.IriIssuer(issuerIri))
        val trustPolicy = TrustPolicy.allowlist(setOf(Did("did:key:test")))
        val result = CredentialValidation.validateTrust(credential, trustPolicy)
        assertNull(result, "Non-DID issuer should skip trust validation")
    }
    
    // Helper functions
    
    private fun createCredentialWithContext(context: List<String>): VerifiableCredential {
        return VerifiableCredential(
            context = context,
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:test"),
                claims = emptyMap()
            )
        )
    }
    
    private fun createCredentialWithProof(proof: CredentialProof?): VerifiableCredential {
        return VerifiableCredential(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:test"),
                claims = emptyMap()
            ),
            proof = proof
        )
    }
    
    private fun createCredentialWithValidFrom(validFrom: Instant?): VerifiableCredential {
        return VerifiableCredential(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            validFrom = validFrom,
            credentialSubject = CredentialSubject(
                id = Iri("did:key:test"),
                claims = emptyMap()
            )
        )
    }
    
    private fun createCredentialWithExpiration(expirationDate: Instant?): VerifiableCredential {
        return VerifiableCredential(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            expirationDate = expirationDate,
            credentialSubject = CredentialSubject(
                id = Iri("did:key:test"),
                claims = emptyMap()
            )
        )
    }
    
    private fun createTestCredential(
        issuer: Issuer = Issuer.IriIssuer(Iri("did:key:test"))
    ): VerifiableCredential {
        return VerifiableCredential(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = issuer,
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:test"),
                claims = emptyMap()
            )
        )
    }
    
}

