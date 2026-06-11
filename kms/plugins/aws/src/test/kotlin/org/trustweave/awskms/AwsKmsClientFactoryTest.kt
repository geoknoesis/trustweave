package org.trustweave.awskms

import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AwsKmsClientFactoryTest {

    @Test
    fun `session token is propagated via AwsSessionCredentials`() {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .accessKeyId("AKIAIOSFODNN7EXAMPLE")
            .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
            .sessionToken("FwoGZXIvYXdzEXAMPLESESSIONTOKEN")
            .build()

        val provider = AwsKmsClientFactory.createCredentialsProvider(config)

        assertTrue(provider is StaticCredentialsProvider, "Static keys must use StaticCredentialsProvider")
        val credentials = provider.resolveCredentials()
        assertTrue(
            credentials is AwsSessionCredentials,
            "Temporary credentials with a session token must resolve to AwsSessionCredentials — " +
                "AWS rejects STS access keys presented without their session token"
        )
        credentials as AwsSessionCredentials
        assertEquals("AKIAIOSFODNN7EXAMPLE", credentials.accessKeyId())
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", credentials.secretAccessKey())
        assertEquals("FwoGZXIvYXdzEXAMPLESESSIONTOKEN", credentials.sessionToken())
    }

    @Test
    fun `static keys without session token resolve to basic credentials`() {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            .accessKeyId("AKIAIOSFODNN7EXAMPLE")
            .secretAccessKey("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")
            .build()

        val provider = AwsKmsClientFactory.createCredentialsProvider(config)

        assertTrue(provider is StaticCredentialsProvider)
        val credentials = provider.resolveCredentials()
        assertTrue(credentials is AwsBasicCredentials, "No session token means plain basic credentials")
        assertEquals("AKIAIOSFODNN7EXAMPLE", credentials.accessKeyId())
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", credentials.secretAccessKey())
    }

    @Test
    fun `missing static keys fall back to the default credential provider chain`() {
        val config = AwsKmsConfig.builder()
            .region("us-east-1")
            // A session token alone (no access key/secret) must NOT trigger static credentials.
            .sessionToken("FwoGZXIvYXdzEXAMPLESESSIONTOKEN")
            .build()

        val provider = AwsKmsClientFactory.createCredentialsProvider(config)

        assertTrue(
            provider !is StaticCredentialsProvider,
            "Without both accessKeyId and secretAccessKey the default chain must be used"
        )
    }
}
