package org.trustweave.googlekms

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.core.CredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.kms.v1.KeyManagementServiceClient
import com.google.cloud.kms.v1.KeyManagementServiceSettings
import java.io.FileInputStream
import java.io.ByteArrayInputStream

/**
 * Factory for creating Google Cloud KMS clients.
 *
 * Handles authentication (service account JSON, Application Default Credentials) and client configuration.
 */
object GoogleKmsClientFactory {
    /**
     * Creates a Google Cloud KMS client from configuration.
     *
     * @param config Google Cloud KMS configuration
     * @return Configured KMS client
     */
    fun createClient(config: GoogleKmsConfig): KeyManagementServiceClient {
        val builder = KeyManagementServiceSettings.newBuilder()

        // Configure credentials
        val credentials = createCredentials(config)
        if (credentials != null) {
            builder.setCredentialsProvider(FixedCredentialsProvider.create(credentials) as CredentialsProvider)
        }

        // Configure endpoint override (for testing with emulator)
        config.endpoint?.let { endpoint ->
            builder.setEndpoint(endpoint)
        }

        return KeyManagementServiceClient.create(builder.build())
    }

    /**
     * Creates credentials based on configuration.
     *
     * Priority:
     * 1. credentialsJson (if provided)
     * 2. credentialsPath (if provided)
     * 3. Application Default Credentials (ADC)
     *
     * @param config Google Cloud KMS configuration
     * @return GoogleCredentials instance, or null to use ADC
     */
    private fun createCredentials(config: GoogleKmsConfig): GoogleCredentials? {
        // Use credentials JSON string if provided
        config.credentialsJson?.let { json ->
            return GoogleCredentials.fromStream(
                ByteArrayInputStream(json.toByteArray())
            )
        }

        // Use credentials file if provided
        config.credentialsPath?.let { path ->
            return GoogleCredentials.fromStream(FileInputStream(path))
        }

        // Otherwise, use Application Default Credentials (ADC)
        // This will be handled automatically by the client if no credentials provider is set
        return null
    }
}

