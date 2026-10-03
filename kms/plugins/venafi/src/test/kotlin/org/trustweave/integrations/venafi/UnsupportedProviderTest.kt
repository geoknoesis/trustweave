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

class VenafiRedactionTest {
    @Test
    fun `toString redacts the api key`() {
        val text = VenafiIntegration("https://example.test", "VENAFI-SECRET").toString()
        kotlin.test.assertFalse("VENAFI-SECRET" in text, text)
        kotlin.test.assertTrue("<redacted>" in text, text)
    }

    @Test
    fun `blank credentials are rejected`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { VenafiIntegration("https://example.test", " ") }
        kotlin.test.assertFailsWith<IllegalArgumentException> { VenafiIntegration(" ", "key") }
    }
}
