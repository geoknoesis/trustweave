package org.trustweave.credential.vi

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.crypto.KmsEs256Signer
import org.trustweave.credential.vi.crypto.selectivePresentation
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
import io.kotest.matchers.string.shouldContain as stringShouldContain

/**
 * End-to-end round trip entirely in Kotlin: mint L1/L2/L3 through the **real in-memory KMS** and the
 * production [KmsEs256Signer], then verify the chain with [VerifiableIntent]. This exercises the
 * issuance side (SD-JWT array-element disclosures, `delegate_payload`, cross-layer `sd_hash`,
 * reference-binding injection, ES256-via-KMS) that the external-vector test cannot reach.
 */
class IssuanceRoundTripTest {
    @Test
    fun `every open mandate must bind the complete agent public key`() =
        runBlocking<Unit> {
            val missing = runAutonomous(20_000, "card-1", omitCheckoutKey = true)
            missing.valid shouldBe false
            val different = runAutonomous(20_000, "card-1", alterCheckoutKeyY = true)
            different.valid shouldBe false
        }

    @Test
    fun `merchant authenticated autonomous checkout verifies and enforces signed quantities`() =
        runBlocking<Unit> {
            val valid = runAutonomous(20_000, "pi-1", includeCheckout = true, authenticatedCheckout = true)
            valid.errors.shouldBeEmpty()
            valid.valid shouldBe true
            valid.checksPerformed shouldContain "authenticated_checkout_cart"
            val invalid =
                runAutonomous(
                    20_000,
                    "pi-1",
                    includeCheckout = true,
                    authenticatedCheckout = true,
                    merchantQuantity = 2,
                )
            invalid.valid shouldBe false
        }

    @Test
    fun `chain budget reservation rejects excess and replay without spending on invalid chains`() =
        runBlocking<Unit> {
            org.testcontainers.containers.PostgreSQLContainer<Nothing>("postgres:16-alpine").use { container ->
                container.start()
                val source =
                    org.postgresql.ds.PGSimpleDataSource().apply {
                        setURL(container.jdbcUrl)
                        user = container.username
                        password = container.password
                    }
                val ledger =
                    org.trustweave.credential.vi.verification
                        .PostgresIntentLedger(source)
                ledger.initializeSchema()
                val excess = runAutonomous(35_000, "pi-1", budgetLedger = ledger)
                excess.valid shouldBe false
                val invalid = runAutonomous(20_000, "pi-1", includeCheckout = true, budgetLedger = ledger)
                invalid.valid shouldBe false
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT count(*) FROM vi_budget_accounts").use { rows ->
                            rows.next()
                            rows.getInt(1) shouldBe 0
                        }
                    }
                }
                val replay =
                    runAutonomous(
                        20_000,
                        "pi-1",
                        budgetLedger = ledger,
                        replayBudget = true,
                        includeCheckout = true,
                        authenticatedCheckout = true,
                    )
                replay.valid shouldBe false
                replay.errors.joinToString() stringShouldContain "already consumed"
                source.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT sum(spent) FROM vi_budget_accounts").use { rows ->
                            rows.next()
                            rows.getLong(1) shouldBe 20_000L
                        }
                    }
                }
                val agentRules =
                    buildJsonObject {
                        put("type", "mandate.payment.agent_recurrence")
                        put("frequency", "WEEK")
                        put("start_date", "2020-01-01")
                        put("end_date", "2030-01-01")
                        put("max_occurrences", 2)
                    }
                runAutonomous(
                    20_000,
                    "pi-1",
                    budgetLedger = ledger,
                    recurrence = agentRules,
                    budgetMinimum = 25_000,
                    paymentNonce = "minimum",
                ).valid shouldBe false
                runAutonomous(
                    20_000,
                    "pi-1",
                    budgetLedger = ledger,
                    recurrence = agentRules,
                    budgetMinimum = 10_000,
                    paymentNonce = "agent",
                ).valid shouldBe true
                val terms =
                    buildJsonObject {
                        put("frequency", "MNTH")
                        put("start_date", "2026-03-01")
                        put("end_date", "2027-03-01")
                        put("number", 12)
                    }
                val subscription = JsonObject(terms + ("type" to kotlinx.serialization.json.JsonPrimitive("mandate.payment.recurrence")))
                runAutonomous(
                    20_000,
                    "pi-1",
                    budgetLedger = ledger,
                    recurrence = subscription,
                    includeCheckout = true,
                    authenticatedCheckout = true,
                    merchantRecurrence = terms,
                    paymentNonce = "merchant",
                ).valid shouldBe true
                runAutonomous(
                    20_000,
                    "pi-1",
                    budgetLedger = ledger,
                    recurrence = subscription,
                    includeCheckout = true,
                    authenticatedCheckout = true,
                    paymentNonce = "missing-metadata",
                ).valid shouldBe false
            }
        }

    private val kms = InMemoryKeyManagementService()

    private fun jwk(
        map: Map<String, Any?>,
        kid: String?,
    ): JsonObject =
        buildJsonObject {
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
    private suspend fun runAutonomous(
        amount: Int,
        cardId: String?,
        withholdPaymentMandateFromL2: Boolean = false,
        includeCheckout: Boolean = false,
        omitL3Claim: String? = null,
        omitInstrument: Boolean = false,
        omitCheckoutKey: Boolean = false,
        alterCheckoutKeyY: Boolean = false,
        authenticatedCheckout: Boolean = false,
        merchantQuantity: Int = 1,
        budgetLedger: org.trustweave.credential.vi.verification.PostgresIntentLedger? = null,
        replayBudget: Boolean = false,
        recurrence: JsonObject? = null,
        merchantRecurrence: JsonObject? = null,
        budgetMinimum: Long? = null,
        paymentNonce: String = "l3-nonce",
    ): ChainVerificationResult {
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
        val paymentInstrument =
            buildJsonObject {
                put("type", "mastercard.srcDigitalCard")
                put("id", "pi-1")
                put("description", "Mastercard ****1234")
            }

        val l1 =
            ViIssuer.createLayer1(
                IssuerCredential(
                    iss = "https://issuer.example",
                    sub = "user-1",
                    iat = now,
                    exp = now + 86_400,
                    userCnfJwk = userJwk,
                    panLastFour = "1234",
                    scheme = "Mastercard",
                    cardId = cardId,
                    email = "alice@example.com",
                ),
                issuerSigner,
                issuerKid = "issuer-key-1",
            )

        val checkoutMandate =
            buildJsonObject {
                put("vct", Vct.CHECKOUT_OPEN)
                if (!omitCheckoutKey) {
                    val checkoutKey =
                        if (alterCheckoutKeyY) {
                            JsonObject(agentJwk + ("y" to kotlinx.serialization.json.JsonPrimitive("different-coordinate")))
                        } else {
                            agentJwk
                        }
                    put("cnf", buildJsonObject { put("jwk", checkoutKey) })
                }
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
        val basePaymentMandate =
            buildJsonObject {
                put("vct", Vct.PAYMENT_OPEN)
                put("cnf", buildJsonObject { put("jwk", agentJwk) })
                if (!omitInstrument) put("payment_instrument", paymentInstrument)
                put(
                    "constraints",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("type", "mandate.payment.amount_range")
                                put("currency", "USD")
                                put("min", 10_000)
                                put("max", 40_000)
                            },
                            buildJsonObject {
                                put("type", "mandate.payment.reference")
                                put("conditional_transaction_id", "")
                            },
                        ) +
                            if (budgetLedger != null) {
                                listOf(
                                    buildJsonObject {
                                        put("type", "mandate.payment.budget")
                                        put("currency", "USD")
                                        put("max", 30_000)
                                        budgetMinimum?.let { put("min", it) }
                                    },
                                )
                            } else {
                                emptyList()
                            },
                    ),
                )
            }
        val paymentMandate =
            if (recurrence == null) {
                basePaymentMandate
            } else {
                JsonObject(
                    basePaymentMandate + ("constraints" to JsonArray((basePaymentMandate["constraints"] as JsonArray) + recurrence)),
                )
            }
        val l2 =
            ViUser.createLayer2Autonomous(
                l1Compact = l1,
                checkoutMandate = checkoutMandate,
                paymentMandate = paymentMandate,
                nonce = "l2-nonce",
                aud = "https://agent.example",
                iat = now,
                exp = now + 86_400,
                iss = "https://wallet.example",
                signer = userSigner,
                kid = "user-key-1",
            )

        // The agent transacts at a merchant; L3a (payment) and L3b (checkout) share the txn id.
        val merchantKey = generate()
        val checkoutTrust =
            org.trustweave.credential.vi.verification.CheckoutTrust(
                "https://merchant.example",
                "checkout-verifier",
                "merchant-challenge",
                jwk(merchantKey.publicKeyJwk!!, null),
                buildJsonObject { put("id", "m-1") },
            )
        val checkoutJwt =
            if (authenticatedCheckout) {
                org.trustweave.credential.vi.crypto.Jws.sign(
                    buildJsonObject {
                        put("alg", "ES256")
                        put("typ", "JWT")
                    },
                    buildJsonObject {
                        put("iss", "https://merchant.example")
                        put("aud", "checkout-verifier")
                        put("nonce", "merchant-challenge")
                        merchantRecurrence?.let { put("recurrence", it) }
                        put("iat", now)
                        put("exp", now + 300)
                        put(
                            "cart",
                            buildJsonObject {
                                put(
                                    "items",
                                    JsonArray(
                                        listOf(
                                            buildJsonObject {
                                                put("id", "product-1")
                                                put("quantity", merchantQuantity)
                                            },
                                        ),
                                    ),
                                )
                            },
                        )
                    },
                    KmsEs256Signer(kms, merchantKey.id),
                )
            } else {
                "eyJtZXJjaGFudCI6ImNoZWNrb3V0LXRva2VuIn0"
            }
        val checkoutHash = sha256B64Url(checkoutJwt.toByteArray(Charsets.US_ASCII))

        val finalPayment =
            buildJsonObject {
                put("vct", Vct.PAYMENT_FINAL)
                put("transaction_id", checkoutHash)
                put(
                    "payee",
                    buildJsonObject {
                        put("id", "m-1")
                        put("name", "Tennis Warehouse")
                        put("website", "https://tw.example")
                    },
                )
                put(
                    "payment_amount",
                    buildJsonObject {
                        put("currency", "USD")
                        put("amount", amount)
                    },
                )
                put("payment_instrument", paymentInstrument)
            }
        val l3a =
            ViAgent.createLayer3Payment(
                finalPayment = finalPayment,
                l2BaseJwt = l2.baseJwt,
                routedL2Disclosures = listOf(l2.paymentDiscB64!!),
                nonce = paymentNonce,
                aud = "https://network.example",
                iat = now,
                exp = now + 300,
                iss = "https://agent.example",
                signer = agentSigner,
                agentKid = "agent-key-1",
            )

        val finalCheckout =
            buildJsonObject {
                put("vct", Vct.CHECKOUT_FINAL)
                put("checkout_jwt", checkoutJwt)
                put("checkout_hash", checkoutHash)
            }
        val l3b =
            ViAgent.createLayer3Checkout(
                finalCheckout = finalCheckout,
                l2BaseJwt = l2.baseJwt,
                routedL2Disclosures = listOf(l2.checkoutDiscB64!!),
                nonce = paymentNonce,
                aud = "https://merchant.example",
                iat = now,
                exp = now + 300,
                iss = "https://agent.example",
                signer = agentSigner,
                agentKid = "agent-key-1",
            )

        // An honest holder presents the full L2 (both mandates). A malicious agent can instead
        // selectively present only the checkout mandate — withholding the payment-open mandate that
        // carries the amount/payee constraints — while still routing the payment disclosure into L3a.
        val presentedL2 =
            if (withholdPaymentMandateFromL2) {
                selectivePresentation(l2.baseJwt, listOf(checkNotNull(l2.checkoutDiscB64)))
            } else {
                l2.compact
            }

        val paymentToken =
            if (omitL3Claim != null) {
                val parsed =
                    org.trustweave.credential.vi.crypto.ViSdJwt
                        .parse(l3a.compact)
                val jwt =
                    org.trustweave.credential.vi.crypto.Jws
                        .sign(parsed.header, JsonObject(parsed.payload - omitL3Claim), agentSigner)
                org.trustweave.credential.vi.crypto
                    .serializeSdJwt(jwt, parsed.disclosures)
            } else {
                l3a.compact
            }

        if (amount == 27_999 &&
            cardId == "pi-1" &&
            !includeCheckout &&
            omitL3Claim == null &&
            !omitInstrument &&
            budgetLedger == null &&
            !withholdPaymentMandateFromL2 &&
            !omitCheckoutKey &&
            !alterCheckoutKeyY &&
            recurrence == null &&
            merchantRecurrence == null
        ) {
            val export =
                buildJsonObject {
                    put(
                        "cases",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("name", "kotlin-autonomous-payment")
                                    put("profile", "autonomous-payment")
                                    put("l1", l1)
                                    put("l2", presentedL2)
                                    put("l3Payment", paymentToken)
                                    put("routedL2", l3a.routedL2)
                                    put("issuerJwk", issuerJwk)
                                    put("now", now + 60)
                                    put("aud", "https://agent.example")
                                    put("nonce", "l2-nonce")
                                    put("paymentAud", "https://network.example")
                                    put("paymentNonce", paymentNonce)
                                    put("expected", true)
                                },
                            ),
                        ),
                    )
                }
            val output =
                java.nio.file.Path
                    .of("build/reports/vi-kotlin-autonomous.json")
            java.nio.file.Files
                .createDirectories(output.parent)
            java.nio.file.Files
                .writeString(output, export.toString())
        }
        if (authenticatedCheckout && includeCheckout && merchantQuantity == 1 && budgetLedger == null && recurrence == null) {
            val export =
                buildJsonObject {
                    put(
                        "cases",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("name", "kotlin-autonomous-checkout")
                                    put("profile", "authenticated-checkout")
                                    put("l1", l1)
                                    put("l2", presentedL2)
                                    put("l3Payment", paymentToken)
                                    put("routedL2", l3a.routedL2)
                                    put("l3Checkout", l3b.compact)
                                    put("routedCheckout", l3b.routedL2)
                                    put("issuerJwk", issuerJwk)
                                    put("now", now + 60)
                                    put("aud", "https://agent.example")
                                    put("nonce", "l2-nonce")
                                    put("paymentAud", "https://network.example")
                                    put("paymentNonce", paymentNonce)
                                    put("checkoutAud", "https://merchant.example")
                                    put("checkoutNonce", paymentNonce)
                                    put("expected", true)
                                },
                            ),
                        ),
                    )
                }
            val output =
                java.nio.file.Path
                    .of("build/reports/vi-kotlin-checkout.json")
            java.nio.file.Files
                .createDirectories(output.parent)
            java.nio.file.Files
                .writeString(output, export.toString())
        }
        if (authenticatedCheckout && budgetLedger == null) {
            return VerifiableIntent.verifyChainWithCheckout(
                checkoutTrust = checkoutTrust,
                l1 = l1,
                l2 = presentedL2,
                issuerJwk = issuerJwk,
                l3Payment = paymentToken,
                expectedL3PaymentAud = "https://network.example",
                expectedL3PaymentNonce = paymentNonce,
                expectedL3CheckoutAud = "https://merchant.example",
                expectedL3CheckoutNonce = paymentNonce,
                l2RoutedForPayment = l3a.routedL2,
                l3Checkout = if (includeCheckout) l3b.compact else null,
                l2RoutedForCheckout = l3b.routedL2,
                now = now + 60,
                expectedL2Aud = "https://agent.example",
                expectedL2Nonce = "l2-nonce",
            )
        }
        if (budgetLedger != null) {
            fun authorize() =
                VerifiableIntent.verifyAndReserveBudget(
                    budgetLedger = budgetLedger,
                    checkoutTrust = if (authenticatedCheckout) checkoutTrust else null,
                    l1 = l1,
                    l2 = presentedL2,
                    issuerJwk = issuerJwk,
                    l3Payment = paymentToken,
                    expectedL3PaymentAud = "https://network.example",
                    expectedL3PaymentNonce = paymentNonce,
                    expectedL3CheckoutAud = "https://merchant.example",
                    expectedL3CheckoutNonce = paymentNonce,
                    l2RoutedForPayment = l3a.routedL2,
                    l3Checkout = if (includeCheckout) l3b.compact else null,
                    l2RoutedForCheckout = l3b.routedL2,
                    now = now + 60,
                    expectedL2Aud = "https://agent.example",
                    expectedL2Nonce = "l2-nonce",
                )
            if (replayBudget) authorize().valid shouldBe true
            return authorize()
        }
        return VerifiableIntent.verifyChain(
            l1 = l1,
            l2 = presentedL2,
            issuerJwk = issuerJwk,
            l3Payment = paymentToken,
            expectedL3PaymentAud = "https://network.example",
            expectedL3PaymentNonce = paymentNonce,
            expectedL3CheckoutAud = "https://merchant.example",
            expectedL3CheckoutNonce = paymentNonce,
            l2RoutedForPayment = l3a.routedL2,
            l3Checkout = if (includeCheckout) l3b.compact else null,
            l2RoutedForCheckout = l3b.routedL2,
            now = now + 60,
            expectedL2Aud = "https://agent.example",
            expectedL2Nonce = "l2-nonce",
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
    fun `L3 payment presented without its L2 payment mandate is rejected (constraint-bypass guard)`() {
        runBlocking {
            // H1: the agent withholds the payment-open mandate from the presented L2 (so the verifier
            // resolves payment == null), yet still presents an over-budget L3a payment plus a routed
            // L2 that includes the payment disclosure. Without a guard, amount_range / payee allowlist
            // / payment_instrument cross-check / pair-identity binding are all silently skipped and the
            // over-budget payment is accepted. The chain MUST be rejected.
            val result = runAutonomous(amount = 99_999, cardId = null, withholdPaymentMandateFromL2 = true)
            result.valid shouldBe false
            result.errors.joinToString() stringShouldContain "payment mandate"
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

            val l1 =
                ViIssuer.createLayer1(
                    IssuerCredential(
                        iss = "https://issuer.example",
                        sub = "user-1",
                        iat = now,
                        exp = now + 86_400,
                        userCnfJwk = userJwk,
                        panLastFour = "1234",
                        scheme = "Mastercard",
                    ),
                    issuerSigner,
                    issuerKid = "issuer-key-1",
                )

            // Immediate: finalized mandates; transaction_id == checkout_hash == SHA-256(checkout_jwt)
            val checkoutJwt = "eyJtZXJjaGFudCI6ImNoZWNrb3V0LXRva2VuIn0"
            val checkoutHash = sha256B64Url(checkoutJwt.toByteArray(Charsets.US_ASCII))
            val checkoutMandate =
                buildJsonObject {
                    put("vct", Vct.CHECKOUT_FINAL)
                    put("checkout_jwt", checkoutJwt)
                    put("checkout_hash", checkoutHash)
                }
            val paymentMandate =
                buildJsonObject {
                    put("vct", Vct.PAYMENT_FINAL)
                    put("transaction_id", checkoutHash)
                    put(
                        "payee",
                        buildJsonObject {
                            put("id", "m-1")
                            put("name", "Tennis Warehouse")
                            put("website", "https://tw.example")
                        },
                    )
                    put(
                        "payment_amount",
                        buildJsonObject {
                            put("currency", "USD")
                            put("amount", 27_999)
                        },
                    )
                    put(
                        "payment_instrument",
                        buildJsonObject {
                            put("type", "mastercard.srcDigitalCard")
                            put("id", "pi-1")
                        },
                    )
                }
            val l2 =
                ViUser.createLayer2Immediate(
                    l1Compact = l1,
                    checkoutMandate = checkoutMandate,
                    paymentMandate = paymentMandate,
                    nonce = "l2-nonce",
                    aud = "https://merchant.example",
                    iat = now,
                    exp = now + 900,
                    iss = "https://wallet.example",
                    signer = userSigner,
                    kid = "user-key-1",
                )

            val result =
                VerifiableIntent.verifyChain(
                    l1 = l1,
                    l2 = l2.compact,
                    issuerJwk = issuerJwk,
                    now = now + 60,
                    expectedL2Aud = "https://merchant.example",
                    expectedL2Nonce = "l2-nonce",
                )

            result.errors.shouldBeEmpty()
            result.valid shouldBe true
            result.checksPerformed shouldContain "l2_checkout_payment_binding"
            val export =
                buildJsonObject {
                    put(
                        "cases",
                        JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("name", "kotlin-immediate")
                                    put("l1", l1)
                                    put("l2", l2.compact)
                                    put("issuerJwk", issuerJwk)
                                    put("now", now + 60)
                                    put("aud", "https://merchant.example")
                                    put("nonce", "l2-nonce")
                                    put("expected", true)
                                },
                            ),
                        ),
                    )
                }
            val output =
                java.nio.file.Path
                    .of("build/reports/vi-kotlin-immediate.json")
            java.nio.file.Files
                .createDirectories(output.parent)
            java.nio.file.Files
                .writeString(output, export.toString())
        }
    }

    @Test
    fun `both fulfilments cannot bypass checkout constraints`() =
        runBlocking<Unit> {
            val result = runAutonomous(27_999, "pi-1", includeCheckout = true)
            result.valid shouldBe false
            result.errors.joinToString() stringShouldContain "authenticated cart binding"
        }

    @Test
    fun `signed L3 without expiration or issued time is rejected`() =
        runBlocking<Unit> {
            for (claim in listOf("iat", "exp")) {
                val result = runAutonomous(27_999, "pi-1", omitL3Claim = claim)
                result.valid shouldBe false
                result.errors.joinToString() stringShouldContain "integer $claim claim"
            }
        }

    @Test
    fun `payment instrument must be authorized by L2`() =
        runBlocking<Unit> {
            val result = runAutonomous(27_999, null, omitInstrument = true)
            result.valid shouldBe false
            result.errors.joinToString() stringShouldContain "missing authorized payment_instrument"
        }
}
