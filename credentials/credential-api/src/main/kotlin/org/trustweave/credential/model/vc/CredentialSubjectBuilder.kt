package org.trustweave.credential.model.vc

import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlinx.serialization.json.*

/**
 * Wrapper type for arrays of objects to avoid JVM signature clash.
 */
class ArrayOfObjects(val blocks: List<SubjectObjectBuilder.() -> Unit>)

/**
 * Helper function to create arrays of objects.
 */
fun arrayOfObjects(vararg blocks: SubjectObjectBuilder.() -> Unit): ArrayOfObjects {
    return ArrayOfObjects(blocks.toList())
}

/**
 * Extension functions to add 'to' infix syntax to kotlinx.serialization.json.JsonObjectBuilder
 * for consistency with SubjectBuilder DSL.
 */
private infix fun String.to(value: String): kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
    put(this@to, value)
}

private infix fun String.to(value: Number): kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
    put(this@to, value)
}

private infix fun String.to(value: Boolean): kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {
    put(this@to, value)
}

// This won't work - need a different approach. Let me use a helper function instead.

/**
 * Sleek builder DSL for creating CredentialSubject with infix `to` syntax.
 * 
 * **Examples:**
 * ```kotlin
 * // Simple subject with properties
 * val subject = subject {
 *     "name" to "John Doe"
 *     "email" to "john@example.com"
 *     "age" to 30
 * }
 * 
 * // Subject with DID
 * val subject = subject(did) {
 *     "name" to "John Doe"
 *     "degree" to buildJsonObject {
 *         "type" to "BachelorDegree"
 *         "name" to "Bachelor of Science"
 *     }
 * }
 * 
 * // Nested objects
 * val subject = subject {
 *     "address" to buildJsonObject {
 *         "street" to "123 Main St"
 *         "city" to "New York"
 *     }
 * }
 * ```
 */
fun subject(
    id: Iri? = null,
    block: SubjectBuilder.() -> Unit = {}
): CredentialSubject {
    val builder = SubjectBuilder(id)
    builder.block()
    return builder.build()
}

/**
 * Create subject from DID.
 */
fun subject(
    did: Did,
    block: SubjectBuilder.() -> Unit = {}
): CredentialSubject {
    return subject(id = did, block = block)
}

/**
 * Create subject from IRI string.
 */
fun subject(
    iri: String,
    block: SubjectBuilder.() -> Unit = {}
): CredentialSubject {
    return subject(id = Iri(iri), block = block)
}

/**
 * Builder for CredentialSubject.
 */
class SubjectBuilder(
    private var id: Iri? = null
) {
    internal val properties = mutableMapOf<String, JsonElement>()
    
    /**
     * Set the subject ID.
     * 
     * @param idString Subject identifier as a string (DID, URI, URN, etc.)
     * @throws IllegalArgumentException if idString is blank or not a valid IRI format
     */
    fun id(idString: String) {
        require(idString.isNotBlank()) { "Subject ID cannot be blank" }
        require(idString.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) {
            "Subject ID must be a valid IRI (URI, URL, DID, URN, etc.) with a scheme. " +
            "Examples: 'https://example.com/subject', 'did:example:123', 'urn:example:subject'. " +
            "Got: $idString"
        }
        this.id = Iri(idString)
    }
    
    /**
     * Set the subject ID from a DID.
     */
    fun id(did: Did) {
        this.id = did
    }
    
    /**
     * Set the subject ID from an IRI.
     */
    fun id(iri: Iri) {
        this.id = iri
    }
    
    /**
     * Add a property using infix `to` syntax.
     * 
     * **Example:**
     * ```kotlin
     * "name" to "John Doe"
     * "age" to 30
     * "active" to true
     * ```
     */
    infix fun String.to(value: String) {
        properties[this] = JsonPrimitive(value)
    }
    
    /**
     * Add a number property.
     */
    infix fun String.to(value: Number) {
        properties[this] = JsonPrimitive(value)
    }
    
    /**
     * Add a boolean property.
     */
    infix fun String.to(value: Boolean) {
        properties[this] = JsonPrimitive(value)
    }
    
    /**
     * Add a JsonElement property (for nested objects, arrays, etc.).
     */
    infix fun String.to(value: JsonElement) {
        properties[this] = value
    }
    
    /**
     * Add a nested object using 'to' syntax with DSL-style builder.
     * 
     * **Example:**
     * ```kotlin
     * "degree" to {
     *     "type" to "BachelorDegree"
     *     "name" to "Bachelor of Science"
     * }
     * ```
     * 
     * Inside the block, use `"key" to value` syntax (DSL-style).
     */
    infix fun String.to(block: SubjectObjectBuilder.() -> Unit) {
        val builder = SubjectObjectBuilder()
        builder.block()
        properties[this] = builder.build()
    }
    
    /**
     * Add a nested object using invoke syntax (more JSON-like).
     * 
     * **Example:**
     * ```kotlin
     * "degree" {
     *     "type" to "BachelorDegree"
     *     "name" to "Bachelor of Science"
     * }
     * ```
     */
    operator fun String.invoke(block: SubjectObjectBuilder.() -> Unit) {
        val builder = SubjectObjectBuilder()
        builder.block()
        properties[this] = builder.build()
    }
    
    /**
     * Add an array of primitives or mixed types.
     * 
     * **Example:**
     * ```kotlin
     * "skills" to listOf("Kotlin", "Java", "Python")
     * "numbers" to listOf(1, 2, 3)
     * ```
     */
    infix fun String.to(value: List<*>) {
        properties[this] = buildJsonArray {
            value.forEach { item ->
                when (item) {
                    is String -> add(JsonPrimitive(item))
                    is Number -> add(JsonPrimitive(item))
                    is Boolean -> add(JsonPrimitive(item))
                    is JsonElement -> add(item)
                    else -> add(JsonPrimitive(item.toString()))
                }
            }
        }
    }
    
    /**
     * Add an array of objects using DSL syntax.
     * 
     * **Example:**
     * ```kotlin
     * "grades" to arrayOfObjects(
     *     { "courseCode" to "CS101"; "grade" to "A" },
     *     { "courseCode" to "MATH101"; "grade" to "B" }
     * )
     * ```
     */
    infix fun String.to(blocks: ArrayOfObjects) {
        properties[this] = JsonArray(blocks.blocks.map { block ->
            val builder = SubjectObjectBuilder()
            builder.block()
            builder.build()
        })
    }
    
    /**
     * Build the CredentialSubject.
     */
    fun build(): CredentialSubject {
        val subjectId = id ?: throw IllegalArgumentException(
            "Subject ID is required. Use subject(did) or subject(iri) to provide an ID."
        )
        return CredentialSubject(id = subjectId, claims = properties)
    }
}

/**
 * Extension to add properties to existing CredentialSubject.
 */
fun CredentialSubject.with(
    block: SubjectBuilder.() -> Unit
): CredentialSubject {
    val builder = SubjectBuilder(id)
    builder.properties.putAll(this.claims)
    builder.block()
    return builder.build()
}

/**
 * Extension to add a single property.
 */
infix fun CredentialSubject.with(key: String): SubjectPropertyBuilder {
    return SubjectPropertyBuilder(this, key)
}

/**
 * Builder for adding a single property to a subject.
 */
class SubjectPropertyBuilder(
    private val subject: CredentialSubject,
    private val key: String
) {
    infix fun value(value: String): CredentialSubject {
        return subject.with { this@SubjectPropertyBuilder.key to value }
    }
    
    infix fun value(value: Number): CredentialSubject {
        return subject.with { this@SubjectPropertyBuilder.key to value }
    }
    
    infix fun value(value: Boolean): CredentialSubject {
        return subject.with { this@SubjectPropertyBuilder.key to value }
    }
    
    infix fun value(value: JsonElement): CredentialSubject {
        return subject.with { this@SubjectPropertyBuilder.key to value }
    }
}

/**
 * Builder for nested objects in SubjectBuilder DSL.
 * Provides JSON-like syntax without exposing buildJsonObject.
 */
class SubjectObjectBuilder {
    private val properties = mutableMapOf<String, JsonElement>()
    
    /**
     * Add a nested property with a nested object builder.
     */
    operator fun String.invoke(block: SubjectObjectBuilder.() -> Unit) {
        val builder = SubjectObjectBuilder()
        builder.block()
        properties[this] = builder.build()
    }
    
    /**
     * Add a nested property using 'to' syntax.
     */
    infix fun String.to(value: Any?) {
        properties[this] = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is JsonElement -> value
            is List<*> -> {
                // Handle arrays - check if items are objects built via DSL
                JsonArray(value.map {
                    when (it) {
                        is String -> JsonPrimitive(it)
                        is Number -> JsonPrimitive(it)
                        is Boolean -> JsonPrimitive(it)
                        is JsonObject -> it
                        is Map<*, *> -> {
                            // Convert Map to JsonObject
                            @Suppress("UNCHECKED_CAST")
                            buildJsonObject {
                                (it as Map<String, Any?>).forEach { (key, v) ->
                                    put(key, when (v) {
                                        is String -> JsonPrimitive(v)
                                        is Number -> JsonPrimitive(v)
                                        is Boolean -> JsonPrimitive(v)
                                        is JsonElement -> v
                                        else -> JsonPrimitive(v.toString())
                                    })
                                }
                            }
                        }
                        else -> JsonPrimitive(it.toString())
                    }
                })
            }
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }
    }
    
    /**
     * Add a nested object using 'to' syntax.
     */
    infix fun String.to(block: SubjectObjectBuilder.() -> Unit) {
        val builder = SubjectObjectBuilder()
        builder.block()
        properties[this] = builder.build()
    }
    
    /**
     * Add an array of objects using DSL syntax.
     */
    infix fun String.to(blocks: List<SubjectObjectBuilder.() -> Unit>) {
        properties[this] = JsonArray(blocks.map { block ->
            val builder = SubjectObjectBuilder()
            builder.block()
            builder.build()
        })
    }
    
    /**
     * Build the JSON object.
     */
    fun build(): JsonObject {
        return buildJsonObject {
            properties.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}

