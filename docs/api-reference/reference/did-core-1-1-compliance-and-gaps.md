---
title: DID Core 1.0 vs 1.1 and TrustWeave compliance gaps
nav_order: 15
parent: Reference
keywords:
  - DID Core
  - DID 1.1
  - W3C
  - compliance
  - Controlled Identifiers
redirect_from:
  - /reference/did-core-1-1-compliance-and-gaps/

---

# DID Core 1.0 vs 1.1 and TrustWeave compliance gaps

This document explains how **[Decentralized Identifiers (DIDs) v1.1](https://www.w3.org/TR/did-1.1/)** relates to the earlier **[DID Core v1.0 Recommendation](https://www.w3.org/TR/did-core/)**, and what would need to change in TrustWeave to credibly claim **conformance** with DID 1.1 (and related specs). It is accurate as of the DID 1.1 Candidate Recommendation snapshot (March 2026) and the TrustWeave codebase layout at authoring time.

---

## 1. How the two specs differ (high level)

| Aspect | DID Core **v1.0** (REC, 2022) | DIDs **v1.1** (CR, 2026) |
|--------|-------------------------------|---------------------------|
| **Architecture** | DID Core is a single document covering syntax, data model, representations, and (historically) much of the resolution story. | DID 1.1 is **layered on [Controlled Identifiers (CID) v1.0](https://www.w3.org/TR/cid-1.0/)**. DID-specific rules extend CID’s controlled identifier document model. |
| **Resolution & dereferencing** | Described in DID Core 1.0. | **Normative resolution/dereferencing interfaces and metadata** are pushed to **[DID Resolution v0.3](https://www.w3.org/TR/did-resolution/)**. DID 1.1 focuses on identifiers, documents, and representations. |
| **JSON-LD context** | Documents typically use `https://www.w3.org/ns/did/v1`. | Adds **`https://www.w3.org/ns/did/v1.1`** as the v1.1 vocabulary context; examples in the spec use v1.1. |
| **Media type** | Multiple representation-related types were discussed over time. | **Consolidated `application/did`** after IANA registration (per DID 1.1 revision history). |
| **DID syntax (method-specific id)** | Final ABNF evolved across CR phases. | **Explicit ABNF**: `method-specific-id = *( *idchar ":" ) 1*idchar` with `idchar = ALPHA / DIGIT / "." / "-" / "_" / pct-encoded` — i.e. **percent-encoding** and a defined colon pattern. |
| **Verification relationships** | String references to verification methods; embedded objects in some usages. | Each of `authentication`, `assertionMethod`, `keyAgreement`, `capabilityInvocation`, `capabilityDelegation` is a **set whose elements are either a DID-syntax string or a verification-method map** (embedded VM). |
| **`alsoKnownAs`** | URIs / DIDs. | **Each entry MUST be a URL (per URL Standard) or a DID** (per §3.1). |
| **Services** | `type` as string in common practice. | **`type` may be a string or a set of strings**; `serviceEndpoint` rules align with CID. |
| **Conformance testing** | Historical DID Core test suite. | W3C expects **implementations against the updated test suite** and interoperability for CR exit ([DID test suite](https://github.com/w3c/did-test-suite/), [implementation report](https://w3c.github.io/did-test-suite/)). |

**Non-normative summary:** DID 1.1 is not a wholesale rewrite of the idea of DIDs; it **aligns the core document model with CID 1.0**, **splits resolution into a separate spec**, updates **syntax**, **context**, and **representation** details, and tightens some **data-model** options (embedded VMs, `alsoKnownAs`, service `type`).

---

## 2. What “fully compliant” means in practice

W3C DID 1.1 defines **conforming DIDs**, **conforming DID documents**, **conforming producers**, **conforming consumers**, and **conforming DID methods** (see §1.4 Conformance in DID 1.1). No single library is a “DID method”; **full ecosystem compliance** requires:

1. **Consumers/producers** that parse and emit documents per the data model and representation rules (e.g. JSON lossless round-trips where applicable).
2. **DID methods** that satisfy §7 Methods (create, resolve, update, deactivate) per their method specs.
3. For **resolution**, alignment with **DID Resolution v0.3** where you expose resolver behavior.

TrustWeave is primarily a **Kotlin library**: typed models, validators, Universal Resolver clients, and **pluggable methods**. Claiming **full DID 1.1 compliance** would require:

- Closing the **gaps below**, and  
- **Running and passing** (or documenting results of) the **[W3C DID test suite](https://github.com/w3c/did-test-suite/)** for the roles you claim (consumer/producer/resolver client).

---

## 3. TrustWeave today (baseline)

- Documentation and notices target **DID Core 1.0** (e.g. `NOTICE.md`, main README feature list).
- **`DidDocument`** in `did/did-core` models the main properties (`id`, `@context`, `alsoKnownAs`, `controller`, verification relationships, `service`) and defaults `@context` to **`https://www.w3.org/ns/did/v1`**.
- **JSON parsing** for resolved documents (e.g. `DefaultUniversalResolver.parseDidDocumentFromJson`, registrar adapter) does **not** cover every 1.1/CID shape (see gaps).

---

## 4. Documented gaps for DID 1.1 / CID-aligned conformance

The following are **known or likely gaps** relative to DID 1.1 + CID 1.0 as a **consumer** of arbitrary conforming JSON documents. Method plugins may partially compensate for some flows, but **generic resolution path** remains incomplete.

| # | Area | Spec expectation (1.1 / CID) | TrustWeave gap |
|---|------|------------------------------|----------------|
| G1 | **Embedded verification methods** | `authentication`, `assertionMethod`, etc. may contain **full verification method objects**, not only id strings. | **Strategy (B): normalize on ingest.** Embedded VM maps are merged into `verificationMethod` (dedupe by `id`) and relationship arrays store **`VerificationMethodId`** only. Conforming consumers that need literal embedded-array layout would require a union type (strategy A); TrustWeave documents strategy B for minimal API churn and round-trip may re-hoist VMs when producing. |
| G2 | **`controller` / `alsoKnownAs` on resolve** | Optional top-level properties on the document. | **Addressed in code:** resolver and registrar parse **`controller`** (string or array) and **`alsoKnownAs`** (array of URL or DID) into `DidDocument`. |
| G3 | **`alsoKnownAs` value types** | Each value is a **URL or DID**. | **Addressed in code:** `DidOrUrl` (`AsDid` / `AsUrl`) on `DidDocument` in `did-core`. |
| G4 | **Service `type`** | String **or set of strings**. | **Addressed in code:** **`DidService.type` is `List<String>`**; parsing/serialization use `parseServiceTypesFromJson` and `toServiceTypeJsonElement()` for single string or array. |
| G5 | **DID syntax validation** | Full **ABNF** including **pct-encoded** octets in method-specific-id. | **Addressed:** **DidValidator** validates per DID 1.1 §3.1 ABNF: method-name (lowercase a-z, 0-9), method-specific-id with idchar (ALPHA/DIGIT/`.`/`-`/`_`/pct-encoded) and colon-separated segments; tests cover pct-encoded and colon rules. |
| G6 | **Default / advertised context** | Producers should use **v1.1 context** when targeting 1.1. | Defaults remain **`.../did/v1`**; no first-class **“produce as 1.1”** mode. |
| G7 | **Representation: `application/did`** | DID 1.1 / IANA consolidation. | **Addressed:** **`APPLICATION_DID_MEDIA_TYPE`** and **`DidDocumentJsonProducer.toBytesWithMediaType()`**; centralized serialization; Content-Type `application/did` where applicable. |
| G8 | **DID Resolution v0.3** | Resolver APIs, metadata, dereferencing, options. | **Partial:** [DidResolutionResult.Success](https://github.com/trustweave/trustweave/blob/main/did/did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResult.kt) carries `document`, `documentMetadata`, `resolutionMetadata` (aligns with resolve output). [ResolutionOptions](https://github.com/trustweave/trustweave/blob/main/did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionV03.kt), [DereferenceResult](https://github.com/trustweave/trustweave/blob/main/did/did-core/src/main/kotlin/org/trustweave/did/resolution/DidResolutionV03.kt) added for API alignment; full dereference(didUrl) not yet implemented. Use **`application/did`** for Accept/Content-Type where applicable. |
| G9 | **Fragment resolution** | CID §3.4 extended by DID 1.1 for fragments. | **No general-purpose DID URL dereferencer** documented as CID/1.1-aligned. |
| G10 | **Conformance evidence** | CR exit requires interoperable implementations + tests. | **No checked-in W3C DID test suite results** or formal conformance report for TrustWeave. |

**Note on DID Core 1.0:** Several gaps (e.g. **embedded VMs** in verification relationships) also affect **strict 1.0 consumer completeness**; DID 1.1’s examples and CID alignment make embedded VMs **more visible**.

---

## 5. Method plugin audit (DID 1.1 document shape)

Plugins that produce or consume DID documents use the shared **DidDocumentJsonProducer** (v1.1 @context, `application/did`, controller, alsoKnownAs, service type as string or array) and **DidDocumentJsonParser** (embedded VMs, controller, alsoKnownAs, service types). Audited plugins:

| Plugin | Produces conforming docs | Consumes (parser) | Notes |
|--------|--------------------------|-------------------|--------|
| did:key (base/key) | Yes (via producer where used) | N/A (generates in code) | Document shape conforms. |
| did:web (base) | Yes (DidDocumentJsonProducer) | Shared parser / service type helpers | HTTPS required by method. |
| did:jwk | Yes (document built in code) | N/A | Document shape conforms. |
| did:plc | Yes (DidDocumentJsonProducer) | parseServiceTypesFromJson | |
| did:ion | Yes (SidetreeClient uses producer) | parseServiceTypesFromJson | |
| did:peer | Yes (listOf type) | N/A | |
| Godiddy registrar/resolver | Yes (producer) | parseServiceTypesFromJson, parser | |
| StandardUniversalRegistrarAdapter | Yes (producer) | DidDocumentJsonParser | |

## 6. Suggested implementation order (for maintainers)

1. ~~**Parsing**~~ — **DidDocumentJsonParser**; relationship arrays accept strings and VM objects (normalize on ingest).
2. ~~**Parse `controller` and `alsoKnownAs`**~~ — in shared parser and registrar/resolver.
3. ~~**Widen `alsoKnownAs`**~~ — **`DidOrUrl`** (`AsDid` / `AsUrl`).
4. ~~**`DidService.type`**~~ — **`List<String>`**; parse/serialize with helpers.
5. ~~**Align `DidValidator`**~~ — ABNF-aligned per §3.1; pct-encoded and colon tests.
6. ~~**v1.1 context / application/did**~~ — **DidDocumentJsonProducer**, **APPLICATION_DID_MEDIA_TYPE**.
7. ~~**DID Resolution v0.3**~~ — Result types align; ResolutionOptions, DereferenceResult added.
8. **Run [did-test-suite](https://github.com/w3c/did-test-suite/)** and publish an implementation report for claimed roles (see §7).

---

## 7. Implementation report and test suite

To claim conforming **consumer** and **producer** (and optionally **resolver**) roles for DID 1.1:

1. Run the [W3C DID test suite](https://github.com/w3c/did-test-suite/) against TrustWeave (consumer/producer and resolver if applicable).
2. Publish results in this repo (e.g. under `docs/conformance/` or as a CI artifact) following the [implementation report](https://w3c.github.io/did-test-suite/) format.
3. Update this document and [NOTICE](../../NOTICE.md) to state the claimed conformance roles and link to the test results.

Until the test suite is run and the report is published, conformance is **self-assessed** based on the implementation work described in this document.

## 8. References

- [Decentralized Identifiers (DIDs) v1.1](https://www.w3.org/TR/did-1.1/) (W3C Candidate Recommendation)
- [Decentralized Identifiers (DIDs) v1.0](https://www.w3.org/TR/did-core/) (W3C Recommendation)
- [Controlled Identifiers v1.0](https://www.w3.org/TR/cid-1.0/)
- [Decentralized Identifier Resolution v0.3](https://www.w3.org/TR/did-resolution/)
- [W3C DID Test Suite](https://github.com/w3c/did-test-suite/)

---

## 9. Related TrustWeave docs

- [Decentralized Identifiers (DIDs) – core concepts](../../core-concepts/dids.md)
- [trustweave-did module](../modules/trustweave-did.md)
