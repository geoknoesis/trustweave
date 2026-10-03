@file:Suppress("DEPRECATION")

package org.trustweave.integrations.servicenow

import kotlinx.coroutines.runBlocking
import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnsupportedProviderTest {
    @Test
    fun `placeholder never fabricates issuance or verification success`() =
        runBlocking<Unit> {
            val provider = ServiceNowIntegration("https://example.test", "user", "password")
            val issue = assertFailsWith<TrustWeaveException.InvalidOperation> { provider.issueCredential("users", "record", "Employee") }
            assertEquals("SERVICENOW_INTEGRATION_NOT_IMPLEMENTED", issue.code)
            val verify = assertFailsWith<TrustWeaveException.InvalidOperation> { provider.verifyCredential("record") }
            assertEquals("verifyCredential", verify.context["operation"])
        }
}
