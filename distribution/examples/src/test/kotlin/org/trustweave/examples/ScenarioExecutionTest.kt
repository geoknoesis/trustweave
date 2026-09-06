package org.trustweave.examples

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Runs each local scenario and propagates its assertion failures to JUnit.
 * The small signed-claim scenarios assert verification, JSON round-trip and tamper rejection;
 * larger scenario programs have their own, varying assertion coverage.
 */
@Tag("local-scenario")
abstract class ScenarioExecutionTest {
    abstract fun runScenario()

    @Test
    @Timeout(30)
    fun `scenario checks complete successfully`() {
        runScenario()
    }
}
