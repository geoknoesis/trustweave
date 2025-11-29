package com.trustweave.did.registration.loader

import com.trustweave.did.DidMethod
import com.trustweave.did.registry.DidMethodRegistry
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility functions for registering DID methods from JSON registration files.
 *
 * This provides a convenient API for loading and registering DID methods
 * from JSON files following the DID Registration specification.
 *
 * **Example Usage:**
 * ```kotlin
 * // Load and register methods from classpath
 * DidMethodRegistration.registerFromClasspath(registry)
 *
 * // Load and register methods from a directory
 * DidMethodRegistration.registerFromDirectory(registry, Paths.get("did-methods"))
 *
 * // Load and register a single method
 * DidMethodRegistration.registerFromFile(registry, Paths.get("did-methods/example.json"))
 * ```
 */
object DidMethodRegistration {

    /**
     * Loads and registers all DID methods from the default classpath location.
     *
     * Scans `did-methods` resource directory for JSON registration files.
     *
     * @param registry The DID method registry to register methods with
     * @return List of registered method names
     */
    fun registerFromClasspath(
        registry: DidMethodRegistry,
        resourcePath: String = "did-methods"
    ): List<String> {
        val loader = JsonDidMethodLoader()
        val methods = loader.loadFromClasspath(resourcePath)
        methods.forEach { registry.register(it) }
        return methods.map { it.method }
    }

    /**
     * Loads and registers all DID methods from a directory.
     *
     * @param registry The DID method registry to register methods with
     * @param directory Path to directory containing JSON registration files
     * @return List of registered method names
     */
    fun registerFromDirectory(
        registry: DidMethodRegistry,
        directory: Path
    ): List<String> {
        val loader = JsonDidMethodLoader()
        val methods = loader.loadFromDirectory(directory)
        methods.forEach { registry.register(it) }
        return methods.map { it.method }
    }

    /**
     * Loads and registers a single DID method from a JSON file.
     *
     * @param registry The DID method registry to register methods with
     * @param filePath Path to JSON registration file
     * @return The registered method name
     */
    fun registerFromFile(
        registry: DidMethodRegistry,
        filePath: Path
    ): String {
        val loader = JsonDidMethodLoader()
        val method = loader.loadFromFile(filePath)
        registry.register(method)
        return method.method
    }

    /**
     * Loads and registers a single DID method from a JSON string.
     *
     * @param registry The DID method registry to register methods with
     * @param jsonString JSON registration string
     * @return The registered method name
     */
    fun registerFromString(
        registry: DidMethodRegistry,
        jsonString: String
    ): String {
        val loader = JsonDidMethodLoader()
        val method = loader.loadFromString(jsonString)
        registry.register(method)
        return method.method
    }

    /**
     * Creates a DidMethod instance without registering it.
     *
     * Useful for testing or when you want to register it manually.
     *
     * @param jsonString JSON registration string
     * @return DidMethod instance
     */
    fun createMethod(jsonString: String): DidMethod {
        val loader = JsonDidMethodLoader()
        return loader.loadFromString(jsonString)
    }
}

