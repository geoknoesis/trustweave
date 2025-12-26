package org.trustweave.did.registry

import org.trustweave.did.DidMethod
import kotlin.DslMarker

/**
 * Builder DSL for creating [DefaultDidMethodRegistry] instances.
 *
 * Provides a fluent, idiomatic Kotlin API for registry configuration.
 *
 * **Example Usage:**
 * ```kotlin
 * val registry = didMethodRegistry {
 *     register(KeyDidMethod(kms))
 *     register(WebDidMethod())
 *     registerAll(OtherMethod1(), OtherMethod2())
 * }
 * ```
 */
@DslMarker
annotation class RegistryDsl

/**
 * Creates a [DefaultDidMethodRegistry] using a builder DSL.
 */
inline fun didMethodRegistry(block: RegistryBuilder.() -> Unit = {}): DefaultDidMethodRegistry {
    val builder = RegistryBuilder()
    builder.block()
    return builder.build()
}

/**
 * Builder for [DefaultDidMethodRegistry].
 */
@RegistryDsl
class RegistryBuilder {
    private val methods = mutableListOf<DidMethod>()

    /**
     * Registers a single DID method.
     */
    fun register(method: DidMethod) {
        methods.add(method)
    }

    /**
     * Registers multiple DID methods.
     */
    fun registerAll(vararg methods: DidMethod) {
        this.methods.addAll(methods)
    }

    /**
     * Registers multiple DID methods from a collection.
     */
    fun registerAll(methods: Collection<DidMethod>) {
        this.methods.addAll(methods)
    }

    fun build(): DefaultDidMethodRegistry {
        val registry = DefaultDidMethodRegistry()
        methods.forEach { registry.register(it) }
        return registry
    }
}

