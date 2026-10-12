package org.trustweave.did.spi

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DidMethodProviderDefaultsBehaviourTest {
    private class Provider(
        override val requiredEnvironmentVariables: List<String>,
    ) : DidMethodProvider {
        override val name = "p"
        override val supportedMethods = listOf("p")

        override fun create(
            methodName: String,
            options: DidCreationOptions,
        ): DidMethod? = null
    }

    private val unset = "TRUSTWEAVE_COV_SURELY_UNSET_VARIABLE"

    @Test
    fun `priority defaults to zero and no variables are required by default`() {
        val provider = Provider(emptyList())
        assertEquals(0, provider.priority)
        assertTrue(provider.hasRequiredEnvironmentVariables())
    }

    @Test
    fun `a required unset variable fails the check`() {
        assertFalse(Provider(listOf(unset)).hasRequiredEnvironmentVariables())
    }

    @Test
    fun `an optional unset variable is ignored`() {
        assertTrue(Provider(listOf("?$unset")).hasRequiredEnvironmentVariables())
    }

    @Test
    fun `one missing required variable among optional ones still fails`() {
        assertFalse(Provider(listOf("?A_$unset", unset)).hasRequiredEnvironmentVariables())
    }

    @Test
    fun `a variable that is set satisfies the requirement`() {
        val existing = System.getenv().keys.firstOrNull() ?: return
        assertTrue(Provider(listOf(existing)).hasRequiredEnvironmentVariables())
    }
}
