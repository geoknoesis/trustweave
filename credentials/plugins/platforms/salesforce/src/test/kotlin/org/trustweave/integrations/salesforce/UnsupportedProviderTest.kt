package org.trustweave.integrations.salesforce

import kotlinx.coroutines.runBlocking
import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UnsupportedProviderTest {
    @Test
    fun `placeholder never fabricates issuance or verification success`() =
        runBlocking<Unit> {
            val provider = SalesforceIntegration("https://example.test", "id", "secret", "user", "password")
            assertFailsWith<TrustWeaveException> { provider.issueCredential("Contact", "record", "Employee") }
            assertFailsWith<TrustWeaveException> { provider.verifyCredential("record") }
        }
}
