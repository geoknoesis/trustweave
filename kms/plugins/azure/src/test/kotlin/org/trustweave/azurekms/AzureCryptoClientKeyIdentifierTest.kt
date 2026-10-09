package org.trustweave.azurekms

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.security.keyvault.keys.cryptography.CryptographyClientBuilder
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

/** Documents what the Azure SDK accepts as a cryptography-client key identifier. */
class AzureCryptoClientKeyIdentifierTest {
    private val credential = TokenCredential { Mono.just(AccessToken("t", java.time.OffsetDateTime.now().plusHours(1))) }

    @Test
    fun `identifier built by the service is accepted by the SDK`() {
        for (resolved in listOf("mykey", "mykey/abc123")) {
            val id = AlgorithmMapping.toKeyIdentifierUrl("https://v.vault.azure.net/", resolved)
            assertEquals("https://v.vault.azure.net/keys/$resolved", id)
            CryptographyClientBuilder().keyIdentifier(id).credential(credential).buildClient()
        }
    }

    @Test
    fun `bare key name is not a valid keyIdentifier but the full url is`() {
        val bare = runCatching { CryptographyClientBuilder().keyIdentifier("mykey").credential(credential).buildClient() }
        val full =
            runCatching {
                CryptographyClientBuilder()
                    .keyIdentifier("https://v.vault.azure.net/keys/mykey/abc")
                    .credential(credential)
                    .buildClient()
            }
        assertEquals(true, bare.isFailure)
        assertEquals(true, full.isSuccess)
    }
}
