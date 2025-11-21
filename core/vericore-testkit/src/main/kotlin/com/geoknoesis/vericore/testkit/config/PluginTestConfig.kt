package com.geoknoesis.vericore.testkit.config

/**
 * Plugin-specific test configurations.
 * 
 * Provides configuration for individual plugins including mock vs real service flags,
 * TestContainers configuration, and timeout settings.
 * 
 * **Example Usage**:
 * ```kotlin
 * val awsConfig = PluginTestConfig.aws()
 * if (awsConfig.useRealService) {
 *     // Use real AWS KMS
 * } else {
 *     // Use LocalStack mock
 * }
 * ```
 */
object PluginTestConfig {
    
    /**
     * Base configuration for all plugins.
     */
    abstract class PluginConfig(
        val pluginName: String,
        open val useRealService: Boolean = TestConfig.useRealServices(),
        open val timeoutSeconds: Long = TestConfig.operationTimeout()
    )
    
    /**
     * AWS KMS plugin test configuration.
     */
    data class AwsConfig(
        val region: String = "us-east-1",
        val endpointOverride: String? = System.getenv("LOCALSTACK_ENDPOINT") ?: "http://localhost:4566",
        val useLocalStack: Boolean = !TestConfig.useRealServices(),
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("aws", useRealService, timeoutSeconds)
    
    /**
     * Azure Key Vault plugin test configuration.
     */
    data class AzureConfig(
        val vaultUrl: String? = System.getenv("AZURE_VAULT_URL"),
        val useEmulator: Boolean = !TestConfig.useRealServices(),
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("azure", useRealService, timeoutSeconds)
    
    /**
     * Google Cloud KMS plugin test configuration.
     */
    data class GoogleConfig(
        val projectId: String? = System.getenv("GCP_PROJECT_ID"),
        val location: String = "us-east1",
        val keyRing: String = "test-keyring",
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("google", useRealService, timeoutSeconds)
    
    /**
     * HashiCorp Vault plugin test configuration.
     */
    data class VaultConfig(
        val vaultUrl: String? = System.getenv("VAULT_ADDR") ?: "http://localhost:8200",
        val token: String? = System.getenv("VAULT_TOKEN") ?: "test-token",
        val useTestContainer: Boolean = !TestConfig.useRealServices(),
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("hashicorp", useRealService, timeoutSeconds)
    
    /**
     * Ethereum chain plugin test configuration.
     */
    data class EthereumConfig(
        val rpcUrl: String? = System.getenv("ETHEREUM_RPC_URL"),
        val useGanache: Boolean = !TestConfig.useRealServices(),
        val ganachePort: Int = 8545,
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("ethereum", useRealService, timeoutSeconds)
    
    /**
     * Polygon chain plugin test configuration.
     */
    data class PolygonConfig(
        val rpcUrl: String? = System.getenv("POLYGON_RPC_URL"),
        val useTestnet: Boolean = !TestConfig.useRealServices(),
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("polygon", useRealService, timeoutSeconds)
    
    /**
     * Algorand chain plugin test configuration.
     */
    data class AlgorandConfig(
        val algodUrl: String? = System.getenv("ALGORAND_ALGOD_URL"),
        val useTestnet: Boolean = !TestConfig.useRealServices(),
        override val useRealService: Boolean = TestConfig.useRealServices(),
        override val timeoutSeconds: Long = TestConfig.operationTimeout()
    ) : PluginConfig("algorand", useRealService, timeoutSeconds)
    
    /**
     * Gets AWS KMS test configuration.
     */
    fun aws(): AwsConfig = AwsConfig()
    
    /**
     * Gets Azure Key Vault test configuration.
     */
    fun azure(): AzureConfig = AzureConfig()
    
    /**
     * Gets Google Cloud KMS test configuration.
     */
    fun google(): GoogleConfig = GoogleConfig()
    
    /**
     * Gets HashiCorp Vault test configuration.
     */
    fun vault(): VaultConfig = VaultConfig()
    
    /**
     * Gets Ethereum chain test configuration.
     */
    fun ethereum(): EthereumConfig = EthereumConfig()
    
    /**
     * Gets Polygon chain test configuration.
     */
    fun polygon(): PolygonConfig = PolygonConfig()
    
    /**
     * Gets Algorand chain test configuration.
     */
    fun algorand(): AlgorandConfig = AlgorandConfig()
}

