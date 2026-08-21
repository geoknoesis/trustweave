package org.trustweave.did.registry

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which provider wins when more than one claims a DID method.
 *
 * `ServiceLoader` returns providers in classpath order, so taking the first one made the outcome
 * depend on jar ordering. That matters concretely: testkit registers a provider for `key` whose
 * `DidKeyMockMethod` mints random-UUID identifiers rather than real self-certifying did:keys, so on
 * a classpath holding both testkit and `did:plugins:key` the DIDs produced could differ between
 * runs with nothing in the code changing.
 */
class DidMethodProviderSelectionTest {
    private class FakeProvider(
        override val name: String,
        override val priority: Int,
    ) : DidMethodProvider {
        override val supportedMethods: List<String> = listOf("key")
        override val requiredEnvironmentVariables: List<String> = emptyList()

        override fun create(
            methodName: String,
            options: DidCreationOptions,
        ): DidMethod? = null
    }

    @Test
    fun `the higher priority provider wins regardless of discovery order`() {
        val real = FakeProvider("real", priority = 0)
        val mock = FakeProvider("mock", priority = -100)

        assertEquals("real", selectProviderFor(listOf(mock, real))?.name)
        assertEquals("real", selectProviderFor(listOf(real, mock))?.name)
    }

    @Test
    fun `equal priorities resolve the same way whichever order they arrive in`() {
        val a = FakeProvider("alpha", priority = 0)
        val b = FakeProvider("beta", priority = 0)

        // Not a preference for either, only a guarantee that the answer does not move with jar
        // ordering — a build that reorders its classpath must not silently change DID behaviour.
        assertEquals(
            selectProviderFor(listOf(a, b))?.name,
            selectProviderFor(listOf(b, a))?.name,
        )
    }

    @Test
    fun `no providers yields nothing`() {
        assertEquals(null, selectProviderFor(emptyList()))
    }
}
