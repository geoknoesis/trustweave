package org.trustweave.credential.internal

import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.rdf.canon.RdfCanon
import com.apicatalog.rdf.nquads.NQuadsWriter
import jakarta.json.Json
import jakarta.json.JsonValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.trustweave.core.exception.SerializationException
import java.io.StringReader
import java.io.StringWriter

/**
 * JSON-LD utility functions for canonicalization and document conversion.
 *
 * This utility object centralizes JSON-LD operations used throughout the credential API,
 * particularly for VC-LD (Verifiable Credentials Linked Data) proof generation and verification.
 *
 * **Key Operations:**
 * - JSON-LD 1.1 expansion and RDF serialization via titanium-json-ld (a conformant
 *   JSON-LD 1.1 processor)
 * - RDF Dataset Canonicalization (RDFC-1.0, formerly URDNA2015) via titanium-rdfc,
 *   producing canonical N-Quads
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
 *   [JsonLdContextLoader]. The bundled W3C contexts are the official, unmodified 1.1
 *   documents, so canonical bytes are interoperable with conformant verifiers.
 *
 * **Note:** This is an internal utility and should not be used directly by API consumers.
 * It is used by proof engines for VC-LD operations.
 */
internal object JsonLdUtils {

    /** Expanded IRI of `credentialSubject` (identical in the VC 1.1 and VC 2.0 vocabularies). */
    private const val CREDENTIAL_SUBJECT_IRI = "https://www.w3.org/2018/credentials#credentialSubject"

    /** Hash algorithm used by RDFC-1.0 canonicalization (the specification default). */
    private const val RDF_CANON_HASH_ALGORITHM = "SHA-256"

    /**
     * Convert a kotlinx.serialization [JsonObject] into the jakarta.json representation
     * consumed by the titanium JSON-LD processor.
     *
     * The conversion round-trips through the serialized JSON text, which preserves every
     * value exactly as it would appear on the wire (numbers, booleans, nulls, nesting).
     */
    fun toJakartaObject(document: JsonObject): jakarta.json.JsonObject =
        Json.createReader(StringReader(document.toString())).use { it.readObject() }

    /**
     * Canonicalize a JSON-LD document to canonical N-Quads using RDFC-1.0 (URDNA2015).
     *
     * The document is deserialized to RDF (JSON-LD 1.1 `toRdf`) and the resulting dataset
     * is canonicalized with the RDF Dataset Canonicalization algorithm. This is essential
     * for signature generation and verification, as it ensures that semantically equivalent
     * JSON-LD documents produce identical byte sequences — and, because the official W3C
     * 1.1 contexts are used, the bytes are interoperable with conformant verifiers.
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
        val jakartaDocument = toJakartaObject(document)

        val canonical = try {
            val canon = RdfCanon.create(RDF_CANON_HASH_ALGORITHM)
            JsonLd.toRdf(JsonDocument.of(jakartaDocument))
                .loader(JsonLdContextLoader.createDocumentLoader())
                .provide(canon)
            val writer = StringWriter()
            canon.provide(NQuadsWriter(writer))
            writer.toString()
        } catch (e: Exception) {
            throw SerializationException.EncodeFailed(
                element = "json-ld-document",
                reason = "JSON-LD canonicalization (RDFC-1.0/URDNA2015) failed: ${e.message}"
            )
        }

        if (canonical.isBlank()) {
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
        verifyCredentialSubjectClaimsPreserved(document, jakartaDocument)

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
    private fun verifyCredentialSubjectClaimsPreserved(
        document: JsonObject,
        jakartaDocument: jakarta.json.JsonObject
    ) {
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

        val expanded: jakarta.json.JsonArray = try {
            JsonLd.expand(JsonDocument.of(jakartaDocument))
                .loader(JsonLdContextLoader.createDocumentLoader())
                .get()
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
    private fun countExpandedSubjectProperties(expanded: JsonValue?): Int {
        val subjectNodes = mutableListOf<jakarta.json.JsonObject>()
        collectSubjectNodes(expanded, subjectNodes)
        return subjectNodes.sumOf { node -> countExpandedNodeProperties(node) }
    }

    /**
     * Recursively count non-keyword (`@`-prefixed) property keys of an expanded JSON-LD
     * node, including the properties of nested nodes.
     */
    private fun countExpandedNodeProperties(value: JsonValue?): Int = when (value?.valueType) {
        JsonValue.ValueType.OBJECT -> value.asJsonObject().entries.sumOf { (key, nested) ->
            val self = if (!key.startsWith("@")) 1 else 0
            self + countExpandedNodeProperties(nested)
        }
        JsonValue.ValueType.ARRAY -> value.asJsonArray().sumOf { countExpandedNodeProperties(it) }
        else -> 0
    }

    private fun collectSubjectNodes(value: JsonValue?, into: MutableList<jakarta.json.JsonObject>) {
        when (value?.valueType) {
            JsonValue.ValueType.ARRAY -> value.asJsonArray().forEach { collectSubjectNodes(it, into) }
            JsonValue.ValueType.OBJECT -> {
                val obj = value.asJsonObject()
                val subjectValue = obj[CREDENTIAL_SUBJECT_IRI]
                when (subjectValue?.valueType) {
                    JsonValue.ValueType.ARRAY -> subjectValue.asJsonArray().forEach { node ->
                        if (node.valueType == JsonValue.ValueType.OBJECT) into.add(node.asJsonObject())
                    }
                    JsonValue.ValueType.OBJECT -> into.add(subjectValue.asJsonObject())
                    else -> {}
                }
                obj.values.forEach { nested ->
                    if (nested !== subjectValue) collectSubjectNodes(nested, into)
                }
            }
            else -> {}
        }
    }
}
