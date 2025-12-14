package com.trustweave.credential.schema

import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.schema.internal.DefaultSchemaValidatorRegistry
import com.trustweave.credential.schema.internal.DefaultSchemaRegistry
import com.trustweave.credential.schema.internal.JsonSchemaValidator
import com.trustweave.credential.schema.internal.ShaclValidator

/**
 * Registry factory object for schema management.
 * 
 * Provides controlled construction of registry instances with sensible defaults.
 */
object SchemaRegistries {
    /**
     * Create a default schema validator registry instance.
     * 
     * Automatically registers built-in validators (JSON Schema, etc.).
     */
    fun defaultValidatorRegistry(): SchemaValidatorRegistry {
        val registry = DefaultSchemaValidatorRegistry()
        // Register default validators
        registry.register(JsonSchemaValidator())
        registry.register(ShaclValidator())
        return registry
    }
    
    /**
     * Create a default schema registry instance.
     * 
     * Includes a default validator registry with built-in validators.
     */
    fun default(): SchemaRegistry {
        return DefaultSchemaRegistry(defaultValidatorRegistry())
    }
    
    /**
     * Create a schema registry with a custom validator registry.
     */
    fun withValidatorRegistry(validatorRegistry: SchemaValidatorRegistry): SchemaRegistry {
        return DefaultSchemaRegistry(validatorRegistry)
    }
}

