package org.trustweave.credential.schema.internal

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.Claims
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.schema.SchemaValidator
import org.trustweave.credential.schema.SchemaValidationError
import org.trustweave.credential.schema.SchemaValidationResult
import kotlinx.serialization.json.*

/**
 * JSON Schema validator.
 *
 * Supports a practical subset of JSON Schema Draft 7 / 2020-12 sufficient for
 * W3C Verifiable Credential subject schemas:
 * - `type`, `enum`, `const`
 * - `required`, `properties`, `additionalProperties`
 * - `minLength`, `maxLength`, `pattern` (string)
 * - `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf` (number)
 * - `minItems`, `maxItems`, `items` (array)
 * - `anyOf`, `oneOf`, `allOf`, `not`
 * - `format` heuristics: `email`, `uri`, `date`, `date-time`, `uuid`
 * - Recursive nested object validation
 */
internal class JsonSchemaValidator : SchemaValidator {
    override val schemaFormat = SchemaFormat.JSON_SCHEMA

    override suspend fun validate(
        credential: VerifiableCredential,
        schema: JsonObject,
    ): SchemaValidationResult {
        val errors = mutableListOf<SchemaValidationError>()

        if (!credential.type.any { it.value == "VerifiableCredential" }) {
            errors.add(
                SchemaValidationError(
                    path = "/type",
                    message = "Credential must include 'VerifiableCredential' in type",
                    code = "missing_type",
                ),
            )
        }

        // Delegate subject claims validation to validateClaims
        val claimsResult = validateClaims(credential.credentialSubject.claims, schema)
        errors.addAll(claimsResult.errors)

        return SchemaValidationResult(valid = errors.isEmpty(), errors = errors, warnings = emptyList())
    }

    override suspend fun validateClaims(
        claims: Claims,
        schema: JsonObject,
    ): SchemaValidationResult {
        val claimsObject = buildJsonObject { claims.forEach { (k, v) -> put(k, v) } }
        val errors = validateObject(claimsObject, schema, "/")
        return SchemaValidationResult(valid = errors.isEmpty(), errors = errors, warnings = emptyList())
    }

    // -------------------------------------------------------------------------
    // Core recursive validation
    // -------------------------------------------------------------------------

    private fun validateValue(value: JsonElement, schema: JsonObject, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        // const
        val const = schema["const"]
        if (const != null && value != const) {
            errors.add(err(path, "Value must equal const ${const}", "const_mismatch"))
        }

        // enum
        val enumArr = schema["enum"]?.jsonArray
        if (enumArr != null && value !in enumArr) {
            errors.add(err(path, "Value must be one of: ${enumArr.joinToString()}", "enum_mismatch"))
        }

        // type check
        val expectedType = schema["type"]?.jsonPrimitive?.contentOrNull
        if (expectedType != null) {
            val actualType = jsonType(value)
            if (!typeCompatible(actualType, expectedType)) {
                errors.add(err(path, "Expected type '$expectedType', got '$actualType'", "type_mismatch"))
                return errors // type mismatch makes further validation unreliable
            }
        }

        // Composition keywords
        errors.addAll(validateComposition(value, schema, path))

        // Type-specific validation
        when (value) {
            is JsonObject -> errors.addAll(validateObject(value, schema, path))
            is JsonArray -> errors.addAll(validateArray(value, schema, path))
            is JsonPrimitive -> errors.addAll(validatePrimitive(value, schema, path))
            else -> {}
        }

        return errors
    }

    private fun validateObject(obj: JsonObject, schema: JsonObject, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        val properties = schema["properties"]?.jsonObject
        val required = schema["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val additionalProperties = schema["additionalProperties"]

        // Required fields
        for (field in required) {
            if (!obj.containsKey(field)) {
                errors.add(err("$path/$field", "Required field '$field' is missing", "required"))
            }
        }

        // Validate each property against its schema
        if (properties != null) {
            for ((key, value) in obj.entries) {
                val propSchema = properties[key]?.jsonObject
                if (propSchema != null) {
                    errors.addAll(validateValue(value, propSchema, "$path/$key"))
                } else if (additionalProperties is JsonPrimitive && additionalProperties.booleanOrNull == false) {
                    errors.add(err("$path/$key", "Additional property '$key' is not allowed", "additional_properties"))
                }
            }
        }

        // minProperties / maxProperties
        schema["minProperties"]?.jsonPrimitive?.intOrNull?.let { min ->
            if (obj.size < min) errors.add(err(path, "Object must have at least $min properties", "min_properties"))
        }
        schema["maxProperties"]?.jsonPrimitive?.intOrNull?.let { max ->
            if (obj.size > max) errors.add(err(path, "Object must have at most $max properties", "max_properties"))
        }

        return errors
    }

    private fun validateArray(arr: JsonArray, schema: JsonObject, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        schema["minItems"]?.jsonPrimitive?.intOrNull?.let { min ->
            if (arr.size < min) errors.add(err(path, "Array must have at least $min items", "min_items"))
        }
        schema["maxItems"]?.jsonPrimitive?.intOrNull?.let { max ->
            if (arr.size > max) errors.add(err(path, "Array must have at most $max items", "max_items"))
        }

        val itemsSchema = schema["items"]?.jsonObject
        if (itemsSchema != null) {
            arr.forEachIndexed { index, item ->
                errors.addAll(validateValue(item, itemsSchema, "$path/$index"))
            }
        }

        // uniqueItems
        if (schema["uniqueItems"]?.jsonPrimitive?.booleanOrNull == true) {
            if (arr.toSet().size != arr.size) {
                errors.add(err(path, "Array items must be unique", "unique_items"))
            }
        }

        return errors
    }

    private fun validatePrimitive(value: JsonPrimitive, schema: JsonObject, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        if (value.isString) {
            val str = value.content

            schema["minLength"]?.jsonPrimitive?.intOrNull?.let { min ->
                if (str.length < min) errors.add(err(path, "String length must be >= $min", "min_length"))
            }
            schema["maxLength"]?.jsonPrimitive?.intOrNull?.let { max ->
                if (str.length > max) errors.add(err(path, "String length must be <= $max", "max_length"))
            }
            schema["pattern"]?.jsonPrimitive?.contentOrNull?.let { pattern ->
                if (!Regex(pattern).containsMatchIn(str)) {
                    errors.add(err(path, "String does not match pattern: $pattern", "pattern"))
                }
            }
            schema["format"]?.jsonPrimitive?.contentOrNull?.let { format ->
                errors.addAll(validateFormat(str, format, path))
            }
        } else {
            val num = value.doubleOrNull
            if (num != null) {
                schema["minimum"]?.jsonPrimitive?.doubleOrNull?.let { min ->
                    if (num < min) errors.add(err(path, "Value must be >= $min", "minimum"))
                }
                schema["maximum"]?.jsonPrimitive?.doubleOrNull?.let { max ->
                    if (num > max) errors.add(err(path, "Value must be <= $max", "maximum"))
                }
                schema["exclusiveMinimum"]?.jsonPrimitive?.doubleOrNull?.let { min ->
                    if (num <= min) errors.add(err(path, "Value must be > $min", "exclusive_minimum"))
                }
                schema["exclusiveMaximum"]?.jsonPrimitive?.doubleOrNull?.let { max ->
                    if (num >= max) errors.add(err(path, "Value must be < $max", "exclusive_maximum"))
                }
                schema["multipleOf"]?.jsonPrimitive?.doubleOrNull?.let { divisor ->
                    if (divisor != 0.0 && num % divisor != 0.0) {
                        errors.add(err(path, "Value must be multiple of $divisor", "multiple_of"))
                    }
                }
            }
        }

        return errors
    }

    private fun validateComposition(value: JsonElement, schema: JsonObject, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()

        schema["allOf"]?.jsonArray?.let { schemas ->
            for (s in schemas) {
                errors.addAll(validateValue(value, s.jsonObject, path))
            }
        }

        schema["anyOf"]?.jsonArray?.let { schemas ->
            val allInvalid = schemas.all { s -> validateValue(value, s.jsonObject, path).isNotEmpty() }
            if (allInvalid) errors.add(err(path, "Value must match at least one of anyOf schemas", "any_of"))
        }

        schema["oneOf"]?.jsonArray?.let { schemas ->
            val matchCount = schemas.count { s -> validateValue(value, s.jsonObject, path).isEmpty() }
            if (matchCount != 1) errors.add(err(path, "Value must match exactly one of oneOf schemas (matched $matchCount)", "one_of"))
        }

        schema["not"]?.jsonObject?.let { notSchema ->
            if (validateValue(value, notSchema, path).isEmpty()) {
                errors.add(err(path, "Value must NOT match the 'not' schema", "not"))
            }
        }

        return errors
    }

    private fun validateFormat(str: String, format: String, path: String): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        when (format) {
            "email" -> if (!str.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
                errors.add(err(path, "Value must be a valid email address", "format_email"))
            }
            "uri", "iri" -> if (!str.matches(Regex("^[a-zA-Z][a-zA-Z0-9+\\-.]*:.*"))) {
                errors.add(err(path, "Value must be a valid URI", "format_uri"))
            }
            "date" -> if (!str.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
                errors.add(err(path, "Value must be a valid date (YYYY-MM-DD)", "format_date"))
            }
            "date-time" -> if (!str.matches(Regex("^\\d{4}-\\d{2}-\\d{2}T.*"))) {
                errors.add(err(path, "Value must be a valid date-time (ISO 8601)", "format_date_time"))
            }
            "uuid" -> if (!str.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))) {
                errors.add(err(path, "Value must be a valid UUID", "format_uuid"))
            }
        }
        return errors
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private fun jsonType(element: JsonElement): String = when (element) {
        is JsonObject -> "object"
        is JsonArray -> "array"
        is JsonNull -> "null"
        is JsonPrimitive -> when {
            element.isString -> "string"
            element.booleanOrNull != null -> "boolean"
            element.longOrNull != null -> "integer"
            element.doubleOrNull != null -> "number"
            else -> "string"
        }
    }

    private fun typeCompatible(actual: String, expected: String): Boolean = when {
        actual == expected -> true
        actual == "integer" && expected == "number" -> true
        else -> false
    }

    private fun err(path: String, message: String, code: String) =
        SchemaValidationError(path = path, message = message, code = code)
}
