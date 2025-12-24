package org.trustweave.credential.schema

import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.model.Claims
import org.trustweave.credential.model.SchemaFormat
import kotlinx.serialization.json.JsonObject

/**
 * Pluggable schema validator interface.
 *
 * Supports both JSON Schema and SHACL validation.
 * Validates Verifiable Credentials against schemas.
 *
 * **Example Usage**:
 * ```kotlin
 * val validator = JsonSchemaValidator()
 * SchemaRegistries.default().register(validator)
 *
 * val result = validator.validate(vc, schema)
 * ```
 */
interface SchemaValidator {
    /**
     * Schema format supported by this validator.
     */
    val schemaFormat: SchemaFormat

    /**
     * Validate a Verifiable Credential against a schema.
     *
     * @param credential VerifiableCredential to validate
     * @param schema Schema definition (JSON Schema or SHACL shape)
     * @return Validation result with errors if any
     */
    suspend fun validate(
        credential: org.trustweave.credential.model.vc.VerifiableCredential,
        schema: JsonObject
    ): SchemaValidationResult

    /**
     * Validate only the claims against a schema.
     *
     * @param claims Credential claims (as Claims map)
     * @param schema Schema definition
     * @return Validation result
     */
    suspend fun validateClaims(
        claims: Claims,
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
    val schemaId: SchemaId? = null,
    val error: String? = null
)

