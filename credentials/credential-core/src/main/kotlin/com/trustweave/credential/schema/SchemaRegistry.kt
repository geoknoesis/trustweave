package com.trustweave.credential.schema

import com.trustweave.credential.models.CredentialSchema
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Schema registry for managing credential schemas.
 *
 * Provides storage and retrieval of credential schemas by ID.
 * Schemas can be registered and then used for credential validation.
 *
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 *
 * **Example Usage**:
 * ```kotlin
 * val schema = CredentialSchema(
 *     id = "https://example.com/schemas/person",
 *     type = "JsonSchemaValidator2018",
 *     schemaFormat = SchemaFormat.JSON_SCHEMA
 * )
 *
 * val schemaDefinition = buildJsonObject {
 *     put("\$schema", "http://json-schema.org/draft-07/schema#")
 *     put("type", "object")
 *     put("properties", buildJsonObject {
 *         put("name", buildJsonObject { put("type", "string") })
 *     })
 * }
 *
 * SchemaRegistry.registerSchema(schema, schemaDefinition)
 *
 * // Later, validate credential against schema
 * val result = SchemaRegistry.validateCredential(credential, schema.id)
 * ```
 */
object SchemaRegistry {
    private val schemas = ConcurrentHashMap<String, CredentialSchema>()
    private val schemaDefinitions = ConcurrentHashMap<String, JsonObject>()

    /**
     * Register a schema with its definition.
     *
     * @param schema Schema metadata
     * @param definition Schema definition (JSON Schema or SHACL shape)
     * @return Registration result
     */
    suspend fun registerSchema(
        schema: CredentialSchema,
        definition: JsonObject
    ): SchemaRegistrationResult {
        try {
            schemas[schema.id] = schema
            schemaDefinitions[schema.id] = definition
            return SchemaRegistrationResult(
                success = true,
                schemaId = schema.id
            )
        } catch (e: Exception) {
            return SchemaRegistrationResult(
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Get schema metadata by ID.
     *
     * @param schemaId Schema ID
     * @return Schema metadata, or null if not found
     */
    fun getSchema(schemaId: String): CredentialSchema? {
        return schemas[schemaId]
    }

    /**
     * Get schema definition by ID.
     *
     * @param schemaId Schema ID
     * @return Schema definition, or null if not found
     */
    fun getSchemaDefinition(schemaId: String): JsonObject? {
        return schemaDefinitions[schemaId]
    }

    /**
     * Validate a credential against a registered schema.
     *
     * @param credential Credential to validate
     * @param schemaId Schema ID to validate against
     * @return Validation result
     * @throws IllegalArgumentException if schema is not registered
     */
    suspend fun validateCredential(
        credential: VerifiableCredential,
        schemaId: String
    ): SchemaValidationResult {
        val schema = schemas[schemaId]
            ?: throw IllegalArgumentException("Schema not found: $schemaId")

        val definition = schemaDefinitions[schemaId]
            ?: throw IllegalArgumentException("Schema definition not found: $schemaId")

        // Use SchemaValidatorRegistry to validate
        return SchemaValidatorRegistry.validate(credential, definition, schema.schemaFormat)
    }

    /**
     * Check if a schema is registered.
     *
     * @param schemaId Schema ID
     * @return true if schema is registered
     */
    fun isRegistered(schemaId: String): Boolean {
        return schemas.containsKey(schemaId)
    }

    /**
     * Get all registered schema IDs.
     *
     * @return List of schema IDs
     */
    fun getAllSchemaIds(): List<String> {
        return schemas.keys.toList()
    }

    /**
     * Unregister a schema.
     *
     * @param schemaId Schema ID to unregister
     */
    fun unregister(schemaId: String) {
        schemas.remove(schemaId)
        schemaDefinitions.remove(schemaId)
    }

    /**
     * Clear all registered schemas.
     * Useful for testing.
     */
    fun clear() {
        schemas.clear()
        schemaDefinitions.clear()
    }
}

