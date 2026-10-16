package org.trustweave.credential.avpmicro.verification

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.avpmicro.AvpMicro
import org.trustweave.credential.avpmicro.Vectors
import org.trustweave.credential.avpmicro.crypto.EcdsaJcs2022
import org.trustweave.credential.avpmicro.crypto.TestSigning
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Issuer trust, holder binding, proof purpose, revocation fail-closed and amount sign. */
class PaymentTrustBindingTest {
    private val now = Instant.parse("2026-03-25T21:30:30Z")
    private val issuer = TestSigning.didKey("issuer")
    private val attacker = TestSigning.didKey("attacker")
    private val payer = TestSigning.didKey("payer")
    private val payee = TestSigning.didKey("payee")
    private val trustIssuer = IssuerTrustPolicy { did, _ -> did == issuer }
    private val neverRevoked = StatusResolver { false }

    private fun credential(
        signerLabel: String = "issuer",
        issuerClaim: String = issuer,
        subject: String = payer,
        withStatus: Boolean = false,
    ): JsonObject {
        val unsigned =
            buildJsonObject {
                put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/credentials/v2"))))
                put("id", "urn:test:vc:1")
                put("type", JsonArray(listOf(JsonPrimitive("VerifiableCredential"), JsonPrimitive("SpendingAuthorizationCredential"))))
                put("issuer", issuerClaim)
                put("validFrom", "2026-03-25T20:00:00Z")
                put("validUntil", "2026-06-25T20:00:00Z")
                if (withStatus) {
                    put(
                        "credentialStatus",
                        buildJsonObject {
                            put("id", "https://issuer.example/status/3#1")
                            put("type", "BitstringStatusListEntry")
                        },
                    )
                }
                put(
                    "credentialSubject",
                    buildJsonObject {
                        put("id", subject)
                        put("currency", "USD")
                        put("maxPerTransaction", "0.05")
                    },
                )
            }
        return TestSigning.sign(unsigned, signerLabel, "2026-03-25T20:00:01Z")
    }

    private fun authorization(
        credential: JsonObject = credential(),
        signerLabel: String = "payer",
        payerClaim: String = payer,
        amount: String = "0.001",
        purpose: String = "assertionMethod",
    ): JsonObject {
        val unsigned =
            buildJsonObject {
                put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/ns/credentials/v2"))))
                put("id", "urn:test:authz:1")
                put("type", "PaymentAuthorization")
                put("payer", payerClaim)
                put("payee", payee)
                put("amount", amount)
                put("currency", "USD")
                put("expires", "2026-03-25T21:31:02Z")
                put("nonce", "n-1")
                put(
                    "vp",
                    buildJsonObject {
                        put("type", "VerifiablePresentation")
                        put("verifiableCredential", JsonArray(listOf(credential)))
                    },
                )
            }
        return EcdsaJcs2022.sign(
            unsigned,
            TestSigning.seedKey(signerLabel),
            TestSigning.verificationMethod(signerLabel),
            "2026-03-25T21:30:02Z",
            purpose,
        )
    }

    private fun verify(
        authorization: JsonObject,
        trust: IssuerTrustPolicy? = trustIssuer,
        status: StatusResolver? = neverRevoked,
    ) = AvpMicro.verifyPayment(authorization, now, statusResolver = status, issuerTrust = trust)

    private fun reason(r: PaymentVerificationResult) = (r as PaymentVerificationResult.Invalid).reason

    @Test
    fun `a bound, trusted authorization is valid`() {
        val r = verify(authorization())
        assertTrue(r is PaymentVerificationResult.Valid, "expected Valid, got $r")
    }

    @Test
    fun `no issuer trust policy fails closed`() {
        assertEquals(VerificationFailure.ISSUER_NOT_TRUSTED, reason(verify(authorization(), trust = null)))
        // The shipped vector is genuinely signed but nobody vouched for its issuer.
        val r = AvpMicro.verifyPayment(Vectors.paymentAuthorization, now, statusResolver = neverRevoked)
        assertEquals(VerificationFailure.ISSUER_NOT_TRUSTED, reason(r))
    }

    @Test
    fun `an issuer the policy does not trust is refused`() {
        val r = verify(authorization(), trust = IssuerTrustPolicy { _, _ -> false })
        assertEquals(VerificationFailure.ISSUER_NOT_TRUSTED, reason(r))
    }

    @Test
    fun `self-issued credential from an untrusted key is refused`() {
        val selfIssued = credential(signerLabel = "attacker", issuerClaim = attacker)
        assertEquals(VerificationFailure.ISSUER_NOT_TRUSTED, reason(verify(authorization(selfIssued))))
    }

    @Test
    fun `credential naming a trusted issuer but signed by another key is refused`() {
        val forged = credential(signerLabel = "attacker", issuerClaim = issuer)
        assertEquals(VerificationFailure.ISSUER_MISMATCH, reason(verify(authorization(forged))))
    }

    @Test
    fun `trust policy receives the credential types`() {
        var seen: List<String> = emptyList()
        verify(
            authorization(),
            trust =
                IssuerTrustPolicy { did, types ->
                    seen = types
                    did == issuer
                },
        )
        assertTrue("SpendingAuthorizationCredential" in seen, "types were $seen")
    }

    @Test
    fun `authorization signed by a key other than the payer is refused`() {
        val r = verify(authorization(signerLabel = "attacker"))
        assertEquals(VerificationFailure.AUTHORIZATION_SIGNER_MISMATCH, reason(r))
    }

    @Test
    fun `authorization proof purpose must be assertionMethod`() {
        val r = verify(authorization(purpose = "authentication"))
        assertEquals(VerificationFailure.PROOF_PURPOSE_INVALID, reason(r))
    }

    @Test
    fun `credential proof purpose must be assertionMethod`() {
        val unsigned = JsonObject(credential().filterKeys { it != "proof" })
        val wrong =
            EcdsaJcs2022.sign(
                unsigned,
                TestSigning.seedKey("issuer"),
                TestSigning.verificationMethod("issuer"),
                "2026-03-25T20:00:01Z",
                "authentication",
            )
        assertEquals(VerificationFailure.PROOF_PURPOSE_INVALID, reason(verify(authorization(wrong))))
    }

    @Test
    fun `credentialStatus without a resolver fails closed`() {
        val withStatus = authorization(credential(withStatus = true))
        assertEquals(VerificationFailure.STATUS_UNVERIFIABLE, reason(verify(withStatus, status = null)))
    }

    @Test
    fun `a resolver that throws fails closed`() {
        val withStatus = authorization(credential(withStatus = true))
        val r = verify(withStatus, status = StatusResolver { error("status list unreachable") })
        assertEquals(VerificationFailure.STATUS_UNVERIFIABLE, reason(r))
    }

    @Test
    fun `a revoked credential is refused and a live one admitted`() {
        val withStatus = authorization(credential(withStatus = true))
        assertEquals(VerificationFailure.CREDENTIAL_REVOKED, reason(verify(withStatus, status = StatusResolver { true })))
        assertTrue(verify(withStatus, status = neverRevoked) is PaymentVerificationResult.Valid)
    }

    @Test
    fun `a credential without credentialStatus needs no resolver`() {
        assertTrue(verify(authorization(), status = null) is PaymentVerificationResult.Valid)
    }

    @Test
    fun `zero and negative amounts are refused`() {
        for (bad in listOf("0", "0.000", "-0.001", "-5")) {
            val r = verify(authorization(amount = bad))
            assertEquals(VerificationFailure.INVALID_AMOUNT, reason(r), "amount $bad")
        }
    }

    @Test
    fun `a non-positive amount override is refused`() {
        val r =
            AvpMicro.checkConstraints(
                authorization(),
                now,
                amountOverride = java.math.BigDecimal("-1"),
                issuerTrust = trustIssuer,
                statusResolver = neverRevoked,
            )
        assertEquals(VerificationFailure.INVALID_AMOUNT, reason(r))
    }
}
