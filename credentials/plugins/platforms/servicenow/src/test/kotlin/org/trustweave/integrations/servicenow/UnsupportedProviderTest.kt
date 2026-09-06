package org.trustweave.integrations.servicenow

import kotlinx.coroutines.runBlocking
import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UnsupportedProviderTest {
    @Test
    fun `placeholder never fabricates issuance or verification success`() =
        runBlocking<Unit> {
            val provider = ServiceNowIntegration("https://example.test", "user", "password")
            assertFailsWith<TrustWeaveException> { provider.issueCredential("users", "record", "Employee") }
            assertFailsWith<TrustWeaveException> { provider.verifyCredential("record") }
        }
}
