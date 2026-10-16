package org.trustweave.credential.avpauth.engine

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.trustweave.credential.avpmicro.verification.IssuerTrustPolicy
import org.trustweave.credential.avpmicro.verification.StatusResolver
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTrustFailClosedTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private val vectorIssuer = "did:key:zDnaekFd1ybjRXtmEJKwuDV4GQhHgQovoGbdVVDSwUwDuy8tv"
    private val trust = IssuerTrustPolicy { did, _ -> did == vectorIssuer }

    private fun vector(): JsonObject =
        Json
            .parseToJsonElement(
                this::class.java.getResource("/vectors/02-payment-authorization.json")!!.readText(),
            ).jsonObject

    private fun reason(v: AuthorizationVerdict) = (v as AuthorizationVerdict.Reject).reason

    @Test
    fun `an engine with no trust policy refuses a genuinely signed authorization`() =
        runTest {
            val engine = AuthorizationEngine(clock = { now }, statusResolver = StatusResolver { false })
            assertEquals("ISSUER_NOT_TRUSTED", reason(engine.decide(vector())))
        }

    @Test
    fun `an untrusting policy refuses`() =
        runTest {
            val engine =
                AuthorizationEngine(
                    clock = { now },
                    issuerTrust = IssuerTrustPolicy { _, _ -> false },
                    statusResolver = StatusResolver { false },
                )
            assertEquals("ISSUER_NOT_TRUSTED", reason(engine.decide(vector())))
        }

    @Test
    fun `credentialStatus without a resolver is refused`() =
        runTest {
            val engine = AuthorizationEngine(clock = { now }, issuerTrust = trust)
            assertEquals("STATUS_UNVERIFIABLE", reason(engine.decide(vector())))
        }

    @Test
    fun `a revoked credential is refused`() =
        runTest {
            val engine = AuthorizationEngine(clock = { now }, issuerTrust = trust, statusResolver = StatusResolver { true })
            assertEquals("CREDENTIAL_REVOKED", reason(engine.decide(vector())))
        }

    @Test
    fun `trusted issuer and live credential are allowed`() =
        runTest {
            val engine = AuthorizationEngine(clock = { now }, issuerTrust = trust, statusResolver = StatusResolver { false })
            assertTrue(engine.decide(vector()) is AuthorizationVerdict.Allow)
        }
}
