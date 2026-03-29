package org.trustweave.did.registrar.method

import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DidMethodRegistry
import java.nio.file.Path

/**
 * Utility functions for registering DID methods from JSON registration files.
 */
object DidMethodRegistration {

    fun registerFromClasspath(registry: DidMethodRegistry, resourcePath: String = "did-methods"): List<String> {
        val methods = JsonDidMethodLoader().loadFromClasspath(resourcePath)
        methods.forEach { registry.register(it) }
        return methods.map { it.method }
    }

    fun registerFromDirectory(registry: DidMethodRegistry, directory: Path): List<String> {
        val methods = JsonDidMethodLoader().loadFromDirectory(directory)
        methods.forEach { registry.register(it) }
        return methods.map { it.method }
    }

    fun registerFromFile(registry: DidMethodRegistry, filePath: Path): String {
        val method = JsonDidMethodLoader().loadFromFile(filePath)
        registry.register(method)
        return method.method
    }

    fun registerFromString(registry: DidMethodRegistry, jsonString: String): String {
        val method = JsonDidMethodLoader().loadFromString(jsonString)
        registry.register(method)
        return method.method
    }

    fun createMethod(jsonString: String): DidMethod = JsonDidMethodLoader().loadFromString(jsonString)
}
