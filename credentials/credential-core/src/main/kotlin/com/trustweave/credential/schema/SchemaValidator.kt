package com.trustweave.credential.schema

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.SchemaFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Pluggable schema validator interface.
 *
 * Supports both JSON Schema and SHACL validation.
 * Implementations can be registered via SchemaValidatorRegistry.
 *
 * **Example Usage**:
 * ```kotlin
 * val validator = JsonSchemaValidator()
 * SchemaValidatorRegistry.register(validator)
 *
 * val result = SchemaValidatorRegistry.validate(credential, schema)
 * ```
 */
interface SchemaValidator {
    /**
     * Schema format supported by this validator.
     */
    val schemaFormat: SchemaFormat

    /**
     * Validate a credential against a schema.
     *
     * @param credential Credential to validate
     * @param schema Schema definition (JSON Schema or SHACL shape)
     * @return Validation result with errors if any
     */
    suspend fun validate(
        credential: VerifiableCredential,
        schema: JsonObject
    ): SchemaValidationResult

    /**
     * Validate only the credentialSubject against a schema.
     *
     * @param subject Credential subject (as JsonElement)
     * @param schema Schema definition
     * @return Validation result
     */
    suspend fun validateCredentialSubject(
        subject: JsonElement,
        schema: JsonObject
    ): SchemaValidationResult
}

/**
 * Schema validation result.
 *
 * @param valid Whether validation passed
 * @param errors List of validation errors
 * @param warnings List of validation warnings
 */
data class SchemaValidationResult(
    val valid: Boolean,
    val errors: List<SchemaValidationError> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * Schema validation error.
 *
 * @param path JSON path to the error location
 * @param message Error message
 * @param code Optional error code
 */
data class SchemaValidationError(
    val path: String,
    val message: String,
    val code: String? = null
)

/**
 * Schema registration result.
 */
data class SchemaRegistrationResult(
    val success: Boolean,
    val schemaId: String? = null,
    val error: String? = null
)

