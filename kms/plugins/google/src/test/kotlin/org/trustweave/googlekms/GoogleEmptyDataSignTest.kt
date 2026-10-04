package org.trustweave.googlekms

import com.google.api.gax.core.NoCredentialsProvider
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyManagementServiceSettings
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.SignResult
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GoogleEmptyDataSignTest {
    /** A client pointed at a closed local port with no credentials: any attempt to use it would fail. */
    private fun unreachableClient(): KeyManagementServiceClient =
        KeyManagementServiceClient.create(
            KeyManagementServiceSettings
                .newBuilder()
                .setEndpoint("127.0.0.1:1")
                .setCredentialsProvider(NoCredentialsProvider.create())
                .setTransportChannelProvider(
                    KeyManagementServiceSettings
                        .defaultGrpcTransportProviderBuilder()
                        .setChannelConfigurator { it.usePlaintext() }
                        .build(),
                ).build(),
        )

    @Test
    fun `empty data is rejected locally with a message naming the Google constraint`() =
        runBlocking<Unit> {
            unreachableClient().use { client ->
                val kms = GoogleCloudKeyManagementService(GoogleKmsConfig(projectId = "p", location = "global", keyRing = "r"), client)

                val failure = assertIs<SignResult.Failure.Error>(kms.sign(KeyId("k"), ByteArray(0)))

                assertTrue("Google Cloud KMS" in failure.reason, failure.reason)
                assertTrue("non-empty" in failure.reason, failure.reason)
                assertTrue("INVALID_ARGUMENT" in failure.reason, failure.reason)
            }
        }
}
