package org.trustweave.kms

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Outcome
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.core.telemetry.TelemetryEvent
import org.trustweave.core.telemetry.TelemetrySink
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TelemetryKeyManagementServiceBehaviourTest {
    private val recorded = CopyOnWriteArrayList<TelemetryEvent>()

    @AfterEach
    fun tearDown() {
        Telemetry.uninstall()
    }

    private fun events(operation: Operation) = recorded.filter { it.operation == operation }

    @Test
    fun `withTelemetry wraps once and is idempotent`() {
        val raw = InMemoryKeyManagementService()
        val wrapped = raw.withTelemetry()

        assertIs<TelemetryKeyManagementService>(wrapped)
        assertSame(wrapped, wrapped.withTelemetry())
    }

    @Test
    fun `successful generate and sign are reported as successes with the algorithm only`() =
        runBlocking {
            Telemetry.install(TelemetrySink { recorded += it })
            val kms = InMemoryKeyManagementService().withTelemetry()

            val handle = (kms.generateKey(Algorithm.Ed25519) as GenerateKeyResult.Success).keyHandle
            val signed = kms.sign(handle.id, "payload".encodeToByteArray())

            assertIs<SignResult.Success>(signed)
            val generated = events(Operation.KMS_GENERATE_KEY).single()
            assertEquals(Outcome.SUCCESS, generated.outcome)
            assertEquals(mapOf("kms.algorithm" to "Ed25519"), generated.attributes)
            val sign = events(Operation.KMS_SIGN).single()
            assertEquals(Outcome.SUCCESS, sign.outcome)
            assertEquals(mapOf("kms.algorithm" to "key-default"), sign.attributes)
            assertTrue(recorded.none { handle.id.value in it.attributes.values }, "key ids must not leak into events")
        }

    @Test
    fun `explicit sign algorithm is reported by name`() =
        runBlocking {
            Telemetry.install(TelemetrySink { recorded += it })
            val kms = InMemoryKeyManagementService().withTelemetry()
            val handle = (kms.generateKey(Algorithm.P256) as GenerateKeyResult.Success).keyHandle

            kms.sign(handle.id, byteArrayOf(1, 2, 3), Algorithm.P256)

            assertEquals("P-256", events(Operation.KMS_SIGN).single().attributes["kms.algorithm"])
        }

    @Test
    fun `a failed result is reported as rejected with the failure class and still returned`() =
        runBlocking {
            Telemetry.install(TelemetrySink { recorded += it })
            val kms = InMemoryKeyManagementService().withTelemetry()

            val result = kms.sign(KeyId("absent"), byteArrayOf(1))

            assertIs<SignResult.Failure.KeyNotFound>(result)
            val event = events(Operation.KMS_SIGN).single { it.outcome == Outcome.REJECTED }
            assertEquals("KeyNotFound", event.reason)
            assertTrue(events(Operation.KMS_SIGN).none { it.outcome == Outcome.FAILURE })
        }

    @Test
    fun `an unsupported algorithm on generate is reported as rejected`() =
        runBlocking {
            Telemetry.install(TelemetrySink { recorded += it })
            val kms = InMemoryKeyManagementService().withTelemetry()

            val result = kms.generateKey(Algorithm.Custom("quantum-x"))

            assertIs<GenerateKeyResult.Failure>(result)
            val event = events(Operation.KMS_GENERATE_KEY).single { it.outcome == Outcome.REJECTED }
            assertEquals(result::class.simpleName, event.reason)
        }

    @Test
    fun `a delegate that throws is reported as failure and the exception propagates`() {
        Telemetry.install(TelemetrySink { recorded += it })
        val exploding =
            object : KeyManagementService by InMemoryKeyManagementService() {
                override suspend fun sign(
                    keyId: KeyId,
                    data: ByteArray,
                    algorithm: Algorithm?,
                ): SignResult = throw IllegalStateException("hsm offline")
            }.withTelemetry()

        assertFailsWith<IllegalStateException> { runBlocking { exploding.sign(KeyId("k"), byteArrayOf()) } }

        val event = events(Operation.KMS_SIGN).single()
        assertEquals(Outcome.FAILURE, event.outcome)
        assertEquals("IllegalStateException", event.reason)
    }

    @Test
    fun `other operations pass straight through to the delegate`() =
        runBlocking {
            val kms = InMemoryKeyManagementService().withTelemetry()
            val handle = (kms.generateKey(Algorithm.Ed25519) as GenerateKeyResult.Success).keyHandle

            assertTrue(kms.getPublicKey(handle.id).isSuccess)
            assertTrue(kms.deleteKey(handle.id).wasDeleted)
            assertTrue(kms.getPublicKey(handle.id).isFailure)
        }
}
