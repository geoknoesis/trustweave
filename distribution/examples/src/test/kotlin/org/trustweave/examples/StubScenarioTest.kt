package org.trustweave.examples

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Minimal smoke-test base for scenarios that are not yet fully implemented.
 *
 * Subclasses should override [assertScenarioResult] once the scenario has a real
 * implementation and domain objects to assert against.  Until then this base class
 * guards against accidental compilation or runtime regressions.
 *
 * **Upgrade path:** When a scenario is ready, replace the subclass with a concrete
 * test class that calls the scenario functions directly and asserts domain-level results
 * (e.g. verifiable credential issuers, DID values, collection contents).
 */
@Tag("stub-scenario")
abstract class StubScenarioTest {

    /**
     * Execute the scenario under test.
     * Must not throw, and must complete within the test timeout.
     */
    abstract fun runScenario()

    /**
     * Optional hook: subclasses can override to add domain-specific assertions.
     * Default implementation does nothing (smoke-test only).
     */
    open fun assertScenarioResult() = Unit

    @Test
    fun `scenario scaffold executes without errors`() {
        runBlocking {
            runScenario()
        }
        assertScenarioResult()
    }
}

