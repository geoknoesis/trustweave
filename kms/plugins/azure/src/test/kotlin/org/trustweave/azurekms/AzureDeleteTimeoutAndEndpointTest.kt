package org.trustweave.azurekms

import com.azure.core.credential.AccessToken
import com.azure.core.credential.TokenCredential
import com.azure.security.keyvault.keys.KeyClientBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Delete timeout reporting and endpoint-override use for the signing client. */
class AzureDeleteTimeoutAndEndpointTest {
    private val credential =
        TokenCredential {
            Mono.just(
                AccessToken(
                    "t",
                    java.time.OffsetDateTime
                        .now()
                        .plusHours(1),
                ),
            )
        }

    @Test
    fun `a timed-out wait is reported as false not thrown as cancellation`() =
        runBlocking<Unit> {
            val result = runCatching { awaitCompletion(50) { Thread.sleep(5_000) } }
            assertEquals(false, result.getOrNull(), "got $result")
        }

    @Test
    fun `a completed wait is true`() =
        runBlocking<Unit> {
            assertTrue(awaitCompletion(5_000) { })
        }

    @Test
    fun `cancellation of the caller still propagates`() =
        runBlocking<Unit> {
            val started = CountDownLatch(1)
            val job =
                async(Dispatchers.Default) {
                    awaitCompletion(60_000) {
                        started.countDown()
                        Thread.sleep(30_000)
                    }
                }
            withContext(Dispatchers.IO) { started.await() }
            job.cancel()
            assertFailsWith<CancellationException> { job.await() }
        }

    @Test
    fun `signing key identifier uses the endpoint override`() {
        val config =
            AzureKmsConfig
                .builder()
                .vaultUrl("https://testvault.vault.azure.net")
                .endpointOverride("https://localhost:8443")
                .build()
        val client =
            KeyClientBuilder()
                .vaultUrl(
                    "https://localhost:8443",
                ).credential(credential)
                .disableChallengeResourceVerification()
                .buildClient()
        val kms = AzureKeyManagementService(config, client)
        assertEquals("https://localhost:8443/keys/mykey/abc", kms.cryptoKeyIdentifier("mykey/abc"))
    }

    @Test
    fun `signing key identifier uses the vault url without an override`() {
        val config = AzureKmsConfig.builder().vaultUrl("https://testvault.vault.azure.net").build()
        val client = KeyClientBuilder().vaultUrl("https://testvault.vault.azure.net").credential(credential).buildClient()
        val kms = AzureKeyManagementService(config, client)
        assertFalse(kms.cryptoKeyIdentifier("mykey").contains("localhost"))
        assertEquals("https://testvault.vault.azure.net/keys/mykey", kms.cryptoKeyIdentifier("mykey"))
    }
}
