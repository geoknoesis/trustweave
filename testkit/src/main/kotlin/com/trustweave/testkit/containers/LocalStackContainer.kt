package com.trustweave.testkit.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * TestContainer for LocalStack (AWS services emulator).
 *
 * Provides local AWS services including KMS, S3, and other services for testing.
 *
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class AwsKmsIntegrationTest {
 *     companion object {
 *         @JvmStatic
 *         val localStack = LocalStackContainer.create()
 *     }
 *
 *     @Test
 *     fun testWithLocalStack() {
 *         val kmsEndpoint = localStack.getKmsEndpoint()
 *         // Use endpoint for AWS KMS testing
 *     }
 * }
 * ```
 */
class LocalStackContainer private constructor(
    dockerImageName: DockerImageName
) : GenericContainer<LocalStackContainer>(dockerImageName) {

    companion object {
        /**
         * Default LocalStack Docker image.
         */
        private val DEFAULT_IMAGE = DockerImageName.parse("localstack/localstack:latest")

        /**
         * Creates a LocalStack container with default configuration.
         */
        @JvmStatic
        fun create(): LocalStackContainer {
            return LocalStackContainer(DEFAULT_IMAGE)
                .withEnv("SERVICES", "kms,s3")
                .withEnv("DEBUG", "1")
                .withEnv("DOCKER_HOST", "unix:///var/run/docker.sock")
                .waitingFor(Wait.forLogMessage(".*Ready\\.\\n", 1))
                .withStartupTimeout(Duration.ofMinutes(2))
        }

        /**
         * Creates a LocalStack container with custom services.
         */
        @JvmStatic
        fun create(services: List<String>): LocalStackContainer {
            return LocalStackContainer(DEFAULT_IMAGE)
                .withEnv("SERVICES", services.joinToString(","))
                .withEnv("DEBUG", "1")
                .withEnv("DOCKER_HOST", "unix:///var/run/docker.sock")
                .waitingFor(Wait.forLogMessage(".*Ready\\.\\n", 1))
                .withStartupTimeout(Duration.ofMinutes(2))
        }
    }

    /**
     * Services available in LocalStack.
     */
    object Services {
        const val KMS = "kms"
        const val S3 = "s3"
        const val IAM = "iam"
        const val LAMBDA = "lambda"
        const val DYNAMODB = "dynamodb"
    }

    /**
     * Gets the KMS endpoint URL.
     */
    fun getKmsEndpoint(): String {
        return "http://${host}:${getMappedPort(4566)}"
    }

    /**
     * Gets the S3 endpoint URL.
     */
    fun getS3Endpoint(): String {
        return "http://${host}:${getMappedPort(4566)}"
    }

    /**
     * Gets the AWS region (defaults to us-east-1).
     */
    fun getRegion(): String = "us-east-1"

    /**
     * Gets the AWS access key ID for LocalStack (default).
     */
    fun getAccessKeyId(): String = "test"

    /**
     * Gets the AWS secret access key for LocalStack (default).
     */
    fun getSecretAccessKey(): String = "test"
}

