package org.trustweave.kms

import org.trustweave.kms.internal.ConfigCacheKey
import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Factory for creating KeyManagementService instances.
 *
 * Provides a simple API that hides ServiceLoader complexity and makes it easy
 * to create KMS instances by provider name.
 *
 * **Instance Caching:**
 * KMS instances are cached based on provider name and configuration. This means
 * calling `create()` with the same provider and configuration will return the
 * same instance, avoiding expensive client initialization overhead.
 *
 * **Resource Management:**
 * Cached instances that implement `AutoCloseable` are automatically closed when
 * [shutdown] is called or when the JVM shuts down. Call [shutdown] explicitly
 * to ensure proper cleanup of resources.
 *
 * **Example with Typed Configuration:**
 * ```kotlin
 * import org.trustweave.awskms.awsKmsOptions
 *
 * val kms = KeyManagementServices.create("aws", awsKmsOptions {
 *     region = "us-east-1"
 * })
 * ```
 *
 * **Example with Map Configuration:**
 * ```kotlin
 * val kms = KeyManagementServices.create("aws", mapOf(
 *     "region" to "us-east-1"
 * ))
 * ```
 */
object KeyManagementServices {
    /**
     * Cached list of providers loaded via ServiceLoader.
     * This prevents repeated ServiceLoader calls and ensures consistency.
     */
    private val cachedProviders: List<KeyManagementServiceProvider> by lazy {
        ServiceLoader.load(KeyManagementServiceProvider::class.java).toList()
    }

    /**
     * Cache of KMS instances keyed by provider name and configuration.
     * Thread-safe ConcurrentHashMap ensures safe concurrent access.
     */
    private val instanceCache = ConcurrentHashMap<String, KeyManagementService>()
    
    /**
     * Flag to track if shutdown has been called.
     */
    private val isShutdown = AtomicBoolean(false)
    
    /**
     * Shutdown hook registered with JVM to ensure cleanup on application exit.
     */
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            shutdown()
        })
    }

    /**
     * Creates a KeyManagementService instance for the specified provider.
     *
     * Instances are cached based on provider name and configuration. Calling this
     * method multiple times with the same provider and configuration will return
     * the same cached instance.
     *
     * @param providerName The provider name (e.g., "aws", "azure", "waltid")
     * @param options Typed configuration options
     * @return KeyManagementService instance (cached if previously created)
     * @throws IllegalArgumentException if provider is not found
     */
    fun create(providerName: String, options: KmsCreationOptions): KeyManagementService {
        checkShutdown()
        
        val cacheKey = ConfigCacheKey.create(providerName, options)
        
        return instanceCache.getOrPut(cacheKey) {
            val provider = cachedProviders.find { it.name == providerName }
                ?: throw IllegalArgumentException(
                    "KMS provider '$providerName' not found. Available providers: ${cachedProviders.map { it.name }}"
                )
            provider.create(options)
        }
    }

    /**
     * Creates a KeyManagementService instance for the specified provider (Map-based).
     *
     * Instances are cached based on provider name and configuration. Calling this
     * method multiple times with the same provider and configuration will return
     * the same cached instance.
     *
     * @param providerName The provider name (e.g., "aws", "azure", "waltid")
     * @param options Map-based configuration options
     * @return KeyManagementService instance (cached if previously created)
     * @throws IllegalArgumentException if provider is not found
     */
    fun create(providerName: String, options: Map<String, Any?> = emptyMap()): KeyManagementService {
        checkShutdown()
        
        val cacheKey = ConfigCacheKey.create(providerName, options)
        
        return instanceCache.getOrPut(cacheKey) {
            val provider = cachedProviders.find { it.name == providerName }
                ?: throw IllegalArgumentException(
                    "KMS provider '$providerName' not found. Available providers: ${cachedProviders.map { it.name }}"
                )
            provider.create(options)
        }
    }

    /**
     * Gets all available provider names.
     *
     * @return List of available provider names
     */
    fun availableProviders(): List<String> = cachedProviders.map { it.name }

    /**
     * Gets a provider by name (for advanced use cases).
     *
     * @param providerName The provider name
     * @return Provider instance, or null if not found
     */
    fun getProvider(providerName: String): KeyManagementServiceProvider? {
        return cachedProviders.find { it.name == providerName }
    }

    /**
     * Clears the instance cache.
     *
     * Useful for testing or when you need to force recreation of KMS instances.
     * Note: Existing cached instances will not be closed - callers are responsible
     * for managing lifecycle of KMS instances. Use [shutdown] to properly close
     * all cached instances.
     */
    fun clearCache() {
        instanceCache.clear()
    }

    /**
     * Shuts down the factory and closes all cached KMS instances that implement `AutoCloseable`.
     *
     * This method:
     * - Closes all cached instances that implement `AutoCloseable`
     * - Clears the cache
     * - Prevents creation of new instances (subsequent calls will throw `IllegalStateException`)
     *
     * **Important:** After calling this method, the factory cannot be used to create new instances.
     * This is automatically called during JVM shutdown via a shutdown hook.
     */
    fun shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            // Close all cached instances that implement AutoCloseable
            val instances = instanceCache.values.toList() // Create snapshot to avoid concurrent modification
            instanceCache.clear()
            
            instances.forEach { instance ->
                if (instance is AutoCloseable) {
                    try {
                        instance.close()
                    } catch (e: Exception) {
                        // Log but don't throw - we want to close all instances
                        System.err.println("Error closing KMS instance: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * Checks if the factory has been shut down.
     *
     * @return true if shutdown has been called, false otherwise
     */
    fun isShutdown(): Boolean = isShutdown.get()

    /**
     * Gets the current cache size (for testing/debugging).
     *
     * @return Number of cached instances
     */
    fun getCacheSize(): Int = instanceCache.size
    
    /**
     * Checks if shutdown has been called and throws if so.
     */
    private fun checkShutdown() {
        if (isShutdown.get()) {
            throw IllegalStateException("KeyManagementServices has been shut down and cannot create new instances")
        }
    }
    
    /**
     * Resets the shutdown state for testing purposes only.
     * This method should not be used in production code.
     */
    fun resetShutdownForTesting() {
        isShutdown.set(false)
    }
}
