package com.trustweave.testkit.containers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * TestContainer for HashiCorp Vault.
 *
 * Provides a local Vault instance for KMS testing.
 *
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class VaultKmsIntegrationTest {
 *     companion object {
 *         @JvmStatic
 *         val vault = VaultContainer.create()
 *     }
 *
 *     @Test
 *     fun testWithVault() {
 *         val vaultUrl = vault.getVaultUrl()
 *         val token = vault.getRootToken()
 *         // Use for HashiCorp Vault KMS testing
 *     }
 * }
 * ```
 */
class VaultContainer private constructor(
    dockerImageName: DockerImageName
) : GenericContainer<VaultContainer>(dockerImageName) {

    companion object {
        /**
         * Default Vault Docker image.
         */
        private val DEFAULT_IMAGE = DockerImageName.parse("hashicorp/vault:latest")

        /**
         * Default root token for testing.
         */
        private const val DEFAULT_ROOT_TOKEN = "test-token"

        /**
         * Creates a Vault container with default configuration.
         */
        @JvmStatic
        fun create(): VaultContainer {
            return VaultContainer(DEFAULT_IMAGE)
                .withEnv("VAULT_DEV_ROOT_TOKEN_ID", DEFAULT_ROOT_TOKEN)
                .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
                .withExposedPorts(8200)
                .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2))
        }

        /**
         * Creates a Vault container with custom root token.
         */
        @JvmStatic
        fun create(rootToken: String): VaultContainer {
            return VaultContainer(DEFAULT_IMAGE)
                .withEnv("VAULT_DEV_ROOT_TOKEN_ID", rootToken)
                .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
                .withExposedPorts(8200)
                .waitingFor(Wait.forHttp("/v1/sys/health").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(2))
        }
    }

    /**
     * Gets the Vault URL.
     */
    fun getVaultUrl(): String {
        return "http://${host}:${getMappedPort(8200)}"
    }

    /**
     * Gets the root token.
     */
    fun getRootToken(): String {
        return DEFAULT_ROOT_TOKEN
    }

    /**
     * Gets the Vault address environment variable value.
     */
    fun getVaultAddr(): String {
        return getVaultUrl()
    }
}

