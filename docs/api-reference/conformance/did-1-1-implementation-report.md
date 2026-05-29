---
redirect_from:
  - /conformance/did-1-1-implementation-report/
parent: Conformance
grand_parent: API Reference
---

# DID 1.1 Implementation Report (TrustWeave)

This document is the placeholder for the **W3C DID test suite** implementation report for TrustWeave.

## Claimed conformance roles

- **Conforming consumer**: Parsing of DID document JSON per DID 1.1 §6.2.2 (shared `DidDocumentJsonParser`), including `controller`, `alsoKnownAs`, embedded verification methods (normalized on ingest), service type (string or array), relative refs.
- **Conforming producer**: Serialization of DID documents per §6.2.1/§6.2.3 via `DidDocumentJsonProducer` (v1.1 @context, `application/did`, controller, alsoKnownAs, service type, verification relationships).
- **Resolver**: Resolution output aligns with DID Resolution v0.3 (document + resolutionMetadata + documentMetadata); full dereference API not yet implemented.

## Running the W3C DID test suite

Step-by-step: **[W3C-DID-TEST-SUITE.md](W3C-DID-TEST-SUITE.md)**.

Upstream `npm run test` (latest clone) completed successfully: **jest-did-matcher** (185 tests) and **did-core-test-server** (12 124 passed, 65 todo). That validates the **W3C harness and bundled implementations**, not TrustWeave until a TrustWeave fixture is added.

To generate a formal implementation report:

1. Clone and set up the [W3C DID test suite](https://github.com/w3c/did-test-suite/).
2. Add TrustWeave as an implementation (fixture JSON and suite `default.js` entries), or configure resolver HTTP endpoints if applicable.
3. Run `npm run test` / `npm run test-and-generate-report` and collect results.
4. Publish the results here (or as a CI artifact) in the format expected by the [implementation report](https://w3c.github.io/did-test-suite/).

Until TrustWeave is added as an implementation in the W3C suite, **TrustWeave-specific** conformance remains **self-assessed** (see [DID Core 1.1 compliance and gaps](../reference/did-core-1-1-compliance-and-gaps.md)). The upstream suite itself runs clean as documented above.

## Self-assessed implementation summary

| Area | Status |
|------|--------|
| DID syntax (§3.1 ABNF) | DidValidator: method-name, method-specific-id with idchar and pct-encoded; tests for colons and pct-encoded. |
| alsoKnownAs (URL or DID) | DidOrUrl (AsDid/AsUrl); parsed and emitted in parser/producer. |
| controller (string or set) | Parsed and emitted in shared parser and producer. |
| Service type (string or set) | DidService.type = List<String>; parseServiceTypesFromJson, toServiceTypeJsonElement. |
| Embedded VMs in relationships | Normalized on ingest (strategy B); parser merges into verificationMethod. |
| @context v1.1 | DidDocumentJsonProducer uses https://www.w3.org/ns/did/v1.1 as first context. |
| application/did | APPLICATION_DID_MEDIA_TYPE; toBytesWithMediaType(). |
| Resolution metadata | DidResolutionResult.Success carries document, documentMetadata, resolutionMetadata. |
