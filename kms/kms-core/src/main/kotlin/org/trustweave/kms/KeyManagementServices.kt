package org.trustweave.kms

import org.trustweave.kms.internal.ConfigCacheKey
import org.trustweave.kms.internal.ProviderLoading
import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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
        ProviderLoading.loadAll()
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

    // Concurrent creations share the read lock; shutdown exclusively drains the cache.
    private val lifecycleLock = ReentrantReadWriteLock(true)

    /**
     * Shutdown hook registered with JVM to ensure cleanup on application exit.
     */
    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                shutdown()
            },
        )
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
    fun create(
        providerName: String,
        options: KmsCreationOptions,
    ): KeyManagementService {
        checkShutdown()

        val cacheKey = ConfigCacheKey.create(providerName, options)

        return createCached(providerName, cacheKey) { it.create(options) }
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
    fun create(
        providerName: String,
        options: Map<String, Any?> = emptyMap(),
    ): KeyManagementService {
        checkShutdown()

        val cacheKey = ConfigCacheKey.create(providerName, options)

        return createCached(providerName, cacheKey) { it.create(options) }
    }

    private fun createCached(
        providerName: String,
        cacheKey: String,
        create: (KeyManagementServiceProvider) -> KeyManagementService,
    ): KeyManagementService =
        lifecycleLock.read {
            // Hashing may have overlapped shutdown; check again before creating or returning a client.
            checkShutdown()
            instanceCache.computeIfAbsent(cacheKey) {
                val provider =
                    cachedProviders.find { it.name == providerName }
                        ?: throw IllegalArgumentException(
                            "KMS provider '$providerName' not found. Available providers: ${cachedProviders.map { it.name }}",
                        )
                create(provider)
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
    fun getProvider(providerName: String): KeyManagementServiceProvider? = cachedProviders.find { it.name == providerName }

    /**
     * Clears the instance cache.
     *
     * Useful for testing or when you need to force recreation of KMS instances.
     * Note: Existing cached instances will not be closed - callers are responsible
     * for managing lifecycle of KMS instances. Use [shutdown] to properly close
     * all cached instances.
     *
     * @throws IllegalStateException if called from a provider's creation callback
     */
    fun clearCache() {
        checkLifecycleCallback()
        lifecycleLock.write { instanceCache.clear() }
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
     *
     * @throws IllegalStateException if called from a provider's creation callback
     */
    fun shutdown() {
        checkLifecycleCallback()
        val instances =
            lifecycleLock.write {
                if (!isShutdown.compareAndSet(false, true)) return
                instanceCache.values.toList().also { instanceCache.clear() }
            }

        // Close outside the lock so provider cleanup can call back into the factory safely.
        val closed = Collections.newSetFromMap(IdentityHashMap<KeyManagementService, Boolean>())
        instances.forEach { instance ->
            if (instance is AutoCloseable && closed.add(instance)) {
                try {
                    instance.close()
                } catch (e: Exception) {
                    // Log but don't throw - we want to close all instances. The JDK platform
                    // logger is used because SLF4J is only a compile-time dependency here; it
                    // routes to java.util.logging, or to SLF4J when a bridge is installed.
                    System
                        .getLogger(KeyManagementServices::class.java.name)
                        .log(
                            System.Logger.Level.WARNING,
                            "Error closing KMS instance ${instance::class.java.name}",
                            e,
                        )
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
        checkLifecycleCallback()
        lifecycleLock.write { isShutdown.set(false) }
    }

    private fun checkLifecycleCallback() {
        // A provider must not drain the cache while its own computeIfAbsent is still publishing a client.
        check(lifecycleLock.readHoldCount == 0) { "Cannot change the KMS factory lifecycle from provider creation" }
    }
}
