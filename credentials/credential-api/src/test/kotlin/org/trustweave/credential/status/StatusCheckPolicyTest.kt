package org.trustweave.credential.status

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.RevocationFailurePolicy
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.spi.status.CredentialStatusChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

class StatusCheckPolicyTest {
    private val credential =
        VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:key:holder"), claims = emptyMap()),
        )

    private fun evaluate(
        policy: RevocationFailurePolicy,
        checker: CredentialStatusChecker,
    ): StatusCheckVerdict = runBlocking { StatusCheckPolicy.evaluate(credential, checker, policy) }

    private fun checker(block: suspend () -> CredentialStatusCheckResult) =
        object : CredentialStatusChecker {
            override suspend fun checkStatus(credential: VerifiableCredential) = block()
        }

    private fun returning(result: CredentialStatusCheckResult) = checker { result }

    @Test
    fun `conclusive results pass through under every policy`() {
        val conclusive =
            listOf(
                CredentialStatusCheckResult.Valid,
                CredentialStatusCheckResult.NoStatus,
                CredentialStatusCheckResult.Revoked("key compromised"),
                CredentialStatusCheckResult.Suspended("review"),
            )
        for (policy in RevocationFailurePolicy.values()) {
            for (result in conclusive) {
                val verdict = evaluate(policy, returning(result))
                assertSame(result, verdict.status)
                assertNull(verdict.failure)
                assertTrue(verdict.warnings.isEmpty())
            }
        }
    }

    @Test
    fun `check failed fails closed by default with the reason`() {
        val verdict = evaluate(RevocationFailurePolicy.FAIL_CLOSED, returning(CredentialStatusCheckResult.CheckFailed("list 404")))
        val failure = assertIs<VerificationResult.Invalid.RevocationCheckFailed>(verdict.failure)
        assertSame(credential, failure.credential)
        assertTrue(failure.errors.single().contains("list 404"))
        assertEquals(CredentialStatusCheckResult.CheckFailed("list 404"), verdict.status)
    }

    @Test
    fun `check failed under fail with warning continues with a warning`() {
        val verdict =
            evaluate(RevocationFailurePolicy.FAIL_WITH_WARNING, returning(CredentialStatusCheckResult.CheckFailed("list 404")))
        assertNull(verdict.failure)
        assertTrue(verdict.warnings.single().contains("list 404"))
    }

    @Test
    fun `check failed under fail open continues silently`() {
        val verdict = evaluate(RevocationFailurePolicy.FAIL_OPEN, returning(CredentialStatusCheckResult.CheckFailed("list 404")))
        assertNull(verdict.failure)
        assertTrue(verdict.warnings.isEmpty())
    }

    @Test
    fun `checker exception yields null status and policy specific outcome`() {
        val throwing = checker { throw IllegalStateException("socket closed") }

        val closed = evaluate(RevocationFailurePolicy.FAIL_CLOSED, throwing)
        assertNull(closed.status)
        val failure = assertIs<VerificationResult.Invalid.RevocationCheckFailed>(closed.failure)
        assertTrue(failure.errors.single().contains("socket closed"))

        val warn = evaluate(RevocationFailurePolicy.FAIL_WITH_WARNING, throwing)
        assertNull(warn.status)
        assertNull(warn.failure)
        assertEquals(1, warn.warnings.size)

        val open = evaluate(RevocationFailurePolicy.FAIL_OPEN, throwing)
        assertNull(open.status)
        assertNull(open.failure)
        assertTrue(open.warnings.isEmpty())
    }

    @Test
    fun `cancellation from the checker is never swallowed by the policy`() {
        val cancelling = checker { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { evaluate(RevocationFailurePolicy.FAIL_OPEN, cancelling) }
    }
}
