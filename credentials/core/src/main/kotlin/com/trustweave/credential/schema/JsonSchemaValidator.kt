package com.trustweave.credential.schema

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.core.SchemaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * JSON Schema validator implementation.
 * 
 * Supports JSON Schema Draft 7 and Draft 2020-12.
 * Validates credential structure and credentialSubject against JSON Schema.
 * 
 * **Note**: This is a placeholder implementation. A full implementation would
 * require a JSON Schema validation library (e.g., everit-json-schema, networknt/json-schema-validator).
 * 
 * **Example Usage**:
 * ```kotlin
 * val validator = JsonSchemaValidator()
 * SchemaValidatorRegistry.register(validator)
 * 
 * val schema = buildJsonObject {
 *     put("\$schema", "http://json-schema.org/draft-07/schema#")
 *     put("type", "object")
 *     put("properties", buildJsonObject {
 *         put("name", buildJsonObject { put("type", "string") })
 *     })
 * }
 * 
 * val result = validator.validate(credential, schema)
 * ```
 */
class JsonSchemaValidator : SchemaValidator {
    override val schemaFormat = SchemaFormat.JSON_SCHEMA
    
    override suspend fun validate(
        credential: VerifiableCredential,
        schema: JsonObject
    ): SchemaValidationResult {
        val errors = mutableListOf<SchemaValidationError>()
        
        // TODO: Implement JSON Schema validation
        // 1. Validate credential structure (issuer, type, etc.)
        // 2. Validate credentialSubject against schema
        // 3. Check required fields
        // 4. Validate types and constraints
        
        // Placeholder: Basic structure validation
        if (!credential.type.contains("VerifiableCredential")) {
            errors.add(SchemaValidationError(
                path = "/type",
                message = "Credential must include 'VerifiableCredential' in type array",
                code = "missing_type"
            ))
        }
        
        if (credential.issuer.isBlank()) {
            errors.add(SchemaValidationError(
                path = "/issuer",
                message = "Credential issuer is required",
                code = "missing_issuer"
            ))
        }
        
        // Validate credentialSubject if schema has properties
        val schemaProperties = schema["properties"]?.jsonObject
        if (schemaProperties != null) {
            val subjectResult = validateCredentialSubject(credential.credentialSubject, schema)
            errors.addAll(subjectResult.errors)
        }
        
        return SchemaValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
    
    override suspend fun validateCredentialSubject(
        subject: kotlinx.serialization.json.JsonElement,
        schema: JsonObject
    ): SchemaValidationResult {
        val errors = mutableListOf<SchemaValidationError>()
        
        // TODO: Implement JSON Schema validation for credentialSubject
        // 1. Parse schema properties
        // 2. Validate each field in credentialSubject
        // 3. Check required fields
        // 4. Validate types and constraints
        
        // Enhanced validation with required fields and type checking
        val schemaProperties = schema["properties"]?.jsonObject
        val requiredFields = schema["required"]?.let {
            when (it) {
                is kotlinx.serialization.json.JsonArray -> {
                    it.mapNotNull { element ->
                        element.jsonPrimitive.contentOrNull
                    }
                }
                else -> emptyList()
            }
        } ?: emptyList()
        
        if (schemaProperties != null && subject is kotlinx.serialization.json.JsonObject) {
            // Check required fields
            for (field in requiredFields) {
                if (!subject.containsKey(field)) {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$field",
                        message = "Required field '$field' is missing",
                        code = "missing_required_field"
                    ))
                }
            }
            
            // Validate each property in credentialSubject against schema
            for ((fieldName, fieldValue) in subject.entries) {
                val fieldSchema = schemaProperties[fieldName]?.jsonObject
                if (fieldSchema != null) {
                    val fieldErrors = validateField(fieldValue, fieldSchema, "/credentialSubject/$fieldName")
                    errors.addAll(fieldErrors)
                }
            }
        }
        
        return SchemaValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Validate a single field against its schema.
     */
    private fun validateField(
        value: kotlinx.serialization.json.JsonElement,
        fieldSchema: kotlinx.serialization.json.JsonObject,
        path: String
    ): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        
        // Check type
        val expectedType = fieldSchema["type"]?.jsonPrimitive?.contentOrNull
        if (expectedType != null) {
            val actualType = when (value) {
                is kotlinx.serialization.json.JsonObject -> "object"
                is kotlinx.serialization.json.JsonArray -> "array"
                is kotlinx.serialization.json.JsonPrimitive -> {
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
                        if (!stringValue.startsWith("http://") && !stringValue.startsWith("https://") && !stringValue.startsWith("did:")) {
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
    
    /**
     * Check if types are compatible (e.g., integer is compatible with number).
     */
    private fun isTypeCompatible(actualType: String, expectedType: String): Boolean {
        return when {
            actualType == expectedType -> true
            actualType == "integer" && expectedType == "number" -> true
            else -> false
        }
    }
}

