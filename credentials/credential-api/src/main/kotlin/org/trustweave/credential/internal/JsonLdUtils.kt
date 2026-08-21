package org.trustweave.credential.internal

import com.apicatalog.jsonld.JsonLd
import com.apicatalog.jsonld.document.JsonDocument
import com.apicatalog.jsonld.uri.UriUtils
import com.apicatalog.rdf.canon.RdfCanon
import com.apicatalog.rdf.nquads.NQuadsWriter
import jakarta.json.Json
import jakarta.json.JsonValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        enforcePreCanonicalizationBounds(document)

        val jakartaDocument = toJakartaObject(document)

        val canonical =
            try {
                val canon = RdfCanon.create(RDF_CANON_HASH_ALGORITHM)
                JsonLd
                    .toRdf(JsonDocument.of(jakartaDocument))
                    .loader(JsonLdContextLoader.createDocumentLoader())
                    .provide(canon)
                val writer = StringWriter()
                canon.provide(NQuadsWriter(writer))
                writer.toString()
            } catch (e: Exception) {
                throw SerializationException.EncodeFailed(
                    element = "json-ld-document",
                    reason = "JSON-LD canonicalization (RDFC-1.0/URDNA2015) failed: ${e.message}",
                )
            }

        if (canonical.isBlank()) {
            throw SerializationException.EncodeFailed(
                element = "json-ld-document",
                reason =
                    "JSON-LD canonicalization produced no RDF statements; the document's " +
                        "@context is missing or does not define any of its terms. Refusing to sign or " +
                        "verify an empty canonical form.",
            )
        }

        // DoS protection: enforce canonical document size limit.
        val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
        if (canonicalBytes.size > SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Canonicalized document exceeds maximum size of " +
                    "${SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES} bytes: " +
                    "${canonicalBytes.size} bytes",
            )
        }

        // Fail closed if @context silently dropped credentialSubject claims.
        verifyCredentialSubjectClaimsPreserved(document, jakartaDocument)

        // Fail closed if a credentialSubject.id is a relative IRI: JsonLd.toRdf drops every
        // triple whose subject is a relative IRI, leaving the subject's claims unsigned.
        verifyCredentialSubjectIdIsAbsolute(document)

        return canonical
    }

    /**
     * Fail closed when a `credentialSubject.id` is not a *valid absolute* IRI.
     *
     * RDFC-1.0 canonicalization runs JSON-LD `toRdf`, which **drops every RDF triple whose
     * subject is not a usable absolute IRI**. That covers two distinct failure modes:
     * - a *relative* IRI — a string with no scheme, e.g. a bare UUID `9bc8be44-...`, a
     *   fragment-only `#foo`, a bare path `subjects/123`, or a network-path `//host/path`; and
     * - a *syntactically invalid* IRI — one that carries a scheme delimiter (a colon) but is
     *   still not a legal IRI, e.g. `urn:has space`, `did:key:abc def`, `urn:a^b`, `urn:a|b`.
     *   These slip past a naive "colon before any slash" scheme heuristic, yet `toRdf` drops
     *   their triples just the same.
     *
     * In either case the subject's claims would be absent from the canonical N-Quads — **not
     * covered by the proof signature** — even though the credential still verifies. That is
     * forgeable claim/revocation data.
     *
     * The check uses titanium's own [UriUtils.isAbsoluteUri], which is exactly the predicate
     * `toRdf` applies when deciding whether a subject IRI is usable, so the guard cannot drift
     * from the canonicaliser's behaviour (no false accepts of an id `toRdf` would silently
     * drop, no false rejects of an id `toRdf` would keep).
     *
     * Rules (precise to avoid false positives):
     * - `credentialSubject` absent → nothing to check (e.g. proof configs / presentations).
     * - `credentialSubject` object → check its `id`; array of subjects → check each element.
     * - `id` absent or JSON null → ALLOWED: an anonymous subject is valid VC 2.0 and becomes
     *   a blank node whose triples ARE emitted and signed.
     * - `id` a string → it MUST be a valid absolute IRI (`http:`, `https:`, `did:`,
     *   `urn:uuid:`, …) per [UriUtils.isAbsoluteUri]. Otherwise throws
     *   [SerializationException.EncodeFailed].
     */
    private fun verifyCredentialSubjectIdIsAbsolute(document: JsonObject) {
        when (val subject = document["credentialSubject"]) {
            null -> return
            is JsonObject -> requireAbsoluteSubjectId(subject)
            is JsonArray ->
                subject.forEach { element ->
                    if (element is JsonObject) requireAbsoluteSubjectId(element)
                }
            else -> {} // non-object/array subjects are handled by the claims-preserved check
        }
    }

    /**
     * Throw if [subject]'s `id` is present, a non-null JSON string, and not a valid absolute
     * IRI. "Valid absolute" is judged by titanium's [UriUtils.isAbsoluteUri] — the same
     * predicate `toRdf` uses — so this rejects BOTH relative IRIs (no scheme, e.g. a bare
     * UUID or `#fragment`) AND syntactically-invalid IRIs that carry a colon but are not legal
     * (e.g. `urn:has space`, `urn:a^b`). Both classes have their subject triples dropped by
     * `toRdf`, leaving the subject's claims unsigned.
     */
    private fun requireAbsoluteSubjectId(subject: JsonObject) {
        val idElement = subject["id"] ?: return
        if (idElement is JsonNull) return
        val idValue = (idElement as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return

        if (!UriUtils.isAbsoluteUri(idValue)) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject.id",
                reason =
                    "credentialSubject.id must be a valid absolute IRI; '$idValue' is not — " +
                        "toRdf would drop its triples, leaving the subject's claims unsigned. " +
                        "JSON-LD RDFC-1.0 canonicalization (JsonLd.toRdf) drops every triple whose " +
                        "subject is not a usable absolute IRI — this covers relative IRIs (no scheme, " +
                        "e.g. a bare UUID or '#fragment') AND syntactically-invalid IRIs that carry a " +
                        "colon but are not legal (e.g. 'urn:has space', 'urn:a^b'). The subject's " +
                        "claims would then NOT be covered by the proof signature (the credential would " +
                        "still verify, making the claims forgeable). Use a valid absolute IRI for " +
                        "credentialSubject.id (e.g. http:, https:, did:, or urn:uuid:), or omit it to " +
                        "mint an anonymous (blank-node) subject whose triples are signed.",
            )
        }
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

    /**
     * Bounds the work canonicalization can be asked to do, before any of it is done.
     *
     * [SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES] measures the *output*, so it only
     * reports a document that was already expensive to process — by then the CPU is spent. RDFC-1.0
     * (URDNA2015) is super-linear in blank nodes, so a small document holding many
     * mutually-referencing blank nodes buys an attacker a large amount of verifier work. Both the
     * input size and the blank-node count are therefore checked up front.
     */
    private fun enforcePreCanonicalizationBounds(document: JsonObject) {
        val sizeBytes = document.toString().toByteArray(Charsets.UTF_8).size
        if (sizeBytes > SecurityConstants.MAX_PRE_CANONICALIZATION_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Document exceeds maximum size of " +
                    "${SecurityConstants.MAX_PRE_CANONICALIZATION_SIZE_BYTES} bytes before " +
                    "canonicalization: $sizeBytes bytes",
            )
        }

        val limit = SecurityConstants.MAX_BLANK_NODES_PER_DOCUMENT
        val blankNodes = countBlankNodes(document, limit)
        if (blankNodes > limit) {
            throw IllegalArgumentException(
                "Document contains more than $limit blank nodes; refusing to canonicalize it. " +
                    "RDFC-1.0 canonicalization is super-linear in blank nodes, so this bound keeps " +
                    "an untrusted document from dictating how much work a verifier does.",
            )
        }
    }

    /**
     * Counts node objects that will become blank nodes, stopping once [limit] is passed.
     *
     * A node object with no `@id` becomes a blank node on expansion. `@value` objects are literals
     * and `@context` holds term definitions, so neither contributes. Iterative on purpose: a
     * recursive walk over a deeply nested untrusted document would itself be a way to exhaust the
     * stack.
     */
    private fun countBlankNodes(
        root: JsonObject,
        limit: Int,
    ): Int {
        var count = 0
        val pending = ArrayDeque<Pair<JsonElement, Boolean>>()
        pending.addLast(root to false)

        while (pending.isNotEmpty() && count <= limit) {
            val (node, insideContext) = pending.removeLast()
            when (node) {
                is JsonObject -> {
                    if (insideContext) continue
                    if (node.containsKey("@value")) continue
                    if (!node.containsKey("@id")) count++
                    node.forEach { (key, value) -> pending.addLast(value to (key == "@context")) }
                }

                is JsonArray -> node.forEach { pending.addLast(it to insideContext) }
                else -> Unit
            }
        }
        return count
    }

    private fun verifyCredentialSubjectClaimsPreserved(
        document: JsonObject,
        jakartaDocument: jakarta.json.JsonObject,
    ) {
        val subject = document["credentialSubject"] ?: return
        if (subject !is JsonObject && subject !is JsonArray) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason =
                    "credentialSubject must be a JSON object (or an array of objects), got " +
                        "${subject::class.simpleName}. Refusing to sign or verify a document whose " +
                        "subject claims cannot be checked against the @context for dropped terms.",
            )
        }

        val declaredNames = mutableListOf<String>()
        collectDeclaredClaimNames(subject, declaredNames)
        if (declaredNames.isEmpty()) return

        // Round-trip the document through expansion + compaction against its own @context.
        // Terms not defined by the @context are dropped at expansion and cannot reappear
        // at compaction; defined terms compact back to their original names.
        val contextValue: JsonValue = jakartaDocument["@context"] ?: JsonValue.EMPTY_JSON_OBJECT
        val compacted: jakarta.json.JsonObject =
            try {
                val contextDocument =
                    JsonDocument.of(
                        Json.createObjectBuilder().add("@context", contextValue).build(),
                    )
                JsonLd
                    .compact(JsonDocument.of(jakartaDocument), contextDocument)
                    .loader(JsonLdContextLoader.createDocumentLoader())
                    .compactToRelative(false)
                    .get()
            } catch (e: Exception) {
                throw SerializationException.EncodeFailed(
                    element = "credentialSubject",
                    reason = "JSON-LD compaction failed while checking for dropped claims: ${e.message}",
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
        val missing =
            declaredNames
                .groupingBy { it }
                .eachCount()
                .filter { (name, declaredCount) -> (survivingCounts[name] ?: 0) < declaredCount }
                .keys
        if (missing.isNotEmpty()) {
            throw SerializationException.EncodeFailed(
                element = "credentialSubject",
                reason =
                    "JSON-LD canonicalization dropped credentialSubject claims: " +
                        "${missing.sorted()} (declared, nested claims included, but absent after a " +
                        "JSON-LD expansion/compaction round-trip). Claims whose terms are not defined " +
                        "in the credential's @context are silently removed and would NOT be covered " +
                        "by the proof signature. Declare an @context that defines every " +
                        "credentialSubject term (e.g. register a context via JsonLdContextLoader and " +
                        "reference it from the credential's @context).",
            )
        }
    }

    /**
     * Recursively collect the names of all claim properties (excluding `id`, `type` and
     * JSON-LD keywords) declared on the `credentialSubject`, including properties of
     * nested objects and of objects inside arrays.
     */
    private fun collectDeclaredClaimNames(
        element: JsonElement,
        into: MutableList<String>,
    ) {
        when (element) {
            is JsonObject ->
                element.forEach { (key, value) ->
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
    private fun collectCompactedClaimNames(
        value: JsonValue?,
        into: MutableList<String>,
    ) {
        when (value?.valueType) {
            JsonValue.ValueType.OBJECT ->
                value.asJsonObject().forEach { (key, nested) ->
                    if (key != "id" && key != "type" && !key.startsWith("@")) {
                        into.add(key)
                    }
                    collectCompactedClaimNames(nested, into)
                }
            JsonValue.ValueType.ARRAY ->
                value.asJsonArray().forEach {
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
        into: MutableList<jakarta.json.JsonObject>,
    ) {
        when (value?.valueType) {
            JsonValue.ValueType.ARRAY ->
                value.asJsonArray().forEach { collectCompactedSubjectNodes(it, into) }
            JsonValue.ValueType.OBJECT -> {
                val obj = value.asJsonObject()
                val subjectValue = obj["credentialSubject"] ?: obj[CREDENTIAL_SUBJECT_IRI]
                when (subjectValue?.valueType) {
                    JsonValue.ValueType.ARRAY ->
                        subjectValue.asJsonArray().forEach { node ->
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
