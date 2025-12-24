package org.trustweave.core.serialization

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.datetime.Instant

/**
 * Common serialization module for TrustWeave SDK.
 * 
 * Provides serializers for types that require contextual serialization,
 * such as kotlinx.datetime.Instant.
 * 
 * **Usage:**
 * ```kotlin
 * val json = Json {
 *     serializersModule = SerializationModule.default
 *     // ... other configuration
 * }
 * ```
 */
object SerializationModule {
    /**
     * Default serialization module with all common serializers.
     * 
     * Registers serializers for Instant. Kotlinx.serialization automatically
     * handles nullable Instant? when Instant serializer is registered.
     * 
     * **Usage:**
     * ```kotlin
     * val json = Json {
     *     serializersModule = SerializationModule.default
     * }
     * ```
     * 
     * All Instant fields (both nullable and non-nullable) will be serialized
     * as ISO 8601 strings (e.g., "2024-01-01T00:00:00Z").
     */
    val default: SerializersModule = SerializersModule {
        contextual(Instant::class, InstantSerializer)
        // Note: Nullable Instant? is automatically handled by kotlinx.serialization
        // when the non-nullable serializer is registered. The compiler plugin may
        // show warnings for @Contextual with nullable types, but it works correctly at runtime.
    }
}

