package com.trustweave.credential.model.vc

import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.json.*

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
    private val id: Iri? = null
) {
    internal val properties = mutableMapOf<String, JsonElement>()
    
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
     * Add a nested object.
     * 
     * **Example:**
     * ```kotlin
     * "degree" to object {
     *     "type" to "BachelorDegree"
     *     "name" to "Bachelor of Science"
     * }
     * ```
     */
    infix fun String.to(block: JsonObjectBuilder.() -> Unit) {
        properties[this] = buildJsonObject(block)
    }
    
    /**
     * Add an array.
     * 
     * **Example:**
     * ```kotlin
     * "skills" to arrayOf("Kotlin", "Java", "Python")
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

