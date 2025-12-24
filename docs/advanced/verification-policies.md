---
title: Verification Policies
nav_exclude: true
---

# Verification Policies

Verifiers often need more than a boolean “valid/invalid”. TrustWeave lets you layer policy checks—expiration, audience, revocation status, anchor validation—on top of signature verification.

## Built-in options

`CredentialVerificationOptions` and `PresentationVerificationOptions` expose toggles for:

- **Expiration** – reject credentials whose `expirationDate` has passed.
- **Proof purpose** – enforce `assertionMethod`, `authentication`, or custom proof purposes.
- **Audience/Domain** – match against the values embedded in the VC or presentation.
- **Status checks** – consult revocation lists when status handlers are registered.

## Custom policy workflow

1. Configure the options object.
2. Call `TrustWeave.verifyCredential` (or presentation variant).
3. Inspect `CredentialVerificationResult` for detailed diagnostics (`valid`, `errors`, `warnings`, individual booleans).
4. Apply domain-specific rules to the result.

**Goal:** Combine built-in verification switches with organisation-specific rules.
**Prerequisites:** A `VerifiableCredential` you want to vet and a `TrustWeave` facade that is already configured with the necessary DID resolvers and status services.

```kotlin
import org.trustweave.credential.CredentialVerificationOptions

val options = CredentialVerificationOptions(
    checkExpiration = true,
    validateSchema = true,
    enforceStatus = true,
    expectedAudience = setOf("did:key:verifier-123"),
    requireAnchoring = true
)

val result = TrustWeave.verifyCredential(credential, options)
result.fold(
    onSuccess = { verification ->
        if (verification.valid) {
            println(
                "Credential verified. Proof=${verification.proofValid}, " +
                    "issuer=${verification.issuerValid}, revocation=${verification.notRevoked}"
            )
            if (verification.warnings.isNotEmpty()) {
                println("Warnings: ${verification.warnings.joinToString()}")
            }
        } else {
            println("Credential failed checks: ${verification.errors.joinToString()}")
        }
    },
    onFailure = { error ->
        when (error) {
            is TrustWeaveError.CredentialInvalid -> {
                println("Credential validation failed: ${error.reason}")
                println("Field: ${error.field}")
            }
            else -> {
                println("Verification failed before policy evaluation: ${error.message}")
                error.context.forEach { (key, value) ->
                    println("  $key: $value")
                }
            }
        }
    }
)
```

**What this does**
- Enables the built-in expiration, schema, revocation, and anchoring checks, while asserting the expected audience DID.
- Uses Kotlin’s `Result` API to surface transport or resolver failures separately from business-rule violations.
- Logs granular booleans (`proofValid`, `notRevoked`, etc.) so you can drive dashboards or structured alerts.

**Result**
A `CredentialVerificationResult` that captures pass/fail state plus individual flags. If any toggle fails, `valid` is `false` and the `errors` list carries human-readable reasons.

**Design significance**
Instead of returning ad-hoc maps, TrustWeave models verification output as a strongly typed data class. This keeps policy code expressive and makes it easy to unit test individual failure paths.

## Extending status and anchor checks

- **Revocation** – register a status list service (e.g., RevocationList2020) and populate `credentialStatus`. The verification result will include `STATUS=true/false`.
- **Anchoring** – set `requireAnchoring = true` and provide a lookup that maps `AnchorRef` back to stored digests. Combine with [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md).
- **Custom policies** – wrap the verification call in a separate function that adds business rules (issue date windows, subject DID allowlists, etc.).

## Testing policies

Use `TrustWeave-testkit` to simulate responses (expired credentials, revoked status, missing anchors). This ensures policy regressions surface in CI:

**Goal:** Assert that an expired credential fails the configured policy before it reaches production.

```kotlin
val expiredCredential = testFixture.createCredential {
    expirationDate = Instant.now().minusSeconds(60).toString()
}
val policyResult = TrustWeave.verifyCredential(expiredCredential, options)
assertFalse(policyResult.getOrThrow().valid)
```

**What this does**
- Overrides the issuance helper to back-date the credential and trigger the expiration branch.
- Verifies that your policy configuration translates into a boolean failure rather than a silent warning.

**Result**
The test fails if `valid` unexpectedly stays `true`, giving you confidence that future changes to verification defaults will not weaken enforcement.

**Design significance**
By leaning on the same DSL-driven options you use in production, your tests double as executable documentation—future contributors see exactly which policy levers matter.

## See also

- [Verifiable Credentials](../core-concepts/verifiable-credentials.md) for credential structure.
- [Blockchain Anchoring](../core-concepts/blockchain-anchoring.md) to understand how anchors feed policy checks.
- [Quick Start sample](../../distribution/TrustWeave-examples/src/main/kotlin/com/geoknoesis/TrustWeave/examples/quickstart/QuickStartSample.kt) demonstrates baseline verification.
- [Key Rotation](key-rotation.md) for maintaining trusted key sets.

