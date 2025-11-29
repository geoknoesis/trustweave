package com.trustweave.examples.financial

import com.trustweave.examples.StubScenarioTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for Financial Services KYC scenario.
 *
 * Verifies that the stub scenario executes without errors.
 */
class FinancialServicesExampleTest : StubScenarioTest() {

    override fun runScenario() {
        main()
    }

    @Test
    fun `test scenario prints expected output`() {
        // Scenario should print message about coming soon
        runScenario()
    }
}

