package org.trustweave.credential.results

import org.trustweave.core.identifiers.Iri
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
import kotlin.time.Clock

class IssuanceResultExtensionsBehaviorTest {
    private val credential =
        VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:key:holder"), claims = emptyMap()),
        )
    private val success: IssuanceResult = IssuanceResult.Success(credential)
    private val failure: IssuanceResult =
        IssuanceResult.Failure.InvalidRequest("issuer", "missing")

    @Test
    fun `getOrReturn yields the credential on success without invoking the handler`() {
        var called = false
        val value = success.getOrReturn<Unit> { called = true }
        assertSame(credential, value)
        assertTrue(!called)
    }

    @Test
    fun `getOrReturn invokes the handler with the failure and returns null`() {
        var seen: IssuanceResult.Failure? = null
        val value = failure.getOrReturn<Unit> { seen = it }
        assertNull(value)
        assertSame<Any?>(failure, seen)
    }

    @Test
    fun `requireSuccess returns the credential or throws with the failure errors`() {
        assertSame(credential, success.requireSuccess())
        val e = assertFailsWith<IllegalStateException> { failure.requireSuccess() }
        assertEquals(
            "Credential issuance failed: Invalid request: field 'issuer' - missing",
            e.message,
        )
    }

    @Test
    fun `getOrElse only evaluates the default on failure`() {
        var evaluated = 0
        val fallback = credential.copy(id = null)
        assertSame(
            credential,
            success.getOrElse {
                evaluated++
                fallback
            },
        )
        assertEquals(0, evaluated)
        assertSame(
            fallback,
            failure.getOrElse {
                evaluated++
                fallback
            },
        )
        assertEquals(1, evaluated)
    }

    @Test
    fun `credential property mirrors credentialOrNull`() {
        assertSame(credential, success.credential)
        assertNull(failure.credential)
    }
}
