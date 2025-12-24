package org.trustweave.awskms

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.KmsClientBuilder
import java.io.Closeable
import java.net.URI

/**
 * Factory for creating AWS KMS clients.
 *
 * Handles authentication (IAM roles or access keys) and client configuration.
 */
object AwsKmsClientFactory {
    /**
     * Creates an AWS KMS client from configuration.
     *
     * @param config AWS KMS configuration
     * @return Configured KMS client
     */
    fun createClient(config: AwsKmsConfig): KmsClient {
        val builder = KmsClient.builder()
            .region(Region.of(config.region))

        // Configure credentials
        val credentialsProvider = createCredentialsProvider(config)
        builder.credentialsProvider(credentialsProvider)

        // Configure endpoint override (for LocalStack testing)
        config.endpointOverride?.let { endpoint ->
            builder.endpointOverride(URI.create(endpoint))
        }

        return builder.build()
    }

    /**
     * Creates credentials provider based on configuration.
     *
     * If access key and secret are provided, uses static credentials.
     * Otherwise, uses default credential provider chain (IAM roles, environment, etc.).
     *
     * @param config AWS KMS configuration
     * @return Credentials provider
     */
    private fun createCredentialsProvider(config: AwsKmsConfig): AwsCredentialsProvider {
        return if (config.accessKeyId != null && config.secretAccessKey != null) {
            val credentials = if (config.sessionToken != null) {
                AwsBasicCredentials.create(
                    config.accessKeyId,
                    config.secretAccessKey
                )
            } else {
                AwsBasicCredentials.create(
                    config.accessKeyId,
                    config.secretAccessKey
                )
            }
            StaticCredentialsProvider.create(credentials)
        } else {
            // Use default credential provider chain (IAM roles, environment variables, etc.)
            DefaultCredentialsProvider.create()
        }
    }
}

