package com.trustweave.credential.schema.internal

import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.Claims
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.credential.schema.SchemaRegistrationResult
import com.trustweave.credential.schema.SchemaValidationResult
import com.trustweave.credential.schema.SchemaValidator
import com.trustweave.credential.schema.SchemaValidatorRegistry
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of SchemaRegistry.
 * 
 * Thread-safe registry for credential schemas.
 */
internal class DefaultSchemaRegistry(
    private val validatorRegistry: SchemaValidatorRegistry
) : SchemaRegistry {
    private val schemaDefinitions = ConcurrentHashMap<SchemaId, JsonObject>()
    private val schemaFormats = ConcurrentHashMap<SchemaId, SchemaFormat>()

    override suspend fun registerSchema(
        schemaId: SchemaId,
        format: SchemaFormat,
        definition: JsonObject
    ): SchemaRegistrationResult {
        return try {
            schemaDefinitions[schemaId] = definition
            schemaFormats[schemaId] = format
            SchemaRegistrationResult(
                success = true,
                schemaId = schemaId
            )
        } catch (e: Exception) {
            SchemaRegistrationResult(
                success = false,
                error = e.message
            )
        }
    }

    override suspend fun getSchemaDefinition(schemaId: SchemaId): JsonObject? {
        return schemaDefinitions[schemaId]
    }

    override suspend fun getSchemaFormat(schemaId: SchemaId): SchemaFormat? {
        return schemaFormats[schemaId]
    }

    override suspend fun validate(
        credential: VerifiableCredential,
        schemaId: SchemaId
    ): SchemaValidationResult {
        val definition = schemaDefinitions[schemaId]
            ?: throw IllegalArgumentException("Schema not found: ${schemaId.value}")

        val format = schemaFormats[schemaId]
            ?: throw IllegalArgumentException("Schema format not found: ${schemaId.value}")

        val validator = validatorRegistry.get(format)
            ?: throw IllegalArgumentException("No validator registered for format: $format")

        return validator.validate(credential, definition)
    }

    override suspend fun validateClaims(
        claims: Claims,
        schemaId: SchemaId
    ): SchemaValidationResult {
        val definition = schemaDefinitions[schemaId]
            ?: throw IllegalArgumentException("Schema not found: ${schemaId.value}")

        val format = schemaFormats[schemaId]
            ?: throw IllegalArgumentException("Schema format not found: ${schemaId.value}")

        val validator = validatorRegistry.get(format)
            ?: throw IllegalArgumentException("No validator registered for format: $format")

        return validator.validateClaims(claims, definition)
    }

    override suspend fun isRegistered(schemaId: SchemaId): Boolean {
        return schemaDefinitions.containsKey(schemaId)
    }

    override suspend fun getAllSchemaIds(): List<SchemaId> {
        return schemaDefinitions.keys.toList()
    }

    override suspend fun unregister(schemaId: SchemaId): Boolean {
        val removed = schemaDefinitions.remove(schemaId) != null
        schemaFormats.remove(schemaId)
        return removed
    }

    override suspend fun clear() {
        schemaDefinitions.clear()
        schemaFormats.clear()
    }
}

