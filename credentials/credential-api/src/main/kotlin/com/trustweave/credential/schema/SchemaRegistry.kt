package com.trustweave.credential.schema

import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.Claims
import com.trustweave.credential.model.SchemaFormat
import kotlinx.serialization.json.JsonObject

/**
 * Schema registry for managing credential schemas.
 *
 * Provides storage and retrieval of credential schemas by ID.
 * Schemas can be registered and then used for credential validation.
 *
 * **Thread Safety**: Registry implementations should be thread-safe for concurrent access.
 *
 * **Example Usage**:
 * ```kotlin
 * val registry = SchemaRegistries.default()
 *
 * val schemaDefinition = buildJsonObject {
 *     put("\$schema", "http://json-schema.org/draft-07/schema#")
 *     put("type", "object")
 *     put("properties", buildJsonObject {
 *         put("name", buildJsonObject { put("type", "string") })
 *     })
 * }
 *
 * val schemaId = SchemaId("https://example.com/schemas/person")
 * registry.registerSchema(
 *     schemaId = schemaId,
 *     format = SchemaFormat.JSON_SCHEMA,
 *     definition = schemaDefinition
 * )
 *
 * // Later, validate credential against schema
 * val result = registry.validate(envelope, schemaId)
 * ```
 */
interface SchemaRegistry {
    /**
     * Register a schema with its definition.
     *
     * @param schemaId Schema identifier
     * @param format Schema format (JSON_SCHEMA or SHACL)
     * @param definition Schema definition (JSON Schema or SHACL shape)
     * @return Registration result
     */
    suspend fun registerSchema(
        schemaId: SchemaId,
        format: SchemaFormat,
        definition: JsonObject
    ): SchemaRegistrationResult

    /**
     * Get schema definition by ID.
     *
     * @param schemaId Schema ID
     * @return Schema definition, or null if not found
     */
    suspend fun getSchemaDefinition(schemaId: SchemaId): JsonObject?

    /**
     * Get schema format by ID.
     *
     * @param schemaId Schema ID
     * @return Schema format, or null if not found
     */
    suspend fun getSchemaFormat(schemaId: SchemaId): SchemaFormat?

    /**
     * Validate a Verifiable Credential against a registered schema.
     *
     * @param credential VerifiableCredential to validate
     * @param schemaId Schema ID to validate against
     * @return Validation result
     * @throws IllegalArgumentException if schema is not registered
     */
    suspend fun validate(
        credential: com.trustweave.credential.model.vc.VerifiableCredential,
        schemaId: SchemaId
    ): SchemaValidationResult

    /**
     * Validate claims against a registered schema.
     *
     * @param claims Claims to validate
     * @param schemaId Schema ID to validate against
     * @return Validation result
     * @throws IllegalArgumentException if schema is not registered
     */
    suspend fun validateClaims(
        claims: Claims,
        schemaId: SchemaId
    ): SchemaValidationResult

    /**
     * Check if a schema is registered.
     *
     * @param schemaId Schema ID
     * @return true if schema is registered
     */
    suspend fun isRegistered(schemaId: SchemaId): Boolean

    /**
     * Get all registered schema IDs.
     *
     * @return List of schema IDs
     */
    suspend fun getAllSchemaIds(): List<SchemaId>

    /**
     * Unregister a schema.
     *
     * @param schemaId Schema ID to unregister
     * @return true if unregistered, false if not found
     */
    suspend fun unregister(schemaId: SchemaId): Boolean

    /**
     * Clear all registered schemas.
     * Useful for testing.
     */
    suspend fun clear()
}

