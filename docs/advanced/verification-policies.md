---
title: Verification Policies
nav_exclude: true
---

# Verification Policies

Verifiers often need more than a single boolean. TrustWeave layers **expiration**, **revocation**, **schema**, **issuer resolution**, and **issuer trust** on top of cryptographic proof checks. The public surface is **`VerificationOptions`** plus optional **`TrustEvaluator`** (or trust-registry wiring via the **`verify { }`** DSL).

## Built-in switches (`VerificationOptions`)

Configure via **`org.trustweave.credential.requests.VerificationOptions`** (defaults are usually sensible):

- **Revocation** – `checkRevocation` (DSL: `checkRevocation()` / `skipRevocation()`).
- **Expiration / not-before** – `checkExpiration`, `checkNotBefore` (DSL: `checkExpiration()` / `skipExpiration()`).
- **Schema** – `validateSchema` + `schemaId` (DSL: `validateSchema("...")` / `skipSchema()`).
- **Issuer DID** – `resolveIssuerDid` when you need document resolution for verification.

Trust rules:

- **`TrustEvaluator`** (`org.trustweave.credential.trust`) – allowlist / blocklist / custom `suspend fun isTrusted(issuer: Did): Boolean`.
- **`verify { }`** – `withTrustPolicy(evaluator)`, `requireTrust(registry)`, `allowUntrusted()`, etc.

## Recommended usage

```kotlin
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.TrustWeave

val result = trustWeave.verify {
    credential(credential)
    checkRevocation()
    checkExpiration()
    validateSchema("https://example.com/schemas/person.json")
    // withTrustPolicy(TrustEvaluator.allowlist(trustedIssuers))
}

when (result) {
    is VerificationResult.Valid -> { /* use result.credential, warnings */ }
    is VerificationResult.Invalid -> { /* inspect sealed subtype + errors */ }
}
```

**Diagnostics:** On **`VerificationResult`**, use **`allErrors`** for a flattened list across subtypes. Each **`VerificationResult.Invalid.*`** subtype also has **`errors`** and **`warnings`**; some add typed fields (e.g. **`InvalidProof.reason`**, **`Expired.expiredAt`**).

## Extending behaviour

- **Revocation** – plug a **`CredentialRevocationManager`** when constructing **`CredentialService`** / **`TrustWeave`** so status lists are consulted during `verify`.
- **Anchoring** – keep tamper evidence outside the VC proof (e.g. store digest via **`BlockchainAnchorClient`**) and enforce it in your application policy layer.
- **Custom rules** – wrap `verify` results with your own checks (tenant allowlists, credential-type rules, audit logging).

## Testing

Use **testkit** fixtures to build credentials that are expired, revoked, or missing proofs, then assert on the **`VerificationResult`** subtype you expect.

See also **[Configure trust policies](../how-to/configure-trust-policies.md)** and **[Verify credentials](../how-to/verify-credentials.md)**.
