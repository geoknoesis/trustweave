package org.trustweave.did.registry

import kotlinx.coroutines.test.runTest
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.telemetry.TelemetryDidMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives [DidMethodRegistry.autoRegister] against the providers declared in
 * `src/test/resources/META-INF/services` (see CovSpiProviders.kt).
 */
class DidMethodRegistryAutoRegisterTest {
    @Test
    fun `working providers are registered`() =
        runTest {
            val result = DidMethodRegistry.autoRegister()
            val method = assertNotNull(result.registry["covok"])
            assertEquals("did:covok:ok", method.createDid().id.value)
        }

    @Test
    fun `creation options are passed through to the provider`() =
        runTest {
            val options = DidCreationOptions(additionalProperties = mapOf("marker" to 42))
            DidMethodRegistry.autoRegister(options = options)
            assertEquals(42, CovSpiProbe.lastOptions.get()!!.additionalProperties["marker"])
        }

    @Test
    fun `a provider returning null is reported as a create failure and not registered`() =
        runTest {
            val result = DidMethodRegistry.autoRegister()
            assertNull(result.registry["covnull"])
            val failure = result.failures.single { "covnull" in it.message }
            assertEquals("create", failure.phase)
            assertTrue(failure.message.contains("returned null"))
            assertNull(failure.cause)
        }

    @Test
    fun `a throwing provider is isolated and its cause is kept`() =
        runTest {
            val result = DidMethodRegistry.autoRegister()
            assertNull(result.registry["covthrow"])
            val failure = result.failures.single { "covthrow" in it.message }
            assertEquals("create", failure.phase)
            assertTrue(failure.message.contains("boom"))
            assertIs<IllegalStateException>(failure.cause)
            // The failure of one provider must not stop others from registering.
            assertNotNull(result.registry["covok"])
        }

    @Test
    fun `a provider missing required environment variables is skipped with an environment failure`() =
        runTest {
            val result = DidMethodRegistry.autoRegister()
            assertNull(result.registry["covenv"])
            val failure = result.failures.single { "covenv" in it.message }
            assertEquals("environment", failure.phase)
            assertTrue(failure.message.contains(CovMissingEnvProvider::class.java.name))
        }

    @Test
    fun `the highest priority provider wins when several claim a method`() =
        runTest {
            val result = DidMethodRegistry.autoRegister()
            assertEquals(
                "did:covdup:high",
                result.registry["covdup"]!!
                    .createDid()
                    .id.value,
            )
            // Losing a priority contest is not a failure.
            assertTrue(result.failures.none { "covdup" in it.message })
        }

    @Test
    fun `result exposes failures and hasFailures consistently`() =
        runTest {
            val result = DidMethodRegistry.autoRegister()
            assertTrue(result.hasFailures)
            assertEquals(setOf("create", "environment"), result.failures.map { it.phase }.toSet())
            assertFalse(DidMethodRegistryAutoRegisterResult(DidMethodRegistry(), emptyList()).hasFailures)
            val registered = result.registry["covok"]
            assertIs<TelemetryDidMethod>(registered)
        }
}
