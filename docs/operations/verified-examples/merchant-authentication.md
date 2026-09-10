# Pinned merchant authentication

This complete SDK test pins a merchant key, identity and challenge. It exercises signed cart extraction, invalid lifetime/challenges, wrong keys and agent shadow data. The verifier provisions CheckoutTrust out of band; never derive trust from the presented token. Run `:credentials:plugins:verifiable-intent:test --tests "*CheckoutTrustTest"`. The helper tests an internal authentication boundary; applications call the public `verifyChainWithCheckout` API.

The source block is an exact copy of a compiled SDK file. Documentation CI checks synchronization, and required test gates check execution.

<!-- example-source: credentials/plugins/verifiable-intent/src/test/kotlin/org/trustweave/credential/vi/CheckoutTrustTest.kt -->
```kotlin
package org.trustweave.credential.vi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.crypto.Jws
import org.trustweave.credential.vi.crypto.KmsEs256Signer
import org.trustweave.credential.vi.crypto.sha256B64Url
import org.trustweave.credential.vi.verification.CheckoutTrust
import org.trustweave.kms.Algorithm
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.trustweave.kms.results.GenerateKeyResult

class CheckoutTrustTest {
    private val merchant = Json.parseToJsonElement("""{"id":"merchant-1"}""") as JsonObject
    private val cart = Json.parseToJsonElement("""{"items":[{"id":"item-1","quantity":1}]}""") as JsonObject
    private val now = 1_780_000_000L

    @Test
    fun `authenticated cart and merchant replace absent agent assertions`() =
        runBlocking<Unit> {
            val (policy, fulfillment) = fixture()
            val authenticated = policy.authenticatedFulfillment(fulfillment, now, 0)
            assertEquals(cart["items"], authenticated["line_items"])
            assertEquals(merchant, authenticated["merchant"])
        }

    @Test
    fun `signed tokens with invalid challenge or lifetime fail closed`() =
        runBlocking<Unit> {
            val mutations =
                listOf(
                    "iss" to JsonPrimitive("attacker"),
                    "aud" to JsonPrimitive("other"),
                    "nonce" to JsonPrimitive("other"),
                    "iat" to JsonPrimitive(now + 1),
                    "exp" to JsonPrimitive(now),
                    "exp" to JsonPrimitive(now + 3601),
                    "iat" to JsonPrimitive(now.toString()),
                )
            for ((name, value) in mutations) {
                val (policy, fulfillment) = fixture(mapOf(name to value))
                assertThrows(IllegalArgumentException::class.java) { policy.authenticatedFulfillment(fulfillment, now, 0) }
            }
        }

    @Test
    fun `wrong key hash and agent shadow cart are rejected`() =
        runBlocking<Unit> {
            val (policy, fulfillment) = fixture()
            val (_, otherSignature) = fixture()
            val mutations =
                listOf(
                    otherSignature,
                    JsonObject(fulfillment + ("checkout_hash" to JsonPrimitive("wrong"))),
                    JsonObject(fulfillment + ("line_items" to Json.parseToJsonElement("[]"))),
                    JsonObject(fulfillment + ("merchant" to Json.parseToJsonElement("""{"id":"other"}"""))),
                )
            for (mutation in mutations) {
                assertThrows(IllegalArgumentException::class.java) { policy.authenticatedFulfillment(mutation, now, 0) }
            }
        }

    private suspend fun fixture(overrides: Map<String, JsonPrimitive> = emptyMap()): Pair<CheckoutTrust, JsonObject> {
        val kms = InMemoryKeyManagementService()
        val key = (kms.generateKey(Algorithm.P256) as GenerateKeyResult.Success).keyHandle
        val publicKey =
            buildJsonObject {
                for (name in listOf("kty", "crv", "x", "y")) put(name, key.publicKeyJwk!![name] as String)
            }
        val policy = CheckoutTrust("merchant", "verifier", "challenge", publicKey, merchant)
        val claims =
            buildJsonObject {
                put("iss", "merchant")
                put("aud", "verifier")
                put("nonce", "challenge")
                put("iat", now)
                put("exp", now + 300)
                put("cart", cart)
            }
        val token =
            Jws.sign(
                buildJsonObject {
                    put("alg", "ES256")
                    put("typ", "JWT")
                },
                JsonObject(claims + overrides),
                KmsEs256Signer(kms, key.id),
            )
        return policy to
            buildJsonObject {
                put("checkout_jwt", token)
                put("checkout_hash", sha256B64Url(token.toByteArray(Charsets.US_ASCII)))
            }
    }
}
```

[All verified examples](README.md) ? [Testing acceptance](../../contributing/testing/acceptance.md)
