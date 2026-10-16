package org.trustweave.credential.results

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.credential.format.ProofSuiteId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultipleFailuresErrorsTest {
    private fun adapterError(vararg messages: String) =
        IssuanceResult.Failure.AdapterError(
            format = ProofSuiteId.VC_LD,
            reason = "adapter failed",
            errors = messages.toList(),
        )

    @Test
    fun `the convenience constructor reports each nested error once`() {
        val result = IssuanceResult.Failure.MultipleFailures(listOf(adapterError("a", "b"), adapterError("c")))

        assertEquals(listOf("a", "b", "c"), result.allErrors)
    }

    @Test
    fun `an error listed only on the result is kept after the nested ones`() {
        val result =
            IssuanceResult.Failure.MultipleFailures(
                failures = listOf(adapterError("a")),
                errors = listOf("extra"),
            )

        assertEquals(listOf("a", "extra"), result.allErrors)
    }

    @Test
    fun `an extra error that repeats a nested message is still reported`() {
        val result =
            IssuanceResult.Failure.MultipleFailures(
                failures = listOf(adapterError("a")),
                errors = listOf("a", "a"),
            )

        assertEquals(listOf("a", "a"), result.allErrors)
    }

    @Test
    fun `getOrThrow names every error once, including with the primary constructor`() {
        val convenience = IssuanceResult.Failure.MultipleFailures(listOf(adapterError("first"), adapterError("second")))
        val primary = IssuanceResult.Failure.MultipleFailures(failures = listOf(adapterError("first"), adapterError("second")))

        listOf(convenience, primary).forEach { failure ->
            val message = assertThrows<IllegalStateException> { failure.getOrThrow() }.message.orEmpty()

            assertEquals(1, Regex("first").findAll(message).count(), message)
            assertEquals(1, Regex("second").findAll(message).count(), message)
            assertTrue(message.contains("first; second"), message)
        }
    }
}
