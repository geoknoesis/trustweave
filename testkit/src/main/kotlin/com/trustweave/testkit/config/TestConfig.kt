package com.trustweave.testkit.config

/**
 * Centralized test configuration.
 * 
 * Provides environment variable support, default test values, and plugin-specific configs.
 * 
 * **Example Usage**:
 * ```kotlin
 * val useRealServices = TestConfig.useRealServices()
 * val testTimeout = TestConfig.operationTimeout()
 * ```
 */
object TestConfig {
    
    /**
     * Default operation timeout in seconds.
     */
    const val DEFAULT_OPERATION_TIMEOUT_SECONDS = 30L
    
    /**
     * Default test retry count.
     */
    const val DEFAULT_MAX_RETRIES = 3
    
    /**
     * Default retry delay in milliseconds.
     */
    const val DEFAULT_RETRY_DELAY_MS = 1000L
    
    /**
     * Environment variable names.
     */
    object EnvVars {
        const val USE_REAL_SERVICES = "VERICORE_TEST_USE_REAL_SERVICES"
        const val TEST_TIMEOUT = "VERICORE_TEST_TIMEOUT_SECONDS"
        const val MAX_RETRIES = "VERICORE_TEST_MAX_RETRIES"
        const val SKIP_INTEGRATION_TESTS = "VERICORE_SKIP_INTEGRATION_TESTS"
        const val TEST_LOG_LEVEL = "VERICORE_TEST_LOG_LEVEL"
        const val SKIP_IF_NO_CREDENTIALS = "VERICORE_TEST_SKIP_IF_NO_CREDENTIALS"
    }
    
    /**
     * Whether to use real services instead of mocks.
     * Defaults to false. Set VERICORE_TEST_USE_REAL_SERVICES=true to enable.
     */
    fun useRealServices(): Boolean {
        return System.getenv(EnvVars.USE_REAL_SERVICES)?.toBoolean() ?: false
    }
    
    /**
     * Operation timeout in seconds.
     * Defaults to 30. Set VERICORE_TEST_TIMEOUT_SECONDS to override.
     */
    fun operationTimeout(): Long {
        return System.getenv(EnvVars.TEST_TIMEOUT)?.toLongOrNull() 
            ?: DEFAULT_OPERATION_TIMEOUT_SECONDS
    }
    
    /**
     * Maximum number of retries for flaky tests.
     * Defaults to 3. Set VERICORE_TEST_MAX_RETRIES to override.
     */
    fun maxRetries(): Int {
        return System.getenv(EnvVars.MAX_RETRIES)?.toIntOrNull() 
            ?: DEFAULT_MAX_RETRIES
    }
    
    /**
     * Whether to skip integration tests.
     * Set VERICORE_SKIP_INTEGRATION_TESTS=true to skip.
     */
    fun skipIntegrationTests(): Boolean {
        return System.getenv(EnvVars.SKIP_INTEGRATION_TESTS)?.toBoolean() ?: false
    }
    
    /**
     * Test log level.
     * Defaults to INFO. Set VERICORE_TEST_LOG_LEVEL to override.
     */
    fun logLevel(): String {
        return System.getenv(EnvVars.TEST_LOG_LEVEL) ?: "INFO"
    }
    
    /**
     * Default test chain ID for blockchain tests.
     */
    const val DEFAULT_TEST_CHAIN_ID = "algorand:testnet"
    
    /**
     * Default test DID method.
     */
    const val DEFAULT_TEST_DID_METHOD = "key"
    
    /**
     * Default test key algorithm.
     */
    const val DEFAULT_TEST_ALGORITHM = "Ed25519"
    
    /**
     * Whether to skip tests that require credentials when credentials are not available.
     * Defaults to true. Set VERICORE_TEST_SKIP_IF_NO_CREDENTIALS=false to fail instead of skip.
     */
    fun skipIfNoCredentials(): Boolean {
        return System.getenv(EnvVars.SKIP_IF_NO_CREDENTIALS)?.toBoolean() ?: true
    }
}

