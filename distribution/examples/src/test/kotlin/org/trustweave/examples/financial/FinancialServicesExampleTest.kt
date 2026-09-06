package org.trustweave.examples.financial

import org.junit.jupiter.api.Test
import org.trustweave.examples.ScenarioExecutionTest

/**
 * Unit tests for Financial Services KYC scenario.
 *
 * Verifies that the stub scenario executes without errors.
 */
class FinancialServicesExampleTest : ScenarioExecutionTest() {
    override fun runScenario() {
        main()
    }

    @Test
    fun `test scenario prints expected output`() {
        // Scenario should print message about coming soon
        runScenario()
    }
}
