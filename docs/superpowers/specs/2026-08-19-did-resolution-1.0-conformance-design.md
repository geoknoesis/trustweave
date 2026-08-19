# DID Resolution 1.0 (CR 2026-08-06) Conformance — Design

**Status:** Design / spec
**Date:** 2026-08-19
**Target spec:** https://www.w3.org/TR/2026/CR-did-resolution-1.0-20260806/
**Supersedes:** the v0.3 alignment recorded in `did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionV03.kt`

## 1. Goal

Make TrustWeave a **conforming DID resolver** as defined in CR §1.2 — i.e. comply
with every normative statement in §4 (DID Resolution), including §3.1 (Datetime),
§4.1 (Resolution Options), §4.2 (Resolution Metadata), §4.3 (Document Metadata),
§4.4 (Resolution Algorithm), §9 (DID Resolution Result), and §11 (Errors).

Two further conformance classes are explicitly **out of scope for this design**
and are tracked as follow-on work:

| Class | Spec sections | Why deferred |
|---|---|---|
| Conforming DID URL dereferencer | §5, §10 | Both sections are marked **Feature at Risk** by the WG and may be removed or heavily changed before PR. |
| Conforming *network-based* DID resolver | §12.1 | Requires a new HTTP server module; independent of library conformance and only meaningful once §4 conformance lands. |

Prerequisite work that both follow-ons need — full DID URL query parsing — **is**
in scope here, because §13.4 versioned resolution needs `versionId` / `versionTime`
parsing anyway.

## 2. Current-state gap analysis

Assessed against the CR on 2026-08-19.

### G1 — Error format (§4.2, §4.4, §11) — MUST

`DidResolutionMetadata.error` is a `String?` holding FPWD/v0.3 camelCase codes
(`"notFound"`, `"invalidDid"`, `"methodNotSupported"`, `"resolutionError"`).

The CR requires an **error object** (RFC 9457 Problem Details) whose `type` is a
URL. §4.4 states the resolver "MUST return … error object with type set to
`https://www.w3.org/ns/did#INVALID_DID`" and equivalents. `title` and `detail`
SHOULD be populated.

`"resolutionError"` is not a spec code at all. Four spec codes have no
representation anywhere in the repo: `FEATURE_NOT_SUPPORTED`, `INVALID_OPTIONS`,
`INVALID_DID_DOCUMENT`, `REPRESENTATION_NOT_SUPPORTED`.

### G2 — `resolutionOptions` absent from the resolve signature (§4, §4.1) — MUST

`DidResolver.resolve(did)` and `DidMethod.resolveDid(did)` take no options.
§4 makes `resolutionOptions` a REQUIRED input (which MAY be empty).

Consequences: `accept`, `expandRelativeUrls` (new in CR), `versionId`,
`versionTime` and `noCache` (§13.2) are unsupported and unrepresentable; and
algorithm steps 3 and 4 (`FEATURE_NOT_SUPPORTED` when an option is unsupported,
`INVALID_OPTIONS` when an option is invalid) cannot be implemented at all.

`ResolutionOptions` exists in `DidResolutionV03.kt` but is dead code — nothing
constructs or consumes it.

### G3 — Deactivated DIDs return a document (§4.4) — MUST

The CR requires, for a deactivated DID: `didDocument: null`,
`didDocumentMetadata: «[ "deactivated" -> true, … ]»`.

`AbstractWebDidMethod.resolveDid` and `AbstractBlockchainDidMethod.resolveDid`
return `DidResolutionResult.Success` carrying the full document plus
`deactivated = true`. That was legal under DID Core 1.0 and is not under this CR.

### G4 — Media types are the legacy set (§9, §10, §12) — MUST

`DidResolutionMetadata.contentType` defaults to `"application/did+ld+json"` and
`DefaultContentNegotiationService.SUPPORTED_TYPES` lists only
`application/did+ld+json`, `application/did+json`, `application/json`.

The CR uses `application/did` for DID documents (11 occurrences; the §9.1 example
shows `"contentType": "application/did"`), `application/did-resolution` for the
resolution-result envelope, and `application/did-url-dereferencing` for the
dereferencing-result envelope.

The repo already defines `APPLICATION_DID_MEDIA_TYPE = "application/did"` in
`DidDocumentJsonProducer.kt` — so it is internally inconsistent today.

### G5 — Datetime serialization (§3.1) — MUST

"All datetime values in this specification MUST be an ASCII string which is a
valid XML datetime value … adjusted to UTC without sub-second decimal precision."

`DidResolutionMetadata.toMap()` emits `Instant.toString()`, which retains
sub-second fractions. The same applies wherever `DidDocumentMetadata` timestamps
are serialized.

### G6 — `nextVersionId` in the wrong structure (§4.2 vs §4.3) — MUST

`nextVersionId`, `nextUpdate`, `canonicalId` and `equivalentId` are all §4.3
**document** metadata properties. `DidResolutionMetadata` carries copies of all
four, and `DidDocumentMetadata` is missing `nextVersionId` entirely. Neither
structure supports `proof`, which both sections define.

### G7 — Resolution-result envelope (§9) — needed for interop

There is no serializer that emits the §9 DID Resolution Result JSON
(`didDocument` + `didResolutionMetadata` + `didDocumentMetadata`). The repo can
only *parse* that shape (`StandardUniversalResolverAdapter`), not produce it.

### G8 — Universal-resolver client is not CR-aware (§12.1 client side)

`DefaultUniversalResolver` sends `Accept: application/json` (CR requires
`application/did-resolution` to request the full result), collapses every
non-404 status to `"resolutionError"` instead of using the CR status↔error-URI
table, and handles neither 410 (deactivated) nor 303 (service endpoint).

### G9 — DID URL query component unparsed (§3, prerequisite for §5/§13.4)

`DidUrl` in `did-identifiers-mp` exposes only `did`, `path` and `fragment`.
There is no query parsing, so `service`, `serviceType`, `relativeRef`,
`versionId` and `versionTime` cannot be read from a DID URL.

### What already conforms

- DID syntax validation matches the DID 1.1 ABNF (`DidValidator`), and
  `DidMethodRegistry.resolve(String)` returns a failure result rather than
  throwing for a malformed DID.
- The three-value return shape (`didDocument`, `didDocumentMetadata`,
  `didResolutionMetadata`) matches §4; failures carry no document, as required.
- `created` / `updated` / `deactivated` / `versionId` / `nextUpdate` /
  `canonicalId` / `equivalentId` are present in `DidDocumentMetadata`.
- The CR **removed** `resolveRepresentation()`; TrustWeave never had it.
- `retrieved` in resolution metadata matches the CR's own §9.1 example.

## 3. Design decisions

### D1 — Error type URIs are the normative surface; sealed subtypes stay ergonomic

A new `DidResolutionError` value type carries the RFC 9457 shape. The existing
`DidResolutionResult.Failure` subtypes are kept as ergonomic sugar and each gains
a correct default `error` object. Only **one** new `Failure` subtype is added
(`OptionsError`, covering `FEATURE_NOT_SUPPORTED` and `INVALID_OPTIONS`), because
those two conditions have no existing home.

Rationale: adding a subtype per error URI would break every exhaustive `when`
across 113 call sites for no conformance benefit — conformance is determined by
the serialized `error.type`, not by the Kotlin type.

### D2 — Deactivation is a distinct result, not a Success

`DidResolutionResult.Deactivated` is added as a sibling of `Success`/`Failure`.
It carries no document, forces `documentMetadata.deactivated = true`, and carries
no `error` (deactivation is not an error per §4.4; it maps to HTTP 410 in §12.1).

This deliberately breaks exhaustive `when (result)` at every call site. That is
the intent: silently continuing to hand callers the document of a deactivated DID
is a security-relevant behaviour change that must be reviewed everywhere.

### D3 — Options are added without breaking the 47 existing `resolveDid` overrides

`DidMethodResolver` keeps `resolveDid(did)` as its single abstract member (it is a
`fun interface`) and gains a **defaulted** `resolveDid(did, options)` whose default
body:

- delegates to `resolveDid(did)` when no method-specific option is present, and
- returns `FEATURE_NOT_SUPPORTED` when `versionId`, `versionTime` or `noCache`
  is present — which is exactly what §4.4 step 3 requires of a resolver that does
  not support the option.

`DidResolver` gets the same treatment. Methods that *do* support versioning
(cheqd, ion, orb, ethr…) override the two-argument form later; that is follow-on
work, not a conformance blocker.

`accept` and `expandRelativeUrls` are method-independent and are handled once in
`RegistryBasedResolver`, not per method.

### D4 — Legacy error codes remain *readable*, not writable

`DidResolutionMetadata.fromMap` must accept both an object-valued `error` and a
legacy string-valued `error`, mapping v0.3 codes to CR URIs. Public universal
resolvers (`dev.uniresolver.io`) still emit v0.3 strings; refusing to parse them
would break real resolution.

Emission is CR-only — TrustWeave never writes a bare string `error`.

### D5 — Document-metadata properties move to document metadata

`nextUpdate`, `nextVersionId`, `canonicalId` and `equivalentId` are removed from
`DidResolutionMetadata` and live only on `DidDocumentMetadata`, which also gains
`nextVersionId` and `proof`. `DidResolutionMetadata` gains `proof`.

### D6 — Media type constants are centralised

A single `DidMediaTypes` object in `did-core` holds the CR types and the legacy
types. `application/did` becomes the default `contentType`. The legacy types stay
*accepted* on input for backward compatibility, and `application/did+ld+json` is
still emitted when the caller explicitly asks for it via `accept`.

### D7 — Datetime truncation happens at the serialization boundary

An `XmlDateTimeSerializer` (`KSerializer<Instant>`) truncates to whole seconds and
emits UTC. It is applied to every `Instant` in resolution and document metadata,
so no call site has to remember.

## 4. Target API surface

```kotlin
// did-core :: org.trustweave.did.resolver
object DidErrorType {
    const val BASE = "https://www.w3.org/ns/did#"
    const val INVALID_DID: String
    const val INVALID_DID_DOCUMENT: String
    const val NOT_FOUND: String
    const val REPRESENTATION_NOT_SUPPORTED: String
    const val INVALID_DID_URL: String
    const val METHOD_NOT_SUPPORTED: String
    const val INVALID_OPTIONS: String
    const val INTERNAL_ERROR: String
    const val FEATURE_NOT_SUPPORTED: String
    fun httpStatus(type: String): Int
    fun fromLegacyCode(code: String): String
}

data class DidResolutionError(val type: String, val title: String?, val detail: String?)

data class ResolutionOptions(
    val accept: String?,
    val expandRelativeUrls: Boolean,
    val versionId: String?,
    val versionTime: Instant?,
    val noCache: Boolean,
    val additional: Map<String, String>,
)

sealed class DidResolutionResult {
    data class Success(...)
    data class Deactivated(...)          // NEW
    sealed class Failure : DidResolutionResult() {
        data class NotFound(...)
        data class InvalidFormat(...)
        data class MethodNotRegistered(...)
        data class ResolutionError(...)
        data class OptionsError(...)     // NEW
    }
}

fun interface DidMethodResolver {
    suspend fun resolveDid(did: Did): DidResolutionResult
    suspend fun resolveDid(did: Did, options: ResolutionOptions): DidResolutionResult // defaulted
}
```

## 5. Verification strategy

A new JUnit 5 suite `DidResolution10ConformanceTest` in the existing
`distribution:conformance` module (`conformanceTest` source set, `@Tag("conformance")`),
one test per normative statement, named `TC-xx`. It runs against
`KeyDidMethod` + `RegistryBasedResolver` with `InMemoryKeyManagementService`,
matching the existing `DidCore11ConformanceTest` pattern.

Per-module unit tests cover the new types directly.

## 6. Breaking changes (release notes required)

1. `DidResolutionMetadata.error` : `String?` -> `DidResolutionError?`;
   `errorMessage` constructor parameter removed (survives as a deprecated
   read-only alias for `error?.detail`).
2. `DidResolutionResult` gains `Deactivated` — exhaustive `when` over the result
   type no longer compiles without a new branch.
3. `DidResolutionResult.Failure` gains `OptionsError` — exhaustive `when` over
   the failure subtypes no longer compiles without a new branch.
4. Deactivated DIDs no longer yield a document.
5. Default `contentType` changes from `application/did+ld+json` to `application/did`.
6. `nextUpdate` / `nextVersionId` / `canonicalId` / `equivalentId` removed from
   `DidResolutionMetadata`; read them from `DidDocumentMetadata`.
