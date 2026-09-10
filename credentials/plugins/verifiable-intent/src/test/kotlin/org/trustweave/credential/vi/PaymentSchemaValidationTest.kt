package org.trustweave.credential.vi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.trustweave.credential.vi.crypto.Es256Signer
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

/** Signed malformed values must fail independently of optional amount_range constraints. */
class PaymentSchemaValidationTest {
    private val now = 1_780_000_000L

    @TestFactory
    fun `payment schema is enforced for immediate and unconstrained autonomous chains`(): List<DynamicTest> {
        val cases =
            listOf(
                Triple("zero", amount(JsonPrimitive(0)), true),
                Triple("positive", amount(JsonPrimitive(100)), true),
                Triple("maximum integer", amount(JsonPrimitive(Long.MAX_VALUE)), true),
                Triple("negative", amount(JsonPrimitive(-1)), false),
                Triple("quoted integer", amount(JsonPrimitive("100")), false),
                Triple("fraction", amount(JsonPrimitive(1.5)), false),
                Triple("integer overflow", amount(Json.parseToJsonElement("9223372036854775808")), false),
                Triple("null amount", amount(JsonNull), false),
                Triple("boolean amount", amount(JsonPrimitive(true)), false),
                Triple("object amount", amount(buildJsonObject {}), false),
                Triple("array amount", amount(JsonArray(emptyList())), false),
                Triple("missing amount", buildJsonObject { put("currency", "USD") }, false),
                Triple("numeric currency", amount(JsonPrimitive(100), JsonPrimitive(840)), false),
                Triple("null currency", amount(JsonPrimitive(100), JsonNull), false),
                Triple("lowercase currency", amount(JsonPrimitive(100), JsonPrimitive("usd")), false),
                Triple("long currency", amount(JsonPrimitive(100), JsonPrimitive("USDD")), false),
                Triple("blank currency", amount(JsonPrimitive(100), JsonPrimitive("   ")), false),
                Triple("missing currency", buildJsonObject { put("amount", 100) }, false),
            )
        return listOf(false, true).flatMap { autonomous ->
            cases.map { (name, paymentAmount, expected) ->
                DynamicTest.dynamicTest("${if (autonomous) "autonomous" else "immediate"}: $name") {
                    val result = runBlocking { verify(paymentAmount, autonomous) }
                    assertEquals(expected, result.valid, result.errors.joinToString())
                }
            }
        }
    }

    private fun amount(
        value: JsonElement,
        currency: JsonElement = JsonPrimitive("USD"),
    ): JsonObject =
        buildJsonObject {
            put("amount", value)
            put("currency", currency)
        }

    private suspend fun verify(
        paymentAmount: JsonObject,
        autonomous: Boolean,
    ): ChainVerificationResult {
        val kms = InMemoryKeyManagementService()

        suspend fun key(kid: String): Pair<JsonObject, Es256Signer> {
            val handle = (kms.generateKey(Algorithm.P256) as GenerateKeyResult.Success).keyHandle
            val publicKey =
                buildJsonObject {
                    for (field in listOf("kty", "crv", "x", "y")) put(field, handle.publicKeyJwk!![field] as String)
                    put("kid", kid)
                }
            return publicKey to KmsEs256Signer(kms, handle.id)
        }
        val (issuerKey, issuer) = key("issuer")
        val (userKey, user) = key("user")
        val (agentKey, agent) = key("agent")
        val l1 =
            ViIssuer.createLayer1(
                IssuerCredential("https://issuer.example", "user", now, now + 86_400, userKey, "1234", "Mastercard"),
                issuer,
                "issuer",
            )
        val checkoutJwt = "synthetic-merchant-checkout"
        val checkoutHash = sha256B64Url(checkoutJwt.toByteArray(Charsets.US_ASCII))
        val instrument =
            buildJsonObject {
                put("id", "card")
                put("type", "mastercard.srcDigitalCard")
            }
        val payment =
            buildJsonObject {
                put("vct", Vct.PAYMENT_FINAL)
                put("transaction_id", checkoutHash)
                put("payment_amount", paymentAmount)
                put("payment_instrument", instrument)
                put(
                    "payee",
                    buildJsonObject {
                        put("name", "Merchant")
                        put("website", "https://merchant.example")
                    },
                )
            }
        val checkout =
            buildJsonObject {
                put("vct", Vct.CHECKOUT_FINAL)
                put("checkout_jwt", checkoutJwt)
                put("checkout_hash", checkoutHash)
            }
        val l2 =
            if (autonomous) {
                val openPayment =
                    buildJsonObject {
                        put("vct", Vct.PAYMENT_OPEN)
                        put("payment_instrument", instrument)
                        put("cnf", buildJsonObject { put("jwk", agentKey) })
                        put(
                            "constraints",
                            JsonArray(
                                listOf(
                                    buildJsonObject {
                                        put("type", "mandate.payment.reference")
                                        put("conditional_transaction_id", checkoutHash)
                                    },
                                ),
                            ),
                        )
                    }
                ViUser.createLayer2Autonomous(
                    l1,
                    null,
                    openPayment,
                    "user-nonce",
                    "https://recipient.example",
                    now,
                    now + 900,
                    "https://wallet.example",
                    user,
                    "user",
                )
            } else {
                ViUser.createLayer2Immediate(
                    l1,
                    checkout,
                    payment,
                    "user-nonce",
                    "https://recipient.example",
                    now,
                    now + 900,
                    "https://wallet.example",
                    user,
                    "user",
                )
            }
        val l3 =
            if (autonomous) {
                ViAgent.createLayer3Payment(
                    payment,
                    l2.baseJwt,
                    listOf(checkNotNull(l2.paymentDiscB64)),
                    "agent-nonce",
                    "https://network.example",
                    now,
                    now + 600,
                    "https://agent.example",
                    agent,
                    "agent",
                )
            } else {
                null
            }
        return VerifiableIntent.verifyChain(
            l1 = l1,
            l2 = l2.compact,
            issuerJwk = issuerKey,
            l3Payment = l3?.compact,
            l2RoutedForPayment = l3?.routedL2,
            now = now + 60,
            expectedL2Aud = "https://recipient.example",
            expectedL2Nonce = "user-nonce",
            expectedL3PaymentAud = "https://network.example",
            expectedL3PaymentNonce = "agent-nonce",
        )
    }
}
