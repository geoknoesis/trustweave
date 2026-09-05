# Credential format compatibility

Capabilities are endpoint-specific. A library proof engine supporting a format does not mean every exchange adapter or wallet can receive and present it.

| Path | Accepted format | Unsupported behavior |
|---|---|---|
| Kotlin OID4VCI holder receive and deferred receive | JSON-LD VC (`ldp_vc`) | `supportedReceiveFormats()` and `compatibleConfigurations()` support negotiation; incompatible advertised offers fail before token exchange. Compact JWT/SD-JWT responses fail closed. |
| SaaS production presentation request | Subject-bound JSON VC objects, inside a signed JSON presentation | The holder JWS must include the entire presentation in its `vp` claim, excluding the enclosing `proof`; nonce/audience and every subject must match the holder. Unsupported array entries fail the whole presentation. |
| Reference web wallet | Compact VC-JWT / SD-JWT VC for its demo exchange endpoints | These demo exchange paths are distinct from SaaS production OID4VP. Do not assume the demo QR envelope interoperates with a standard OID4VP endpoint. |

SaaS presentation clients must sign the full `vp` JSON object, including `holder` and `verifiableCredential`, and carry the JWS in `proof.jws`. `proof.verificationMethod` must identify that holder's did:key. The signed JWT carries the request's nonce (or challenge) and audience (or domain). The service compares parsed signed/received JSON, so member order is immaterial but adding/removing/changing a claim is rejected. This constrained profile is identified by `trustweave_profile=subject-bound-json-vp-v1`; it is not a claim of general proof-suite conformance.

Requested claims use dot-separated object property names. Every requested type must be represented by a credential satisfying all requested claims. Optional `presentation_submission` mappings must identify this definition and map each descriptor to an actual JSON credential satisfying it. Arbitrary JSONPath expressions and nested descriptor mappings are unsupported.

Bearer and delegated credentials require a separately implemented authorization policy. They are not implicitly accepted by this subject-bound profile. Do not remove subject checks to add delegation.
