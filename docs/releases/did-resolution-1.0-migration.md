# Migrating to DID Resolution 1.0 (CR 2026-08-06)

TrustWeave's DID resolution layer now targets
[DID Resolution 1.0 CR](https://www.w3.org/TR/2026/CR-did-resolution-1.0-20260806/),
replacing the previous v0.3-era surface. The `distribution:conformance` module carries a
`DidResolution10ConformanceTest` suite (17 tests, including TC-16 for §4.4 deactivation) alongside
the DID Core 1.1, VC Data Model 2.0, Presentation Exchange, and deactivated-DID-verification
suites — 38 tests across 5 suites in total, enforced by a hard floor in the conformance test
listener.

This release contains breaking changes. Read this whole document before upgrading — several of
the changes interact (in particular #2 and #8 below).

## 1. Errors are objects, not strings

`DidResolutionMetadata.error` changed from `String?` to `DidResolutionError?`:

```kotlin
// Before
if (metadata.error == "notFound") { … }

// After
if (metadata.error?.type == DidErrorType.NOT_FOUND) { … }
```

`DidResolutionError` is an RFC 9457 error object (`type`, `title`, `detail`, computed
`httpStatus`). The `errorMessage` constructor parameter is gone; a deprecated read-only
`errorMessage` property remains on `DidResolutionMetadata` for one release, deriving from
`error?.detail`.

Legacy v0.3 camelCase codes (`notFound`, `invalidDid`, `methodNotSupported`, …) emitted by
upstream resolvers (e.g. public Universal Resolver instances) are still **parsed** —
`DidErrorType.fromLegacyCode` upgrades them to their §11 URI form — but TrustWeave itself never
**emits** the old string form anymore.

The ergonomic accessors over `DidResolutionResult` itself (`org.trustweave.did.resolver`,
`DidResolutionResultExtensions.kt`) changed too, separately from the `DidResolutionMetadata`
members above:

- `DidResolutionResult.errorCode` — the old short code (`"NOT_FOUND"`, `"INVALID_FORMAT"`,
  `"METHOD_NOT_REGISTERED"`, `"RESOLUTION_ERROR"`) — is **removed**, with no replacement of the
  same shape. This is a compile break, not a silent one.
- `DidResolutionResult.errorMessage` still exists at the same name but changed what it derives
  from: it used to read the `Failure` subtype's own `reason` field (e.g. `"DID not found"` as
  `NotFound`'s default), and now reads `error?.detail` instead. Code that compared this string
  against a specific message, rather than just logging it, may see a different value — this is a
  **silent** behaviour change, not a compile error.

## 2. Deactivated DIDs no longer return a document

`DidResolutionResult` gained a `Deactivated` variant, a sibling of `Success` and `Failure` — not
a case of either:

```kotlin
sealed class DidResolutionResult {
    data class Success(document, documentMetadata, resolutionMetadata) : DidResolutionResult()
    data class Deactivated(did, documentMetadata, resolutionMetadata) : DidResolutionResult()
    sealed class Failure : DidResolutionResult() { /* NotFound, InvalidFormat, MethodNotRegistered, ResolutionError, OptionsError */ }
}
```

Per §4.4, a deactivated DID resolves to **no document at all** — the caller learns of the
deactivation only from `documentMetadata.deactivated`. This is deliberate: conflating "revoked"
with "never registered" is the defect this migration exists to remove, and it is why every
DID method now produces `Deactivated` via the shared
`DidMethodUtils.createSuccessResolutionResult` factory (see #10) instead of returning a `Success`
result whose metadata happens to say `deactivated: true`.

The same conversion now also happens on the remote-resolver route: `DefaultUniversalResolver` and
`GodiddyResolver` check `documentMetadata.deactivated` **before** document presence, so an
upstream HTTP 200 carrying both a `didDocument` and `deactivated: true` — the shape a DID
Resolution v0.3-era resolver emits — yields `Deactivated`, not a `Success` carrying the revoked
document. `UniversalResolver.asDidResolver()` applies the same check again as defence in depth,
so a third-party `UniversalResolver` implementation that skips it is still covered.

```kotlin
when (val result = resolver.resolve(did)) {
    is DidResolutionResult.Success -> use(result.document)
    is DidResolutionResult.Deactivated -> rejectAsRevoked(result.did)
    is DidResolutionResult.Failure -> handle(result.error)
}
```

**Any exhaustive `when` over `DidResolutionResult` in your code needs a new branch**, or it will
fail to compile. Verification and authorization paths in particular MUST treat `Deactivated` as
a failure, never as "document missing" — a revoked identity and an identity that never existed
are different security postures, and code that folds them together will treat a revoked DID as
merely absent instead of actively untrusted.

## 3. `Failure.OptionsError` is new

`DidResolutionResult.Failure` gained a fifth subtype, `OptionsError`, covering §4.4 steps 3 and
4: an unsupported resolution option (`FEATURE_NOT_SUPPORTED`), an invalid option combination
(`INVALID_OPTIONS` — e.g. `versionId` and `versionTime` both set), or an unsupported
representation (`REPRESENTATION_NOT_SUPPORTED`). Exhaustive `when` over `Failure` needs a new
branch alongside `NotFound`, `InvalidFormat`, `MethodNotRegistered`, and `ResolutionError`.

## 4. The map-based constructors and `resolutionMetadataMap` accessors are removed

`DidResolutionMetadata`'s map-based secondary constructors and its `resolutionMetadataMap`
accessors are gone. Use `DidResolutionMetadata.fromMap(map)` / `.fromJson(json)` to build one from
a loosely-typed map or JSON object, and `.toMap()` / `.toJson()` to go the other way.

Removing the constructors is a compile error you can't miss. The part that isn't: the map's
*shape* also changed. `map["error"]` is now a `JsonObject` (the RFC 9457 error, serialized via
`DidResolutionError.toJson()`) rather than a `String`, and `map["errorMessage"]` doesn't exist in
the map at all — `toMap()`/`toJson()` never emit that key. Code left over from the old shape that
does `(map["error"] as? String)` or `(map["errorMessage"] as? String)` **still compiles** against
the new output; the cast just silently degrades to `null` instead of failing loudly or returning
the error text. If anything in your codebase serializes `DidResolutionMetadata` to a map/JSON and
reads it back with hand-rolled `as?` casts instead of `fromMap`/`fromJson`, audit it specifically
— this is exactly the kind of change the type checker won't catch for you.

## 5. Resolution options

`DidResolver.resolve` and `DidMethod.resolveDid` gained a two-argument overload that accepts
`ResolutionOptions` (`accept`, `expandRelativeUrls`, `versionId`, `versionTime`, `noCache`,
`additional`). The single-argument forms are unchanged and still work — existing `DidMethod`
implementations do not need modification. The default two-argument implementation validates the
options and returns `OptionsError` for anything method-specific (`versionId`, `versionTime`,
`noCache`) that the method doesn't override; methods that support those options should override
the two-argument `resolveDid`. **No shipped method currently does** — see "Not yet implemented"
below.

`CachingDidResolver`, `FallbackDidResolver`, `DecentralizedResolutionStrategy`, and
`ResolutionFallbackStrategy` now all override the two-argument form too, forwarding `options` to
their delegate(s) instead of silently dropping them via the interface's single-argument default.
`CachingDidResolver` additionally honours `noCache` itself: a `noCache` request bypasses its cache
read and is not cached around, since it is the one layer positioned to act on that option
directly.

**`org.trustweave.did.resolution.ResolutionOptions` was replaced at the same fully-qualified
name**, not just extended. The pre-CR type (`did-resolution-v0.3`-aligned, a single `accept: String?`
field defaulting to `"application/did"`) is gone; the CR type at the same FQN has a different
shape (`accept` now defaults to `null`) and gains `expandRelativeUrls`, `versionId`, `versionTime`,
`noCache`, `additional`. Existing code that constructed the old `ResolutionOptions(accept = ...)`
still compiles against named or default arguments, but a positional-arg construction or an
`accept`-defaulting call site now behaves differently. `DereferenceResult` and `DereferenceContent`
— also in the removed `DidResolutionV03.kt` — are deleted outright with no replacement; §5/§10
dereferencing is not implemented in this release (see "Not yet implemented" below).

## 6. Media types

The default `contentType` on `DidResolutionMetadata` changed from `application/did+ld+json` to
`application/did` (`DidMediaTypes.DID`). The legacy `application/did+ld+json` and
`application/did+json` types remain accepted on input (`DidMediaTypes.SUPPORTED_DOCUMENT_TYPES`)
and can still be requested explicitly via `ResolutionOptions.accept`.

The same default change reaches `ContentNegotiationService.negotiateContentType`'s `defaultType`
parameter — it changed from a literal `"application/did+ld+json"` default to
`DidMediaTypes.DID` (`"application/did"`) — and `DefaultContentNegotiationService.SUPPORTED_TYPES`,
which now reads `DidMediaTypes.SUPPORTED_DOCUMENT_TYPES` and so gained `application/did` as its
first (most-preferred) entry rather than listing only the two legacy `+json` forms plus
`application/json`. Both are call-site-invisible unless you read the value: a caller that
previously relied on `negotiateContentType`'s implicit default, or iterated `SUPPORTED_TYPES` by
position, now sees `application/did` where it used to see `application/did+ld+json`.

## 7. Metadata property moves

`nextUpdate`, `nextVersionId`, `canonicalId`, and `equivalentId` are §4.3 **document** metadata,
not §4.2 **resolution** metadata. They moved from `DidResolutionMetadata` to
`DidDocumentMetadata`, which also gained `nextVersionId` and `proof` (§4.3 also defines `proof`
on document metadata; resolution metadata has its own separate `proof` list for §4.2 resolver
proofs). Code that read `result.resolutionMetadata.canonicalId` (etc.) must now read
`result.documentMetadata.canonicalId`.

The move also changed `canonicalId` and `equivalentId`'s types, not just their location:
`canonicalId` was `String?` and is now `Did?`; `equivalentId` was `List<String>` and is now
`List<Did>`. A read-only call site that only interpolated these into a string still compiles and
behaves the same either way, but any code that compared them against a plain `String`, or passed
them to a `String`-typed parameter, needs updating regardless of the property-owner move above.

## 8. `resolveOrNull` / `resolveOrDefault` now throw for a deactivated DID

`Did.resolveOrThrow`, `Did.resolveOrNull`, and `Did.resolveOrDefault` (`DidExtensions.kt`, in
`org.trustweave.did.dsl`) all now treat a deactivated DID the same way: they throw
`DidException.DidResolutionFailed`, converging with `resolveOrThrow`'s existing behaviour.

`DidResolver.resolveOrThrow` and `DidResolver.resolveOrNull` (`ResolverExtensions.kt`, same
package) are separate public functions — extensions on `DidResolver` rather than on `Did` — and
changed identically: both now throw `DidException.DidResolutionFailed` for `Deactivated` instead
of treating it as absence. If your code calls the `DidResolver` receiver forms rather than the
`Did` receiver forms, audit those call sites too; the fix below does not cover them just because
it covers the `Did.` ones.

Before this change, `resolveOrNull` returned `null` for *any* non-`Success` result, so a
deactivated DID and a DID that never existed were indistinguishable to the caller — both came
back as `null`. `resolveOrDefault` delegates to `resolveOrNull`, so it inherited the same
ambiguity. That ambiguity is exactly the defect this migration removes: a caller using
`resolveOrDefault(resolver, fallbackDocument)` to paper over resolution failures would silently
accept a **revoked** identity's fallback document as if the identity had simply never been
registered. Now:

```kotlin
val doc = did.resolveOrNull(resolver)       // null: not found / invalid / method error
                                             // throws DidException.DidResolutionFailed: deactivated
val doc2 = did.resolveOrDefault(resolver, fallback) // same throw behaviour for deactivated
```

If your code calls `resolveOrNull` or `resolveOrDefault` on a DID that might legitimately be
deactivated, wrap the call and handle `DidException.DidResolutionFailed` explicitly instead of
relying on a `null` check.

## 9. `DidUrl.path` and `DidUrl.fragment` semantics changed

`DidUrl` (`org.trustweave.did.identifiers`, re-exported by `did-core`) now splits its components
in RFC 3986 order: `did` → `path` → `query` → `fragment`. Previously, `path` and `fragment` did
not account for a query component, so a query string leaked into `path`. For example:

```kotlin
val url = DidUrl("did:example:123/a/b?x=1#frag")
// Before: url.path == "a/b?x=1"   (query swallowed into path)
// After:  url.path == "a/b"
//         url.query == "x=1"      (new accessor)
//         url.fragment == "frag"
```

A query-only DID URL (e.g. `did:example:123?versionId=3`) also now parses correctly — `path` is
`null` and `query` is `"versionId=3"`, instead of the query being lost entirely. `DidUrl` also
gained `parameters` (a decoded map of the §3 DID parameters), `hasDuplicateParameters`, and
named accessors for the registered parameters: `service`, `serviceType`, `relativeRef`,
`versionId`, `versionTime`.

`DidUrl` is public, `@Serializable`, and re-exported by `did-core`, so consumers outside this
repository may be affected even though there are no in-repo callers of `path`/`fragment` today.

## 10. Deactivated DIDs no longer yield a document from any DID method

Every DID method that uses the shared `DidMethodUtils.createSuccessResolutionResult` factory
(all first-party plugins: cheqd, ebsi, ens, ethr, ion, jwk, key, orb, peer, plc, polygon, sol,
and the blockchain/web base classes) now returns `DidResolutionResult.Deactivated` instead of
`Success` when `deactivated = true` is passed. If you maintain a custom `DidMethod`
implementation that constructs `DidResolutionResult.Success` directly for a deactivated DID
(rather than using the shared factory), update it to return `Deactivated` — see
[Creating Custom Adapters](../api-reference/advanced/custom-adapters.md).

## 11. Timestamps

All resolution and document metadata timestamps (`retrieved`, `created`, `updated`,
`nextUpdate`, `versionTime`) serialize as UTC XML datetimes without sub-second precision, per
§3.1, via a shared `XmlDateTimeSerializer` / `toXmlDateTime()`.

## Not yet implemented

Being explicit about scope, because it's part of the deliverable:

- **DID URL dereferencing (§5, §10)** — the WG has marked both sections **Feature at Risk**.
  `DidUrl` now parses path, query, fragment, and the §3 DID parameters (see #9), but there is no
  `dereference(didUrl, options)` function. Fragment dereferencing, service-endpoint construction
  from `service` + `relativeRef`, the `application/did-url-dereferencing` envelope, and the
  §13.6 dereferencing-cycle guard are all unimplemented. A follow-on plan exists for this once
  the WG resolves the section's at-risk status.
- **The HTTP(S) binding (§12.1), server side** — TrustWeave is a conforming DID **resolver**, not
  a conforming **network-based** DID resolver: there is no resolver HTTP endpoint shipped in this
  release, no `GET /1.0/identifiers/{did}` server. The building blocks for one exist but are not
  wired up yet: `ResolutionOptions.fromQueryParameters` / `fromJson` parse the §12.1 GET/POST
  binding shapes, and `DidErrorType.httpStatus` / `DidResolutionError.httpStatus` implement the
  §12.1 error-to-status table — but neither has a production caller today (only the client HTTP
  resolver does real §12.1 work: `DefaultUniversalResolver` sends `Accept: application/did-resolution`
  on its outbound requests to a remote resolver, per §9/§12.1, though it does not yet forward
  `ResolutionOptions` as query parameters on that request).
- **Per-method versioning** — `versionId` and `versionTime` return `FEATURE_NOT_SUPPORTED` for
  every DID method (via `DidMethodResolver`'s default two-argument `resolveDid`). No VDR-backed
  method (cheqd, ion, orb, ethr, plc, …) currently overrides the two-argument `resolveDid` to
  implement history, so no method actually resolves a specific version.
- **Deactivation regression coverage is partial** — the `distribution:conformance` module's
  `DeactivatedDidVerificationTest` suite covers only `credential-api` (3 of 10 code paths that
  consume DID resolution results for verification/authorization). `bbs`, `oidc4vp`, `siop`, and
  `trust` have no regression coverage for the "deactivated DID must be rejected, not treated as
  absent" rule, because adding those modules as `distribution:conformance` dependencies would
  invert the intended dependency direction (leaf feature modules depending on the top-level
  distribution/conformance harness). Closing this gap needs either a different test-harness
  placement or per-module conformance smoke tests.
- **§4.3 `proof` on document metadata is encode-only** — `DidDocumentMetadata.toJson()` emits
  `proof`, but the class has no `fromJson`, and the one parser that builds a `DidDocumentMetadata`
  from wire data (`DefaultUniversalResolver`'s internal `parseDidDocumentMetadata`) does not read
  it back either — a §9 round-trip through that path silently loses a resolver-attached document
  proof. `DidResolutionMetadata` (§4.2 resolution metadata) does **not** have this gap: its own
  `proof` list round-trips correctly through both `fromJson` and `fromMap`.
- **`AbstractWebDidMethod.resolveFromHttp`'s live-fetch path never sets `deactivated`** — it
  consults the locally stored document's `deactivated` flag only on its offline-fallback path (the
  `IOException` branch, when the live HTTP fetch itself fails); the ordinary HTTP-200 path calls
  `DidMethodUtils.createSuccessResolutionResult(document, method)` without a `deactivated`
  argument, which defaults to `false`. So **did:web never returns `Deactivated` from a live
  fetch** — only from a fallback to a previously-stored, already-flagged document. §10 above
  should not be read as covering did:web's live-fetch path.

## See also

- [DID Core 1.0 vs 1.1 and TrustWeave compliance gaps](../api-reference/did-core-1-1-compliance-and-gaps.md)
- [DID 1.1 Implementation Report](../api-reference/conformance/did-1-1-implementation-report.md)
- [trustweave-did module reference](../api-reference/modules/trustweave-did.md)
- [did-core idiomatic API](../api-reference/modules/did-core-idiomatic-api.md)
