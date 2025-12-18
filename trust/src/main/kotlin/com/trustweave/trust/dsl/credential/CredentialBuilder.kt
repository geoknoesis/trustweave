package com.trustweave.trust.dsl.credential

import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.Evidence
import com.trustweave.credential.model.vc.RefreshService
import com.trustweave.credential.model.vc.TermsOfUse
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.core.identifiers.Iri
import com.trustweave.credential.model.vc.SubjectBuilder as VcSubjectBuilder
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

// Extension for years (not in standard library)
private val Int.years: kotlin.time.Duration get() = (this * 365).days

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
 *     expires(Clock.System.now().plus(10.years))
 *     schema("https://example.edu/schemas/degree.json")
 * }
 * ```
 */
class CredentialBuilder {
    private var id: String? = null
    private val types = mutableListOf<String>()
    private var issuer: String? = null
    private var subjectBuilder: VcSubjectBuilder? = null
    private var issuanceDate: Instant? = null
    private var expirationDate: Instant? = null
    private var credentialStatus: CredentialStatus? = null
    private var credentialSchema: CredentialSchema? = null
    private val evidenceList = mutableListOf<Evidence>()
    private var termsOfUse: List<TermsOfUse>? = null
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
     * Set credential issuer DID.
     * 
     * @param did The issuer DID
     */
    fun issuer(did: Did) {
        this.issuer = did.value
    }

    /**
     * Configure credential subject.
     */
    fun subject(block: VcSubjectBuilder.() -> Unit) {
        val builder = VcSubjectBuilder()
        builder.block()
        subjectBuilder = builder
    }
    
    /**
     * Configure credential subject with DID.
     */
    fun subject(did: Did, block: VcSubjectBuilder.() -> Unit = {}) {
        val builder = VcSubjectBuilder(did)
        builder.block()
        subjectBuilder = builder
    }
    
    /**
     * Configure credential subject with IRI string.
     */
    fun subject(iri: String, block: VcSubjectBuilder.() -> Unit = {}) {
        val builder = VcSubjectBuilder(Iri(iri))
        builder.block()
        subjectBuilder = builder
    }

    /**
     * Set issuance date.
     * 
     * If not provided, defaults to `Clock.System.now()` when building the credential.
     * 
     * **Example:**
     * ```kotlin
     * credential {
     *     issued(Clock.System.now())  // Explicit issuance date
     *     // Or omit for automatic: issued()
     * }
     * ```
     * 
     * @param date The issuance date (defaults to current time if not specified)
     */
    fun issued(date: Instant) {
        // Allow issuance date up to 1 minute in the future for clock skew tolerance
        val maxFutureDate = Clock.System.now().plus(1.minutes)
        require(date <= maxFutureDate) {
            "Issuance date cannot be more than 1 minute in the future. Got: $date"
        }
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
     * 
     * **Example:**
     * ```kotlin
     * credential {
     *     expires(365.days)  // Expires in 1 year
     * }
     * ```
     */
    fun expires(duration: kotlin.time.Duration) {
        val now = Clock.System.now()
        this.expirationDate = now.plus(duration)
    }

    /**
     * Set expiration date as duration from now (convenience alias).
     * 
     * This is an alias for `expires(duration)` for better readability.
     * 
     * **Example:**
     * ```kotlin
     * credential {
     *     issued(Clock.System.now())
     *     expiresIn(365.days)  // More readable: "expires in 1 year"
     * }
     * ```
     */
    fun expiresIn(duration: kotlin.time.Duration) {
        expires(duration)
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
     * @throws IllegalArgumentException if schemaId is blank or not a valid URI
     */
    fun schema(schemaId: String, type: String = "JsonSchemaValidator2018") {
        require(schemaId.isNotBlank()) { "Schema ID cannot be blank" }
        require(schemaId.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) { 
            "Schema ID must be a valid URI. Got: $schemaId" 
        }
        credentialSchema = CredentialSchema(SchemaId(schemaId), type)
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
        termsOfUse = listOf(builder.build())
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
        refreshService = RefreshService(Iri(id), type)
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


        // Auto-set issuance date if not provided (safe default)
        val issuanceDateInstant = issuanceDate ?: Clock.System.now()

        val subjectCredential = subjectBuilder?.build()
            ?: throw IllegalStateException(
                "Credential subject is required. Use subject { ... } to build the credential subject."
            )

        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = allTypes.map { CredentialType.fromString(it) },
            issuer = issuer?.let { Issuer.from(it) } 
                ?: throw IllegalStateException(
                    "Issuer is required. Use issuer(did) to specify the credential issuer DID."
                ),
            credentialSubject = subjectCredential,
            issuanceDate = issuanceDateInstant,
            expirationDate = expirationDate,
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
                        @Suppress("UNCHECKED_CAST")
                        (value as? Map<String, Any>)?.forEach { (nestedKey, nestedValue) ->
                            nestedKey to nestedValue
                        } ?: run {
                            // Fallback: convert to string if not a String-keyed map
                            "value" to value.toString()
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
     * Add a nested object using 'to' syntax.
     * 
     * **Example:**
     * ```kotlin
     * "degree" to {
     *     "type" to "BachelorDegree"
     *     "name" to "Bachelor of Science"
     * }
     * ```
     */
    infix fun String.to(block: JsonObjectBuilder.() -> Unit) {
        val builder = JsonObjectBuilder()
        builder.block()
        properties[this] = builder.build()
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
                            buildJsonObject {
                                @Suppress("UNCHECKED_CAST")
                                (it as? Map<String, Any?>)?.forEach { (key, v) ->
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
     * Set an array property with a list of object builders.
     * This allows creating arrays of objects using DSL syntax.
     * 
     * **Example:**
     * ```kotlin
     * "grades" to listOf(
     *     { "courseCode" to "CS101"; "grade" to "A" },
     *     { "courseCode" to "MATH101"; "grade" to "B" }
     * )
     * ```
     */
    infix fun String.to(blocks: List<JsonObjectBuilder.() -> Unit>) {
        properties[this] = JsonArray(blocks.map { block ->
            JsonObjectBuilder().apply(block).build()
        })
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
    private var statusPurpose: StatusPurpose = StatusPurpose.REVOCATION
    private var statusListIndex: String? = null
    private var statusListCredential: String? = null

    fun id(id: String) {
        this.id = id
    }

    fun type(type: String) {
        this.type = type
    }

    fun statusPurpose(purpose: String) {
        this.statusPurpose = when (purpose.lowercase()) {
            "revocation" -> StatusPurpose.REVOCATION
            "suspension" -> StatusPurpose.SUSPENSION
            else -> throw IllegalArgumentException("Invalid status purpose: $purpose. Must be 'revocation' or 'suspension'")
        }
    }

    fun statusListIndex(index: String) {
        this.statusListIndex = index
    }

    fun statusListCredential(credential: String) {
        this.statusListCredential = credential
    }

    fun build(): CredentialStatus {
        val statusId = id ?: throw IllegalStateException("Status ID is required")
        return CredentialStatus(
            id = StatusListId(statusId),
            type = type,
            statusPurpose = statusPurpose,
            statusListIndex = statusListIndex,
            statusListCredential = statusListCredential?.let { StatusListId(it) }
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
            id = id?.let { com.trustweave.credential.identifiers.CredentialId(it) },
            type = if (types.isEmpty()) listOf("Evidence") else types,
            evidenceDocument = evidenceDocument,
            verifier = verifier?.let { com.trustweave.credential.identifiers.IssuerId(it) },
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
        val termsObj = terms as? JsonObject ?: buildJsonObject { }
        return TermsOfUse(
            id = id,
            type = type,
            additionalProperties = termsObj
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

