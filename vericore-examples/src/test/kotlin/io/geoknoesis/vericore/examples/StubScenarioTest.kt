package io.geoknoesis.vericore.examples

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Base test class for stub scenario examples.
 * Verifies that stub examples execute without errors.
 */
abstract class StubScenarioTest {
    abstract fun runScenario()
    
    @Test
    fun `test scenario executes without errors`() {
        runBlocking {
            runScenario()
        }
    }
}

