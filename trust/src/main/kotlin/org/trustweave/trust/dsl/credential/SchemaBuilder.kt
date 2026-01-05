package org.trustweave.trust.dsl.credential

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.credential.schema.SchemaRegistrationResult
import org.trustweave.credential.schema.SchemaValidationResult
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.trust.TrustWeave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.trust.dsl.credential.JsonObjectBuilder

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
    private val schemaRegistry: SchemaRegistry? = null
) {
    private var schemaId: String? = null
    private var format: SchemaFormat = SchemaFormat.JSON_SCHEMA
    private var definition: JsonObject? = null

    /**
     * Set schema ID.
     */
    fun id(schemaId: String) {
        this.schemaId = schemaId
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
        val builder = JsonObjectBuilder()
        builder.block()
        this.definition = builder.build()
    }

    /**
     * Build SHACL shape definition.
     */
    fun shacl(block: JsonObjectBuilder.() -> Unit) {
        this.format = SchemaFormat.SHACL
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
        val registry = schemaRegistry ?: throw IllegalStateException(
            "SchemaRegistry is not available. Configure it in TrustWeave.build { ... }"
        )
        val id = schemaId ?: throw IllegalStateException(
            "Schema ID is required. Use id(\"https://example.com/schemas/...\")"
        )
        val def = definition ?: throw IllegalStateException(
            "Schema definition is required. Use jsonSchema { } or shacl { }"
        )

        registry.registerSchema(SchemaId(id), format, def)
    }

    /**
     * Validate a credential against this schema.
     *
     * @param credential Credential to validate
     * @return Validation result
     */
    suspend fun validate(credential: VerifiableCredential): SchemaValidationResult = withContext(Dispatchers.IO) {
        val registry = schemaRegistry ?: throw IllegalStateException(
            "SchemaRegistry is not available. Configure it in TrustWeave.build { ... }"
        )
        val id = schemaId ?: throw IllegalStateException(
            "Schema ID is required. Use id(\"https://example.com/schemas/...\")"
        )

        registry.validate(credential, SchemaId(id))
    }
}

// JsonObjectBuilder is defined in CredentialDsl.kt and shared across DSL files

/**
 * Extension function to access schema operations.
 */
fun TrustWeave.schema(schemaId: String? = null, block: SchemaBuilder.() -> Unit = {}): SchemaBuilder {
    val schemaRegistry = getSchemaRegistry()
    val builder = SchemaBuilder(schemaRegistry)
    if (schemaId != null) {
        builder.id(schemaId)
    }
    builder.block()
    return builder
}

/**
 * Extension function to register a schema.
 */
suspend fun TrustWeave.registerSchema(block: SchemaBuilder.() -> Unit): SchemaRegistrationResult {
    val schemaRegistry = getSchemaRegistry()
    val builder = SchemaBuilder(schemaRegistry)
    builder.block()
    return builder.register()
}

