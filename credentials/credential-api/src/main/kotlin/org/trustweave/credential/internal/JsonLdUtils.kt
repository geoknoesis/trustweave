package org.trustweave.credential.internal

import com.github.jsonldjava.core.JsonLdOptions
import com.github.jsonldjava.core.JsonLdProcessor
import kotlinx.serialization.json.*
import org.trustweave.core.exception.SerializationException

/**
 * JSON-LD utility functions for canonicalization and document conversion.
 *
 * This utility object centralizes JSON-LD operations used throughout the credential API,
 * particularly for VC-LD (Verifiable Credentials Linked Data) proof generation and verification.
 *
 * **Key Operations:**
 * - JSON object to Map conversion for jsonld-java library compatibility
 * - JSON-LD canonicalization (URDNA2015) to N-Quads format
 * - Dropped-claims detection for `credentialSubject` properties
 *
 * **Security properties (fail-closed):**
 * - Canonicalization failures **throw** — there is no fallback to plain JSON serialization.
 *   A non-deterministic or empty signing input would silently weaken every signature.
 * - Canonicalization that produces no RDF statements throws, because an empty signing
 *   input covers nothing.
 * - If `credentialSubject` properties (at any nesting depth) are not defined by the
 *   document's `@context`, JSON-LD silently drops them from the canonical form, leaving
 *   them unsigned. [canonicalizeDocument] detects this and throws.
 * - Remote `@context` URLs are not fetched over the network by default; see
 *   [JsonLdContextLoader].
 *
 * **Note:** This is an internal utility and should not be used directly by API consumers.
 * It is used by proof engines for VC-LD operations.
 */
internal object JsonLdUtils {

    /** Expanded IRI of `credentialSubject` (identical in the VC 1.1 and VC 2.0 vocabularies). */
    private const val CREDENTIAL_SUBJECT_IRI = "https://www.w3.org/2018/credentials#credentialSubject"

    /**
     * Convert a kotlinx.serialization.json.JsonObject to a Map for jsonld-java library.
     *
     * Recursively converts all JSON elements to their Java equivalents.
     * This conversion is necessary because the jsonld-java library expects Java Map structures
     * rather than Kotlin JsonObject/JsonElement types.
     *
     * **Conversion Algorithm:**
     * 1. JsonPrimitive values are converted to their Java equivalents:
     *    - Strings: remain as String
     *    - Booleans: converted to Boolean
     *    - Numbers: converted to Long (integers) or Double (floating-point)
     *    - Other primitives: converted to String via content
     * 2. JsonArray values are converted to List<Any> by recursively processing each element
     * 3. JsonObject values are recursively converted using this same function
     * 4. JsonNull values are converted to String representation
     *
     * @param jsonObject The JSON object to convert
     * @return Map representation suitable for jsonld-java
     */
    fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
        return jsonObject.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is JsonArray -> value.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content
                        is JsonObject -> jsonObjectToMap(element)
                        is JsonArray -> element.toString() // Handle nested arrays
                        is JsonNull -> element.toString()
                    }
                }
                is JsonObject -> jsonObjectToMap(value)
                is JsonNull -> value.toString()
            }
        }
    }

    /**
     * Canonicalize a JSON-LD document to N-Quads format using URDNA2015.
     *
     * This canonicalization is essential for signature generation and verification, as it
     * ensures that semantically equivalent JSON-LD documents produce identical byte sequences.
     *
     * **Fail-closed behaviour:**
     * - Throws [SerializationException.EncodeFailed] if canonicalization fails for any
     *   reason (e.g. unresolvable `@context`). There is **no** fallback to plain JSON
     *   serialization: a fallback would make the signing input non-deterministic and mask
     *   context resolution failures.
     * - Throws [SerializationException.EncodeFailed] if canonicalization produces no RDF
     *   statements — an empty canonical form would mean the signature covers nothing.
     * - Throws [SerializationException.EncodeFailed] if `credentialSubject` properties
     *   (including nested ones) were dropped because they are not defined in the
     *   document's `@context` (such claims would not be covered by the signature).
     * - Throws [IllegalArgumentException] if the canonical form exceeds
     *   [SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES] (DoS protection).
     *
     * Remote contexts are resolved through [JsonLdContextLoader] (offline-first, remote
     * fetching disabled by default).
     *
     * @param document The JSON-LD document to canonicalize
     * @return Canonicalized document as N-Quads string
     */
    fun canonicalizeDocument(document: JsonObject): String {
        val documentMap = jsonObjectToMap(document)

        val canonical = try {
            JsonLdProcessor.normalize(documentMap, newJsonLdOptions())?.toString()
        } catch (e: Exception) {
            throw SerializationException.EncodeFailed(
                element = "json-ld-document",
                reason = "JSON-LD canonicalization (URDNA2015) failed: ${e.message}"
            )
        }

        if (canonical.isNullOrBlank()) {
            throw SerializationException.EncodeFailed(
                element = "json-ld-document",
                reason = "JSON-LD canonicalization produced no RDF statements; the document's " +
                    "@context is missing or does not define any of its terms. Refusing to sign or " +
                    "verify an empty canonical form."
            )
        }

        // DoS protection: enforce canonical document size limit.
        val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
        if (canonicalBytes.size > SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Canonicalized document exceeds maximum size of " +
                    "${SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES} bytes: " +
                    "${canonicalBytes.size} bytes"
            )
        }

        // Fail closed if @context silently dropped credentialSubject claims.
        verifyCredentialSubjectClaimsPreserved(document, documentMap)

        return canonical
    }

    /**
     * Detect `credentialSubject` claims that were silently dropped by JSON-LD processing.
     *
     * JSON-LD expansion removes properties whose terms are not defined by the active
     * `@context`. Such properties would be absent from the canonical N-Quads and therefore
     * **not covered by the signature** — an attacker could tamper with them freely.
     *
     * This guard expands the document and checks that every `credentialSubject` property
     * (other than `id`/`type`), **at any nesting depth** (nested objects and arrays
     * included), is represented in the expanded output. It throws
     * [SerializationException.EncodeFailed] when one or more claims were dropped.
     *
     * Fail-closed: a `credentialSubject` that is present but is neither a JSON object nor
     * an array of objects also throws — its claims cannot be checked for dropped terms.
     */
    private fun verifyCredentialSubjectClaimsPreserved(document: JsonObject, documentMap: Map<String, Any>) {
        val subject = document["credentialSubject"] ?: return
        if (subject !is JsonObject && subject !is JsonArray) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason = "credentialSubject must be a JSON object (or an array of objects), got " +
                    "${subject::class.simpleName}. Refusing to sign or verify a document whose " +
                    "subject claims cannot be checked against the @context for dropped terms."
            )
        }

        val claimNames = mutableListOf<String>()
        collectDeclaredClaimNames(subject, claimNames)
        if (claimNames.isEmpty()) return

        val expanded = try {
            JsonLdProcessor.expand(documentMap, newJsonLdOptions())
        } catch (e: Exception) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason = "JSON-LD expansion failed while checking for dropped claims: ${e.message}"
            )
        }

        val expandedPropertyCount = countExpandedSubjectProperties(expanded)
        if (expandedPropertyCount < claimNames.size) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason = "JSON-LD canonicalization dropped credentialSubject claims: " +
                    "${claimNames.size} claim(s) declared (${claimNames.sorted()}, nested claims " +
                    "included) but only $expandedPropertyCount survived JSON-LD expansion. Claims " +
                    "whose terms are not defined in the credential's @context are silently removed " +
                    "and would NOT be covered by the proof signature. Declare an @context that " +
                    "defines every credentialSubject term (e.g. register a context via " +
                    "JsonLdContextLoader and reference it from the credential's @context)."
            )
        }
    }

    /**
     * Recursively collect the names of all claim properties (excluding `id`, `type` and
     * JSON-LD keywords) declared on the `credentialSubject`, including properties of
     * nested objects and of objects inside arrays.
     */
    private fun collectDeclaredClaimNames(element: JsonElement, into: MutableList<String>) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                if (key != "id" && key != "type" && !key.startsWith("@")) {
                    into.add(key)
                }
                collectDeclaredClaimNames(value, into)
            }
            is JsonArray -> element.forEach { collectDeclaredClaimNames(it, into) }
            else -> {}
        }
    }

    /**
     * Count the non-keyword properties on the expanded `credentialSubject` node(s),
     * recursing into nested node objects and value lists so dropped nested terms are
     * detected as well.
     */
    private fun countExpandedSubjectProperties(expanded: Any?): Int {
        val subjectNodes = mutableListOf<Map<*, *>>()
        collectSubjectNodes(expanded, subjectNodes)
        return subjectNodes.sumOf { node -> countExpandedNodeProperties(node) }
    }

    /**
     * Recursively count non-keyword (`@`-prefixed) property keys of an expanded JSON-LD
     * node, including the properties of nested nodes.
     */
    private fun countExpandedNodeProperties(element: Any?): Int = when (element) {
        is Map<*, *> -> element.entries.sumOf { (key, value) ->
            val self = if (key is String && !key.startsWith("@")) 1 else 0
            self + countExpandedNodeProperties(value)
        }
        is List<*> -> element.sumOf { countExpandedNodeProperties(it) }
        else -> 0
    }

    private fun collectSubjectNodes(element: Any?, into: MutableList<Map<*, *>>) {
        when (element) {
            is List<*> -> element.forEach { collectSubjectNodes(it, into) }
            is Map<*, *> -> {
                val subjectValue = element[CREDENTIAL_SUBJECT_IRI]
                if (subjectValue is List<*>) {
                    subjectValue.filterIsInstance<Map<*, *>>().forEach { into.add(it) }
                } else if (subjectValue is Map<*, *>) {
                    into.add(subjectValue)
                }
                element.values.forEach { value ->
                    if (value !== subjectValue) collectSubjectNodes(value, into)
                }
            }
            else -> {}
        }
    }

    private fun newJsonLdOptions(): JsonLdOptions {
        val options = JsonLdOptions()
        options.format = CredentialConstants.JsonLdFormats.N_QUADS
        options.documentLoader = JsonLdContextLoader.createDocumentLoader()
        return options
    }
}
