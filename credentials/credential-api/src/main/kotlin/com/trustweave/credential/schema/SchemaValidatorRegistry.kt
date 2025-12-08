package com.trustweave.credential.schema

import com.trustweave.credential.model.SchemaFormat

/**
 * Registry for schema validators.
 *
 * Allows registration of JSON Schema and SHACL validators.
 * Provides automatic format detection and validation.
 *
 * **Thread Safety**: Registry implementations should be thread-safe for concurrent access.
 *
 * **Example Usage**:
 * ```kotlin
 * val registry = SchemaRegistries.defaultValidatorRegistry()
 *
 * // Register validators
 * registry.register(JsonSchemaValidator())
 * registry.register(ShaclValidator())
 *
 * // Get validator for format
 * val validator = registry.get(SchemaFormat.JSON_SCHEMA)
 * ```
 */
interface SchemaValidatorRegistry {
    /**
     * Register a schema validator.
     *
     * @param validator Validator to register
     */
    fun register(validator: SchemaValidator)

    /**
     * Unregister a validator for a format.
     *
     * @param format Schema format
     * @return true if unregistered, false if not found
     */
    fun unregister(format: SchemaFormat): Boolean

    /**
     * Get validator for a specific format.
     *
     * @param format Schema format
     * @return Validator, or null if not registered
     */
    fun get(format: SchemaFormat): SchemaValidator?

    /**
     * Check if a format has a registered validator.
     *
     * @param format Schema format
     * @return true if validator is registered
     */
    fun hasValidator(format: SchemaFormat): Boolean

    /**
     * Get all registered formats.
     *
     * @return List of formats with registered validators
     */
    fun getRegisteredFormats(): List<SchemaFormat>

    /**
     * Clear all registered validators.
     * Useful for testing.
     */
    fun clear()
}

