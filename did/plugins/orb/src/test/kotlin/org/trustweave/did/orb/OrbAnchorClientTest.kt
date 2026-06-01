package org.trustweave.did.orb

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Tests the Orb operator-credit cost model surfaced through [OrbAnchorClient].
 */
class OrbAnchorClientTest {

    private lateinit var server: MockWebServer
    private lateinit var method: OrbDidMethod

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val config = OrbDidConfig(
            baseUrl = server.url("/").toString().trimEnd('/'),
            operatorId = "operator-test",
        )
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        method = OrbDidMethod(InMemoryKeyManagementService(), config, http)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `chainId derives from operatorId`() {
        assertEquals("orb:operator-test", method.anchorClient.chainId)
        assertEquals("operator-test", method.anchorClient.operatorId)
        assertEquals(AssetRef.OperatorCredit("operator-test"), method.anchorClient.asset)
    }

    @Test
    fun `chainId defaults to baseUrl host when operatorId is absent`() {
        val cfg = OrbDidConfig(baseUrl = "https://orb.example.com")
        assertEquals("orb.example.com", cfg.effectiveOperatorId)
        assertEquals("orb:orb.example.com", cfg.chainId)
    }

    @Test
    fun `estimate returns one operator credit regardless of operation kind`() = runBlocking<Unit> {
        val client = method.anchorClient
        val estimate = client.estimate(
            OperationDescriptor(kind = "did.create", chainId = client.chainId),
        )
        assertEquals(client.chainId, estimate.chainId)
        assertEquals(AssetRef.OperatorCredit("operator-test"), estimate.asset)
        assertEquals(BigInteger.ONE, estimate.amount)
    }

    @Test
    fun `writePayload returns one credit fee and operator payerAddress`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"didDocument":{"id":"did:orb:abc"}}"""),
        )
        val client = method.anchorClient
        val payload = JsonObject(mapOf("type" to JsonPrimitive("create")))
        val result = client.writePayload(payload, PaymentContext.unmanaged(client.chainId))

        assertEquals(BigInteger.ONE, result.fee?.amount)
        assertEquals(AssetRef.OperatorCredit("operator-test"), result.fee?.asset)
        assertEquals("operator:operator-test", result.payerAddress)
        assertEquals(client.chainId, result.ref.chainId)
    }

    @Test
    fun `writePayload enforces caller maxFee when denominated in OperatorCredit`() = runBlocking<Unit> {
        val client = method.anchorClient
        val ctx = PaymentContext(
            domainId = "domain-1",
            payerDid = "did:example:domain-1",
            chainId = client.chainId,
            feeStrategy = FeeStrategy.DomainPays,
            maxFee = TokenAmount(client.chainId, AssetRef.OperatorCredit("operator-test"), BigInteger.ZERO),
        )
        val payload = JsonObject(mapOf("type" to JsonPrimitive("create")))
        val ex = assertFailsWith<TreasuryException.CallerCapExceeded> {
            client.writePayload(payload, ctx)
        }
        assertEquals(client.chainId, ex.chainId)
        assertEquals(BigInteger.ONE, ex.estimated.amount)
        // No HTTP request was issued.
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `writePayload skips cap enforcement for unmanaged ctx`() = runBlocking<Unit> {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"didDocument":{"id":"did:orb:xyz"}}"""),
        )
        val client = method.anchorClient
        val payload = JsonObject(mapOf("type" to JsonPrimitive("create")))
        val result = client.writePayload(payload, PaymentContext.unmanaged(client.chainId))
        assertIs<TokenAmount>(result.fee)
    }
}
