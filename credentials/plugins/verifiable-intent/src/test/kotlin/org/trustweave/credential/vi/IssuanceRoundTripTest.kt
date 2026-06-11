package org.trustweave.credential.vi

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.crypto.KmsEs256Signer
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.issuance.ViAgent
import org.trustweave.credential.vi.issuance.ViIssuer
import org.trustweave.credential.vi.issuance.ViUser
import org.trustweave.credential.vi.model.IssuerCredential
import org.trustweave.credential.vi.model.Vct
import org.trustweave.credential.vi.verification.ChainVerificationResult
import org.trustweave.kms.Algorithm
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.trustweave.kms.results.GenerateKeyResult

/**
 * End-to-end round trip entirely in Kotlin: mint L1/L2/L3 through the **real in-memory KMS** and the
 * production [KmsEs256Signer], then verify the chain with [VerifiableIntent]. This exercises the
 * issuance side (SD-JWT array-element disclosures, `delegate_payload`, cross-layer `sd_hash`,
 * reference-binding injection, ES256-via-KMS) that the external-vector test cannot reach.
 */
class IssuanceRoundTripTest {

    private val kms = InMemoryKeyManagementService()

    private fun jwk(map: Map<String, Any?>, kid: String?): JsonObject = buildJsonObject {
        put("kty", map["kty"] as String)
        put("crv", map["crv"] as String)
        put("x", map["x"] as String)
        put("y", map["y"] as String)
        kid?.let { put("kid", it) }
    }

    private suspend fun generate() = (kms.generateKey(Algorithm.P256) as GenerateKeyResult.Success).keyHandle

    /**
     * Issues a full autonomous chain (L1, L2 with both open mandates, L3a + L3b) for a payment of
     * [amount] minor units against an amount_range of [10000, 40000], and verifies it.
     */
    private suspend fun runAutonomous(amount: Int, cardId: String?): ChainVerificationResult {
        val issuer = generate()
        val user = generate()
        val agent = generate()

        val issuerSigner = KmsEs256Signer(kms, issuer.id)
        val userSigner = KmsEs256Signer(kms, user.id)
        val agentSigner = KmsEs256Signer(kms, agent.id)

        val issuerJwk = jwk(issuer.publicKeyJwk!!, null)
        val userJwk = jwk(user.publicKeyJwk!!, null)
        val agentJwk = jwk(agent.publicKeyJwk!!, "agent-key-1")

        val now = 1_780_000_000L
        val paymentInstrument = buildJsonObject {
            put("type", "mastercard.srcDigitalCard")
            put("id", "pi-1")
            put("description", "Mastercard ****1234")
        }

        val l1 = ViIssuer.createLayer1(
            IssuerCredential(
                iss = "https://issuer.example", sub = "user-1", iat = now, exp = now + 86_400,
                userCnfJwk = userJwk, panLastFour = "1234", scheme = "Mastercard", cardId = cardId,
                email = "alice@example.com",
            ),
            issuerSigner, issuerKid = "issuer-key-1",
        )

        val checkoutMandate = buildJsonObject {
            put("vct", Vct.CHECKOUT_OPEN)
            put("cnf", buildJsonObject { put("jwk", agentJwk) })
            put(
                "constraints",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "mandate.checkout.line_items")
                            put(
                                "items",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("id", "li-1")
                                            put("acceptable_items", JsonArray(emptyList()))
                                            put("quantity", 1)
                                        },
                                    ),
                                ),
                            )
                            put("match_mode", "minimum")
                        },
                    ),
                ),
            )
        }
        val paymentMandate = buildJsonObject {
            put("vct", Vct.PAYMENT_OPEN)
            put("cnf", buildJsonObject { put("jwk", agentJwk) })
            put("payment_instrument", paymentInstrument)
            put(
                "constraints",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", "mandate.payment.amount_range")
                            put("currency", "USD"); put("min", 10_000); put("max", 40_000)
                        },
                        buildJsonObject { put("type", "mandate.payment.reference"); put("conditional_transaction_id", "") },
                    ),
                ),
            )
        }
        val l2 = ViUser.createLayer2Autonomous(
            l1Compact = l1, checkoutMandate = checkoutMandate, paymentMandate = paymentMandate,
            nonce = "l2-nonce", aud = "https://agent.example", iat = now, exp = now + 86_400,
            iss = "https://wallet.example", signer = userSigner, kid = "user-key-1",
        )

        // The agent transacts at a merchant; L3a (payment) and L3b (checkout) share the txn id.
        val checkoutJwt = "eyJtZXJjaGFudCI6ImNoZWNrb3V0LXRva2VuIn0"
        val checkoutHash = sha256B64Url(checkoutJwt.toByteArray(Charsets.US_ASCII))

        val finalPayment = buildJsonObject {
            put("vct", Vct.PAYMENT_FINAL)
            put("transaction_id", checkoutHash)
            put("payee", buildJsonObject { put("id", "m-1"); put("name", "Tennis Warehouse"); put("website", "https://tw.example") })
            put("payment_amount", buildJsonObject { put("currency", "USD"); put("amount", amount) })
            put("payment_instrument", paymentInstrument)
        }
        val l3a = ViAgent.createLayer3Payment(
            finalPayment = finalPayment, l2BaseJwt = l2.baseJwt,
            routedL2Disclosures = listOf(l2.paymentDiscB64!!),
            nonce = "l3-nonce", aud = "https://network.example", iat = now, exp = now + 300,
            iss = "https://agent.example", signer = agentSigner, agentKid = "agent-key-1",
        )

        val finalCheckout = buildJsonObject {
            put("vct", Vct.CHECKOUT_FINAL)
            put("checkout_jwt", checkoutJwt)
            put("checkout_hash", checkoutHash)
        }
        val l3b = ViAgent.createLayer3Checkout(
            finalCheckout = finalCheckout, l2BaseJwt = l2.baseJwt,
            routedL2Disclosures = listOf(l2.checkoutDiscB64!!),
            nonce = "l3-nonce", aud = "https://merchant.example", iat = now, exp = now + 300,
            iss = "https://agent.example", signer = agentSigner, agentKid = "agent-key-1",
        )

        return VerifiableIntent.verifyChain(
            l1 = l1, l2 = l2.compact, issuerJwk = issuerJwk,
            l3Payment = l3a.compact, l2RoutedForPayment = l3a.routedL2,
            l3Checkout = l3b.compact, l2RoutedForCheckout = l3b.routedL2,
            now = now + 60,
        )
    }

    @Test
    fun `issue an autonomous chain via KMS and verify it`() {
        runBlocking {
            val result = runAutonomous(amount = 27_999, cardId = "pi-1")
            result.errors.shouldBeEmpty()
            result.valid shouldBe true
            result.checksPerformed shouldContain "constraints_satisfied"
            result.checksPerformed shouldContain "l2_reference_binding"
            result.checksPerformed shouldContain "l3_cross_reference"
            result.checksPerformed shouldContain "l1_card_id_cross_check"
        }
    }

    @Test
    fun `over-budget payment is rejected by constraint enforcement`() {
        runBlocking {
            val result = runAutonomous(amount = 99_999, cardId = null) // exceeds max 40000
            result.valid shouldBe false
            result.errors.joinToString() stringShouldContain "exceeds maximum"
        }
    }

    @Test
    fun `issue an immediate chain via KMS and verify it`() {
        runBlocking {
            val issuer = generate()
            val user = generate()
            val issuerSigner = KmsEs256Signer(kms, issuer.id)
            val userSigner = KmsEs256Signer(kms, user.id)
            val issuerJwk = jwk(issuer.publicKeyJwk!!, null)
            val userJwk = jwk(user.publicKeyJwk!!, null)
            val now = 1_780_000_000L

            val l1 = ViIssuer.createLayer1(
                IssuerCredential(
                    iss = "https://issuer.example", sub = "user-1", iat = now, exp = now + 86_400,
                    userCnfJwk = userJwk, panLastFour = "1234", scheme = "Mastercard",
                ),
                issuerSigner, issuerKid = "issuer-key-1",
            )

            // Immediate: finalized mandates; transaction_id == checkout_hash == SHA-256(checkout_jwt)
            val checkoutJwt = "eyJtZXJjaGFudCI6ImNoZWNrb3V0LXRva2VuIn0"
            val checkoutHash = sha256B64Url(checkoutJwt.toByteArray(Charsets.US_ASCII))
            val checkoutMandate = buildJsonObject {
                put("vct", Vct.CHECKOUT_FINAL)
                put("checkout_jwt", checkoutJwt)
                put("checkout_hash", checkoutHash)
            }
            val paymentMandate = buildJsonObject {
                put("vct", Vct.PAYMENT_FINAL)
                put("transaction_id", checkoutHash)
                put("payee", buildJsonObject { put("id", "m-1"); put("name", "Tennis Warehouse"); put("website", "https://tw.example") })
                put("payment_amount", buildJsonObject { put("currency", "USD"); put("amount", 27_999) })
                put("payment_instrument", buildJsonObject { put("type", "mastercard.srcDigitalCard"); put("id", "pi-1") })
            }
            val l2 = ViUser.createLayer2Immediate(
                l1Compact = l1, checkoutMandate = checkoutMandate, paymentMandate = paymentMandate,
                nonce = "l2-nonce", aud = "https://merchant.example", iat = now, exp = now + 900,
                iss = "https://wallet.example", signer = userSigner, kid = "user-key-1",
            )

            val result = VerifiableIntent.verifyChain(
                l1 = l1, l2 = l2.compact, issuerJwk = issuerJwk, now = now + 60,
            )

            result.errors.shouldBeEmpty()
            result.valid shouldBe true
            result.checksPerformed shouldContain "l2_checkout_payment_binding"
        }
    }
}
