package com.trustweave.trust.dsl.credential

import com.trustweave.credential.models.CredentialSchema
import com.trustweave.credential.models.CredentialStatus
import com.trustweave.credential.models.Evidence
import com.trustweave.credential.models.RefreshService
import com.trustweave.credential.models.TermsOfUse
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.SchemaFormat
import com.trustweave.trust.types.CredentialType
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Credential Builder DSL.
 *
 * Provides a fluent API for creating verifiable credentials with minimal boilerplate.
 *
 * **Example Usage**:
 * ```kotlin
 * val credential = credential {
 *     id("https://example.edu/credentials/123")
 *     type(CredentialType.Degree, CredentialType.Custom("BachelorDegreeCredential"))
 *     issuer("did:key:university")
 *     subject {
 *         id("did:key:student")
 *         "degree" {
 *             "type" to "BachelorDegree"
 *             "name" to "Bachelor of Science"
 *             "university" to "Example University"
 *         }
 *     }
 *     issued(Instant.now())
 *     expires(Instant.now().plus(10, ChronoUnit.YEARS))
 *     schema("https://example.edu/schemas/degree.json")
 * }
 * ```
 */
class CredentialBuilder {
    private var id: String? = null
    private val types = mutableListOf<String>()
    private var issuer: String? = null
    private var subjectBuilder: SubjectBuilder? = null
    private var issuanceDate: Instant? = null
    private var expirationDate: Instant? = null
    private var credentialStatus: CredentialStatus? = null
    private var credentialSchema: CredentialSchema? = null
    private val evidenceList = mutableListOf<Evidence>()
    private var termsOfUse: TermsOfUse? = null
    private var refreshService: RefreshService? = null

    /**
     * Set credential ID.
     * 
     * @param id Must be a valid URI or follow the format: `credential:<identifier>`
     * @throws IllegalArgumentException if id is blank or contains invalid characters
     */
    fun id(id: String) {
        require(id.isNotBlank()) { "Credential ID cannot be blank" }
        require(id.matches(Regex("^[a-zA-Z0-9._:/?#\\[\\]@!$&'()*+,;=%-]+$"))) { 
            "Credential ID contains invalid characters. Must be a valid URI or identifier." 
        }
        this.id = id
    }

    /**
     * Add credential type(s) using type-safe CredentialType instances.
     * "VerifiableCredential" is automatically added.
     * 
     * @param types One or more CredentialType instances (e.g., CredentialType.Education, CredentialType.Custom("MyType"))
     */
    fun type(vararg types: CredentialType) {
        this.types.addAll(types.map { it.value })
    }

    /**
     * Add credential type(s) using string values (for backward compatibility).
     * "VerifiableCredential" is automatically added.
     * 
     * @param types One or more credential type strings (e.g., "EducationCredential", "PersonCredential")
     */
    fun type(vararg types: String) {
        this.types.addAll(types)
    }

    /**
     * Set issuer DID.
     * 
     * @param did Must be a valid DID starting with "did:"
     * @throws IllegalArgumentException if did is blank or doesn't start with "did:"
     */
    fun issuer(did: String) {
        require(did.isNotBlank()) { "Issuer DID cannot be blank" }
        require(did.startsWith("did:")) { 
            "Issuer DID must start with 'did:'. Got: $did" 
        }
        this.issuer = did
    }

    /**
     * Configure credential subject.
     */
    fun subject(block: SubjectBuilder.() -> Unit) {
        val builder = SubjectBuilder()
        builder.block()
        subjectBuilder = builder
    }

    /**
     * Set issuance date.
     */
    fun issued(date: Instant) {
        this.issuanceDate = date
    }

    /**
     * Set expiration date.
     */
    fun expires(date: Instant) {
        this.expirationDate = date
    }

    /**
     * Set expiration date as duration from now.
     */
    fun expires(duration: Long, unit: ChronoUnit) {
        val now = Instant.now()
        this.expirationDate = now.plus(duration, unit)
    }

    /**
     * Set credential status (for revocation).
     */
    fun status(block: CredentialStatusBuilder.() -> Unit) {
        val builder = CredentialStatusBuilder()
        builder.block()
        credentialStatus = builder.build()
    }

    /**
     * Set credential schema.
     * 
     * @param schemaId Must be a valid URI (e.g., "https://example.com/schemas/degree.json")
     * @param type Schema validator type (default: "JsonSchemaValidator2018")
     * @param format Schema format (default: JSON_SCHEMA)
     * @throws IllegalArgumentException if schemaId is blank or not a valid URI
     */
    fun schema(schemaId: String, type: String = "JsonSchemaValidator2018", format: SchemaFormat = SchemaFormat.JSON_SCHEMA) {
        require(schemaId.isNotBlank()) { "Schema ID cannot be blank" }
        require(schemaId.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) { 
            "Schema ID must be a valid URI. Got: $schemaId" 
        }
        credentialSchema = CredentialSchema(schemaId, type, format)
    }

    /**
     * Add evidence.
     */
    fun evidence(block: EvidenceBuilder.() -> Unit) {
        val builder = EvidenceBuilder()
        builder.block()
        evidenceList.add(builder.build())
    }

    /**
     * Set terms of use.
     */
    fun termsOfUse(block: TermsOfUseBuilder.() -> Unit) {
        val builder = TermsOfUseBuilder()
        builder.block()
        termsOfUse = builder.build()
    }

    /**
     * Set refresh service.
     * 
     * @param id Must be a valid URI for the refresh service
     * @param type Service type identifier
     * @param endpoint Must be a valid URI for the service endpoint
     * @throws IllegalArgumentException if any parameter is blank or not a valid URI
     */
    fun refreshService(id: String, type: String, endpoint: String) {
        require(id.isNotBlank()) { "Refresh service ID cannot be blank" }
        require(type.isNotBlank()) { "Refresh service type cannot be blank" }
        require(endpoint.isNotBlank()) { "Refresh service endpoint cannot be blank" }
        require(id.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) { 
            "Refresh service ID must be a valid URI. Got: $id" 
        }
        require(endpoint.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) { 
            "Refresh service endpoint must be a valid URI. Got: $endpoint" 
        }
        refreshService = RefreshService(id, type, endpoint)
    }

    /**
     * Build the verifiable credential.
     */
    fun build(): VerifiableCredential {
        // Ensure "VerifiableCredential" is in types
        val allTypes = if (types.isEmpty() || !types.contains("VerifiableCredential")) {
            mutableListOf("VerifiableCredential").apply { addAll(types) }
        } else {
            types
        }

        val subject = subjectBuilder?.build()
            ?: throw IllegalStateException(
                "Credential subject is required. Use subject { ... } to build the credential subject."
            )

        val issuanceDateStr = issuanceDate?.toString()
            ?: throw IllegalStateException(
                "Issuance date is required. Use issued(Instant) to set the issuance date."
            )

        return VerifiableCredential(
            id = id,
            type = allTypes,
            issuer = issuer ?: throw IllegalStateException(
                "Issuer is required. Use issuer(did) to specify the credential issuer DID."
            ),
            credentialSubject = subject,
            issuanceDate = issuanceDateStr,
            expirationDate = expirationDate?.toString(),
            credentialStatus = credentialStatus,
            credentialSchema = credentialSchema,
            evidence = if (evidenceList.isEmpty()) null else evidenceList,
            proof = null, // Proof is added during issuance
            termsOfUse = termsOfUse,
            refreshService = refreshService
        )
    }
}

/**
 * Subject Builder DSL.
 *
 * Provides a fluent API for building credential subject JSON.
 */
class SubjectBuilder {
    private val properties = mutableMapOf<String, JsonElement>()

    /**
     * Set subject ID.
     * 
     * According to W3C Verifiable Credentials spec, subject IDs must be an IRI
     * (Internationalized Resource Identifier), which includes:
     * - A DID (starting with "did:")
     * - A URI/URL (starting with "http:", "https:", "urn:", etc.)
     * - Any valid IRI with a scheme
     * 
     * If the subject ID is omitted, the subject becomes a blank node in JSON-LD terms.
     * 
     * @param id Subject identifier (must be a valid IRI/URI)
     * @throws IllegalArgumentException if id is blank or not a valid IRI format
     */
    fun id(id: String) {
        require(id.isNotBlank()) { "Subject ID cannot be blank" }
        // W3C VC spec requires IRI format (URI, URL, DID, URN, etc.)
        // An IRI must have a scheme (e.g., "https:", "did:", "urn:", etc.)
        require(id.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) { 
            "Subject ID must be a valid IRI (URI, URL, DID, URN, etc.) with a scheme. " +
            "Examples: 'https://example.com/subject', 'did:example:123', 'urn:example:subject'. " +
            "Got: $id" 
        }
        properties["id"] = JsonPrimitive(id)
    }

    /**
     * Add claims from a map, handling nested structures.
     * 
     * This is a helper method to add multiple claims at once, properly handling
     * nested maps and primitive values.
     */
    internal fun addClaims(claims: Map<String, Any>) {
        claims.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> {
                    // Nested object
                    key {
                        (value as Map<String, Any>).forEach { (nestedKey, nestedValue) ->
                            nestedKey to nestedValue
                        }
                    }
                }
                else -> {
                    key to value
                }
            }
        }
    }

    /**
     * Set a property value.
     */
    operator fun String.invoke(block: JsonObjectBuilder.() -> Unit) {
        val builder = JsonObjectBuilder()
        builder.block()
        properties[this] = builder.build()
    }

    /**
     * Set a simple property value.
     */
    infix fun String.to(value: Any?) {
        properties[this] = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is JsonElement -> value
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * Build the credential subject JSON.
     */
    fun build(): JsonObject {
        return buildJsonObject {
            properties.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}

/**
 * JSON Object Builder for nested structures.
 */
class JsonObjectBuilder {
    private val properties = mutableMapOf<String, JsonElement>()

    /**
     * Set a nested property with a nested object builder.
     */
    operator fun String.invoke(block: JsonObjectBuilder.() -> Unit) {
        val builder = JsonObjectBuilder()
        builder.block()
        properties[this] = builder.build()
    }

    /**
     * Set a nested property.
     */
    infix fun String.to(value: Any?) {
        properties[this] = when (value) {
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is JsonElement -> value
            is List<*> -> JsonArray(value.map {
                when (it) {
                    is String -> JsonPrimitive(it)
                    is Number -> JsonPrimitive(it)
                    is Boolean -> JsonPrimitive(it)
                    else -> JsonPrimitive(it.toString())
                }
            })
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * Put a property value.
     * 
     * This method supports the SchemaDsl interface pattern.
     */
    fun put(key: String, value: String) {
        properties[key] = JsonPrimitive(value)
    }

    /**
     * Put a property value (number).
     */
    fun put(key: String, value: Number) {
        properties[key] = JsonPrimitive(value)
    }

    /**
     * Put a property value (boolean).
     */
    fun put(key: String, value: Boolean) {
        properties[key] = JsonPrimitive(value)
    }

    /**
     * Put a nested JSON object.
     */
    fun put(key: String, block: JsonObjectBuilder.() -> Unit) {
        val builder = JsonObjectBuilder()
        builder.block()
        properties[key] = builder.build()
    }

    /**
     * Put a JSON array.
     */
    fun put(key: String, values: List<JsonElement>) {
        properties[key] = JsonArray(values)
    }

    /**
     * Put a JsonObject directly.
     */
    fun put(key: String, value: JsonObject) {
        properties[key] = value
    }

    /**
     * Build nested JSON object.
     */
    fun build(): JsonObject {
        return buildJsonObject {
            properties.forEach { (key, value) ->
                put(key, value)
            }
        }
    }
}

/**
 * Credential Status Builder.
 */
class CredentialStatusBuilder {
    private var id: String? = null
    private var type: String = "StatusList2021Entry"
    private var statusPurpose: String = "revocation"
    private var statusListIndex: String? = null
    private var statusListCredential: String? = null

    fun id(id: String) {
        this.id = id
    }

    fun type(type: String) {
        this.type = type
    }

    fun statusPurpose(purpose: String) {
        this.statusPurpose = purpose
    }

    fun statusListIndex(index: String) {
        this.statusListIndex = index
    }

    fun statusListCredential(credential: String) {
        this.statusListCredential = credential
    }

    fun build(): CredentialStatus {
        return CredentialStatus(
            id = id ?: throw IllegalStateException("Status ID is required"),
            type = type,
            statusPurpose = statusPurpose,
            statusListIndex = statusListIndex,
            statusListCredential = statusListCredential
        )
    }
}

/**
 * Evidence Builder.
 */
class EvidenceBuilder {
    private var id: String? = null
    private val types = mutableListOf<String>()
    private var evidenceDocument: JsonObject? = null
    private var verifier: String? = null
    private var evidenceDate: String? = null

    fun id(id: String) {
        this.id = id
    }

    fun type(vararg types: String) {
        this.types.addAll(types)
    }

    fun document(block: JsonObjectBuilder.() -> Unit) {
        val builder = JsonObjectBuilder()
        builder.block()
        evidenceDocument = builder.build()
    }

    fun verifier(did: String) {
        this.verifier = did
    }

    fun date(date: String) {
        this.evidenceDate = date
    }

    fun build(): Evidence {
        return Evidence(
            id = id,
            type = if (types.isEmpty()) listOf("Evidence") else types,
            evidenceDocument = evidenceDocument,
            verifier = verifier,
            evidenceDate = evidenceDate
        )
    }
}

/**
 * Terms of Use Builder.
 */
class TermsOfUseBuilder {
    private var id: String? = null
    private var type: String? = null
    private var terms: JsonElement? = null

    fun id(id: String) {
        this.id = id
    }

    fun type(type: String) {
        this.type = type
    }

    fun terms(block: JsonObjectBuilder.() -> Unit) {
        val builder = JsonObjectBuilder()
        builder.block()
        terms = builder.build()
    }

    fun build(): TermsOfUse {
        return TermsOfUse(
            id = id,
            type = type,
            termsOfUse = terms ?: buildJsonObject { }
        )
    }
}

/**
 * DSL function to create a credential.
 */
fun credential(block: CredentialBuilder.() -> Unit): VerifiableCredential {
    val builder = CredentialBuilder()
    builder.block()
    return builder.build()
}

