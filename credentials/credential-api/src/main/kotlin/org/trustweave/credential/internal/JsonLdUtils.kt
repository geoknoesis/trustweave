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
     * **Name-based check:** the document is round-tripped through JSON-LD
     * expansion + compaction against its own `@context`, and every declared
     * `credentialSubject` claim NAME (other than `id`/`type`, **at any nesting depth**,
     * arrays included, counted as a multiset) must survive the round-trip. A dropped term
     * never reappears after compaction, so it is detected by name — a property-count
     * comparison is NOT used, because a term that expands into additional properties
     * (e.g. a context that maps `type` to a regular property the declared-name collector
     * ignores) could otherwise mask a dropped term. Throws
     * [SerializationException.EncodeFailed], naming the missing claims, when one or more
     * claims were dropped.
     *
     * Fail-closed:
     * - a `credentialSubject` that is present but is neither a JSON object nor an array
     *   of objects throws — its claims cannot be checked for dropped terms;
     * - a round-trip in which the `credentialSubject` node itself cannot be located
     *   (e.g. the context does not define `credentialSubject` at all) throws;
     * - a context that compacts a declared term back to a *different* alias also throws
     *   (the claim cannot be proven preserved by name).
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

        val declaredNames = mutableListOf<String>()
        collectDeclaredClaimNames(subject, declaredNames)
        if (declaredNames.isEmpty()) return

        // Round-trip the document through expansion + compaction against its own @context.
        // Terms not defined by the @context are dropped at expansion and cannot reappear
        // at compaction; defined terms compact back to their original names.
        val contextValue: JsonValue = jakartaDocument["@context"] ?: JsonValue.EMPTY_JSON_OBJECT
        val compacted: jakarta.json.JsonObject = try {
            val contextDocument = JsonDocument.of(
                Json.createObjectBuilder().add("@context", contextValue).build()
            )
            JsonLd.compact(JsonDocument.of(jakartaDocument), contextDocument)
                .loader(JsonLdContextLoader.createDocumentLoader())
                .compactToRelative(false)
                .get()
        } catch (e: Exception) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason = "JSON-LD compaction failed while checking for dropped claims: ${e.message}"
            )
        }

        // Note: when every claim was dropped (or the credentialSubject term itself is not
        // defined), the subject node may compact away entirely (e.g. to a bare IRI string)
        // — zero located nodes then simply means zero surviving claim names, and every
        // declared claim is reported missing below.
        val subjectNodes = mutableListOf<jakarta.json.JsonObject>()
        collectCompactedSubjectNodes(compacted, subjectNodes)

        val survivingNames = mutableListOf<String>()
        subjectNodes.forEach { collectCompactedClaimNames(it, survivingNames) }

        // Multiset comparison by NAME: every declared occurrence of a claim name must be
        // matched by a surviving occurrence. Extra surviving properties cannot mask a
        // missing name.
        val survivingCounts = survivingNames.groupingBy { it }.eachCount()
        val missing = declaredNames.groupingBy { it }.eachCount()
            .filter { (name, declaredCount) -> (survivingCounts[name] ?: 0) < declaredCount }
            .keys
        if (missing.isNotEmpty()) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason = "JSON-LD canonicalization dropped credentialSubject claims: " +
                    "${missing.sorted()} (declared, nested claims included, but absent after a " +
                    "JSON-LD expansion/compaction round-trip). Claims whose terms are not defined " +
                    "in the credential's @context are silently removed and would NOT be covered " +
                    "by the proof signature. Declare an @context that defines every " +
                    "credentialSubject term (e.g. register a context via JsonLdContextLoader and " +
                    "reference it from the credential's @context)."
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
     * Mirror of [collectDeclaredClaimNames] for the jakarta.json representation produced
     * by JSON-LD compaction: recursively collect non-`id`/`type`, non-keyword property
     * names of the compacted subject node(s).
     */
    private fun collectCompactedClaimNames(value: JsonValue?, into: MutableList<String>) {
        when (value?.valueType) {
            JsonValue.ValueType.OBJECT -> value.asJsonObject().forEach { (key, nested) ->
                if (key != "id" && key != "type" && !key.startsWith("@")) {
                    into.add(key)
                }
                collectCompactedClaimNames(nested, into)
            }
            JsonValue.ValueType.ARRAY -> value.asJsonArray().forEach {
                collectCompactedClaimNames(it, into)
            }
            else -> {}
        }
    }

    /**
     * Locate the `credentialSubject` node(s) in a compacted document: matched by the
     * compacted term name `credentialSubject` or by the full expanded IRI (when the
     * context does not define the term).
     */
    private fun collectCompactedSubjectNodes(
        value: JsonValue?,
        into: MutableList<jakarta.json.JsonObject>
    ) {
        when (value?.valueType) {
            JsonValue.ValueType.ARRAY ->
                value.asJsonArray().forEach { collectCompactedSubjectNodes(it, into) }
            JsonValue.ValueType.OBJECT -> {
                val obj = value.asJsonObject()
                val subjectValue = obj["credentialSubject"] ?: obj[CREDENTIAL_SUBJECT_IRI]
                when (subjectValue?.valueType) {
                    JsonValue.ValueType.ARRAY -> subjectValue.asJsonArray().forEach { node ->
                        if (node.valueType == JsonValue.ValueType.OBJECT) into.add(node.asJsonObject())
                    }
                    JsonValue.ValueType.OBJECT -> into.add(subjectValue.asJsonObject())
                    else -> {}
                }
                obj.values.forEach { nested ->
                    if (nested !== subjectValue) collectCompactedSubjectNodes(nested, into)
                }
            }
            else -> {}
        }
    }
}
