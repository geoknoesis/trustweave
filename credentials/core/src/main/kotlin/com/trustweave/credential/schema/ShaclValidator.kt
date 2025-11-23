package com.trustweave.credential.schema

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.core.SchemaFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * SHACL (Shapes Constraint Language) validator implementation.
 * 
 * Supports SHACL Core validation for RDF data structures.
 * Validates credential structure and credentialSubject against SHACL shapes.
 * 
 * **Note**: This is a basic implementation. A full implementation would
 * require a SHACL validation library (e.g., TopQuadrant/shacl-java, Apache Jena SHACL).
 * 
 * **Example Usage**:
 * ```kotlin
 * val validator = ShaclValidator()
 * SchemaValidatorRegistry.register(validator)
 * 
 * val schema = buildJsonObject {
 *     put("@context", "https://www.w3.org/ns/shacl#")
 *     put("sh:targetClass", "EducationCredential")
 *     put("sh:property", buildJsonArray {
 *         add(buildJsonObject {
 *             put("sh:path", "credentialSubject.degree")
 *             put("sh:datatype", "http://www.w3.org/2001/XMLSchema#string")
 *             put("sh:minCount", 1)
 *         })
 *     })
 * }
 * 
 * val result = validator.validate(credential, schema)
 * ```
 */
class ShaclValidator : SchemaValidator {
    override val schemaFormat = SchemaFormat.SHACL
    
    override suspend fun validate(
        credential: VerifiableCredential,
        schema: JsonObject
    ): SchemaValidationResult {
        val errors = mutableListOf<SchemaValidationError>()
        
        // Validate credential structure
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
        
        // Validate against SHACL shape
        val shapeErrors = validateShaclShape(credential, schema)
        errors.addAll(shapeErrors)
        
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
        
        // Extract SHACL properties
        val properties = schema["sh:property"]?.let {
            when (it) {
                is kotlinx.serialization.json.JsonArray -> it.mapNotNull { elem ->
                    elem.jsonObject
                }
                is kotlinx.serialization.json.JsonObject -> listOf(it)
                else -> emptyList()
            }
        } ?: emptyList()
        
        if (subject is kotlinx.serialization.json.JsonObject) {
            // Validate each property constraint
            for (propertyShape in properties) {
                val path = propertyShape["sh:path"]?.jsonPrimitive?.content
                if (path != null) {
                    val propertyErrors = validatePropertyConstraint(subject, path, propertyShape)
                    errors.addAll(propertyErrors)
                }
            }
            
            // Check required properties (sh:minCount > 0)
            for (propertyShape in properties) {
                val path = propertyShape["sh:path"]?.jsonPrimitive?.content
                val minCount = propertyShape["sh:minCount"]?.jsonPrimitive?.intOrNull ?: 0
                
                if (path != null && minCount > 0) {
                    val fieldName = path.substringAfterLast("/").substringAfterLast(":")
                    if (!subject.containsKey(fieldName)) {
                        errors.add(SchemaValidationError(
                            path = "/credentialSubject/$fieldName",
                            message = "Required property '$fieldName' is missing (minCount: $minCount)",
                            code = "missing_required_property"
                        ))
                    }
                }
            }
        }
        
        return SchemaValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
    
    /**
     * Validate credential against SHACL shape.
     */
    private fun validateShaclShape(
        credential: VerifiableCredential,
        schema: JsonObject
    ): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        
        // Check target class if specified
        val targetClass = schema["sh:targetClass"]?.jsonPrimitive?.content
        if (targetClass != null) {
            val credentialType = credential.type.find { it != "VerifiableCredential" }
            if (credentialType != targetClass) {
                errors.add(SchemaValidationError(
                    path = "/type",
                    message = "Credential type '$credentialType' does not match SHACL target class '$targetClass'",
                    code = "type_mismatch"
                ))
            }
        }
        
        // Validate credentialSubject against property shapes
        // Note: validateCredentialSubject is suspend, but this function is not
        // For now, we'll do basic validation inline
        val subject = credential.credentialSubject
        if (subject is JsonObject) {
            val properties = schema["sh:property"]?.let {
                when (it) {
                    is JsonArray -> it
                    else -> null
                }
            }
            if (properties != null) {
                for (propertyShape in properties) {
                    val propertyObj = propertyShape.jsonObject
                    val path = propertyObj["sh:path"]?.jsonPrimitive?.contentOrNull
                    val minCount = propertyObj["sh:minCount"]?.jsonPrimitive?.intOrNull ?: 0
                    
                    if (path != null && minCount > 0) {
                        val fieldName = path.substringAfterLast("/").substringAfterLast(":")
                        if (!subject.containsKey(fieldName)) {
                            errors.add(SchemaValidationError(
                                path = "/credentialSubject/$fieldName",
                                message = "Required property '$fieldName' is missing (minCount: $minCount)",
                                code = "missing_required_property"
                            ))
                        }
                    }
                }
            }
        }
        
        return errors
    }
    
    /**
     * Validate a property constraint.
     */
    private fun validatePropertyConstraint(
        subject: kotlinx.serialization.json.JsonObject,
        path: String,
        propertyShape: JsonObject
    ): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        
        // Extract field name from path (e.g., "credentialSubject.degree" -> "degree")
        val fieldName = path.substringAfterLast("/").substringAfterLast(":").substringAfterLast(".")
        
        val fieldValue = subject[fieldName]
        
        // Check datatype constraint
        val datatype = propertyShape["sh:datatype"]?.jsonPrimitive?.content
        if (datatype != null && fieldValue != null) {
            val datatypeErrors = validateDatatype(fieldValue, datatype, fieldName)
            errors.addAll(datatypeErrors)
        }
        
        // Check minCount constraint
        val minCount = propertyShape["sh:minCount"]?.jsonPrimitive?.intOrNull
        if (minCount != null && minCount > 0 && fieldValue == null) {
            errors.add(SchemaValidationError(
                path = "/credentialSubject/$fieldName",
                message = "Property '$fieldName' is required (minCount: $minCount)",
                code = "min_count_violation"
            ))
        }
        
        // Check maxCount constraint
        val maxCount = propertyShape["sh:maxCount"]?.jsonPrimitive?.intOrNull
        if (maxCount != null && maxCount == 1 && fieldValue is kotlinx.serialization.json.JsonArray && fieldValue.size > 1) {
            errors.add(SchemaValidationError(
                path = "/credentialSubject/$fieldName",
                message = "Property '$fieldName' exceeds maxCount ($maxCount)",
                code = "max_count_violation"
            ))
        }
        
        // Check minLength constraint
        val minLength = propertyShape["sh:minLength"]?.jsonPrimitive?.intOrNull
        if (minLength != null && fieldValue is kotlinx.serialization.json.JsonPrimitive && fieldValue.isString) {
            if (fieldValue.content.length < minLength) {
                errors.add(SchemaValidationError(
                    path = "/credentialSubject/$fieldName",
                    message = "Property '$fieldName' length must be at least $minLength",
                    code = "min_length_violation"
                ))
            }
        }
        
        // Check maxLength constraint
        val maxLength = propertyShape["sh:maxLength"]?.jsonPrimitive?.intOrNull
        if (maxLength != null && fieldValue is kotlinx.serialization.json.JsonPrimitive && fieldValue.isString) {
            if (fieldValue.content.length > maxLength) {
                errors.add(SchemaValidationError(
                    path = "/credentialSubject/$fieldName",
                    message = "Property '$fieldName' length must be at most $maxLength",
                    code = "max_length_violation"
                ))
            }
        }
        
        // Check pattern constraint
        val pattern = propertyShape["sh:pattern"]?.jsonPrimitive?.content
        if (pattern != null && fieldValue is kotlinx.serialization.json.JsonPrimitive && fieldValue.isString) {
            try {
                val regex = java.util.regex.Pattern.compile(pattern)
                if (!regex.matcher(fieldValue.content).matches()) {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' does not match pattern: $pattern",
                        code = "pattern_violation"
                    ))
                }
            } catch (e: Exception) {
                // Invalid regex pattern - add warning
                errors.add(SchemaValidationError(
                    path = "/credentialSubject/$fieldName",
                    message = "Invalid pattern constraint: $pattern",
                    code = "invalid_pattern"
                ))
            }
        }
        
        // Check in constraint (enum-like)
        val `in` = propertyShape["sh:in"]?.let {
            when (it) {
                is kotlinx.serialization.json.JsonArray -> it.mapNotNull { elem ->
                    elem.jsonPrimitive?.content
                }
                else -> emptyList()
            }
        }
        if (`in` != null && fieldValue is kotlinx.serialization.json.JsonPrimitive && fieldValue.isString) {
            if (!`in`.contains(fieldValue.content)) {
                errors.add(SchemaValidationError(
                    path = "/credentialSubject/$fieldName",
                    message = "Property '$fieldName' value '${fieldValue.content}' is not in allowed values: ${`in`.joinToString()}"
                ))
            }
        }
        
        return errors
    }
    
    /**
     * Validate datatype constraint.
     */
    private fun validateDatatype(
        value: kotlinx.serialization.json.JsonElement,
        datatype: String,
        fieldName: String
    ): List<SchemaValidationError> {
        val errors = mutableListOf<SchemaValidationError>()
        
        when (datatype) {
            "http://www.w3.org/2001/XMLSchema#string",
            "xsd:string" -> {
                if (value !is kotlinx.serialization.json.JsonPrimitive || !value.isString) {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' must be a string",
                        code = "datatype_mismatch"
                    ))
                }
            }
            "http://www.w3.org/2001/XMLSchema#integer",
            "xsd:integer" -> {
                if (value !is kotlinx.serialization.json.JsonPrimitive || value.longOrNull == null) {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' must be an integer",
                        code = "datatype_mismatch"
                    ))
                }
            }
            "http://www.w3.org/2001/XMLSchema#decimal",
            "http://www.w3.org/2001/XMLSchema#double",
            "xsd:decimal",
            "xsd:double" -> {
                if (value !is kotlinx.serialization.json.JsonPrimitive || value.doubleOrNull == null) {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' must be a number",
                        code = "datatype_mismatch"
                    ))
                }
            }
            "http://www.w3.org/2001/XMLSchema#boolean",
            "xsd:boolean" -> {
                if (value !is kotlinx.serialization.json.JsonPrimitive || value.booleanOrNull == null) {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' must be a boolean",
                        code = "datatype_mismatch"
                    ))
                }
            }
            "http://www.w3.org/2001/XMLSchema#dateTime",
            "xsd:dateTime" -> {
                if (value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                    try {
                        java.time.Instant.parse(value.content)
                    } catch (e: Exception) {
                        errors.add(SchemaValidationError(
                            path = "/credentialSubject/$fieldName",
                            message = "Property '$fieldName' must be a valid dateTime",
                            code = "datatype_mismatch"
                        ))
                    }
                } else {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' must be a dateTime string",
                        code = "datatype_mismatch"
                    ))
                }
            }
            "http://www.w3.org/2001/XMLSchema#anyURI",
            "xsd:anyURI" -> {
                if (value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                    val uri = value.content
                    if (!uri.startsWith("http://") && !uri.startsWith("https://") && !uri.startsWith("did:") && !uri.startsWith("urn:")) {
                        errors.add(SchemaValidationError(
                            path = "/credentialSubject/$fieldName",
                            message = "Property '$fieldName' must be a valid URI",
                            code = "datatype_mismatch"
                        ))
                    }
                } else {
                    errors.add(SchemaValidationError(
                        path = "/credentialSubject/$fieldName",
                        message = "Property '$fieldName' must be a URI string",
                        code = "datatype_mismatch"
                    ))
                }
            }
        }
        
        return errors
    }
}

