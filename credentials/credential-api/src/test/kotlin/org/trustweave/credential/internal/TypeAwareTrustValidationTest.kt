package org.trustweave.credential.internal

import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.trust.TypeAwareTrustEvaluator
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class TypeAwareTrustValidationTest {
    private val issuer = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")

    private fun credential() =
        VerifiableCredential(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("EducationCredential")),
            issuer = Issuer.IriIssuer(issuer),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:key:subject"), claims = emptyMap()),
        )

    private class Recording(
        private val allowTypes: Set<String>,
    ) : TypeAwareTrustEvaluator {
        var seen: List<String>? = null
        var typelessCalls = 0

        override suspend fun isTrusted(issuer: Did): Boolean {
            typelessCalls++
            return false
        }

        override suspend fun isTrusted(
            issuer: Did,
            credentialTypes: List<String>,
        ): Boolean {
            seen = credentialTypes
            return credentialTypes.any { it in allowTypes }
        }
    }

    @Test
    fun `validateTrust hands the credential types to a type-aware evaluator`() =
        runBlocking<Unit> {
            val evaluator = Recording(setOf("EducationCredential"))
            assertNull(CredentialValidation.validateTrust(credential(), evaluator))
            assertEquals(listOf("VerifiableCredential", "EducationCredential"), evaluator.seen)
            assertEquals(0, evaluator.typelessCalls, "the typeless check must not be used when types are known")
        }

    @Test
    fun `a type-aware evaluator that rejects the types makes the issuer untrusted`() =
        runBlocking<Unit> {
            val result = CredentialValidation.validateTrust(credential(), Recording(setOf("Other")))
            assertTrue(result is VerificationResult.Invalid.UntrustedIssuer)
        }
}
