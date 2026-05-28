package org.trustweave.credential.pex

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.VerifiableCredential
import java.util.UUID

/**
 * DIF Presentation Exchange v2.0 matcher with full JSONPath evaluation.
 *
 * Maps a [PresentationDefinition] against a wallet of [VerifiableCredential] objects and
 * produces the set of credentials that satisfy each [InputDescriptor].
 *
 * **JSONPath evaluation**: Each [Field.path] is a JSONPath expression evaluated against the
 * serialized JSON representation of the credential. The first path that resolves to a value
 * wins. [Field.filter] is a JSON Schema sub-schema applied to that resolved value.
 *
 * **Supported JSON Schema filter keywords**: `const`, `enum`, `type`, `minimum`, `maximum`,
 * `exclusiveMinimum`, `exclusiveMaximum`, `minLength`, `maxLength`, `pattern`.
 */
object PresentationDefinitionMatcher {

    private val jsonPathConfig: Configuration = Configuration.builder()
        .options(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL)
        .build()

    /**
     * Match credentials against a [PresentationDefinition].
     *
     * @return A map of [InputDescriptor.id] → list of matching [VerifiableCredential]s.
     *   Descriptors with no matches are included with an empty list.
     */
    fun match(
        definition: PresentationDefinition,
        credentials: List<VerifiableCredential>,
    ): Map<String, List<VerifiableCredential>> =
        definition.inputDescriptors.associate { descriptor ->
            descriptor.id to matchDescriptor(descriptor, credentials)
        }

    /**
     * Build a [PresentationSubmission] from the result of [match].
     *
     * Only descriptors with at least one matching credential are included.
     * The first matching credential for each descriptor is used.
     */
    fun buildSubmission(
        definition: PresentationDefinition,
        matches: Map<String, List<VerifiableCredential>>,
    ): PresentationSubmission {
        val descriptorById = definition.inputDescriptors.associateBy { it.id }
        val descriptorMap = matches
            .filter { (_, creds) -> creds.isNotEmpty() }
            .entries
            .mapIndexed { index, (descriptorId, creds) ->
                val descriptor = descriptorById[descriptorId]
                val limitDisclosure = descriptor?.constraints?.limitDisclosure
                val format = when {
                    limitDisclosure != null -> {
                        // Prefer the format declared on the descriptor, then fall back to the
                        // first matched credential's proof type, finally "vc+sd-jwt".
                        val credFormat = descriptor.format
                        when {
                            credFormat?.sdJwtVc != null -> "vc+sd-jwt"
                            credFormat?.msoMdoc != null -> "mso_mdoc"
                            creds.first().proof is CredentialProof.MdocProof -> "mso_mdoc"
                            else -> "vc+sd-jwt"
                        }
                    }
                    creds.first().proof is CredentialProof.MdocProof -> "mso_mdoc"
                    creds.first().proof is CredentialProof.SdJwtVcProof -> "vc+sd-jwt"
                    else -> "ldp_vc"
                }
                DescriptorMap(
                    id = descriptorId,
                    format = format,
                    path = "$.verifiableCredential[$index]",
                )
            }

        return PresentationSubmission(
            id = UUID.randomUUID().toString(),
            definitionId = definition.id,
            descriptorMap = descriptorMap,
        )
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun matchDescriptor(
        descriptor: InputDescriptor,
        credentials: List<VerifiableCredential>,
    ): List<VerifiableCredential> {
        val constraints = descriptor.constraints
        val fields = constraints?.fields
        val limitDisclosure = constraints?.limitDisclosure

        // limit_disclosure: "required" → only credentials that natively support selective disclosure
        val filtered = if (limitDisclosure == LimitDisclosure.REQUIRED) {
            credentials.filter { vc ->
                vc.proof is CredentialProof.SdJwtVcProof || vc.proof is CredentialProof.MdocProof
            }
        } else {
            credentials
        }

        if (fields.isNullOrEmpty()) return filtered

        val required = fields.filter { !it.optional }
        if (required.isEmpty()) return filtered

        return filtered.filter { vc ->
            val doc = vcToDocument(vc)
            required.all { field -> evaluateField(field, doc) }
        }
    }

    /**
     * Evaluate a single [Field] against a document.
     *
     * Tries each path in [Field.path] in order; the first that resolves to a non-null
     * value is used for filter evaluation. Returns `true` if any path resolves and the
     * resolved value satisfies the filter (or there is no filter).
     *
     * Per DIF PEX v2.0 spec: if the path expression returns an array, the filter is applied
     * to each element and succeeds if any element satisfies the filter.
     */
    private fun evaluateField(field: Field, document: Any): Boolean {
        val resolvedValue = resolveFirstPath(field.path, document) ?: return false
        val filter = field.filter ?: return true
        return if (resolvedValue is List<*>) {
            resolvedValue.any { element -> element != null && evaluateFilter(filter, element) }
        } else {
            evaluateFilter(filter, resolvedValue)
        }
    }

    /**
     * Evaluate each JSONPath expression in [paths] against [document], returning the first
     * non-null result. Returns `null` if all paths fail to resolve or return null.
     */
    private fun resolveFirstPath(paths: List<String>, document: Any): Any? =
        paths.firstNotNullOfOrNull { path ->
            runCatching {
                JsonPath.using(jsonPathConfig).parse(document).read<Any?>(path)
            }.getOrNull()
        }

    /**
     * Apply a JSON Schema sub-schema to a resolved value.
     *
     * Supports: `const`, `enum`, `type`, `minimum`, `maximum`,
     * `exclusiveMinimum`, `exclusiveMaximum`, `minLength`, `maxLength`, `pattern`.
     */
    @Suppress("ReturnCount")
    private fun evaluateFilter(filter: JsonObject, value: Any): Boolean {
        // type check
        filter["type"]?.let { typeEl ->
            val requiredType = (typeEl as? JsonPrimitive)?.content ?: return@let
            if (!matchesJsonSchemaType(requiredType, value)) return false
        }

        // const
        filter["const"]?.let { constEl ->
            val constValue = jsonElementToJava(constEl)
            if (!looseEquals(value, constValue)) return false
        }

        // enum
        filter["enum"]?.let { enumEl ->
            if (enumEl !is JsonArray) return false
            val allowed = enumEl.map { jsonElementToJava(it) }
            if (allowed.none { looseEquals(value, it) }) return false
        }

        // Numeric range constraints
        val num = toDouble(value)
        if (num != null) {
            filter["minimum"]?.let { minEl ->
                val min = (minEl as? JsonPrimitive)?.doubleOrNull ?: return@let
                if (num < min) return false
            }
            filter["maximum"]?.let { maxEl ->
                val max = (maxEl as? JsonPrimitive)?.doubleOrNull ?: return@let
                if (num > max) return false
            }
            filter["exclusiveMinimum"]?.let { el ->
                val min = (el as? JsonPrimitive)?.doubleOrNull ?: return@let
                if (num <= min) return false
            }
            filter["exclusiveMaximum"]?.let { el ->
                val max = (el as? JsonPrimitive)?.doubleOrNull ?: return@let
                if (num >= max) return false
            }
        }

        // String constraints
        val str = value as? String
        if (str != null) {
            filter["minLength"]?.let { el ->
                val min = (el as? JsonPrimitive)?.longOrNull ?: return@let
                if (str.length < min) return false
            }
            filter["maxLength"]?.let { el ->
                val max = (el as? JsonPrimitive)?.longOrNull ?: return@let
                if (str.length > max) return false
            }
            filter["pattern"]?.let { el ->
                val pattern = (el as? JsonPrimitive)?.content ?: return@let
                if (!Regex(pattern).containsMatchIn(str)) return false
            }
        }

        return true
    }

    private fun matchesJsonSchemaType(type: String, value: Any): Boolean = when (type) {
        "string" -> value is String
        "number" -> value is Number
        "integer" -> value is Number && (value as Number).toDouble() % 1.0 == 0.0
        "boolean" -> value is Boolean
        "array" -> value is List<*>
        "object" -> value is Map<*, *>
        "null" -> value == null
        else -> true
    }

    private fun looseEquals(a: Any?, b: Any?): Boolean {
        if (a == b) return true
        // String vs list: check if the string is contained in the list
        if (a is List<*>) return a.any { looseEquals(it, b) }
        if (b is List<*>) return b.any { looseEquals(a, it) }
        // Number comparison across types (e.g. Int vs Long)
        val da = toDouble(a); val db = toDouble(b)
        if (da != null && db != null) return da == db
        return a?.toString() == b?.toString()
    }

    private fun toDouble(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    // -------------------------------------------------------------------------
    // VC → document (Map/List/primitive) conversion for Jayway JSONPath
    // -------------------------------------------------------------------------

    /**
     * Convert a [VerifiableCredential] to a plain Java Map/List structure that Jayway
     * JSONPath can traverse with standard dot-notation paths.
     */
    internal fun vcToDocument(vc: VerifiableCredential): Map<String, Any?> {
        val doc = LinkedHashMap<String, Any?>()
        doc["@context"] = vc.context
        vc.id?.let { doc["id"] = it.value }
        doc["type"] = vc.type.map { it.value }
        doc["issuer"] = vc.issuer.id.value
        vc.issuanceDate?.let { doc["issuanceDate"] = it.toString() }
        vc.validFrom?.let { doc["validFrom"] = it.toString() }
        vc.expirationDate?.let { doc["expirationDate"] = it.toString() }
        vc.validUntil?.let { doc["validUntil"] = it.toString() }
        vc.name?.let { doc["name"] = it }
        vc.description?.let { doc["description"] = it }
        vc.credentialStatus?.let { doc["credentialStatus"] = mapOf("id" to it.id, "type" to it.type) }
        vc.credentialSchema?.let { doc["credentialSchema"] = mapOf("id" to it.id, "type" to it.type) }

        // credentialSubject: flatten id + claims
        val subjectMap = LinkedHashMap<String, Any?>()
        subjectMap["id"] = vc.credentialSubject.id?.value
        vc.credentialSubject.claims.forEach { (key, element) ->
            subjectMap[key] = jsonElementToJava(element)
        }
        doc["credentialSubject"] = subjectMap

        return doc
    }

    /** Recursively convert a [JsonElement] to plain Java types for Jayway. */
    private fun jsonElementToJava(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.booleanOrNull != null -> element.booleanOrNull!!
            element.longOrNull != null -> element.longOrNull!!
            element.doubleOrNull != null -> element.doubleOrNull!!
            else -> element.content
        }
        is JsonArray -> element.map { jsonElementToJava(it) }
        is JsonObject -> element.entries.associate { (k, v) -> k to jsonElementToJava(v) }
    }
}
