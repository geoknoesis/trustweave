package org.trustweave.credential.didcomm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.secret.Secret
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver
import org.trustweave.credential.didcomm.crypto.rotation.KeyMetadata
import org.trustweave.credential.didcomm.crypto.rotation.KeyMetadataProvider
import org.trustweave.credential.didcomm.crypto.rotation.KeyRotationManager
import org.trustweave.credential.didcomm.crypto.rotation.TimeBasedRotationPolicy
import org.trustweave.credential.didcomm.crypto.secret.HybridKmsSecretResolver
import org.trustweave.credential.didcomm.crypto.secret.InMemoryLocalKeyStore
import org.trustweave.credential.didcomm.crypto.secret.KmsSecretResolver
import org.trustweave.credential.didcomm.crypto.secret.LocalKeyStore
import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.AttributeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.did.identifiers.Did
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class DidCommHardeningTest {
    // ---------------------------------------------------------------- replay store / guards

    private fun message(
        id: String,
        expires: Long? = null,
    ) = DidCommMessage(
        id = id,
        type = "https://didcomm.org/basicmessage/2.0/message",
        body = buildJsonObject { put("content", "hi") },
        expiresTime = expires?.toString(),
    )

    @Test
    fun `replayed message id is rejected`() =
        runBlocking<Unit> {
            val guards = DidCommReceiveGuards(nowEpochSeconds = { 1_000 })
            guards.check(message("m1"))
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("m1")) }
        }

    @Test
    fun `a full store refuses new messages instead of evicting unexpired ids`() =
        runBlocking<Unit> {
            var now = 1_000L
            val store = InMemoryDidCommReplayStore(capacity = 2)
            val guards = DidCommReceiveGuards(store, defaultRetentionSeconds = 100, maxRetentionSeconds = 100) { now }
            guards.check(message("a"))
            guards.check(message("b"))

            // Flooding with a fresh id must not push "a" out of the window.
            val e = shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("c")) }
            e.reason shouldContain "replay protection"
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("a")) }.reason shouldContain "already received"

            // Once the retention has lapsed, room is made by evicting only expired ids.
            now += 101
            guards.check(message("c"))
            store.size() shouldBe 1
        }

    @Test
    fun `ids are remembered until the message's own expires_time`() =
        runBlocking<Unit> {
            var now = 1_000L
            val store = InMemoryDidCommReplayStore(capacity = 10)
            val guards = DidCommReceiveGuards(store, defaultRetentionSeconds = 10, maxRetentionSeconds = 10_000) { now }
            guards.check(message("long", expires = 5_000), "did:example:alice")
            now = 2_000 // past the default retention, still before expires_time
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("long", expires = 5_000), "did:example:alice") }
        }

    @Test
    fun `a custom replay store is honoured`() =
        runBlocking<Unit> {
            val calls = mutableListOf<String>()
            val store =
                object : DidCommReplayStore {
                    override suspend fun recordIfAbsent(
                        messageId: String,
                        retainUntilEpochSeconds: Long,
                        nowEpochSeconds: Long,
                    ): Boolean {
                        calls += messageId
                        return !messageId.endsWith(":seen-elsewhere")
                    }
                }
            val guards = DidCommReceiveGuards(store)
            guards.check(message("fresh"))
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("seen-elsewhere")) }
            calls.map { it.substringAfterLast(":") } shouldBe listOf("fresh", "seen-elsewhere")
        }

    // ---------------------------------------------------------------- secret resolvers

    private fun secret(kid: String) =
        Secret(
            kid,
            VerificationMethodType.JSON_WEB_KEY_2020,
            VerificationMaterial(VerificationMaterialFormat.JWK, """{"kty":"OKP","crv":"X25519","x":"AA","d":"AA"}"""),
        )

    private class CountingStore(
        private val delegate: LocalKeyStore = InMemoryLocalKeyStore(),
    ) : LocalKeyStore by delegate {
        var gets = 0

        override suspend fun get(keyId: String): Secret? {
            gets++
            return delegate.get(keyId)
        }
    }

    @Test
    fun `preloaded secrets are served from cache without touching the store`() =
        runBlocking<Unit> {
            val store = CountingStore()
            store.store("did:key:a#k1", secret("did:key:a#k1"))
            val resolver = HybridKmsSecretResolver(store)
            resolver.preloadAll()
            val before = store.gets
            resolver.findKey("did:key:a#k1").isPresent shouldBe true
            store.gets shouldBe before
        }

    @Test
    fun `KMS-only kid resolves to absent and a cache miss still works`() =
        runBlocking<Unit> {
            val store = InMemoryLocalKeyStore()
            store.store("did:key:a#local", secret("did:key:a#local"))
            val resolver = KmsSecretResolver(InMemoryKeyManagementService(), { null }, store)
            resolver.findKey("did:key:a#local").isPresent shouldBe true
            resolver.findKey("did:key:a#unknown").isPresent shouldBe false
        }

    // ---------------------------------------------------------------- key rotation

    @Test
    fun `keys with unknown age are not rotated`() =
        runBlocking<Unit> {
            val store = InMemoryLocalKeyStore()
            store.store("k1", secret("k1"))
            val manager = KeyRotationManager(store, InMemoryKeyManagementService(), TimeBasedRotationPolicy(maxAgeDays = 90))
            manager.checkAndRotate().rotatedCount shouldBe 0
            store.list() shouldBe listOf("k1")
        }

    @Test
    fun `keys are rotated on real metadata, and freshly generated keys are not`() =
        runBlocking<Unit> {
            val store = InMemoryLocalKeyStore()
            store.store("k1", secret("k1"))
            val old = KeyMetadataProvider { id -> if (id == "k1") KeyMetadata(id, Clock.System.now().minus(100.days), null) else null }
            val manager = KeyRotationManager(store, InMemoryKeyManagementService(), TimeBasedRotationPolicy(maxAgeDays = 90), old)
            manager.checkAndRotate().rotatedCount shouldBe 1
            // Second pass: k1 is still old per the provider (rotated again is the provider's call),
            // but the generated key and the archived copy must not be rotated.
            val second = manager.checkAndRotate()
            second.results.none { it.oldKeyId.startsWith("archived-") || it.oldKeyId.contains("-v") } shouldBe true
        }

    // ---------------------------------------------------------------- predicates

    private fun findKey(
        element: JsonElement,
        key: String,
    ): JsonElement? =
        when (element) {
            is JsonObject -> element[key] ?: element.values.firstNotNullOfOrNull { findKey(it, key) }
            is JsonArray -> element.firstNotNullOfOrNull { findKey(it, key) }
            else -> null
        }

    private suspend fun requestProof(options: ExchangeOptions): JsonObject {
        val service =
            DidCommFactory.createInMemoryService(InMemoryKeyManagementService(), { null }, MapSecretResolver())
        val envelope =
            DidCommExchangeProtocol(service).requestProof(
                ProofExchangeRequest.Request(
                    protocolName = ExchangeProtocolName.DidComm,
                    verifierDid = Did("did:key:verifier"),
                    proverDid = Did("did:key:prover"),
                    proofRequest =
                        ProofRequest(
                            name = "age check",
                            requestedAttributes = mapOf("attr1" to AttributeRequest(name = "name")),
                            options = options,
                        ),
                ),
            )
        return envelope.messageData as JsonObject
    }

    @Test
    fun `requested predicates are passed through, not dropped`() =
        runBlocking<Unit> {
            val predicates =
                buildJsonObject {
                    put(
                        "pred1",
                        buildJsonObject {
                            put("name", "age")
                            put("p_type", ">=")
                            put("p_value", 18)
                        },
                    )
                }
            val data = requestProof(ExchangeOptions(metadata = mapOf("requestedPredicates" to predicates)))
            val pred = (findKey(data, "requested_predicates") as JsonObject)["pred1"] as JsonObject
            pred["name"]!!.jsonPrimitive.content shouldBe "age"
            pred["p_type"]!!.jsonPrimitive.content shouldBe ">="
            pred["p_value"]!!.jsonPrimitive.content shouldBe "18"
        }

    @Test
    fun `malformed predicates fail instead of being dropped`() =
        runBlocking<Unit> {
            val bad = buildJsonObject { put("pred1", buildJsonObject { put("name", "age") }) }
            shouldThrow<IllegalArgumentException> {
                requestProof(ExchangeOptions(metadata = mapOf("requestedPredicates" to bad)))
            }
            shouldThrow<IllegalArgumentException> {
                requestProof(ExchangeOptions(metadata = mapOf("requestedPredicates" to JsonPrimitive("x"))))
            }
        }
}
