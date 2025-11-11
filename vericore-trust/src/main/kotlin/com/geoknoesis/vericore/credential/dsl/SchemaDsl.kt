package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.models.CredentialSchema
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.schema.SchemaRegistry
import com.geoknoesis.vericore.credential.schema.SchemaRegistrationResult
import com.geoknoesis.vericore.credential.schema.SchemaValidationResult
import com.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Schema Builder DSL.
 * 
 * Provides a fluent API for registering and validating credential schemas.
 * Supports both JSON Schema and SHACL formats.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Register JSON Schema
 * trustLayer.registerSchema {
 *     id("https://example.edu/schemas/degree")
 *     type("JsonSchemaValidator2018")
 *     jsonSchema {
 *         put("$schema", "http://json-schema.org/draft-07/schema#")
 *         put("type", "object")
 *         put("properties", buildJsonObject {
 *             put("degree", buildJsonObject {
 *                 put("type", "object")
 *                 put("properties", buildJsonObject {
 *                     put("type", buildJsonObject { put("type", "string") })
 *                     put("field", buildJsonObject { put("type", "string") })
 *                 })
 *             })
 *         })
 *     }
 * }
 * 
 * // Register SHACL
 * trustLayer.registerSchema {
 *     id("https://example.edu/schemas/degree-shacl")
 *     type("ShaclValidator2020")
 *     shacl {
 *         put("@context", "https://www.w3.org/ns/shacl#")
 *         put("sh:targetClass", "DegreeCredential")
 *         // ... SHACL shape definition
 *     }
 * }
 * 
 * // Validate credential
 * val result = trustLayer.schema("https://example.edu/schemas/degree")
 *     .validate(credential)
 * ```
 */
class SchemaBuilder(
    private val context: TrustLayerContext
) {
    private var schemaId: String? = null
    private var schemaType: String = "JsonSchemaValidator2018"
    private var format: SchemaFormat = SchemaFormat.JSON_SCHEMA
    private var definition: JsonObject? = null
    
    /**
     * Set schema ID.
     */
    fun id(schemaId: String) {
        this.schemaId = schemaId
    }
    
    /**
     * Set schema validator type.
     */
    fun type(type: String) {
        this.schemaType = type
    }
    
    /**
     * Set schema format explicitly.
     */
    fun format(format: SchemaFormat) {
        this.format = format
    }
    
    /**
     * Build JSON Schema definition.
     */
    fun jsonSchema(block: JsonObjectBuilder.() -> Unit) {
        this.format = SchemaFormat.JSON_SCHEMA
        this.schemaType = "JsonSchemaValidator2018"
        val builder = JsonObjectBuilder()
        builder.block()
        this.definition = builder.build()
    }
    
    /**
     * Build SHACL shape definition.
     */
    fun shacl(block: JsonObjectBuilder.() -> Unit) {
        this.format = SchemaFormat.SHACL
        this.schemaType = "ShaclValidator2020"
        val builder = JsonObjectBuilder()
        builder.block()
        this.definition = builder.build()
    }
    
    /**
     * Set schema definition directly.
     */
    fun definition(definition: JsonObject) {
        this.definition = definition
    }
    
    /**
     * Register the schema.
     * 
     * @return Registration result
     */
    suspend fun register(): SchemaRegistrationResult = withContext(Dispatchers.IO) {
        val id = schemaId ?: throw IllegalStateException(
            "Schema ID is required. Use id(\"https://example.com/schemas/...\")"
        )
        val def = definition ?: throw IllegalStateException(
            "Schema definition is required. Use jsonSchema { } or shacl { }"
        )
        
        val schema = CredentialSchema(
            id = id,
            type = schemaType,
            schemaFormat = format
        )
        
        SchemaRegistry.registerSchema(schema, def)
    }
    
    /**
     * Validate a credential against this schema.
     * 
     * @param credential Credential to validate
     * @return Validation result
     */
    suspend fun validate(credential: VerifiableCredential): SchemaValidationResult = withContext(Dispatchers.IO) {
        val id = schemaId ?: throw IllegalStateException(
            "Schema ID is required. Use id(\"https://example.com/schemas/...\")"
        )
        
        SchemaRegistry.validateCredential(credential, id)
    }
}

// JsonObjectBuilder is defined in CredentialDsl.kt and shared across DSL files

/**
 * Extension function to access schema operations using trust layer configuration.
 */
fun TrustLayerContext.schema(schemaId: String? = null, block: SchemaBuilder.() -> Unit = {}): SchemaBuilder {
    val builder = SchemaBuilder(this)
    if (schemaId != null) {
        builder.id(schemaId)
    }
    builder.block()
    return builder
}

/**
 * Extension function for direct schema operations on trust layer config.
 */
fun TrustLayerConfig.schema(schemaId: String? = null, block: SchemaBuilder.() -> Unit = {}): SchemaBuilder {
    return dsl().schema(schemaId, block)
}

/**
 * Extension function to register a schema using trust layer configuration.
 */
suspend fun TrustLayerContext.registerSchema(block: SchemaBuilder.() -> Unit): SchemaRegistrationResult {
    val builder = SchemaBuilder(this)
    builder.block()
    return builder.register()
}

/**
 * Extension function for direct schema registration on trust layer config.
 */
suspend fun TrustLayerConfig.registerSchema(block: SchemaBuilder.() -> Unit): SchemaRegistrationResult {
    return dsl().registerSchema(block)
}

