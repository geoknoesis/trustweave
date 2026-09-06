package org.trustweave.integrations.venafi

import kotlinx.coroutines.runBlocking
import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UnsupportedProviderTest {
    @Test
    fun `placeholder never fabricates issuance or verification success`() =
        runBlocking<Unit> {
            val provider = VenafiIntegration("https://example.test", "secret")
            assertFailsWith<TrustWeaveException> { provider.issueCredentialWithCertificate("certificate", "Employee") }
        }
}
