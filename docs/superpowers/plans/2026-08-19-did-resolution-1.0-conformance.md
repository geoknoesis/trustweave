# DID Resolution 1.0 Conformance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make TrustWeave a **conforming DID resolver** per W3C CR-did-resolution-1.0-20260806 §4, replacing the current v0.3-era resolution surface.

**Architecture:** Introduce an RFC 9457 error object with `https://www.w3.org/ns/did#*` type URIs as the normative error surface, add a first-class `ResolutionOptions` input threaded through the resolver interfaces via *defaulted* interface members (so none of the 47 existing `resolveDid` overrides break), make deactivation a distinct result that carries no document, and centralise media types and datetime formatting so conformance is enforced at the serialization boundary rather than at each call site.

**Tech Stack:** Kotlin 2.3.21 (JVM 21), kotlinx-serialization-json, kotlinx-datetime, kotlinx-coroutines, JUnit 5 + `kotlin.test` assertions, Gradle 9.5.0.

**Spec:** `docs/superpowers/specs/2026-08-19-did-resolution-1.0-conformance-design.md`

## Global Constraints

- Target spec: `https://www.w3.org/TR/2026/CR-did-resolution-1.0-20260806/`. Section references below (§4.4, §11, …) are to that document.
- All error `type` values MUST be absolute URLs prefixed `https://www.w3.org/ns/did#` (§11).
- All datetime values MUST be UTC XML datetimes **without sub-second precision**, e.g. `2020-12-20T19:17:47Z` (§3.1).
- The DID document media type is `application/did`; the resolution-result envelope media type is `application/did-resolution` (§9, §12.1).
- Every task ends with `./gradlew ktlintFormat` before committing — KTLint is enforced in CI.
- Gradle on this machine must be run with `--max-workers 3` or lower; higher parallelism destabilises the build box.
- Use `testkit` in-memory doubles (`InMemoryKeyManagementService`, `DidKeyMockMethod`) in tests — never Mockito for these types.
- Conventional Commits are required. Use `feat(did-core):`, `fix(did-core):`, `test(conformance):` prefixes.
- **Stage explicit paths. Never `git add -A`, `git add .`, or `git commit -a`.** This repository
  carries pre-existing uncommitted and untracked files that belong to unrelated in-progress work;
  a wildcard stage would sweep them into this branch's history.
- **Every task must leave `:did:did-core` compiling with its own tests green.** Gradle compiles a
  module's whole main source set before running any of its tests, so a task that breaks a sibling
  file in the same module cannot run its own test. Where a task changes a shared signature, that
  task also migrates the same-module call sites it breaks — it does not defer them.
- Do not touch §5 (DID URL dereferencing) or §12.1 (HTTP binding) behaviour in this plan — they are separate conformance classes with their own follow-on plans. Task 15 lands only the DID URL *parsing* they both need, which §13.4 versioned resolution requires anyway.

---

### Task 1: Error type URIs and the RFC 9457 error object

**Files:**
- Create: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionError.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionErrorTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `object DidErrorType` with `const val BASE/INVALID_DID/INVALID_DID_DOCUMENT/NOT_FOUND/REPRESENTATION_NOT_SUPPORTED/INVALID_DID_URL/METHOD_NOT_SUPPORTED/INVALID_OPTIONS/INTERNAL_ERROR/FEATURE_NOT_SUPPORTED: String`, `fun httpStatus(type: String): Int`, `fun title(type: String): String`, `fun fromLegacyCode(code: String): String`; `data class DidResolutionError(val type: String, val title: String? = null, val detail: String? = null)` with `val httpStatus: Int`, `fun toJson(): JsonObject`, and companion factories `of(type, detail)`, `invalidDid(detail)`, `invalidDidDocument(detail)`, `notFound(detail)`, `representationNotSupported(detail)`, `invalidDidUrl(detail)`, `methodNotSupported(detail)`, `invalidOptions(detail)`, `internalError(detail)`, `featureNotSupported(detail)`, `fromJson(element: JsonElement?): DidResolutionError?`.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionErrorTest.kt`:

```kotlin
package org.trustweave.did.resolver

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DidResolutionErrorTest {

    @Test
    fun `error type constants use the spec URI prefix`() {
        assertEquals("https://www.w3.org/ns/did#INVALID_DID", DidErrorType.INVALID_DID)
        assertEquals("https://www.w3.org/ns/did#INVALID_DID_DOCUMENT", DidErrorType.INVALID_DID_DOCUMENT)
        assertEquals("https://www.w3.org/ns/did#NOT_FOUND", DidErrorType.NOT_FOUND)
        assertEquals(
            "https://www.w3.org/ns/did#REPRESENTATION_NOT_SUPPORTED",
            DidErrorType.REPRESENTATION_NOT_SUPPORTED
        )
        assertEquals("https://www.w3.org/ns/did#INVALID_DID_URL", DidErrorType.INVALID_DID_URL)
        assertEquals("https://www.w3.org/ns/did#METHOD_NOT_SUPPORTED", DidErrorType.METHOD_NOT_SUPPORTED)
        assertEquals("https://www.w3.org/ns/did#INVALID_OPTIONS", DidErrorType.INVALID_OPTIONS)
        assertEquals("https://www.w3.org/ns/did#INTERNAL_ERROR", DidErrorType.INTERNAL_ERROR)
        assertEquals("https://www.w3.org/ns/did#FEATURE_NOT_SUPPORTED", DidErrorType.FEATURE_NOT_SUPPORTED)
    }

    @Test
    fun `http status mapping follows the section 12-1 table`() {
        assertEquals(400, DidErrorType.httpStatus(DidErrorType.INVALID_DID))
        assertEquals(400, DidErrorType.httpStatus(DidErrorType.INVALID_DID_URL))
        assertEquals(400, DidErrorType.httpStatus(DidErrorType.INVALID_OPTIONS))
        assertEquals(404, DidErrorType.httpStatus(DidErrorType.NOT_FOUND))
        assertEquals(406, DidErrorType.httpStatus(DidErrorType.REPRESENTATION_NOT_SUPPORTED))
        assertEquals(500, DidErrorType.httpStatus(DidErrorType.INVALID_DID_DOCUMENT))
        assertEquals(501, DidErrorType.httpStatus(DidErrorType.METHOD_NOT_SUPPORTED))
        assertEquals(501, DidErrorType.httpStatus(DidErrorType.FEATURE_NOT_SUPPORTED))
        assertEquals(500, DidErrorType.httpStatus(DidErrorType.INTERNAL_ERROR))
        assertEquals(500, DidErrorType.httpStatus("https://example.com/some-other-error"))
    }

    @Test
    fun `legacy v0-3 codes map to spec URIs`() {
        assertEquals(DidErrorType.NOT_FOUND, DidErrorType.fromLegacyCode("notFound"))
        assertEquals(DidErrorType.INVALID_DID, DidErrorType.fromLegacyCode("invalidDid"))
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, DidErrorType.fromLegacyCode("methodNotSupported"))
        assertEquals(DidErrorType.INTERNAL_ERROR, DidErrorType.fromLegacyCode("resolutionError"))
        assertEquals(DidErrorType.INTERNAL_ERROR, DidErrorType.fromLegacyCode("internalError"))
    }

    @Test
    fun `unknown legacy code is prefixed with the spec base URL`() {
        assertEquals("https://www.w3.org/ns/did#somethingElse", DidErrorType.fromLegacyCode("somethingElse"))
    }

    @Test
    fun `an already absolute error URI passes through unchanged`() {
        assertEquals(
            "https://example.com/errors/custom",
            DidErrorType.fromLegacyCode("https://example.com/errors/custom")
        )
    }

    @Test
    fun `factory populates type title and detail`() {
        val error = DidResolutionError.notFound("did:example:123 does not exist")
        assertEquals(DidErrorType.NOT_FOUND, error.type)
        assertEquals("Not found", error.title)
        assertEquals("did:example:123 does not exist", error.detail)
        assertEquals(404, error.httpStatus)
    }

    @Test
    fun `a relative error type is rejected`() {
        assertFailsWith<IllegalArgumentException> { DidResolutionError("NOT_FOUND") }
    }

    @Test
    fun `toJson emits only populated members`() {
        val json = DidResolutionError(DidErrorType.INTERNAL_ERROR).toJson()
        assertEquals(1, json.size)
        assertEquals(JsonPrimitive(DidErrorType.INTERNAL_ERROR), json["type"])
    }

    @Test
    fun `fromJson parses an RFC 9457 object`() {
        val json = buildJsonObject {
            put("type", DidErrorType.INVALID_OPTIONS)
            put("title", "Invalid options")
            put("detail", "versionId and versionTime are mutually exclusive")
        }
        val error = DidResolutionError.fromJson(json)
        assertEquals(DidErrorType.INVALID_OPTIONS, error?.type)
        assertEquals("versionId and versionTime are mutually exclusive", error?.detail)
    }

    @Test
    fun `fromJson upgrades a legacy string error from an upstream resolver`() {
        val error = DidResolutionError.fromJson(JsonPrimitive("notFound"))
        assertEquals(DidErrorType.NOT_FOUND, error?.type)
        assertEquals("notFound", error?.detail)
    }

    @Test
    fun `fromJson returns null for null and JsonNull`() {
        assertNull(DidResolutionError.fromJson(null))
        assertNull(DidResolutionError.fromJson(JsonNull))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DidResolutionErrorTest" --max-workers 3`
Expected: FAIL — compilation error, `Unresolved reference: DidErrorType`.

- [ ] **Step 3: Write the implementation**

Create `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionError.kt`:

```kotlin
package org.trustweave.did.resolver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Error type URIs defined by DID Resolution 1.0 §11, plus the §12.1 HTTP status mapping.
 *
 * Per §11, error types that are not already URLs MUST be prefixed with [BASE].
 */
object DidErrorType {
    /** Prefix mandated by §11 for all non-URL error identifiers. */
    const val BASE: String = "https://www.w3.org/ns/did#"

    const val INVALID_DID: String = BASE + "INVALID_DID"
    const val INVALID_DID_DOCUMENT: String = BASE + "INVALID_DID_DOCUMENT"
    const val NOT_FOUND: String = BASE + "NOT_FOUND"
    const val REPRESENTATION_NOT_SUPPORTED: String = BASE + "REPRESENTATION_NOT_SUPPORTED"
    const val INVALID_DID_URL: String = BASE + "INVALID_DID_URL"
    const val METHOD_NOT_SUPPORTED: String = BASE + "METHOD_NOT_SUPPORTED"
    const val INVALID_OPTIONS: String = BASE + "INVALID_OPTIONS"
    const val INTERNAL_ERROR: String = BASE + "INTERNAL_ERROR"
    const val FEATURE_NOT_SUPPORTED: String = BASE + "FEATURE_NOT_SUPPORTED"

    /** HTTP status code for an error type per the §12.1 binding table. Unknown types map to 500. */
    fun httpStatus(type: String): Int = when (type) {
        INVALID_DID, INVALID_DID_URL, INVALID_OPTIONS -> 400
        NOT_FOUND -> 404
        REPRESENTATION_NOT_SUPPORTED -> 406
        METHOD_NOT_SUPPORTED, FEATURE_NOT_SUPPORTED -> 501
        else -> 500
    }

    /** Short human-readable title for an error type (§11: `title` SHOULD be present). */
    fun title(type: String): String = when (type) {
        INVALID_DID -> "Invalid DID"
        INVALID_DID_DOCUMENT -> "Invalid DID document"
        NOT_FOUND -> "Not found"
        REPRESENTATION_NOT_SUPPORTED -> "Representation not supported"
        INVALID_DID_URL -> "Invalid DID URL"
        METHOD_NOT_SUPPORTED -> "Method not supported"
        INVALID_OPTIONS -> "Invalid options"
        INTERNAL_ERROR -> "Internal error"
        FEATURE_NOT_SUPPORTED -> "Feature not supported"
        else -> "DID resolution error"
    }

    /**
     * Maps a DID Resolution v0.3 / FPWD camelCase error code to its CR type URI.
     *
     * Public universal resolvers still emit the legacy strings, so inbound responses must be
     * upgraded rather than rejected. Values that are already absolute URLs pass through; any
     * other unknown value is prefixed with [BASE] as §11 requires.
     */
    fun fromLegacyCode(code: String): String = when (code) {
        "invalidDid", "invalidDidFormat" -> INVALID_DID
        "invalidDidDocument" -> INVALID_DID_DOCUMENT
        "notFound" -> NOT_FOUND
        "representationNotSupported" -> REPRESENTATION_NOT_SUPPORTED
        "invalidDidUrl" -> INVALID_DID_URL
        "methodNotSupported", "unsupportedDidMethod" -> METHOD_NOT_SUPPORTED
        "invalidOptions" -> INVALID_OPTIONS
        "internalError", "resolutionError" -> INTERNAL_ERROR
        "featureNotSupported" -> FEATURE_NOT_SUPPORTED
        else -> if (code.startsWith("http://") || code.startsWith("https://")) code else BASE + code
    }
}

/**
 * An error data structure per [RFC 9457][https://www.rfc-editor.org/rfc/rfc9457] as required by
 * DID Resolution 1.0 §4.2 / §5.2 / §11.
 *
 * @param type absolute URL identifying the error condition; see [DidErrorType]
 * @param title short human-readable summary (SHOULD be present per §11)
 * @param detail longer human-readable explanation (SHOULD be present per §11)
 */
@Serializable
data class DidResolutionError(
    val type: String,
    val title: String? = null,
    val detail: String? = null
) {
    init {
        require(type.startsWith("http://") || type.startsWith("https://")) {
            "RFC 9457 error type MUST be a URL (DID Resolution 1.0 §11), got: '$type'"
        }
    }

    /** HTTP status code this error maps to per §12.1. */
    val httpStatus: Int get() = DidErrorType.httpStatus(type)

    /** Serializes to the RFC 9457 JSON object, omitting absent members. */
    fun toJson(): JsonObject = buildJsonObject {
        put("type", type)
        title?.let { put("title", it) }
        detail?.let { put("detail", it) }
    }

    companion object {
        /** Builds an error with the registered [DidErrorType.title] for [type]. */
        fun of(type: String, detail: String? = null): DidResolutionError =
            DidResolutionError(type = type, title = DidErrorType.title(type), detail = detail)

        fun invalidDid(detail: String): DidResolutionError = of(DidErrorType.INVALID_DID, detail)

        fun invalidDidDocument(detail: String): DidResolutionError =
            of(DidErrorType.INVALID_DID_DOCUMENT, detail)

        fun notFound(detail: String): DidResolutionError = of(DidErrorType.NOT_FOUND, detail)

        fun representationNotSupported(detail: String): DidResolutionError =
            of(DidErrorType.REPRESENTATION_NOT_SUPPORTED, detail)

        fun invalidDidUrl(detail: String): DidResolutionError = of(DidErrorType.INVALID_DID_URL, detail)

        fun methodNotSupported(detail: String): DidResolutionError =
            of(DidErrorType.METHOD_NOT_SUPPORTED, detail)

        fun invalidOptions(detail: String): DidResolutionError = of(DidErrorType.INVALID_OPTIONS, detail)

        fun internalError(detail: String): DidResolutionError = of(DidErrorType.INTERNAL_ERROR, detail)

        fun featureNotSupported(detail: String): DidResolutionError =
            of(DidErrorType.FEATURE_NOT_SUPPORTED, detail)

        /**
         * Parses an `error` member from a resolution-metadata JSON structure.
         *
         * Accepts both the CR object form and the legacy v0.3 bare-string form; the legacy
         * string is preserved as [detail] so upstream diagnostics are not lost.
         */
        fun fromJson(element: JsonElement?): DidResolutionError? = when {
            element == null || element is JsonNull -> null
            element is JsonPrimitive && element.isString ->
                DidResolutionError(
                    type = DidErrorType.fromLegacyCode(element.content),
                    title = DidErrorType.title(DidErrorType.fromLegacyCode(element.content)),
                    detail = element.content
                )
            element is JsonObject -> {
                val type = element["type"]?.jsonPrimitive?.contentOrNull
                if (type == null) {
                    null
                } else {
                    DidResolutionError(
                        type = DidErrorType.fromLegacyCode(type),
                        title = element["title"]?.jsonPrimitive?.contentOrNull
                            ?: DidErrorType.title(DidErrorType.fromLegacyCode(type)),
                        detail = element["detail"]?.jsonPrimitive?.contentOrNull
                    )
                }
            }
            else -> null
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DidResolutionErrorTest" --max-workers 3`
Expected: PASS, 11 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionError.kt did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionErrorTest.kt
git commit -m "feat(did-core): add RFC 9457 DID resolution error type per DID Resolution 1.0 section 11"
```

---

### Task 2: XML datetime serialization (§3.1)

**Files:**
- Create: `did/did-core/src/main/kotlin/org/trustweave/did/util/XmlDateTime.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/util/XmlDateTimeTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `fun Instant.toXmlDateTime(): String` and `object XmlDateTimeSerializer : KSerializer<Instant>` in package `org.trustweave.did.util`.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/util/XmlDateTimeTest.kt`:

```kotlin
package org.trustweave.did.util

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class XmlDateTimeTest {

    @Serializable
    private data class Holder(
        @Serializable(with = XmlDateTimeSerializer::class) val at: Instant
    )

    @Test
    fun `sub-second precision is truncated`() {
        val instant = Instant.parse("2020-12-20T19:17:47.123456789Z")
        assertEquals("2020-12-20T19:17:47Z", instant.toXmlDateTime())
    }

    @Test
    fun `whole-second instants are unchanged`() {
        val instant = Instant.parse("2020-12-20T19:17:47Z")
        assertEquals("2020-12-20T19:17:47Z", instant.toXmlDateTime())
    }

    @Test
    fun `non-UTC offsets are normalized to Z`() {
        val instant = Instant.parse("2020-12-20T20:17:47+01:00")
        assertEquals("2020-12-20T19:17:47Z", instant.toXmlDateTime())
    }

    @Test
    fun `serializer emits truncated UTC form`() {
        val json = Json.encodeToString(Holder.serializer(), Holder(Instant.parse("2024-06-01T19:07:24.5Z")))
        assertEquals("""{"at":"2024-06-01T19:07:24Z"}""", json)
    }

    @Test
    fun `serializer round-trips a truncated value`() {
        val decoded = Json.decodeFromString(Holder.serializer(), """{"at":"2024-06-01T19:07:24Z"}""")
        assertEquals(Instant.parse("2024-06-01T19:07:24Z"), decoded.at)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.util.XmlDateTimeTest" --max-workers 3`
Expected: FAIL — `Unresolved reference: toXmlDateTime`.

- [ ] **Step 3: Write the implementation**

Create `did/did-core/src/main/kotlin/org/trustweave/did/util/XmlDateTime.kt`:

```kotlin
package org.trustweave.did.util

import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Formats this instant per DID Resolution 1.0 §3.1: a valid XML datetime, adjusted to UTC,
 * without sub-second decimal precision (e.g. `2020-12-20T19:17:47Z`).
 */
fun Instant.toXmlDateTime(): String = Instant.fromEpochSeconds(this.epochSeconds).toString()

/**
 * Serializes [Instant] in the DID Resolution 1.0 §3.1 datetime format.
 *
 * Apply to every timestamp that crosses a resolution wire boundary so conformance is enforced
 * once, at the serialization boundary, rather than at each call site.
 */
object XmlDateTimeSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("XmlDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toXmlDateTime())
    }

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.util.XmlDateTimeTest" --max-workers 3`
Expected: PASS, 5 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/util/XmlDateTime.kt did/did-core/src/test/kotlin/org/trustweave/did/util/XmlDateTimeTest.kt
git commit -m "feat(did-core): add section 3.1 XML datetime formatting for resolution timestamps"
```

---

### Task 3: Centralised media types (§9, §12.1)

**Files:**
- Create: `did/did-core/src/main/kotlin/org/trustweave/did/representation/DidMediaTypes.kt`
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/representation/DidDocumentJsonProducer.kt:14`
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/negotiation/ContentNegotiationService.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/representation/DidMediaTypesTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object DidMediaTypes` with `const val DID`, `DID_RESOLUTION`, `DID_URL_DEREFERENCING`, `DID_LD_JSON`, `DID_JSON`, `JSON`, plus `val SUPPORTED_DOCUMENT_TYPES: List<String>` and `fun isSupportedDocumentType(mediaType: String): Boolean`.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/representation/DidMediaTypesTest.kt`:

```kotlin
package org.trustweave.did.representation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DidMediaTypesTest {

    @Test
    fun `spec media types have their CR values`() {
        assertEquals("application/did", DidMediaTypes.DID)
        assertEquals("application/did-resolution", DidMediaTypes.DID_RESOLUTION)
        assertEquals("application/did-url-dereferencing", DidMediaTypes.DID_URL_DEREFERENCING)
    }

    @Test
    fun `legacy representation types remain available`() {
        assertEquals("application/did+ld+json", DidMediaTypes.DID_LD_JSON)
        assertEquals("application/did+json", DidMediaTypes.DID_JSON)
    }

    @Test
    fun `application-did is the first supported document type`() {
        assertEquals(DidMediaTypes.DID, DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.first())
    }

    @Test
    fun `supported document types accept CR and legacy forms`() {
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/did"))
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/did+ld+json"))
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/did+json"))
        assertTrue(DidMediaTypes.isSupportedDocumentType("application/json"))
        assertFalse(DidMediaTypes.isSupportedDocumentType("application/did+cbor"))
    }

    @Test
    fun `media type matching ignores parameters and case`() {
        assertTrue(DidMediaTypes.isSupportedDocumentType("APPLICATION/DID; profile=\"https://www.w3.org/ns/did\""))
    }

    @Test
    fun `the legacy producer constant now delegates to DidMediaTypes`() {
        assertEquals(DidMediaTypes.DID, APPLICATION_DID_MEDIA_TYPE)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.representation.DidMediaTypesTest" --max-workers 3`
Expected: FAIL — `Unresolved reference: DidMediaTypes`.

- [ ] **Step 3: Write the implementation**

Create `did/did-core/src/main/kotlin/org/trustweave/did/representation/DidMediaTypes.kt`:

```kotlin
package org.trustweave.did.representation

/**
 * Media types defined by DID 1.1 and DID Resolution 1.0.
 *
 * `application/did` is the DID document media type (§9.1 example). `application/did-resolution`
 * and `application/did-url-dereferencing` identify the result envelopes of §9 and §10.
 * The `+ld+json` / `+json` forms are DID Core 1.0 representation types, retained so existing
 * peers and stored documents keep working.
 */
object DidMediaTypes {
    const val DID: String = "application/did"
    const val DID_RESOLUTION: String = "application/did-resolution"
    const val DID_URL_DEREFERENCING: String = "application/did-url-dereferencing"

    const val DID_LD_JSON: String = "application/did+ld+json"
    const val DID_JSON: String = "application/did+json"
    const val JSON: String = "application/json"

    /** Document representations this build can produce and consume, most preferred first. */
    val SUPPORTED_DOCUMENT_TYPES: List<String> = listOf(DID, DID_LD_JSON, DID_JSON, JSON)

    /** True when [mediaType] (parameters and case ignored) is a supported document representation. */
    fun isSupportedDocumentType(mediaType: String): Boolean =
        SUPPORTED_DOCUMENT_TYPES.contains(normalize(mediaType))

    /** Strips media type parameters and lowercases, per RFC 9110 §8.3.1. */
    fun normalize(mediaType: String): String = mediaType.substringBefore(';').trim().lowercase()
}
```

- [ ] **Step 4: Point the existing constant at the new object**

In `did/did-core/src/main/kotlin/org/trustweave/did/representation/DidDocumentJsonProducer.kt`, replace line 14:

```kotlin
const val APPLICATION_DID_MEDIA_TYPE: String = "application/did"
```

with:

```kotlin
@Deprecated(
    "Use DidMediaTypes.DID",
    ReplaceWith("DidMediaTypes.DID", "org.trustweave.did.representation.DidMediaTypes")
)
const val APPLICATION_DID_MEDIA_TYPE: String = DidMediaTypes.DID
```

- [ ] **Step 5: Update content negotiation to prefer `application/did`**

In `did/did-core/src/main/kotlin/org/trustweave/did/negotiation/ContentNegotiationService.kt`:

Change the interface default in `negotiateContentType` from
`defaultType: String = "application/did+ld+json"` to
`defaultType: String = DidMediaTypes.DID`.

Replace the `SUPPORTED_TYPES` companion value with a delegation:

```kotlin
companion object {
    val SUPPORTED_TYPES: List<String> = DidMediaTypes.SUPPORTED_DOCUMENT_TYPES
}
```

In `negotiateContentType`, normalize before matching:

```kotlin
val acceptedTypes = parseAcceptHeader(acceptHeader)
return acceptedTypes.firstOrNull { DidMediaTypes.isSupportedDocumentType(it) }
    ?.let { DidMediaTypes.normalize(it) }
    ?: defaultType
```

In `serializeDocument` and `deserializeDocument`, replace both `when (contentType)` subject
expressions with `when (DidMediaTypes.normalize(contentType))` and add `DidMediaTypes.DID` to
each branch's list of matched constants, so the branch head reads:

```kotlin
DidMediaTypes.DID,
DidMediaTypes.DID_LD_JSON,
DidMediaTypes.DID_JSON,
DidMediaTypes.JSON -> {
```

In `parseAcceptHeader`, replace the `sortedByDescending` block with:

```kotlin
.sortedByDescending { type ->
    val index = DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.indexOf(DidMediaTypes.normalize(type))
    if (index < 0) -1 else DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.size - index
}
```

Add `import org.trustweave.did.representation.DidMediaTypes` at the top of the file.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :did:did-core:test --max-workers 3`
Expected: PASS. If `ContentNegotiationServiceTest` asserts the old `application/did+ld+json` default, update those assertions to `application/did` — that change is the point of this task.

- [ ] **Step 7: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/representation/ did/did-core/src/main/kotlin/org/trustweave/did/negotiation/ did/did-core/src/test/kotlin/org/trustweave/did/representation/
git commit -m "feat(did-core): centralize DID media types and default to application/did"
```

---

### Task 4: Rewrite `DidResolutionMetadata` (§4.2)

**Files:**
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionMetadata.kt` (whole file)
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionMetadataTest.kt`
- Modify (same-module migration, required by the Global Constraint on green modules):
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResult.kt` — the four
    default `error = "…"` / `errorMessage = …` values in the `Failure` subtypes
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/RegistryBasedResolver.kt:73,111,122`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DefaultUniversalResolver.kt:237,251,293,312`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DecentralizedResolutionStrategy.kt:127`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/FallbackDidResolver.kt:84`
  - the `did-core` tests that assert on the old string error:
    `DidDocumentMetadataComprehensiveTest.kt`, `DidMethodEdgeCasesTest.kt`,
    `DidMethodInterfaceContractTest.kt`, `DidModelsBranchCoverageTest.kt`,
    `DidModelsEdgeCasesTest.kt`

**Interfaces:**
- Consumes: `DidResolutionError`, `DidErrorType` (Task 1); `XmlDateTimeSerializer` (Task 2); `DidMediaTypes` (Task 3).
- Produces: `data class DidResolutionMetadata(contentType: String = DidMediaTypes.DID, error: DidResolutionError? = null, proof: List<JsonObject> = emptyList(), pattern: String? = null, driverUrl: String? = null, duration: Long? = null, retrieved: Instant? = null, properties: Map<String, String> = emptyMap())` with `fun toMap(): Map<String, Any?>`, `fun toJson(): JsonObject`, deprecated `val errorMessage: String?`, and `companion object { fun fromMap(map: Map<String, Any?>): DidResolutionMetadata; fun fromJson(json: JsonObject): DidResolutionMetadata }`.
  `nextUpdate`, `nextVersionId`, `canonicalId` and `equivalentId` are **removed** — they are §4.3 document metadata.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionMetadataTest.kt`:

```kotlin
package org.trustweave.did.resolver

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidResolutionMetadataTest {

    @Test
    fun `default content type is application-did`() {
        assertEquals("application/did", DidResolutionMetadata().contentType)
    }

    @Test
    fun `error serializes as an RFC 9457 object`() {
        val metadata = DidResolutionMetadata(error = DidResolutionError.notFound("no such DID"))
        val json = metadata.toJson()
        assertEquals(
            JsonPrimitive("https://www.w3.org/ns/did#NOT_FOUND"),
            json["error"]!!.jsonObject["type"]
        )
        assertEquals(JsonPrimitive("no such DID"), json["error"]!!.jsonObject["detail"])
    }

    @Test
    fun `retrieved timestamp is truncated to whole seconds`() {
        val metadata = DidResolutionMetadata(retrieved = Instant.parse("2024-06-01T19:07:24.987Z"))
        assertEquals("2024-06-01T19:07:24Z", metadata.toMap()["retrieved"])
    }

    @Test
    fun `fromMap upgrades a legacy string error code`() {
        val metadata = DidResolutionMetadata.fromMap(mapOf("error" to "notFound"))
        assertEquals(DidErrorType.NOT_FOUND, metadata.error?.type)
    }

    @Test
    fun `fromJson parses a CR error object`() {
        val json = buildJsonObject {
            put("contentType", "application/did")
            put(
                "error",
                buildJsonObject {
                    put("type", "https://www.w3.org/ns/did#METHOD_NOT_SUPPORTED")
                    put("detail", "did:nope is unknown")
                }
            )
        }
        val metadata = DidResolutionMetadata.fromJson(json)
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, metadata.error?.type)
        assertEquals("did:nope is unknown", metadata.error?.detail)
    }

    @Test
    fun `unknown members are preserved in properties`() {
        val metadata = DidResolutionMetadata.fromMap(mapOf("blockNumber" to 42))
        assertEquals("42", metadata.properties["blockNumber"])
    }

    @Test
    fun `toMap omits absent members`() {
        val map = DidResolutionMetadata().toMap()
        assertEquals(setOf("contentType"), map.keys)
        assertNull(map["error"])
    }

    @Test
    fun `deprecated errorMessage reads the error detail`() {
        @Suppress("DEPRECATION")
        val message = DidResolutionMetadata(error = DidResolutionError.internalError("boom")).errorMessage
        assertEquals("boom", message)
    }

    @Test
    fun `proof entries round-trip through toJson`() {
        val proof = buildJsonObject { put("type", "DataIntegrityProof") }
        val json = DidResolutionMetadata(proof = listOf(proof)).toJson()
        assertTrue(json.containsKey("proof"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DidResolutionMetadataTest" --max-workers 3`
Expected: FAIL — `error` is still `String?`, `toJson` does not exist.

- [ ] **Step 3: Replace the file contents**

Replace `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionMetadata.kt` entirely with:

```kotlin
package org.trustweave.did.resolver

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.util.XmlDateTimeSerializer
import org.trustweave.did.util.toXmlDateTime

/**
 * DID resolution metadata per DID Resolution 1.0 §4.2 — metadata about the resolution *process*.
 *
 * Note that `nextUpdate`, `nextVersionId`, `canonicalId` and `equivalentId` are **document**
 * metadata (§4.3) and live on [org.trustweave.did.model.DidDocumentMetadata], not here.
 *
 * **Example:**
 * ```kotlin
 * DidResolutionMetadata(
 *     contentType = DidMediaTypes.DID,
 *     retrieved = Clock.System.now()
 * )
 * ```
 */
@Serializable
data class DidResolutionMetadata(
    /** Media type of the returned `didDocument` (§4.2). OPTIONAL in the spec; always emitted here. */
    val contentType: String = DidMediaTypes.DID,

    /** RFC 9457 error object; MUST be present when resolution is unsuccessful (§4). */
    val error: DidResolutionError? = null,

    /** Proofs generated by the resolver (§4.2). Each entry is a proof map. */
    val proof: List<JsonObject> = emptyList(),

    /** Non-normative: resolution pattern that matched (e.g. `did:web`). */
    val pattern: String? = null,

    /** Non-normative: driver URL when a Universal Resolver driver served the request. */
    val driverUrl: String? = null,

    /** Non-normative: resolution duration in milliseconds (§4.2 lists duration as an example). */
    val duration: Long? = null,

    /** Timestamp the document was retrieved; §3.1 datetime format on the wire. */
    @Serializable(with = XmlDateTimeSerializer::class) val retrieved: Instant? = null,

    /** Additional registered or method-specific members (§4.2 extensibility). */
    val properties: Map<String, String> = emptyMap()
) {
    /** Human-readable error text. */
    @Deprecated("Read error?.detail directly", ReplaceWith("error?.detail"))
    val errorMessage: String? get() = error?.detail

    /** Serializes to the §4.2 JSON structure, omitting absent members. */
    fun toJson(): JsonObject = buildJsonObject {
        put("contentType", contentType)
        error?.let { put("error", it.toJson()) }
        if (proof.isNotEmpty()) put("proof", JsonArray(proof))
        pattern?.let { put("pattern", it) }
        driverUrl?.let { put("driverUrl", it) }
        duration?.let { put("duration", it) }
        retrieved?.let { put("retrieved", it.toXmlDateTime()) }
        properties.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
    }

    /** Flat map view; timestamps use the §3.1 datetime format. */
    fun toMap(): Map<String, Any?> = buildMap {
        put("contentType", contentType)
        error?.let { put("error", it.toJson()) }
        if (proof.isNotEmpty()) put("proof", proof)
        pattern?.let { put("pattern", it) }
        driverUrl?.let { put("driverUrl", it) }
        duration?.let { put("duration", it) }
        retrieved?.let { put("retrieved", it.toXmlDateTime()) }
        if (properties.isNotEmpty()) putAll(properties)
    }

    companion object {
        private val KNOWN_KEYS = setOf(
            "contentType", "error", "errorMessage", "proof",
            "pattern", "driverUrl", "duration", "retrieved", "properties"
        )

        /**
         * Builds metadata from a loosely-typed map, as produced by upstream resolver responses.
         *
         * Accepts both the CR object-valued `error` and the legacy v0.3 string-valued `error`.
         * Unrecognised members are retained in [properties] rather than dropped.
         */
        fun fromMap(map: Map<String, Any?>): DidResolutionMetadata {
            val rawError = map["error"]
            val error = when (rawError) {
                null -> null
                is JsonObject -> DidResolutionError.fromJson(rawError)
                is String -> DidResolutionError.fromJson(JsonPrimitive(rawError))
                is Map<*, *> -> {
                    val type = rawError["type"]?.toString()
                    type?.let {
                        DidResolutionError(
                            type = DidErrorType.fromLegacyCode(it),
                            title = rawError["title"]?.toString()
                                ?: DidErrorType.title(DidErrorType.fromLegacyCode(it)),
                            detail = rawError["detail"]?.toString()
                                ?: map["errorMessage"] as? String
                        )
                    }
                }
                else -> null
            }?.let { parsed ->
                // A legacy response carries its human-readable text in a sibling errorMessage member.
                if (parsed.detail == null) parsed.copy(detail = map["errorMessage"] as? String) else parsed
            }

            return DidResolutionMetadata(
                contentType = (map["contentType"] as? String) ?: DidMediaTypes.DID,
                error = error,
                proof = (map["proof"] as? List<*>)?.filterIsInstance<JsonObject>() ?: emptyList(),
                pattern = map["pattern"] as? String,
                driverUrl = map["driverUrl"] as? String,
                duration = (map["duration"] as? Number)?.toLong(),
                retrieved = (map["retrieved"] as? String)?.let { Instant.parse(it) },
                properties = (map["properties"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> (k as? String)?.let { it to (v?.toString() ?: "") } }
                    ?.toMap()
                    ?: map.entries
                        .filter { it.key !in KNOWN_KEYS }
                        .associate { (k, v) -> k to (v?.toString() ?: "") }
            )
        }

        /**
         * Builds metadata from a §4.2 JSON structure.
         *
         * This does not delegate to [fromMap]: a [JsonObject]'s values are [kotlinx.serialization.json.JsonElement]s,
         * so `as? String` casts would silently drop every member.
         */
        fun fromJson(json: JsonObject): DidResolutionMetadata = DidResolutionMetadata(
            contentType = json["contentType"]?.jsonPrimitive?.contentOrNull ?: DidMediaTypes.DID,
            error = DidResolutionError.fromJson(json["error"]),
            proof = (json["proof"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList(),
            pattern = json["pattern"]?.jsonPrimitive?.contentOrNull,
            driverUrl = json["driverUrl"]?.jsonPrimitive?.contentOrNull,
            duration = json["duration"]?.jsonPrimitive?.longOrNull,
            retrieved = json["retrieved"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it) },
            properties = json.entries
                .filter { it.key !in KNOWN_KEYS }
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }
                .toMap()
        )
    }
}
```

- [ ] **Step 3b: Migrate the same-module call sites so `:did:did-core` compiles**

Changing `error` from `String?` to `DidResolutionError?` and dropping the `errorMessage`
parameter breaks eight files in this module. Gradle compiles the whole main source set before
running any test, so the Step 1 test cannot run until these are fixed. Apply this rewrite at each
site listed in **Files** above:

```kotlin
// Before
DidResolutionMetadata(error = "notFound", errorMessage = "DID not found")
// After
DidResolutionMetadata(error = DidResolutionError.notFound("DID not found"))
```

Factory per legacy code: `notFound` → `notFound`, `invalidDid`/`invalidDidFormat` → `invalidDid`,
`methodNotSupported` → `methodNotSupported`, `resolutionError` → `internalError`. Where a site
passed only `errorMessage` with no `error` (`FallbackDidResolver.kt:84`), use
`DidResolutionError.internalError(<the message>)`.

For `did-core` tests asserting the old shape, `assertEquals("notFound", md.error)` becomes
`assertEquals(DidErrorType.NOT_FOUND, md.error?.type)`.

Leave `resolutionMetadataMap` and the map-based secondary constructors in
`DidResolutionResult.kt` alone — Task 7 removes them.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --max-workers 3`
Expected: PASS — the new `DidResolutionMetadataTest` green (9 tests) and the whole `did-core`
module suite still green.

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionMetadata.kt did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionMetadataTest.kt
git commit -m "feat(did-core)!: replace string error code with RFC 9457 error object in resolution metadata"
```

---

### Task 5: Complete `DidDocumentMetadata` (§4.3)

**Files:**
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/model/DidModels.kt:80-103`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/model/DidDocumentMetadataConformanceTest.kt`

**Interfaces:**
- Consumes: `XmlDateTimeSerializer` (Task 2).
- Produces: `DidDocumentMetadata` gains `nextVersionId: String?` and `proof: List<JsonObject>`; all four `Instant` members use `XmlDateTimeSerializer`; new `fun toJson(): JsonObject`.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/model/DidDocumentMetadataConformanceTest.kt`:

```kotlin
package org.trustweave.did.model

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DidDocumentMetadataConformanceTest {

    @Test
    fun `nextVersionId is a document metadata property`() {
        val metadata = DidDocumentMetadata(versionId = "3", nextVersionId = "4")
        assertEquals("4", metadata.nextVersionId)
    }

    @Test
    fun `timestamps serialize without sub-second precision`() {
        val metadata = DidDocumentMetadata(
            created = Instant.parse("2019-03-23T06:35:22.512Z"),
            updated = Instant.parse("2023-08-10T13:40:06.001Z")
        )
        val json = metadata.toJson()
        assertEquals(JsonPrimitive("2019-03-23T06:35:22Z"), json["created"])
        assertEquals(JsonPrimitive("2023-08-10T13:40:06Z"), json["updated"])
    }

    @Test
    fun `deactivated is omitted when false and emitted when true`() {
        assertFalse(DidDocumentMetadata().toJson().containsKey("deactivated"))
        assertEquals(JsonPrimitive(true), DidDocumentMetadata(deactivated = true).toJson()["deactivated"])
    }

    @Test
    fun `proof entries are emitted as an array`() {
        val proof = buildJsonObject { put("type", "DataIntegrityProof") }
        assertTrue(DidDocumentMetadata(proof = listOf(proof)).toJson().containsKey("proof"))
    }

    @Test
    fun `an empty metadata structure serializes to an empty object`() {
        assertTrue(DidDocumentMetadata().toJson().isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.model.DidDocumentMetadataConformanceTest" --max-workers 3`
Expected: FAIL — `No parameter with name 'nextVersionId'`.

- [ ] **Step 3: Replace the `DidDocumentMetadata` declaration**

In `did/did-core/src/main/kotlin/org/trustweave/did/model/DidModels.kt`, replace the KDoc block and
`data class DidDocumentMetadata(...)` (currently lines 80-103) with:

```kotlin
/**
 * DID document metadata per DID Resolution 1.0 §4.3 — metadata about the DID *document*.
 *
 * @param created timestamp of the Create operation (SHOULD be present)
 * @param updated timestamp of the last Update operation (SHOULD be present)
 * @param deactivated MUST be true when the DID is deactivated; omitted otherwise
 * @param versionId version of the last Update operation (SHOULD be present)
 * @param nextUpdate timestamp of the next Update, when this is not the latest version
 * @param nextVersionId version of the next Update, when this is not the latest version
 * @param canonicalId the canonical DID for the subject, per the DID method
 * @param equivalentId DIDs the method guarantees are logically equivalent to `id`
 * @param proof proofs added by the controller or the verifiable data registry
 */
@Serializable
data class DidDocumentMetadata(
    @Serializable(with = XmlDateTimeSerializer::class) val created: Instant? = null,
    @Serializable(with = XmlDateTimeSerializer::class) val updated: Instant? = null,
    val deactivated: Boolean = false,
    val versionId: String? = null,
    @Serializable(with = XmlDateTimeSerializer::class) val nextUpdate: Instant? = null,
    val nextVersionId: String? = null,
    val canonicalId: Did? = null,
    val equivalentId: List<Did> = emptyList(),
    val proof: List<JsonObject> = emptyList()
) {
    /** Serializes to the §4.3 JSON structure, omitting absent members. */
    fun toJson(): JsonObject = buildJsonObject {
        created?.let { put("created", it.toXmlDateTime()) }
        updated?.let { put("updated", it.toXmlDateTime()) }
        if (deactivated) put("deactivated", true)
        versionId?.let { put("versionId", it) }
        nextUpdate?.let { put("nextUpdate", it.toXmlDateTime()) }
        nextVersionId?.let { put("nextVersionId", it) }
        canonicalId?.let { put("canonicalId", it.value) }
        if (equivalentId.isNotEmpty()) {
            put("equivalentId", JsonArray(equivalentId.map { JsonPrimitive(it.value) }))
        }
        if (proof.isNotEmpty()) put("proof", JsonArray(proof))
    }
}
```

Add these imports to the top of `DidModels.kt`:

```kotlin
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.did.util.XmlDateTimeSerializer
import org.trustweave.did.util.toXmlDateTime
```

Remove the now-unused `import kotlinx.serialization.Contextual` only if no other declaration in
the file still uses it (`VerificationMethod.publicKeyJwk` does — keep it).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.model.DidDocumentMetadataConformanceTest" --max-workers 3`
Expected: PASS, 5 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/model/DidModels.kt did/did-core/src/test/kotlin/org/trustweave/did/model/DidDocumentMetadataConformanceTest.kt
git commit -m "feat(did-core): add nextVersionId and proof to DID document metadata per section 4.3"
```

---

### Task 6: `ResolutionOptions` (§4.1, §13.2, §13.4)

**Files:**
- Create: `did/did-core/src/main/kotlin/org/trustweave/did/resolution/ResolutionOptions.kt`
- Delete: `did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionV03.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolution/ResolutionOptionsTest.kt`

**Interfaces:**
- Consumes: `DidResolutionError` (Task 1), `XmlDateTimeSerializer` (Task 2).
- Produces: `data class ResolutionOptions(accept: String? = null, expandRelativeUrls: Boolean = false, versionId: String? = null, versionTime: Instant? = null, noCache: Boolean = false, additional: Map<String, String> = emptyMap())` with `fun isEmpty(): Boolean`, `fun methodSpecificOptions(): Set<String>`, `fun validate(): DidResolutionError?`, and `companion object { val EMPTY: ResolutionOptions; fun fromQueryParameters(params: Map<String, String>): ResolutionOptions }`.

The `DereferenceResult` / `DereferenceContent` stubs in `DidResolutionV03.kt` are deleted with it;
nothing references them (verify with the grep in Step 4).

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolution/ResolutionOptionsTest.kt`:

```kotlin
package org.trustweave.did.resolution

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.trustweave.did.resolver.DidErrorType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResolutionOptionsTest {

    @Test
    fun `EMPTY is empty`() {
        assertTrue(ResolutionOptions.EMPTY.isEmpty())
    }

    @Test
    fun `any populated option makes it non-empty`() {
        assertFalse(ResolutionOptions(accept = "application/did").isEmpty())
        assertFalse(ResolutionOptions(expandRelativeUrls = true).isEmpty())
        assertFalse(ResolutionOptions(noCache = true).isEmpty())
    }

    @Test
    fun `accept and expandRelativeUrls are not method specific`() {
        val options = ResolutionOptions(accept = "application/did", expandRelativeUrls = true)
        assertTrue(options.methodSpecificOptions().isEmpty())
    }

    @Test
    fun `versionId versionTime and noCache are method specific`() {
        assertEquals(setOf("versionId"), ResolutionOptions(versionId = "3").methodSpecificOptions())
        assertEquals(
            setOf("versionTime"),
            ResolutionOptions(versionTime = Instant.parse("2021-05-10T17:00:00Z")).methodSpecificOptions()
        )
        assertEquals(setOf("noCache"), ResolutionOptions(noCache = true).methodSpecificOptions())
    }

    @Test
    fun `versionId and versionTime are mutually exclusive`() {
        val error = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z")
        ).validate()
        assertEquals(DidErrorType.INVALID_OPTIONS, error?.type)
    }

    @Test
    fun `valid options produce no error`() {
        assertNull(ResolutionOptions(accept = "application/did").validate())
    }

    @Test
    fun `an unparseable versionTime is rejected by fromQueryParameters`() {
        val options = ResolutionOptions.fromQueryParameters(mapOf("versionTime" to "not-a-date"))
        assertEquals(DidErrorType.INVALID_OPTIONS, options.validate()?.type)
    }

    @Test
    fun `fromQueryParameters reads the spec-defined options`() {
        val options = ResolutionOptions.fromQueryParameters(
            mapOf(
                "versionId" to "3",
                "expandRelativeUrls" to "true",
                "noCache" to "true",
                "blockHeight" to "9001"
            )
        )
        assertEquals("3", options.versionId)
        assertTrue(options.expandRelativeUrls)
        assertTrue(options.noCache)
        assertEquals("9001", options.additional["blockHeight"])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolution.ResolutionOptionsTest" --max-workers 3`
Expected: FAIL — `Unresolved reference: EMPTY`, `methodSpecificOptions`.

- [ ] **Step 3: Write the implementation**

Create `did/did-core/src/main/kotlin/org/trustweave/did/resolution/ResolutionOptions.kt`:

```kotlin
package org.trustweave.did.resolution

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.trustweave.did.resolver.DidResolutionError
import org.trustweave.did.util.XmlDateTimeSerializer

/**
 * Input options to the DID resolution function per DID Resolution 1.0 §4.1.
 *
 * The structure is REQUIRED as an input to `resolve` but MAY be empty — use [EMPTY].
 *
 * @param accept media type of the caller's preferred DID document representation (§4.1)
 * @param expandRelativeUrls expand relative DID URLs in the document to absolute ones (§4.1)
 * @param versionId resolve a specific document version (§3, §13.4)
 * @param versionTime resolve the version valid at this time (§3, §13.4)
 * @param noCache request a fresh read from the verifiable data registry (§13.2)
 * @param additional registered or method-specific options not modelled above
 */
@Serializable
data class ResolutionOptions(
    val accept: String? = null,
    val expandRelativeUrls: Boolean = false,
    val versionId: String? = null,
    @Serializable(with = XmlDateTimeSerializer::class) val versionTime: Instant? = null,
    val noCache: Boolean = false,
    val additional: Map<String, String> = emptyMap(),
    /**
     * Set when an inbound option value failed to parse (e.g. a malformed `versionTime`).
     * Surfaced by [validate] as INVALID_OPTIONS rather than thrown, because §4.4 requires an
     * error *result*, not an exception.
     */
    @Transient private val parseError: String? = null
) {
    /** True when no option is set — the "MAY be empty" case of §4. */
    fun isEmpty(): Boolean =
        accept == null && !expandRelativeUrls && versionId == null &&
            versionTime == null && !noCache && additional.isEmpty() && parseError == null

    /**
     * Options that only a DID method can satisfy. A resolver that cannot delegate these MUST
     * return FEATURE_NOT_SUPPORTED (§4.4 step 3).
     *
     * `accept` and `expandRelativeUrls` are deliberately excluded: they are method-independent
     * and handled by the generic resolver.
     */
    fun methodSpecificOptions(): Set<String> = buildSet {
        if (versionId != null) add("versionId")
        if (versionTime != null) add("versionTime")
        if (noCache) add("noCache")
    }

    /** Returns an INVALID_OPTIONS error when the options are invalid (§4.4 step 4), else null. */
    fun validate(): DidResolutionError? = when {
        parseError != null -> DidResolutionError.invalidOptions(parseError)
        versionId != null && versionTime != null ->
            DidResolutionError.invalidOptions(
                "versionId and versionTime are mutually exclusive (DID Resolution 1.0 §13.4)"
            )
        else -> null
    }

    companion object {
        /** The empty options structure. */
        val EMPTY: ResolutionOptions = ResolutionOptions()

        private val SPEC_KEYS = setOf("accept", "expandRelativeUrls", "versionId", "versionTime", "noCache")

        /**
         * Builds options from query-parameter style input, as delivered by the §12.1 GET binding.
         * A malformed `versionTime` is recorded and surfaced by [validate] as INVALID_OPTIONS.
         */
        fun fromQueryParameters(params: Map<String, String>): ResolutionOptions {
            var parseError: String? = null
            val versionTime = params["versionTime"]?.let { raw ->
                try {
                    Instant.parse(raw)
                } catch (_: IllegalArgumentException) {
                    parseError = "versionTime is not a valid datetime (DID Resolution 1.0 §3.1): '$raw'"
                    null
                }
            }
            return ResolutionOptions(
                accept = params["accept"],
                expandRelativeUrls = params["expandRelativeUrls"]?.equals("true", ignoreCase = true) == true,
                versionId = params["versionId"],
                versionTime = versionTime,
                noCache = params["noCache"]?.equals("true", ignoreCase = true) == true,
                additional = params.filterKeys { it !in SPEC_KEYS },
                parseError = parseError
            )
        }
    }
}
```

- [ ] **Step 4: Delete the v0.3 file after confirming nothing uses its types**

```bash
grep -rn "DereferenceResult\|DereferenceContent\|resolution.ResolutionOptions" --include=*.kt . | grep -v "/bin/" | grep -v "/.claude/"
```
Expected: only matches in `DidResolutionV03.kt` itself and the new `ResolutionOptions.kt`. Then:

```bash
git rm did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionV03.kt
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolution.ResolutionOptionsTest" --max-workers 3`
Expected: PASS, 8 tests.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolution/ did/did-core/src/test/kotlin/org/trustweave/did/resolution/
git commit -m "feat(did-core): add DID Resolution 1.0 resolution options and drop the v0.3 stubs"
```

---

### Task 7: `DidResolutionResult` — `Deactivated` and `OptionsError`

**Files:**
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResult.kt` (whole file)
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResultExtensions.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionResultConformanceTest.kt`

**Interfaces:**
- Consumes: `DidResolutionError`, `DidErrorType` (Task 1); `DidDocumentMetadata` (Task 5); `DidResolutionMetadata` (Task 4).
- Produces: `DidResolutionResult.Deactivated(did: Did, documentMetadata: DidDocumentMetadata, resolutionMetadata: DidResolutionMetadata)`; `DidResolutionResult.Failure.OptionsError(did: Did?, reason: String, errorType: String, resolutionMetadata: DidResolutionMetadata)`; extension properties `val DidResolutionResult.isDeactivated: Boolean`, `val DidResolutionResult.documentOrNull: DidDocument?`, `val DidResolutionResult.error: DidResolutionError?`.
  The map-based backward-compatibility secondary constructors and `resolutionMetadataMap` accessors are removed — `DidResolutionMetadata.fromMap` is the supported path.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionResultConformanceTest.kt`:

```kotlin
package org.trustweave.did.resolver

import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocumentMetadata
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidResolutionResultConformanceTest {

    private val did = Did("did:example:123456789abcdefghi")

    @Test
    fun `a deactivated result carries no document`() {
        val result = DidResolutionResult.Deactivated(did)
        assertNull(result.documentOrNull)
        assertTrue(result.isDeactivated)
    }

    @Test
    fun `a deactivated result forces the deactivated flag`() {
        assertTrue(DidResolutionResult.Deactivated(did).documentMetadata.deactivated)
    }

    @Test
    fun `constructing a deactivated result with a non-deactivated metadata is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DidResolutionResult.Deactivated(did, DidDocumentMetadata(deactivated = false))
        }
    }

    @Test
    fun `a deactivated result carries no error`() {
        assertNull(DidResolutionResult.Deactivated(did).error)
    }

    @Test
    fun `not found defaults to the NOT_FOUND error URI`() {
        val result = DidResolutionResult.Failure.NotFound(did)
        assertEquals(DidErrorType.NOT_FOUND, result.error?.type)
    }

    @Test
    fun `invalid format defaults to the INVALID_DID error URI`() {
        val result = DidResolutionResult.Failure.InvalidFormat("did:", "missing method-specific id")
        assertEquals(DidErrorType.INVALID_DID, result.error?.type)
    }

    @Test
    fun `method not registered defaults to the METHOD_NOT_SUPPORTED error URI`() {
        val result = DidResolutionResult.Failure.MethodNotRegistered("nope")
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, result.error?.type)
    }

    @Test
    fun `resolution error defaults to the INTERNAL_ERROR error URI`() {
        val result = DidResolutionResult.Failure.ResolutionError(did, "socket closed")
        assertEquals(DidErrorType.INTERNAL_ERROR, result.error?.type)
    }

    @Test
    fun `options error defaults to FEATURE_NOT_SUPPORTED and accepts INVALID_OPTIONS`() {
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            DidResolutionResult.Failure.OptionsError(did, "versionId is not supported").error?.type
        )
        assertEquals(
            DidErrorType.INVALID_OPTIONS,
            DidResolutionResult.Failure.OptionsError(
                did,
                "mutually exclusive",
                DidErrorType.INVALID_OPTIONS
            ).error?.type
        )
    }

    @Test
    fun `every failure exposes a non-null error`() {
        val failures: List<DidResolutionResult.Failure> = listOf(
            DidResolutionResult.Failure.NotFound(did),
            DidResolutionResult.Failure.InvalidFormat("did:", "bad"),
            DidResolutionResult.Failure.MethodNotRegistered("nope"),
            DidResolutionResult.Failure.ResolutionError(did, "boom"),
            DidResolutionResult.Failure.OptionsError(did, "nope")
        )
        failures.forEach { failure ->
            assertTrue(failure.error != null, "${failure::class.simpleName} must carry an error object")
            assertNull(failure.documentOrNull, "${failure::class.simpleName} must carry no document")
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DidResolutionResultConformanceTest" --max-workers 3`
Expected: FAIL — `Unresolved reference: Deactivated`.

- [ ] **Step 3: Replace `DidResolutionResult.kt`**

Replace the file entirely with:

```kotlin
package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata

/**
 * Result of the DID resolution function per DID Resolution 1.0 §4.
 *
 * The three outputs of `resolve` — `didDocument`, `didDocumentMetadata` and
 * `didResolutionMetadata` — are modelled as a sealed hierarchy so that the spec's invariants
 * hold by construction:
 *
 * - [Success] is the only variant carrying a document.
 * - [Deactivated] carries no document and forces `deactivated = true` (§4.4).
 * - [Failure] carries no document, empty document metadata, and a non-null RFC 9457 error (§4).
 *
 * **Example Usage:**
 * ```kotlin
 * when (val result = resolver.resolve(did)) {
 *     is DidResolutionResult.Success -> println(result.document.id)
 *     is DidResolutionResult.Deactivated -> println("deactivated: ${result.did}")
 *     is DidResolutionResult.Failure -> println(result.error?.type)
 * }
 * ```
 */
sealed class DidResolutionResult {

    /**
     * Resolution succeeded.
     *
     * @param document the resolved DID document; its `id` equals the DID that was resolved
     * @param documentMetadata §4.3 document metadata
     * @param resolutionMetadata §4.2 resolution metadata
     */
    data class Success(
        val document: DidDocument,
        val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
        val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata()
    ) : DidResolutionResult()

    /**
     * The DID exists but has been deactivated (§4.4).
     *
     * Per the spec the resolver returns no document in this case; the caller learns of the
     * deactivation from [documentMetadata]. This is not an error — the §12.1 binding maps it
     * to HTTP 410, not to a 4xx error response.
     */
    data class Deactivated(
        val did: Did,
        val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(deactivated = true),
        val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata()
    ) : DidResolutionResult() {
        init {
            require(documentMetadata.deactivated) {
                "Deactivated result requires documentMetadata.deactivated = true (§4.4)"
            }
        }
    }

    /**
     * Resolution failed. Every variant carries a non-null [DidResolutionMetadata.error].
     */
    sealed class Failure : DidResolutionResult() {

        /** §4.4: the DID does not exist. */
        data class NotFound(
            val did: Did,
            val reason: String? = null,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.notFound(reason ?: "DID not found: ${did.value}")
            )
        ) : Failure()

        /** §4.4 step 1: the input does not conform to the DID syntax. */
        data class InvalidFormat(
            val did: String,
            val reason: String,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.invalidDid(reason)
            )
        ) : Failure()

        /** §4.4 step 2: the DID method is not supported by this resolver. */
        data class MethodNotRegistered(
            val method: String,
            val availableMethods: List<String> = emptyList(),
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.methodNotSupported("DID method '$method' is not registered")
            )
        ) : Failure()

        /** §4.4 final step: an unexpected error during resolution. */
        data class ResolutionError(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.internalError(reason)
            )
        ) : Failure()

        /**
         * §4.4 steps 3 and 4: a resolution option is unsupported or invalid.
         *
         * @param errorType [DidErrorType.FEATURE_NOT_SUPPORTED] (default) or
         *   [DidErrorType.INVALID_OPTIONS]
         */
        data class OptionsError(
            val did: Did?,
            val reason: String,
            val errorType: String = DidErrorType.FEATURE_NOT_SUPPORTED,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.of(errorType, reason)
            )
        ) : Failure()
    }
}
```

- [ ] **Step 4: Rewrite the extensions file**

Replace `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResultExtensions.kt` with:

```kotlin
package org.trustweave.did.resolver

import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata

/**
 * Ergonomic accessors over [DidResolutionResult].
 */

/** True when the DID resolved to a document. */
val DidResolutionResult.isSuccess: Boolean
    get() = this is DidResolutionResult.Success

/** True when the DID exists but is deactivated (§4.4). */
val DidResolutionResult.isDeactivated: Boolean
    get() = this is DidResolutionResult.Deactivated

/** True when the DID could not be found. */
val DidResolutionResult.isNotFound: Boolean
    get() = this is DidResolutionResult.Failure.NotFound

/** True when resolution failed with an error. Deactivation is NOT an error. */
val DidResolutionResult.hasError: Boolean
    get() = this is DidResolutionResult.Failure

/** The resolved document, or null for deactivated and failed resolutions (§4). */
val DidResolutionResult.documentOrNull: DidDocument?
    get() = (this as? DidResolutionResult.Success)?.document

/** §4.2 resolution metadata for any outcome. */
val DidResolutionResult.resolutionMetadata: DidResolutionMetadata
    get() = when (this) {
        is DidResolutionResult.Success -> resolutionMetadata
        is DidResolutionResult.Deactivated -> resolutionMetadata
        is DidResolutionResult.Failure.NotFound -> resolutionMetadata
        is DidResolutionResult.Failure.InvalidFormat -> resolutionMetadata
        is DidResolutionResult.Failure.MethodNotRegistered -> resolutionMetadata
        is DidResolutionResult.Failure.ResolutionError -> resolutionMetadata
        is DidResolutionResult.Failure.OptionsError -> resolutionMetadata
    }

/** §4.3 document metadata; empty for failed resolutions, as §4 requires. */
val DidResolutionResult.documentMetadata: DidDocumentMetadata
    get() = when (this) {
        is DidResolutionResult.Success -> documentMetadata
        is DidResolutionResult.Deactivated -> documentMetadata
        is DidResolutionResult.Failure -> DidDocumentMetadata()
    }

/** The RFC 9457 error object, or null when resolution did not fail. */
val DidResolutionResult.error: DidResolutionError?
    get() = resolutionMetadata.error

/** The error type URI, or null when resolution did not fail. */
val DidResolutionResult.errorType: String?
    get() = error?.type

/** Human-readable failure text, or null when resolution did not fail. */
val DidResolutionResult.errorMessage: String?
    get() = error?.detail

/**
 * Returns the resolved [DidDocument] or throws.
 *
 * A deactivated DID throws — per §4.4 there is no document to return.
 */
fun DidResolutionResult.getOrThrow(): DidDocument = when (this) {
    is DidResolutionResult.Success -> document
    is DidResolutionResult.Deactivated -> throw IllegalStateException("DID is deactivated: ${did.value}")
    is DidResolutionResult.Failure -> throw IllegalStateException(
        errorMessage ?: "DID resolution failed: ${errorType ?: "unknown error"}"
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DidResolutionResultConformanceTest" --max-workers 3`
Expected: PASS, 10 tests.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResult.kt did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResultExtensions.kt did/did-core/src/test/kotlin/org/trustweave/did/resolver/DidResolutionResultConformanceTest.kt
git commit -m "feat(did-core)!: add Deactivated and OptionsError resolution results per section 4.4"
```

---

### Task 8: Repair the repo-wide compile fallout

**Files:**
- Modify: every file the compiler flags. The known set, from a pre-change survey:
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/RegistryBasedResolver.kt:73`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DefaultUniversalResolver.kt:237,251,293,312`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/CachingDidResolver.kt`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/FallbackDidResolver.kt`
  - `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DecentralizedResolutionStrategy.kt`
  - `did/did-core/src/main/kotlin/org/trustweave/did/registry/DidMethodRegistry.kt:105`
  - `did/did-core/src/main/kotlin/org/trustweave/did/batch/BatchOperationsService.kt`
  - `did/did-core/src/main/kotlin/org/trustweave/did/dsl/DidExtensions.kt`, `dsl/ResolverExtensions.kt`
  - `did/did-core/src/main/kotlin/org/trustweave/did/rotation/DidRotationService.kt`
  - `did/did-core/src/main/kotlin/org/trustweave/did/verifier/DidDocumentDelegationVerifier.kt`
  - `did/plugins/base/src/main/kotlin/org/trustweave/did/base/DidMethodUtils.kt:234`
  - `did/plugins/{cheqd,ebsi,ens,ethr,ion,orb,peer,plc,polygon,sol,web}/…`
  - `did/plugins/godiddy/src/main/kotlin/org/trustweave/godiddy/resolver/GodiddyResolver.kt:231-241`
  - `did/registrar/src/main/kotlin/org/trustweave/did/registrar/method/HttpDidMethod.kt`
  - `credentials/credential-api/src/main/kotlin/org/trustweave/credential/did/CredentialServiceDidExtensions.kt`
  - `credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/ProofEngineUtils.kt`
  - `credentials/plugins/{bbs,oidc4vp,siop}/…`
  - `trust/src/main/kotlin/org/trustweave/trust/TrustWeave.kt`
  - `kms/plugins/waltid/src/main/kotlin/org/trustweave/waltid/did/WaltIdDidMethods.kt`
  - `testkit/src/main/kotlin/org/trustweave/testkit/…`
  - `distribution/examples/src/main/kotlin/org/trustweave/examples/…`

**Interfaces:**
- Consumes: everything from Tasks 4, 5, 7.
- Produces: a compiling repository. No new public API.

Apply these three mechanical rewrites:

1. `DidResolutionMetadata(error = "someCode", errorMessage = "text")`
   → `DidResolutionMetadata(error = DidResolutionError.<factory>("text"))`, choosing the factory
   from the legacy code: `notFound` → `notFound`, `invalidDid`/`invalidDidFormat` → `invalidDid`,
   `methodNotSupported` → `methodNotSupported`, `resolutionError` → `internalError`.
2. `result.resolutionMetadataMap` → `result.resolutionMetadata.toMap()`.
3. Every `when` over `DidResolutionResult` that the compiler reports as non-exhaustive gains a
   `is DidResolutionResult.Deactivated ->` branch; every `when` over
   `DidResolutionResult.Failure` gains `is DidResolutionResult.Failure.OptionsError ->`.

**Deactivation semantics are a judgement call per call site.** Use this rule: any consumer that
resolves a DID in order to *verify a signature, validate a presentation, or authorise an action*
MUST treat `Deactivated` as a verification failure, not as a missing document. That covers
`ProofEngineUtils`, `Bbs2023ProofEngine`, `Oidc4VpService`, `SiopV2Service`,
`DidDocumentDelegationVerifier` and `CredentialServiceDidExtensions`. Purely informational
consumers (examples, batch listing, rotation reporting) may surface it as a distinct state.

- [ ] **Step 1: Get the full failure list**

Run: `./gradlew compileKotlin compileTestKotlin --max-workers 3 --continue 2>&1 | grep -E "^e: " | sort -u`
Expected: a list of `e: file:line` errors. Save it; it is the task's work queue.

- [ ] **Step 2: Apply rewrite 1 and 2 across the flagged files**

Example — `did/did-core/src/main/kotlin/org/trustweave/did/resolver/RegistryBasedResolver.kt:73`, change:

```kotlin
resolutionMetadata = DidResolutionMetadata(
    error = "methodNotSupported",
    errorMessage = "DID method '${did.method}' is not registered",
    properties = mapOf("did" to didString)
)
```

to:

```kotlin
resolutionMetadata = DidResolutionMetadata(
    error = DidResolutionError.methodNotSupported("DID method '${did.method}' is not registered"),
    properties = mapOf("did" to didString)
)
```

Example — `did/plugins/base/src/main/kotlin/org/trustweave/did/base/DidMethodUtils.kt:234`, the
legacy-code `when` keeps mapping to sealed subtypes; only add an `"invalidoptions",
"featurenotsupported" ->` branch producing `DidResolutionResult.Failure.OptionsError`.

- [ ] **Step 3: Apply rewrite 3 with the verification-failure rule**

Example — `credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/ProofEngineUtils.kt`, wherever a `when` resolves an issuer DID, add:

```kotlin
is DidResolutionResult.Deactivated ->
    throw IllegalStateException("Issuer DID is deactivated: ${result.did.value}")
```

matching the surrounding file's existing failure-handling idiom (throw vs. return a
`VerificationResult.Failure`) rather than introducing a new one.

- [ ] **Step 4: Verify the whole build compiles**

Run: `./gradlew build -x test --max-workers 3`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full test suite and fix red tests**

Run: `./gradlew test --max-workers 3`
Expected: BUILD SUCCESSFUL. Tests that asserted `resolutionMetadata.error == "notFound"` become
`resolutionMetadata.error?.type == DidErrorType.NOT_FOUND`. Tests that asserted a deactivated DID
resolves to a `Success` with a document become assertions on `DidResolutionResult.Deactivated`.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
# Stage explicit trees only. NEVER `git add -A` here: the repo carries unrelated untracked work.
git add did/ credentials/ trust/ kms/ testkit/ distribution/
git status --short   # confirm nothing outside those trees is staged
git commit -m "refactor: migrate all resolution call sites to the DID Resolution 1.0 result model"
```

---

### Task 9: Thread `ResolutionOptions` through the resolver interfaces (§4)

**Files:**
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/DidMethod.kt:22-30`
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolver.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolver/ResolverOptionsContractTest.kt`

**Interfaces:**
- Consumes: `ResolutionOptions` (Task 6), `DidResolutionResult.Failure.OptionsError` (Task 7).
- Produces: `DidMethodResolver.resolveDid(did: Did, options: ResolutionOptions): DidResolutionResult` (defaulted) and `DidResolver.resolve(did: Did, options: ResolutionOptions): DidResolutionResult` (defaulted). The single-argument forms remain the only abstract members, so all 47 existing overrides keep compiling.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolver/ResolverOptionsContractTest.kt`:

```kotlin
package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.trustweave.did.DidMethodResolver
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolverOptionsContractTest {

    private val did = Did("did:example:123456789abcdefghi")

    private val method = DidMethodResolver { requested ->
        DidResolutionResult.Success(document = DidDocument(id = requested))
    }

    @Test
    fun `empty options delegate to the single-argument form`() = runBlocking {
        val result = method.resolveDid(did, ResolutionOptions.EMPTY)
        assertTrue(result is DidResolutionResult.Success)
    }

    @Test
    fun `method-independent options still delegate`() = runBlocking {
        val result = method.resolveDid(did, ResolutionOptions(accept = "application/did"))
        assertTrue(result is DidResolutionResult.Success)
    }

    @Test
    fun `an unsupported versionId yields FEATURE_NOT_SUPPORTED`() = runBlocking {
        val result = method.resolveDid(did, ResolutionOptions(versionId = "3"))
        assertEquals(DidErrorType.FEATURE_NOT_SUPPORTED, result.errorType)
    }

    @Test
    fun `an unsupported versionTime yields FEATURE_NOT_SUPPORTED`() = runBlocking {
        val options = ResolutionOptions(versionTime = Instant.parse("2021-05-10T17:00:00Z"))
        assertEquals(DidErrorType.FEATURE_NOT_SUPPORTED, method.resolveDid(did, options).errorType)
    }

    @Test
    fun `an unsupported noCache yields FEATURE_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            method.resolveDid(did, ResolutionOptions(noCache = true)).errorType
        )
    }

    @Test
    fun `invalid options yield INVALID_OPTIONS ahead of feature support`() = runBlocking {
        val options = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z")
        )
        assertEquals(DidErrorType.INVALID_OPTIONS, method.resolveDid(did, options).errorType)
    }

    @Test
    fun `a DidResolver gets the same default behaviour`() = runBlocking {
        val resolver = DidResolver { requested -> DidResolutionResult.Success(DidDocument(id = requested)) }
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            resolver.resolve(did, ResolutionOptions(versionId = "3")).errorType
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.ResolverOptionsContractTest" --max-workers 3`
Expected: FAIL — `Too many arguments for resolveDid`.

- [ ] **Step 3: Add the defaulted member to `DidMethodResolver`**

In `did/did-core/src/main/kotlin/org/trustweave/did/DidMethod.kt`, replace the body of
`fun interface DidMethodResolver` with:

```kotlin
fun interface DidMethodResolver {
    /**
     * Resolves a DID to its DID Document with empty resolution options.
     *
     * @param did Type-safe DID identifier
     * @return A [org.trustweave.did.resolver.DidResolutionResult] containing the document and metadata
     */
    suspend fun resolveDid(did: Did): DidResolutionResult

    /**
     * Resolves a DID with DID Resolution 1.0 §4.1 resolution options.
     *
     * The default implementation satisfies §4.4 steps 3 and 4 for methods that do not implement
     * versioning or cache control: invalid options yield INVALID_OPTIONS, unsupported
     * method-specific options yield FEATURE_NOT_SUPPORTED, and everything else delegates to
     * [resolveDid]. Methods that support `versionId`, `versionTime` or `noCache` override this.
     */
    suspend fun resolveDid(did: Did, options: ResolutionOptions): DidResolutionResult {
        options.validate()?.let { error ->
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = error.detail ?: "Invalid resolution options",
                errorType = error.type
            )
        }
        val unsupported = options.methodSpecificOptions()
        if (unsupported.isNotEmpty()) {
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = "Resolution options not supported by this DID method: " +
                    unsupported.sorted().joinToString(", ")
            )
        }
        return resolveDid(did)
    }
}
```

Add `import org.trustweave.did.resolution.ResolutionOptions` to the file's imports.

- [ ] **Step 4: Add the mirror member to `DidResolver`**

In `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolver.kt`, add inside the
interface, after the existing `resolve(did)` declaration:

```kotlin
    /**
     * Resolves a DID with DID Resolution 1.0 §4.1 resolution options.
     *
     * Default behaviour mirrors [org.trustweave.did.DidMethodResolver.resolveDid]: options are
     * validated (§4.4 step 4), unsupported method-specific options produce FEATURE_NOT_SUPPORTED
     * (§4.4 step 3), and anything else delegates to [resolve].
     */
    suspend fun resolve(did: Did, options: ResolutionOptions): DidResolutionResult {
        options.validate()?.let { error ->
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = error.detail ?: "Invalid resolution options",
                errorType = error.type
            )
        }
        val unsupported = options.methodSpecificOptions()
        if (unsupported.isNotEmpty()) {
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = "Resolution options not supported by this resolver: " +
                    unsupported.sorted().joinToString(", ")
            )
        }
        return resolve(did)
    }
```

Add `import org.trustweave.did.resolution.ResolutionOptions` to the file's imports.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.ResolverOptionsContractTest" --max-workers 3`
Expected: PASS, 7 tests.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/DidMethod.kt did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolver.kt did/did-core/src/test/kotlin/org/trustweave/did/resolver/ResolverOptionsContractTest.kt
git commit -m "feat(did-core): accept resolution options on resolveDid and resolve per section 4"
```

---

### Task 10: `expandRelativeUrls` (§4.1, §4.4)

**Files:**
- Create: `did/did-core/src/main/kotlin/org/trustweave/did/model/RelativeUrlExpansion.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/model/RelativeUrlExpansionTest.kt`

**Interfaces:**
- Consumes: `DidDocument`, `DidService` (existing model).
- Produces: `fun DidDocument.expandRelativeDidUrls(): DidDocument` in package `org.trustweave.did.model`.

**Design note for the implementer:** `VerificationMethodId` is `(did, keyId)` and therefore always
renders an absolute DID URL — verification methods and verification relationships cannot be
relative in this model, so they need no expansion. The only relative-capable identifier is
`DidService.id`, which is a raw `String`. Expansion resolves a leading `#` or a bare relative
reference against the document's `id`, per the DID Core "Relative DID URLs" rules.

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/model/RelativeUrlExpansionTest.kt`:

```kotlin
package org.trustweave.did.model

import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RelativeUrlExpansionTest {

    private val did = Did("did:example:123456789abcdefghi")

    private fun documentWithService(serviceId: String) = DidDocument(
        id = did,
        service = listOf(
            DidService(
                id = serviceId,
                type = listOf("VerifiableCredentialService"),
                serviceEndpoint = ServiceEndpoint.Url("https://example.com/vc/")
            )
        )
    )

    @Test
    fun `a fragment service id is expanded against the document id`() {
        val expanded = documentWithService("#vcs").expandRelativeDidUrls()
        assertEquals("did:example:123456789abcdefghi#vcs", expanded.service.first().id)
    }

    @Test
    fun `a relative path service id is expanded against the document id`() {
        val expanded = documentWithService("some/path").expandRelativeDidUrls()
        assertEquals("did:example:123456789abcdefghi/some/path", expanded.service.first().id)
    }

    @Test
    fun `an absolute DID URL service id is unchanged`() {
        val expanded = documentWithService("did:example:other#vcs").expandRelativeDidUrls()
        assertEquals("did:example:other#vcs", expanded.service.first().id)
    }

    @Test
    fun `an absolute http service id is unchanged`() {
        val expanded = documentWithService("https://example.com/svc").expandRelativeDidUrls()
        assertEquals("https://example.com/svc", expanded.service.first().id)
    }

    @Test
    fun `a document with nothing to expand is returned unchanged`() {
        val document = DidDocument(id = did)
        assertSame(document, document.expandRelativeDidUrls())
    }
}
```

`ServiceEndpoint` is the sealed class in
`did/did-core/src/main/kotlin/org/trustweave/did/model/ServiceEndpointExtensions.kt:39`; its
string variant is `ServiceEndpoint.Url(url: String)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.model.RelativeUrlExpansionTest" --max-workers 3`
Expected: FAIL — `Unresolved reference: expandRelativeDidUrls`.

- [ ] **Step 3: Write the implementation**

Create `did/did-core/src/main/kotlin/org/trustweave/did/model/RelativeUrlExpansion.kt`:

```kotlin
package org.trustweave.did.model

/**
 * Expands relative DID URLs in this document to absolute DID URLs, per the
 * `expandRelativeUrls` resolution option (DID Resolution 1.0 §4.1, §4.4).
 *
 * Verification methods and verification relationships are modelled as
 * [org.trustweave.did.identifiers.VerificationMethodId], which always renders an absolute DID URL,
 * so only [DidService.id] can be relative. A value that already carries a URI scheme is left
 * untouched; a leading `#` is appended to the document `id`; anything else is treated as a
 * relative path reference.
 *
 * Returns this document unchanged (same instance) when there is nothing to expand.
 */
fun DidDocument.expandRelativeDidUrls(): DidDocument {
    if (service.none { it.id.isRelativeDidUrl() }) return this
    return copy(
        service = service.map { svc ->
            if (svc.id.isRelativeDidUrl()) svc.copy(id = expandAgainst(id.value, svc.id)) else svc
        }
    )
}

/** True when this identifier has no URI scheme and therefore needs expansion. */
private fun String.isRelativeDidUrl(): Boolean {
    if (isEmpty()) return false
    if (startsWith("#")) return true
    // A scheme is ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":" per RFC 3986 §3.1.
    val colon = indexOf(':')
    if (colon <= 0) return true
    val scheme = substring(0, colon)
    return !(scheme[0].isLetter() && scheme.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' })
}

private fun expandAgainst(baseDid: String, relative: String): String = when {
    relative.startsWith("#") -> baseDid + relative
    relative.startsWith("/") -> baseDid + relative
    else -> "$baseDid/$relative"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.model.RelativeUrlExpansionTest" --max-workers 3`
Expected: PASS, 5 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/model/RelativeUrlExpansion.kt did/did-core/src/test/kotlin/org/trustweave/did/model/RelativeUrlExpansionTest.kt
git commit -m "feat(did-core): implement expandRelativeUrls resolution option"
```

---

### Task 11: Implement the §4.4 resolution algorithm in `RegistryBasedResolver`

**Files:**
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/RegistryBasedResolver.kt` (whole file)
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolver/RegistryBasedResolverAlgorithmTest.kt`

**Interfaces:**
- Consumes: `ResolutionOptions` (Task 6), `DidResolutionResult` variants (Task 7), `expandRelativeDidUrls` (Task 10), `DidMediaTypes` (Task 3).
- Produces: `RegistryBasedResolver.resolve(did: Did, options: ResolutionOptions)` overriding the interface default and executing §4.4 steps 2-6 in order. (Step 1 — DID syntax validation — is enforced by the `Did` constructor and by `DidMethodRegistry.resolve(String)`.)

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolver/RegistryBasedResolverAlgorithmTest.kt`:

```kotlin
package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegistryBasedResolverAlgorithmTest {

    private val did = Did("did:example:123456789abcdefghi")

    private class StubMethod(private val document: DidDocument) : DidMethod {
        override val method: String = "example"
        override suspend fun createDid(options: DidCreationOptions): DidDocument = document
        override suspend fun resolveDid(did: Did): DidResolutionResult =
            DidResolutionResult.Success(document)
        override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
            updater(document)
        override suspend fun deactivateDid(did: Did): Boolean = true
    }

    private fun resolverFor(document: DidDocument): RegistryBasedResolver {
        val registry = DidMethodRegistry()
        registry.register(StubMethod(document))
        return RegistryBasedResolver(registry)
    }

    @Test
    fun `an unregistered method yields METHOD_NOT_SUPPORTED`() = runBlocking {
        val resolver = RegistryBasedResolver(DidMethodRegistry())
        val result = resolver.resolve(Did("did:nope:123"))
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, result.errorType)
    }

    @Test
    fun `invalid options yield INVALID_OPTIONS before the method is consulted`() = runBlocking {
        val resolver = RegistryBasedResolver(DidMethodRegistry())
        val options = ResolutionOptions(
            versionId = "3",
            versionTime = kotlinx.datetime.Instant.parse("2021-05-10T17:00:00Z")
        )
        assertEquals(DidErrorType.INVALID_OPTIONS, resolver.resolve(did, options).errorType)
    }

    @Test
    fun `an unsupported accept media type yields REPRESENTATION_NOT_SUPPORTED`() = runBlocking {
        val resolver = resolverFor(DidDocument(id = did))
        val result = resolver.resolve(did, ResolutionOptions(accept = "application/did+cbor"))
        assertEquals(DidErrorType.REPRESENTATION_NOT_SUPPORTED, result.errorType)
    }

    @Test
    fun `a supported accept media type is echoed in contentType`() = runBlocking {
        val resolver = resolverFor(DidDocument(id = did))
        val result = resolver.resolve(did, ResolutionOptions(accept = "application/did+ld+json"))
        assertEquals("application/did+ld+json", (result as DidResolutionResult.Success).resolutionMetadata.contentType)
    }

    @Test
    fun `contentType defaults to application-did`() = runBlocking {
        val resolver = resolverFor(DidDocument(id = did))
        val result = resolver.resolve(did) as DidResolutionResult.Success
        assertEquals("application/did", result.resolutionMetadata.contentType)
    }

    @Test
    fun `expandRelativeUrls rewrites relative service ids`() = runBlocking {
        val document = DidDocument(
            id = did,
            service = listOf(
                DidService("#vcs", listOf("VerifiableCredentialService"), ServiceEndpoint.Url("https://example.com/vc/"))
            )
        )
        val resolver = resolverFor(document)
        val result = resolver.resolve(did, ResolutionOptions(expandRelativeUrls = true))
        assertEquals(
            "did:example:123456789abcdefghi#vcs",
            (result as DidResolutionResult.Success).document.service.first().id
        )
    }

    @Test
    fun `without the option relative service ids are left alone`() = runBlocking {
        val document = DidDocument(
            id = did,
            service = listOf(
                DidService("#vcs", listOf("VerifiableCredentialService"), ServiceEndpoint.Url("https://example.com/vc/"))
            )
        )
        val result = resolverFor(document).resolve(did) as DidResolutionResult.Success
        assertEquals("#vcs", result.document.service.first().id)
    }

    @Test
    fun `a document whose id does not match the requested DID is rejected`() = runBlocking {
        val resolver = resolverFor(DidDocument(id = Did("did:example:someoneelse")))
        val result = resolver.resolve(did)
        assertEquals(DidErrorType.INVALID_DID_DOCUMENT, result.errorType)
    }

    @Test
    fun `an unexpected method failure yields INTERNAL_ERROR`() = runBlocking {
        val registry = DidMethodRegistry()
        registry.register(object : DidMethod {
            override val method: String = "example"
            override suspend fun createDid(options: DidCreationOptions): DidDocument =
                throw UnsupportedOperationException()
            override suspend fun resolveDid(did: Did): DidResolutionResult = error("boom")
            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
                throw UnsupportedOperationException()
            override suspend fun deactivateDid(did: Did): Boolean = false
        })
        val result = RegistryBasedResolver(registry).resolve(did)
        assertEquals(DidErrorType.INTERNAL_ERROR, result.errorType)
        assertTrue(result is DidResolutionResult.Failure)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.RegistryBasedResolverAlgorithmTest" --max-workers 3`
Expected: FAIL — `REPRESENTATION_NOT_SUPPORTED` and `INVALID_DID_DOCUMENT` are never produced.

- [ ] **Step 3: Rewrite the resolver body**

Replace the class body of `RegistryBasedResolver` (keep the existing KDoc header, updating the
`resolve` example to include a `Deactivated` branch) with:

```kotlin
class RegistryBasedResolver(
    private val registry: DidMethodRegistry
) : DidResolver {

    override suspend fun resolve(did: Did): DidResolutionResult = resolve(did, ResolutionOptions.EMPTY)

    /**
     * Executes the DID Resolution 1.0 §4.4 algorithm.
     *
     * Step 1 (DID syntax validation) is enforced by the [Did] constructor and by
     * [DidMethodRegistry.resolve] for string input, so this method starts at step 2.
     */
    override suspend fun resolve(did: Did, options: ResolutionOptions): DidResolutionResult {
        // §4.4 step 2 — is the DID method supported?
        val method = registry.get(did.method)
            ?: return DidResolutionResult.Failure.MethodNotRegistered(
                method = did.method,
                availableMethods = registry.getAllMethodNames(),
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.methodNotSupported(
                        "DID method '${did.method}' is not registered"
                    ),
                    properties = mapOf("did" to did.value)
                )
            )

        // §4.4 step 4 — are the options valid? (checked before step 3 so that a contradictory
        // option set is reported as INVALID_OPTIONS rather than FEATURE_NOT_SUPPORTED)
        options.validate()?.let { error ->
            return DidResolutionResult.Failure.OptionsError(
                did = did,
                reason = error.detail ?: "Invalid resolution options",
                errorType = error.type
            )
        }

        // §4.4 step 3 — is the requested representation supported?
        val contentType = options.accept?.let { accept ->
            if (!DidMediaTypes.isSupportedDocumentType(accept)) {
                return DidResolutionResult.Failure.OptionsError(
                    did = did,
                    reason = "Representation not supported: '$accept'",
                    errorType = DidErrorType.REPRESENTATION_NOT_SUPPORTED
                )
            }
            DidMediaTypes.normalize(accept)
        } ?: DidMediaTypes.DID

        // §4.4 step 5 — execute the method's Resolve operation.
        val result = try {
            method.resolveDid(did, options)
        } catch (e: DidException) {
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error",
                cause = e,
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.internalError(e.message ?: "Unknown error"),
                    properties = buildMap {
                        put("did", did.value)
                        e.context.forEach { (k, v) -> put(k, v?.toString() ?: "") }
                    }
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = e.message ?: "Unknown error during resolution",
                cause = e,
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.internalError(e.message ?: "Unknown error during resolution"),
                    properties = mapOf("did" to did.value)
                )
            )
        }

        if (result !is DidResolutionResult.Success) return result

        // §4 — the resolved document's `id` MUST equal the DID that was resolved.
        if (result.document.id != did) {
            return DidResolutionResult.Failure.ResolutionError(
                did = did,
                reason = "Resolved document id '${result.document.id.value}' does not match " +
                    "requested DID '${did.value}'",
                resolutionMetadata = DidResolutionMetadata(
                    error = DidResolutionError.invalidDidDocument(
                        "Resolved document id '${result.document.id.value}' does not match " +
                            "requested DID '${did.value}'"
                    )
                )
            )
        }

        // §4.4 — a deactivated DID returns no document.
        if (result.documentMetadata.deactivated) {
            return DidResolutionResult.Deactivated(
                did = did,
                documentMetadata = result.documentMetadata,
                resolutionMetadata = result.resolutionMetadata.copy(contentType = contentType)
            )
        }

        // §4.4 — expandRelativeUrls post-processing.
        val document =
            if (options.expandRelativeUrls) result.document.expandRelativeDidUrls() else result.document

        return result.copy(
            document = document,
            resolutionMetadata = result.resolutionMetadata.copy(contentType = contentType)
        )
    }
}
```

Update the file's imports to include:

```kotlin
import org.trustweave.did.model.expandRelativeDidUrls
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolution.ResolutionOptions
```

and drop the now-unused `import org.trustweave.did.validation.DidValidator` and
`import org.trustweave.did.DidMethod` if the compiler reports them as unused.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.RegistryBasedResolverAlgorithmTest" --max-workers 3`
Expected: PASS, 9 tests.

- [ ] **Step 5: Run the module suite**

Run: `./gradlew :did:did-core:test --max-workers 3`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolver/RegistryBasedResolver.kt did/did-core/src/test/kotlin/org/trustweave/did/resolver/RegistryBasedResolverAlgorithmTest.kt
git commit -m "feat(did-core): implement the section 4.4 DID resolution algorithm end to end"
```

---

### Task 12: Deactivated DIDs stop returning a document

**Files:**
- Modify: `did/plugins/base/src/main/kotlin/org/trustweave/did/base/DidMethodUtils.kt:185-205`
  (`createSuccessResolutionResult` — the shared factory)
- Test: `did/plugins/base/src/test/kotlin/org/trustweave/did/base/DidMethodUtilsSpecComplianceTest.kt` (add tests)
- Test: `did/plugins/base/src/test/kotlin/org/trustweave/did/base/AbstractBlockchainDidMethodTest.kt` (add a test)

**Interfaces:**
- Consumes: `DidResolutionResult.Deactivated` (Task 7).
- Produces: no new API. `createSuccessResolutionResult` returns `Deactivated` instead of `Success`
  when its `deactivated` argument is true.

**Why this file and not the two abstract base classes:** a survey of the codebase found that
`DidMethodUtils.createSuccessResolutionResult(document, method, created, updated, deactivated)` is
the single shared factory that builds every method's success result — 31 call sites across 16
files (`AbstractWebDidMethod`, `AbstractBlockchainDidMethod`, and the cheqd, ebsi, ens, ethr, ion,
jwk, key, orb, peer, plc, polygon and sol plugins). It already receives the `deactivated` flag and
already puts it into `DidDocumentMetadata`. Branching there fixes every DID method at once.
Patching the two abstract base classes individually — as an earlier draft of this plan proposed —
would have left the twelve concrete plugins still returning a document for a deactivated DID.

Task 11 independently converts any `Success` whose `documentMetadata.deactivated` is true into
`Deactivated` at the resolver layer. That is deliberate defence in depth: it catches methods that
build their result without going through this factory.

- [ ] **Step 1: Write the failing tests**

Add to `did/plugins/base/src/test/kotlin/org/trustweave/did/base/DidMethodUtilsSpecComplianceTest.kt`,
following the fixture and import style already in that file:

```kotlin
    @Test
    fun `createSuccessResolutionResult returns Deactivated when the DID is deactivated`() {
        val document = DidMethodUtils.buildDidDocument(
            did = "did:testchain:abc123",
            verificationMethod = emptyList()
        )
        val result = DidMethodUtils.createSuccessResolutionResult(
            document = document,
            method = "testchain",
            deactivated = true
        )

        assertTrue(result is DidResolutionResult.Deactivated, "Expected Deactivated, got $result")
        assertTrue(result.documentMetadata.deactivated)
        assertEquals(document.id, result.did)
    }

    @Test
    fun `createSuccessResolutionResult returns Success when the DID is live`() {
        val document = DidMethodUtils.buildDidDocument(
            did = "did:testchain:abc123",
            verificationMethod = emptyList()
        )
        val result = DidMethodUtils.createSuccessResolutionResult(
            document = document,
            method = "testchain"
        )

        assertTrue(result is DidResolutionResult.Success, "Expected Success, got $result")
        assertFalse(result.documentMetadata.deactivated)
    }
```

If `DidMethodUtils.buildDidDocument` has a different signature in this codebase, read the file and
use whatever the existing tests in `DidMethodUtilsSpecComplianceTest` already use to build a
document — the assertions target the result type, not the document's contents.

Add to `AbstractBlockchainDidMethodTest`, reusing the `TestBlockchainDidMethod(kms, anchorClient,
txHashLookup)` fixture and the `document(DID)` helper its existing tests already use:

```kotlin
    @Test
    fun `a deactivated blockchain DID resolves to Deactivated with no document`() = runBlocking {
        val method = TestBlockchainDidMethod(kms, anchorClient)
        val doc = document(DID)
        method.anchor(doc)
        method.deactivate(DID, doc)

        val result = method.resolveDid(Did(DID))

        assertTrue(result is DidResolutionResult.Deactivated, "Expected Deactivated, got $result")
        assertTrue(result.documentMetadata.deactivated)
    }
```

If that test file builds its anchor client inline rather than holding it in a field named
`anchorClient`, construct it here the same way rather than referencing a field that does not exist.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :did:plugins:base:test --max-workers 3`
Expected: FAIL — "Expected Deactivated, got Success(...)" from both new tests.

- [ ] **Step 3: Branch the shared factory**

In `did/plugins/base/src/main/kotlin/org/trustweave/did/base/DidMethodUtils.kt`, replace the body
of `createSuccessResolutionResult` (lines 185-205) with:

```kotlin
    fun createSuccessResolutionResult(
        document: DidDocument,
        method: String,
        created: Instant? = null,
        updated: Instant? = null,
        deactivated: Boolean = false
    ): DidResolutionResult {
        val now = Clock.System.now()
        val documentMetadata = DidDocumentMetadata(
            created = created ?: now,
            updated = updated ?: now,
            deactivated = deactivated
        )
        val resolutionMetadata = DidResolutionMetadata(
            pattern = method,
            properties = mapOf("driver" to "TrustWeave")
        )

        // DID Resolution 1.0 §4.4: a deactivated DID resolves to no document at all. The caller
        // learns of the deactivation from documentMetadata.
        return if (deactivated) {
            DidResolutionResult.Deactivated(
                did = document.id,
                documentMetadata = documentMetadata,
                resolutionMetadata = resolutionMetadata
            )
        } else {
            DidResolutionResult.Success(
                document = document,
                documentMetadata = documentMetadata,
                resolutionMetadata = resolutionMetadata
            )
        }
    }
```

Update the function's KDoc to state that it returns `Deactivated` when `deactivated` is true.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :did:plugins:base:test --max-workers 3`
Expected: PASS. Existing tests that asserted a document came back for a deactivated DID must be
updated to assert `DidResolutionResult.Deactivated` — that behaviour change is the point of this
task, and any such test is evidence the old behaviour was relied upon.

- [ ] **Step 5: Run the dependent plugin suites**

Run: `./gradlew :did:plugins:web:test :did:plugins:ethr:test :did:plugins:orb:test :did:plugins:key:test :did:plugins:peer:test --max-workers 3`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/plugins/base/
git commit -m "fix(did)!: return no document for deactivated DIDs per DID Resolution 1.0 section 4.4"
```

---

### Task 13: DID Resolution Result envelope (§9)

**Files:**
- Create: `did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionResultJson.kt`
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolution/DidResolutionResultJsonTest.kt`

**Interfaces:**
- Consumes: `DidResolutionResult` variants (Task 7), `DidDocumentJsonProducer` (existing), `DidMediaTypes` (Task 3), `documentMetadata`/`resolutionMetadata` extensions (Task 7).
- Produces: `object DidResolutionResultJson` with `fun toJson(result: DidResolutionResult): JsonObject` and `const val MEDIA_TYPE: String` (= `DidMediaTypes.DID_RESOLUTION`).

- [ ] **Step 1: Write the failing test**

Create `did/did-core/src/test/kotlin/org/trustweave/did/resolution/DidResolutionResultJsonTest.kt`:

```kotlin
package org.trustweave.did.resolution

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DidResolutionResultJsonTest {

    private val did = Did("did:example:123456789abcdefghi")

    @Test
    fun `media type is application-did-resolution`() {
        assertEquals("application/did-resolution", DidResolutionResultJson.MEDIA_TYPE)
    }

    @Test
    fun `a success carries all three members`() {
        val json = DidResolutionResultJson.toJson(
            DidResolutionResult.Success(DidDocument(id = did))
        )
        assertTrue(json.containsKey("didDocument"))
        assertTrue(json.containsKey("didResolutionMetadata"))
        assertTrue(json.containsKey("didDocumentMetadata"))
        assertEquals(JsonPrimitive(did.value), json["didDocument"]!!.jsonObject["id"])
    }

    @Test
    fun `a failure has a null document and an empty document metadata`() {
        val json = DidResolutionResultJson.toJson(DidResolutionResult.Failure.NotFound(did))
        assertEquals(JsonNull, json["didDocument"])
        assertTrue(json["didDocumentMetadata"]!!.jsonObject.isEmpty())
        assertEquals(
            JsonPrimitive(DidErrorType.NOT_FOUND),
            json["didResolutionMetadata"]!!.jsonObject["error"]!!.jsonObject["type"]
        )
    }

    @Test
    fun `a deactivated result has a null document and deactivated metadata`() {
        val json = DidResolutionResultJson.toJson(
            DidResolutionResult.Deactivated(did, DidDocumentMetadata(deactivated = true))
        )
        assertEquals(JsonNull, json["didDocument"])
        assertEquals(JsonPrimitive(true), json["didDocumentMetadata"]!!.jsonObject["deactivated"])
        assertTrue(json["didResolutionMetadata"]!!.jsonObject["error"] == null)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolution.DidResolutionResultJsonTest" --max-workers 3`
Expected: FAIL — `Unresolved reference: DidResolutionResultJson`.

- [ ] **Step 3: Write the implementation**

Create `did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionResultJson.kt`:

```kotlin
package org.trustweave.did.resolution

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.documentMetadata
import org.trustweave.did.resolver.documentOrNull
import org.trustweave.did.resolver.resolutionMetadata

/**
 * Serializes a [DidResolutionResult] to the DID Resolution Result JSON structure of
 * DID Resolution 1.0 §9.
 *
 * The structure always carries all three members: `didDocument` (JSON null when there is no
 * document), `didResolutionMetadata` and `didDocumentMetadata` (an empty object on failure, as
 * §4 requires).
 */
object DidResolutionResultJson {

    /** Media type of this data structure (§9). */
    const val MEDIA_TYPE: String = DidMediaTypes.DID_RESOLUTION

    fun toJson(result: DidResolutionResult): JsonObject = buildJsonObject {
        val document = result.documentOrNull
        if (document == null) {
            put("didDocument", JsonNull)
        } else {
            put("didDocument", DidDocumentJsonProducer.toJsonObject(document))
        }
        put("didResolutionMetadata", result.resolutionMetadata.toJson())
        put("didDocumentMetadata", result.documentMetadata.toJson())
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolution.DidResolutionResultJsonTest" --max-workers 3`
Expected: PASS, 4 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionResultJson.kt did/did-core/src/test/kotlin/org/trustweave/did/resolution/DidResolutionResultJsonTest.kt
git commit -m "feat(did-core): serialize the section 9 DID resolution result envelope"
```

---

### Task 14: Make the Universal Resolver client CR-aware

**Files:**
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DefaultUniversalResolver.kt:207,216-320`
- Modify: `did/did-core/src/main/kotlin/org/trustweave/did/resolver/DefaultUniversalResolver.kt:440-460` (`parseDidDocumentMetadata`)
- Test: `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DefaultUniversalResolverCrTest.kt`

**Interfaces:**
- Consumes: `DidErrorType`, `DidResolutionError` (Task 1); `DidResolutionResult.Deactivated` (Task 7); `DidMediaTypes` (Task 3).
- Produces: no new public API. Behaviour: `Accept: application/did-resolution`; HTTP 410 → `Deactivated`; other non-2xx statuses map through the §12.1 table to the matching error type; upstream `didDocumentMetadata.nextVersionId` is parsed.

- [ ] **Step 1: Reuse the existing HTTP test fixture — do NOT add MockWebServer**

An earlier draft of this plan told you to add `libs.mockwebserver` to `did/did-core/build.gradle.kts`.
**Do not.** Task 4's fix rounds already built a JDK `com.sun.net.httpserver.HttpServer` fixture
inside `did/did-core/src/test/kotlin/org/trustweave/did/resolver/DefaultUniversalResolverTest.kt`
— the exact file this task extends. It binds port 0 (ephemeral, no collision), registers the
context path `/1.0/identifiers/` that `StandardUniversalResolverAdapter.buildResolveUrl` actually
produces, stops the server in a `finally` block, and drives the real `HttpClient.sendAsync` path.

Read that existing test before writing anything and reuse its helper shape. Adding a second HTTP
mocking technology to the same test class for the same purpose would be gratuitous.

- [ ] **Step 2: Write the failing tests**

Add these cases to `DefaultUniversalResolverTest.kt`, in the style of the existing HttpServer test.
Note `DefaultUniversalResolver`'s constructor validates `baseUrl` against `^https?://[^/]+`, so the
base URL must carry **no trailing slash** — build it as `"http://localhost:${server.address.port}"`.

Cases to cover:

1. **The resolve request asks for `application/did-resolution`.** Capture the inbound `Accept`
   header in the handler and assert it equals `application/did-resolution`.
2. **HTTP 410 maps to `Deactivated`.** Serve status 410 with body
   `{"didDocument":null,"didDocumentMetadata":{"deactivated":true}}`; assert the result is
   `DidResolutionResult.Deactivated` and `documentMetadata.deactivated` is true.
3. **HTTP 501 maps to `METHOD_NOT_SUPPORTED`.** Serve 501; assert `result.errorType` is
   `DidErrorType.METHOD_NOT_SUPPORTED`.
4. **HTTP 400 maps to `INVALID_DID`.** Serve 400; assert `result.errorType` is
   `DidErrorType.INVALID_DID`.
5. **A legacy string error in a 200 body is upgraded.** Serve
   `{"didResolutionMetadata":{"error":"notFound"},"didDocumentMetadata":{}}`; assert
   `result.errorType` is `DidErrorType.NOT_FOUND`.
6. **Upstream `nextVersionId` lands in document metadata.** Serve a 200 body whose
   `didDocumentMetadata` carries `{"versionId":"3","nextVersionId":"4"}` alongside a valid
   `didDocument`; assert `(result as DidResolutionResult.Success).documentMetadata.nextVersionId`
   is `"4"`.

`RetryConfig.retryableStatusCodes` does not contain 400, 410 or 501, so one response per test is
correct. If a test reports an unexpected extra request, serve the same response again rather than
weakening the assertion.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DefaultUniversalResolverTest" --max-workers 3`
Expected: FAIL — Accept is `application/json`, and 410/501/400 all fall into the generic error branch.

- [ ] **Step 4: Change the request Accept header**

At line 207 and at the equivalent line in `getSupportedMethods` (line 364), replace

```kotlin
.header("Accept", "application/json")
```

with, for the resolve request only:

```kotlin
.header("Accept", DidMediaTypes.DID_RESOLUTION)
```

Leave the `getSupportedMethods` request on `application/json` — the `/methods` endpoint is not part
of the §12.1 binding.

- [ ] **Step 5: Add the 410 and status-mapping branches**

In `performResolution`, insert a `410` branch after the `404` branch:

```kotlin
410 -> {
    val documentMetadata = try {
        val body = String(bodyStream.readNBytes(MAX_RESPONSE_BYTES), Charsets.UTF_8)
        parseJsonResponse(body)
            ?.let { protocolAdapter.extractDocumentMetadata(it) }
            ?.let { parseDidDocumentMetadata(it) }
    } catch (_: kotlinx.serialization.SerializationException) {
        null
    } ?: DidDocumentMetadata(deactivated = true)

    DidResolutionResult.Deactivated(
        did = Did(did),
        documentMetadata = documentMetadata.copy(deactivated = true),
        resolutionMetadata = DidResolutionMetadata(
            properties = mapOf("provider" to protocolAdapter.providerName)
        )
    )
}
```

Replace the `else ->` non-retryable branch's result construction with a status-driven mapping:

```kotlin
else -> {
    val statusCode = response.statusCode()
    if (statusCode in retryConfig.retryableStatusCodes) {
        throw IOException("Retryable HTTP error: $statusCode")
    }
    val errorType = when (statusCode) {
        400 -> DidErrorType.INVALID_DID
        406 -> DidErrorType.REPRESENTATION_NOT_SUPPORTED
        501 -> DidErrorType.METHOD_NOT_SUPPORTED
        else -> DidErrorType.INTERNAL_ERROR
    }
    val detail = "Upstream resolver returned HTTP $statusCode"
    if (errorType == DidErrorType.METHOD_NOT_SUPPORTED) {
        DidResolutionResult.Failure.MethodNotRegistered(
            method = Did(did).method,
            resolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.methodNotSupported(detail),
                properties = mapOf(
                    "statusCode" to statusCode.toString(),
                    "provider" to protocolAdapter.providerName
                )
            )
        )
    } else {
        DidResolutionResult.Failure.ResolutionError(
            did = Did(did),
            reason = detail,
            cause = null,
            resolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.of(errorType, detail),
                properties = mapOf(
                    "statusCode" to statusCode.toString(),
                    "provider" to protocolAdapter.providerName
                )
            )
        )
    }
}
```

- [ ] **Step 6: Parse `nextVersionId` from upstream document metadata**

In `parseDidDocumentMetadata` (around line 447), alongside the existing `versionId` extraction add:

```kotlin
val nextVersionId = metadataJson["nextVersionId"]?.jsonPrimitive?.content
```

and pass `nextVersionId = nextVersionId` into the `DidDocumentMetadata(...)` construction.

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :did:did-core:test --tests "org.trustweave.did.resolver.DefaultUniversalResolver*" --max-workers 3`
Expected: PASS.

- [ ] **Step 8: Apply the same `nextVersionId` parse to the GoDiddy resolver**

In `did/plugins/godiddy/src/main/kotlin/org/trustweave/godiddy/resolver/GodiddyResolver.kt:231-241`,
add the identical `nextVersionId` extraction and constructor argument.

Run: `./gradlew :did:plugins:godiddy:test --max-workers 3`
Expected: PASS.

- [ ] **Step 9: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-core/src/main/kotlin/org/trustweave/did/resolver/DefaultUniversalResolver.kt did/did-core/src/test/kotlin/org/trustweave/did/resolver/DefaultUniversalResolverCrTest.kt did/plugins/godiddy/
git commit -m "feat(did-core): make the universal resolver client speak the DID Resolution 1.0 HTTP binding"
```

---

### Task 15: DID URL query parsing (§3)

**Files:**
- Modify: `did/did-identifiers-mp/src/commonMain/kotlin/org/trustweave/did/identifiers/DidIdentifiers.kt` (the `DidUrl` value class, around line 268)
- Test: `did/did-identifiers-mp/src/commonTest/kotlin/org/trustweave/did/identifiers/DidUrlParsingTest.kt`

**Interfaces:**
- Consumes: `Did` (existing).
- Produces: `DidUrl` gains `val query: String?`, `val parameters: Map<String, String>`, `val service: String?`, `val serviceType: String?`, `val relativeRef: String?`, `val versionId: String?`, `val versionTime: String?`, `val hasDuplicateParameters: Boolean`. `path` and `fragment` are corrected to exclude the query component.

**Correctness note for the implementer:** the current `path` and `fragment` accessors do not
account for a query component, so `did:example:123/a?b=c#d` currently yields `path = "a?b=c"`.
Fix the split order to `did` → `path` → `query` → `fragment`, per RFC 3986.

- [ ] **Step 1: Write the failing test**

Create `did/did-identifiers-mp/src/commonTest/kotlin/org/trustweave/did/identifiers/DidUrlParsingTest.kt`.
The module's `build.gradle.kts` already configures a `commonTest` source set with
`libs.kotlin.test`, so only the directory and file are needed — no build-file change.

```kotlin
package org.trustweave.did.identifiers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidUrlParsingTest {

    @Test
    fun `a bare DID has no path query or fragment`() {
        val url = DidUrl("did:example:123")
        assertEquals(Did("did:example:123"), url.did)
        assertNull(url.path)
        assertNull(url.query)
        assertNull(url.fragment)
    }

    @Test
    fun `path query and fragment are separated in RFC 3986 order`() {
        val url = DidUrl("did:example:123/a/b?x=1#frag")
        assertEquals("a/b", url.path)
        assertEquals("x=1", url.query)
        assertEquals("frag", url.fragment)
    }

    @Test
    fun `a query without a path is parsed`() {
        val url = DidUrl("did:example:123?versionId=3")
        assertNull(url.path)
        assertEquals("versionId=3", url.query)
        assertEquals("3", url.versionId)
    }

    @Test
    fun `spec DID parameters are exposed by name`() {
        val url = DidUrl("did:example:123?service=files&relativeRef=%2Fresume.pdf")
        assertEquals("files", url.service)
        assertEquals("/resume.pdf", url.relativeRef)
    }

    @Test
    fun `serviceType and versionTime are exposed`() {
        val url = DidUrl("did:example:123?serviceType=LinkedDomains&versionTime=2021-05-10T17:00:00Z")
        assertEquals("LinkedDomains", url.serviceType)
        assertEquals("2021-05-10T17:00:00Z", url.versionTime)
    }

    @Test
    fun `percent-encoded unreserved characters are decoded in parameter values`() {
        assertEquals("a b", DidUrl("did:example:123?x=a%20b").parameters["x"])
    }

    @Test
    fun `multi-byte percent-encoded characters decode as UTF-8`() {
        assertEquals("café", DidUrl("did:example:123?x=caf%C3%A9").parameters["x"])
    }

    @Test
    fun `duplicate parameters are flagged`() {
        assertTrue(DidUrl("did:example:123?service=files&service=agent").hasDuplicateParameters)
        assertFalse(DidUrl("did:example:123?service=files").hasDuplicateParameters)
    }

    @Test
    fun `a fragment only DID URL is parsed`() {
        val url = DidUrl("did:example:123#keys-1")
        assertEquals("keys-1", url.fragment)
        assertNull(url.query)
        assertNull(url.path)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :did:did-identifiers-mp:allTests --max-workers 3`
Expected: FAIL — `Unresolved reference: query`.

- [ ] **Step 3: Rewrite the `DidUrl` accessors**

Replace the `DidUrl` value class in
`did/did-identifiers-mp/src/commonMain/kotlin/org/trustweave/did/identifiers/DidIdentifiers.kt`
with:

```kotlin
/**
 * A DID URL: `did:method:id[/path][?query][#fragment]`.
 *
 * Components are split in RFC 3986 order so a query never leaks into [path] and a fragment never
 * leaks into [query]. DID parameters defined by DID Resolution 1.0 §3 are exposed by name.
 */
@Serializable(with = DidUrlSerializer::class)
@JvmInline
value class DidUrl(val value: String) {

    /** The DID portion, with path, query and fragment removed. */
    val did: Did
        get() = Did(value.substringBefore("#").substringBefore("?").substringBefore("/"))

    /** The path component without its leading `/`, or null when absent. */
    val path: String?
        get() {
            val beforeQuery = value.substringBefore("#").substringBefore("?")
            val slash = beforeQuery.indexOf('/')
            return if (slash < 0) null else beforeQuery.substring(slash + 1).takeIf { it.isNotEmpty() }
        }

    /** The raw query component without its leading `?`, or null when absent. */
    val query: String?
        get() {
            val beforeFragment = value.substringBefore("#")
            val mark = beforeFragment.indexOf('?')
            return if (mark < 0) null else beforeFragment.substring(mark + 1).takeIf { it.isNotEmpty() }
        }

    /** The fragment without its leading `#`, or null when absent. */
    val fragment: String?
        get() {
            val hash = value.indexOf('#')
            return if (hash < 0) null else value.substring(hash + 1).takeIf { it.isNotEmpty() }
        }

    /**
     * DID parameters parsed from [query], with percent-encoded octets decoded.
     *
     * When a parameter name repeats, the first occurrence wins; callers that care should check
     * [hasDuplicateParameters], which §3.2.2 identifies as ambiguous input.
     */
    val parameters: Map<String, String>
        get() = query?.split('&')
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) null else percentDecode(pair.substring(0, eq)) to percentDecode(pair.substring(eq + 1))
            }
            ?.reversed()
            ?.toMap()
            ?: emptyMap()

    /** True when a parameter name occurs more than once (§3.2.2: ambiguous input). */
    val hasDuplicateParameters: Boolean
        get() {
            val names = query?.split('&')
                ?.filter { it.isNotEmpty() }
                ?.map { it.substringBefore('=') }
                ?: return false
            return names.size != names.toSet().size
        }

    /** The `service` DID parameter (§3). */
    val service: String? get() = parameters["service"]

    /** The `serviceType` DID parameter (§3). */
    val serviceType: String? get() = parameters["serviceType"]

    /** The `relativeRef` DID parameter (§3). */
    val relativeRef: String? get() = parameters["relativeRef"]

    /** The `versionId` DID parameter (§3, §13.4). */
    val versionId: String? get() = parameters["versionId"]

    /** The `versionTime` DID parameter (§3, §13.4), as its raw datetime string. */
    val versionTime: String? get() = parameters["versionTime"]
}

/**
 * Decodes percent-encoded octets in a DID URL query component.
 *
 * Percent-escapes are accumulated as a byte sequence and decoded as UTF-8, so multi-byte
 * characters (`%C3%A9` -> `e-acute`) survive. Decoding each escape independently as a char would
 * corrupt them. An escape that is not valid hex is left literal.
 */
private fun percentDecode(raw: String): String {
    if (!raw.contains('%')) return raw
    val out = StringBuilder(raw.length)
    val pending = ArrayList<Byte>()

    fun flush() {
        if (pending.isEmpty()) return
        out.append(pending.toByteArray().decodeToString())
        pending.clear()
    }

    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c == '%' && i + 2 < raw.length) {
            val code = raw.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                pending.add(code.toByte())
                i += 3
                continue
            }
        }
        flush()
        out.append(c)
        i++
    }
    flush()
    return out.toString()
}
```

`reversed().toMap()` makes the *first* occurrence win, matching the documented behaviour.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :did:did-identifiers-mp:allTests --max-workers 3`
Expected: PASS, 8 tests.

- [ ] **Step 5: Verify no existing caller relied on the old path behaviour**

Run: `./gradlew build -x test --max-workers 3 && ./gradlew test --max-workers 3`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add did/did-identifiers-mp/
git commit -m "feat(did-identifiers): parse the DID URL query component and section 3 DID parameters"
```

---

### Task 16: Conformance test suite

**Files:**
- Create: `distribution/conformance/src/conformanceTest/kotlin/org/trustweave/conformance/DidResolution10ConformanceTest.kt`
- Modify: `distribution/conformance/src/conformanceTest/kotlin/org/trustweave/conformance/ConformanceTestSuite.kt`

**Interfaces:**
- Consumes: everything from Tasks 1-15.
- Produces: `class DidResolution10ConformanceTest` tagged `@Tag("conformance")` and `@Tag("did-resolution-1.0")`, one `TC-xx` test per normative statement.

- [ ] **Step 1: Write the suite**

Create `distribution/conformance/src/conformanceTest/kotlin/org/trustweave/conformance/DidResolution10ConformanceTest.kt`:

```kotlin
package org.trustweave.conformance

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolution.DidResolutionResultJson
import org.trustweave.did.resolution.ResolutionOptions
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.RegistryBasedResolver
import org.trustweave.did.resolver.documentMetadata
import org.trustweave.did.resolver.errorType
import org.trustweave.did.util.toXmlDateTime
import org.trustweave.keydid.KeyDidMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("conformance")
@Tag("did-resolution-1.0")
class DidResolution10ConformanceTest {

    private val kms = InMemoryKeyManagementService()
    private val method = KeyDidMethod(kms)
    private val resolver = RegistryBasedResolver(DidMethodRegistry().apply { register(method) })

    private fun newDid(): Did = runBlocking {
        method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519)).id
    }

    /** A method whose DIDs never exist, to exercise the NOT_FOUND branch of §4.4. */
    private class MissingDidMethod : DidMethod {
        override val method: String = "missing"
        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed")
        override suspend fun resolveDid(did: Did): DidResolutionResult =
            DidResolutionResult.Failure.NotFound(did)
        override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
            throw UnsupportedOperationException("not needed")
        override suspend fun deactivateDid(did: Did): Boolean = false
    }

    @Test
    fun `TC-01 section 4 resolve returns a document whose id equals the input DID`() = runBlocking {
        val did = newDid()
        val result = resolver.resolve(did) as DidResolutionResult.Success
        assertEquals(did, result.document.id)
    }

    @Test
    fun `TC-02 section 4-4 step 2 an unsupported method yields METHOD_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.METHOD_NOT_SUPPORTED,
            resolver.resolve(Did("did:unsupportedmethod:123")).errorType
        )
    }

    @Test
    fun `TC-03 section 4-4 step 3 an unsupported option yields FEATURE_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            resolver.resolve(newDid(), ResolutionOptions(versionId = "3")).errorType
        )
    }

    @Test
    fun `TC-04 section 4-4 step 4 contradictory options yield INVALID_OPTIONS`() = runBlocking {
        val options = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z")
        )
        assertEquals(DidErrorType.INVALID_OPTIONS, resolver.resolve(newDid(), options).errorType)
    }

    @Test
    fun `TC-05 section 4-4 a DID that does not exist yields NOT_FOUND`() = runBlocking {
        // did:key is deterministic and never misses, so a method that reports non-existence is
        // registered to exercise the NOT_FOUND path of the algorithm.
        val registry = DidMethodRegistry().apply { register(MissingDidMethod()) }
        val result = RegistryBasedResolver(registry).resolve(Did("did:missing:123"))
        assertEquals(DidErrorType.NOT_FOUND, result.errorType)
    }

    @Test
    fun `TC-06 section 11 every error type is an absolute w3c URI`() = runBlocking {
        val error = resolver.resolve(Did("did:unsupportedmethod:123")).errorType
        assertNotNull(error)
        assertTrue(error.startsWith("https://www.w3.org/ns/did#"), "error type must be a spec URI: $error")
    }

    @Test
    fun `TC-07 section 4 a failed resolution carries no document`() = runBlocking {
        val result = resolver.resolve(Did("did:unsupportedmethod:123"))
        assertNull((result as? DidResolutionResult.Success)?.document)
    }

    @Test
    fun `TC-08 section 4 a failed resolution carries empty document metadata`() = runBlocking {
        val result = resolver.resolve(Did("did:unsupportedmethod:123"))
        assertEquals(DidDocumentMetadata(), result.documentMetadata)
    }

    @Test
    fun `TC-09 section 4-2 contentType defaults to application-did`() = runBlocking {
        val result = resolver.resolve(newDid()) as DidResolutionResult.Success
        assertEquals(DidMediaTypes.DID, result.resolutionMetadata.contentType)
    }

    @Test
    fun `TC-10 section 4-4 an unsupported accept yields REPRESENTATION_NOT_SUPPORTED`() = runBlocking {
        assertEquals(
            DidErrorType.REPRESENTATION_NOT_SUPPORTED,
            resolver.resolve(newDid(), ResolutionOptions(accept = "application/did+cbor")).errorType
        )
    }

    @Test
    fun `TC-11 section 3-1 datetimes are UTC without sub-second precision`() {
        assertEquals("2020-12-20T19:17:47Z", Instant.parse("2020-12-20T19:17:47.999999Z").toXmlDateTime())
    }

    @Test
    fun `TC-12 section 9 the resolution result carries all three members`() = runBlocking {
        val json = DidResolutionResultJson.toJson(resolver.resolve(newDid()))
        assertTrue(json.containsKey("didDocument"))
        assertTrue(json.containsKey("didResolutionMetadata"))
        assertTrue(json.containsKey("didDocumentMetadata"))
    }

    @Test
    fun `TC-13 section 9 the resolution result media type is application-did-resolution`() {
        assertEquals("application/did-resolution", DidResolutionResultJson.MEDIA_TYPE)
    }

    @Test
    fun `TC-14 section 4-1 expandRelativeUrls is accepted and does not fail resolution`() = runBlocking {
        val result = resolver.resolve(newDid(), ResolutionOptions(expandRelativeUrls = true))
        assertTrue(result is DidResolutionResult.Success)
    }

    @Test
    fun `TC-15 section 4 an empty options structure is accepted`() = runBlocking {
        assertTrue(resolver.resolve(newDid(), ResolutionOptions.EMPTY) is DidResolutionResult.Success)
    }
}
```

- [ ] **Step 2: Register the suite**

Read `distribution/conformance/src/conformanceTest/kotlin/org/trustweave/conformance/ConformanceTestSuite.kt`
and add `DidResolution10ConformanceTest` to it in whatever form the file already uses to list
`DidCore11ConformanceTest`.

- [ ] **Step 3: Add the `did:key` plugin dependency if missing**

`distribution/conformance/build.gradle.kts` already declares `:did:plugins:key` and `:testkit`.
No change needed — verify by reading the `dependencies` block.

- [ ] **Step 4: Run the conformance suite**

Run: `./gradlew :distribution:conformance:conformanceTest --max-workers 3`
Expected: PASS, all `TC-xx` tests green. The HTML report lands in
`distribution/conformance/build/reports/conformance/html/index.html`.

- [ ] **Step 5: Format and commit**

```bash
./gradlew ktlintFormat --max-workers 3
git add distribution/conformance/
git commit -m "test(conformance): add DID Resolution 1.0 CR conformance suite"
```

---

### Task 17: Documentation and release notes

**Files:**
- Modify: `CLAUDE.md` (Architecture Overview → note the resolution conformance target)
- Create: `docs/releases/did-resolution-1.0-migration.md`
- Modify: any `docs/` page that documents `DidResolutionResult` or `resolutionMetadata.error` — find them with the grep in Step 1.

**Interfaces:**
- Consumes: the complete implementation.
- Produces: no code.

- [ ] **Step 1: Find the documentation that references the old surface**

```bash
grep -rn "resolutionMetadata\|DidResolutionResult\|application/did+ld+json" --include=*.md docs/ README.md | grep -v superpowers
```

- [ ] **Step 2: Write the migration note**

Create `docs/releases/did-resolution-1.0-migration.md`:

```markdown
# Migrating to DID Resolution 1.0 (CR 2026-08-06)

TrustWeave's DID resolution layer now targets
[DID Resolution 1.0 CR](https://www.w3.org/TR/2026/CR-did-resolution-1.0-20260806/),
replacing the previous v0.3-era surface. This release contains breaking changes.

## 1. Errors are objects, not strings

`DidResolutionMetadata.error` changed from `String?` to `DidResolutionError?`.

```kotlin
// Before
if (metadata.error == "notFound") { … }

// After
if (metadata.error?.type == DidErrorType.NOT_FOUND) { … }
```

The `errorMessage` constructor parameter is gone; `error.detail` replaces it. A deprecated
`errorMessage` read-only property remains for one release.

Legacy v0.3 codes emitted by upstream resolvers are still *parsed* and upgraded to their spec
URIs — only emission changed.

## 2. Deactivated DIDs no longer return a document

`DidResolutionResult` gained a `Deactivated` variant. Per §4.4 a deactivated DID resolves to no
document at all.

```kotlin
when (val result = resolver.resolve(did)) {
    is DidResolutionResult.Success -> use(result.document)
    is DidResolutionResult.Deactivated -> rejectAsRevoked(result.did)
    is DidResolutionResult.Failure -> handle(result.error)
}
```

Verification paths MUST treat `Deactivated` as a failure, not as "document missing".

## 3. `Failure.OptionsError` is new

Covers `FEATURE_NOT_SUPPORTED` and `INVALID_OPTIONS`. Exhaustive `when` over
`DidResolutionResult.Failure` needs a new branch.

## 4. Resolution options

`resolve` and `resolveDid` accept an optional `ResolutionOptions`. The single-argument forms are
unchanged, so `DidMethod` implementations do not need modification; methods that support
`versionId`, `versionTime` or `noCache` should override the two-argument form.

## 5. Media types

The default `contentType` is now `application/did`, not `application/did+ld+json`. The legacy
types remain accepted on input and can still be requested via `ResolutionOptions.accept`.

## 6. Metadata property moves

`nextUpdate`, `nextVersionId`, `canonicalId` and `equivalentId` are DID *document* metadata
(§4.3). Read them from `DidDocumentMetadata`; they were removed from `DidResolutionMetadata`.

## 7. Timestamps

All resolution timestamps serialize as UTC XML datetimes without sub-second precision, per §3.1.

## Not yet implemented

- **DID URL dereferencing (§5, §10)** — the WG has marked this Feature at Risk; TrustWeave parses
  DID URL parameters but does not expose a `dereference()` function.
- **HTTP(S) binding (§12.1)** — TrustWeave is a conforming DID resolver, not a conforming
  *network-based* DID resolver; there is no resolver HTTP endpoint in this release.
```

- [ ] **Step 3: Update CLAUDE.md**

In the Architecture Overview table row for **DID**, change the "What it does" cell to:

```
DID CRUD, resolution (W3C DID Resolution 1.0 CR), batch ops, registrar
```

- [ ] **Step 4: Update the docs pages found in Step 1**

Apply the same substitutions the migration note describes to each page.

- [ ] **Step 5: Full verification**

```bash
./gradlew ktlintCheck --max-workers 3
./gradlew build --max-workers 3
./gradlew :distribution:conformance:conformanceTest --max-workers 3
```
Expected: all three BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md docs/
git commit -m "docs: document the DID Resolution 1.0 conformance migration"
```

---

## Follow-on plans (not in scope here)

Write these as separate plans once the tasks above are merged:

1. **DID URL dereferencing (§5, §10)** — `dereference(didUrl, options)`, the
   `verificationRelationship` option, service-endpoint construction from `service` +
   `relativeRef`, fragment dereferencing, the `application/did-url-dereferencing` envelope, and the
   §13.6 dereferencing-cycle guard. Gate on whether the WG keeps the section: it is Feature at Risk.
2. **HTTP(S) binding (§12.1)** — a `did:resolver-server-ktor` module mirroring
   `did:registrar-server-ktor`: `GET /1.0/identifiers/{did}`, `Accept` handling for
   `application/did-resolution` vs a bare representation, the error-type→status table, 410 for
   deactivated, 303 + `Location` for service-endpoint dereferencing, and TLS enforcement.
3. **Per-method versioning** — override `resolveDid(did, options)` in the methods whose VDRs
   support history (cheqd, ion, orb, ethr, plc) so `versionId` / `versionTime` stop returning
   FEATURE_NOT_SUPPORTED.
