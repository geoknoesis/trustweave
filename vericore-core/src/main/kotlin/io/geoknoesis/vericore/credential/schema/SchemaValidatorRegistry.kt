package io.geoknoesis.vericore.credential.schema

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Registry for schema validators.
 * 
 * Allows registration of JSON Schema and SHACL validators.
 * Provides automatic format detection and validation.
 * 
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Register validators
 * SchemaValidatorRegistry.register(JsonSchemaValidator())
 * SchemaValidatorRegistry.register(ShaclValidator())
 * 
 * // Auto-detect format and validate
 * val result = SchemaValidatorRegistry.validate(credential, schema)
 * 
 * // Or specify format explicitly
 * val result = SchemaValidatorRegistry.validate(
 *     credential, 
 *     schema, 
 *     SchemaFormat.JSON_SCHEMA
 * )
 * ```
 */
object SchemaValidatorRegistry {
    private val validators = mutableMapOf<SchemaFormat, SchemaValidator>()
    
    /**
     * Register a schema validator.
     * 
     * @param validator Validator to register
     */
    fun register(validator: SchemaValidator) {
        validators[validator.schemaFormat] = validator
    }
    
    /**
     * Unregister a validator for a format.
     * 
     * @param format Schema format
     */
    fun unregister(format: SchemaFormat) {
        validators.remove(format)
    }
    
    /**
     * Get validator for a specific format.
     * 
     * @param format Schema format
     * @return Validator, or null if not registered
     */
    fun get(format: SchemaFormat): SchemaValidator? {
        return validators[format]
    }
    
    /**
     * Auto-detect schema format and validate.
     * 
     * Automatically detects whether the schema is JSON Schema or SHACL
     * based on schema structure, then validates using the appropriate validator.
     * 
     * @param credential Credential to validate
     * @param schema Schema definition
     * @param format Optional format override (if null, auto-detects)
     * @return Validation result
     * @throws IllegalArgumentException if no validator is registered for the format
     */
    suspend fun validate(
        credential: VerifiableCredential,
        schema: JsonObject,
        format: SchemaFormat? = null
    ): SchemaValidationResult {
        val detectedFormat = format ?: detectSchemaFormat(schema)
        val validator = get(detectedFormat)
            ?: throw IllegalArgumentException("No validator registered for format: $detectedFormat")
        
        return validator.validate(credential, schema)
    }
    
    /**
     * Validate credentialSubject only.
     * 
     * @param subject Credential subject
     * @param schema Schema definition
     * @param format Optional format override
     * @return Validation result
     */
    suspend fun validateCredentialSubject(
        subject: JsonElement,
        schema: JsonObject,
        format: SchemaFormat? = null
    ): SchemaValidationResult {
        val detectedFormat = format ?: detectSchemaFormat(schema)
        val validator = get(detectedFormat)
            ?: throw IllegalArgumentException("No validator registered for format: $detectedFormat")
        
        return validator.validateCredentialSubject(subject, schema)
    }
    
    /**
     * Detect schema format from schema structure.
     * 
     * Checks for format indicators:
     * - SHACL: @context with sh: prefix, sh:NodeShape, sh:PropertyShape
     * - JSON Schema: $schema, type, properties
     * 
     * @param schema Schema definition
     * @return Detected format (defaults to JSON_SCHEMA)
     */
    fun detectSchemaFormat(schema: JsonObject): SchemaFormat {
        // Check for SHACL indicators
        val context = schema["@context"]
        if (context != null) {
            when (context) {
                is JsonElement -> {
                    val contextStr = context.toString()
                    if (contextStr.contains("shacl") || 
                        contextStr.contains("sh:") ||
                        contextStr.contains("http://www.w3.org/ns/shacl")) {
                        return SchemaFormat.SHACL
                    }
                }
            }
        }
        
        // Check for SHACL shape indicators
        if (schema.containsKey("sh:targetClass") ||
            schema.containsKey("sh:property") ||
            schema.containsKey("sh:node")) {
            return SchemaFormat.SHACL
        }
        
        // Check for JSON Schema indicators
        if (schema.containsKey("\$schema") || 
            schema.containsKey("type") || 
            schema.containsKey("properties")) {
            return SchemaFormat.JSON_SCHEMA
        }
        
        // Default to JSON Schema
        return SchemaFormat.JSON_SCHEMA
    }
    
    /**
     * Check if a format has a registered validator.
     * 
     * @param format Schema format
     * @return true if validator is registered
     */
    fun hasValidator(format: SchemaFormat): Boolean {
        return validators.containsKey(format)
    }
    
    /**
     * Get all registered formats.
     * 
     * @return List of formats with registered validators
     */
    fun getRegisteredFormats(): List<SchemaFormat> {
        return validators.keys.toList()
    }
    
    /**
     * Clear all registered validators.
     * Useful for testing.
     */
    fun clear() {
        validators.clear()
    }
}

