package com.trustweave.credential.schema.internal

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.Claims
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.schema.SchemaValidator
import com.trustweave.credential.schema.SchemaValidationError
import com.trustweave.credential.schema.SchemaValidationResult
import kotlinx.serialization.json.*
import kotlinx.serialization.json.JsonObject

/**
 * JSON Schema validator implementation.
 *
 * Supports JSON Schema Draft 7 and Draft 2020-12.
 * Validates credential envelope structure and claims against JSON Schema.
 *
 * **Note**: This is a basic implementation. For production use, consider
 * integrating a full JSON Schema validation library.
 *
 * **Example Usage**:
 * ```kotlin
 * val validator = JsonSchemaValidator()
 * SchemaRegistries.defaultValidatorRegistry().register(validator)
 * ```
 */
internal class JsonSchemaValidator : SchemaValidator {
    override val schemaFormat = SchemaFormat.JSON_SCHEMA

    override suspend fun validate(
        credential: VerifiableCredential,
        schema: JsonObject
    ): SchemaValidationResult {
        val errors = mutableListOf<SchemaValidationError>()

        // Validate credential structure
        if (!credential.type.any { it.value == "VerifiableCredential" }) {
            errors.add(SchemaValidationError(
                path = "/type",
                message = "Credential must include 'VerifiableCredential' in type array",
                code = "missing_type"
            ))
        }

        // Validate claims if schema has properties
        val schemaProperties = schema["properties"]?.jsonObject
        if (schemaProperties != null) {
            // Claims are in credentialSubject.claims
            val claims = credential.credentialSubject.claims
            val claimsResult = validateClaims(claims, schema)
            errors.addAll(claimsResult.errors)
        }

        return SchemaValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }

    override suspend fun validateClaims(
        claims: Claims,
        schema: JsonObject
    ): SchemaValidationResult {
        val errors = mutableListOf<SchemaValidationError>()

        // Convert Claims (Map<String, JsonElement>) to JsonObject for validation
        val claimsObject = buildJsonObject {
            for (entry in claims) {
                put(entry.key, entry.value)
            }
        }

        // Extract required fields
        val requiredFields = schema["required"]?.jsonArray?.mapNotNull { element ->
            element.jsonPrimitive.contentOrNull
        } ?: emptyList()

        val schemaProperties = schema["properties"]?.jsonObject

        if (schemaProperties != null) {
            // Check required fields
            for (field in requiredFields) {
                if (!claimsObject.containsKey(field)) {
                    errors.add(SchemaValidationError(
                        path = "/claims/$field",
                        message = "Required field '$field' is missing",
                        code = "missing_required_field"
                    ))
                }
            }

            // Validate each claim against schema
            for ((fieldName, fieldValue) in claimsObject.entries) {
                val fieldSchema = schemaProperties[fieldName]?.jsonObject
                if (fieldSchema != null) {
                    val fieldErrors = validateField(fieldValue, fieldSchema, "/claims/$fieldName")
                    errors.addAll(fieldErrors)
                }
            }
        }

        return SchemaValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = emptyList()
        )
    }

    private fun validateField(
        value: JsonElement,
        fieldSchema: JsonObject,
        path: String
    ): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        // Check type
        val expectedType = fieldSchema["type"]?.jsonPrimitive?.contentOrNull
        if (expectedType != null) {
            val actualType = when (value) {
                is JsonObject -> "object"
                is JsonArray -> "array"
                is JsonPrimitive -> {
                    when {
                        value.isString -> "string"
                        value.booleanOrNull != null -> "boolean"
                        value.longOrNull != null || value.doubleOrNull != null -> "number"
                        else -> "string"
                    }
                }
                else -> "unknown"
            }

            if (actualType != expectedType && !isTypeCompatible(actualType, expectedType)) {
                errors.add(SchemaValidationError(
                    path = path,
                    message = "Field type mismatch: expected '$expectedType', got '$actualType'",
                    code = "type_mismatch"
                ))
            }
        }

        // Check enum values
        val enumValues = fieldSchema["enum"]?.jsonArray
        if (enumValues != null && !enumValues.contains(value)) {
            errors.add(SchemaValidationError(
                path = path,
                message = "Field value is not in allowed enum values",
                code = "enum_mismatch"
            ))
        }

        // Check string format constraints
        if (expectedType == "string") {
            val format = fieldSchema["format"]?.jsonPrimitive?.contentOrNull
            val stringValue = value.jsonPrimitive.contentOrNull

            if (format != null && stringValue != null) {
                when (format) {
                    "email" -> {
                        if (!stringValue.contains("@") || !stringValue.contains(".")) {
                            errors.add(SchemaValidationError(
                                path = path,
                                message = "Field must be a valid email address",
                                code = "format_invalid"
                            ))
                        }
                    }
                    "uri" -> {
                        if (!stringValue.startsWith("http://") && 
                            !stringValue.startsWith("https://") && 
                            !stringValue.startsWith("did:")) {
                            errors.add(SchemaValidationError(
                                path = path,
                                message = "Field must be a valid URI",
                                code = "format_invalid"
                            ))
                        }
                    }
                }
            }

            // Check string length constraints
            val minLength = fieldSchema["minLength"]?.jsonPrimitive?.intOrNull
            val maxLength = fieldSchema["maxLength"]?.jsonPrimitive?.intOrNull

            if (stringValue != null) {
                if (minLength != null && stringValue.length < minLength) {
                    errors.add(SchemaValidationError(
                        path = path,
                        message = "Field length must be at least $minLength characters",
                        code = "min_length_violation"
                    ))
                }
                if (maxLength != null && stringValue.length > maxLength) {
                    errors.add(SchemaValidationError(
                        path = path,
                        message = "Field length must be at most $maxLength characters",
                        code = "max_length_violation"
                    ))
                }
            }
        }

        // Check number constraints
        if (expectedType == "number" || expectedType == "integer") {
            val numValue = value.jsonPrimitive.doubleOrNull
            if (numValue != null) {
                val minimum = fieldSchema["minimum"]?.jsonPrimitive?.doubleOrNull
                val maximum = fieldSchema["maximum"]?.jsonPrimitive?.doubleOrNull

                if (minimum != null && numValue < minimum) {
                    errors.add(SchemaValidationError(
                        path = path,
                        message = "Field value must be at least $minimum",
                        code = "minimum_violation"
                    ))
                }
                if (maximum != null && numValue > maximum) {
                    errors.add(SchemaValidationError(
                        path = path,
                        message = "Field value must be at most $maximum",
                        code = "maximum_violation"
                    ))
                }
            }
        }

        return errors
    }

    private fun isTypeCompatible(actualType: String, expectedType: String): Boolean {
        return when {
            actualType == expectedType -> true
            actualType == "integer" && expectedType == "number" -> true
            else -> false
        }
    }
}

