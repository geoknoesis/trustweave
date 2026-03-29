---
title: Frequently Asked Questions
nav_order: 2
parent: Reference
keywords:
  - faq
  - frequently asked questions
  - troubleshooting
  - help
  - common questions
---

# Frequently Asked Questions

TrustWeave is produced by **Geoknoesis LLC** ([www.geoknoesis.com](https://www.geoknoesis.com)). This FAQ highlights the questions developers ask most often when wiring the SDK into real systems.

## How do I run the quick-start sample?

**Purpose:** Verify your toolchain and observe the issuance → verification → anchoring loop end to end.
**Command:** Run the Gradle helper task; it bootstraps in-memory services so no external dependencies are required.

```bash
./gradlew :TrustWeave-examples:runQuickStartSample
```

**Result:** A credential is issued, verified with full error handling, and anchored via the in-memory blockchain client—use the output as a baseline for your own experiments.

## How do I add a new DID method?

**Overview:** Implement the DID interface, register it, and point call sites at the new method name—no global singletons required.

1. Implement `DidMethod` (and optionally `DidMethodProvider` if you want SPI auto-discovery).
2. Register it with `DidMethodRegistry` while building your `TrustWeaveConfig`.
3. Update wallets or services that create DIDs so they pass the new method identifier.

See [DIDs](core-concepts/dids.md) and [Wallet API – DidManagement](api-reference/wallet-api.md#didmanagement) for code samples that show the typed option builders involved.

## What licence applies to TrustWeave?

TrustWeave uses a **dual licence**:

- **Non-commercial / education:** open-source licence.
- **Commercial deployments:** Geoknoesis commercial licence.

Details and contact paths live in the [Licensing Overview](licensing/README.md).

## How do I test without a blockchain or external KMS?

Use `TrustWeave-testkit`. It ships in-memory DID methods, KMS, and blockchain anchor clients that mirror the production interfaces. Because everything stays in process, your unit tests and CI runs remain deterministic and fast.

## Where can I find API signatures and parameters?

- Wallet API Reference](api-reference/wallet-api.md) — wallet capabilities, typed option builders, and extension helpers.
- Credential Service API Reference](api-reference/credential-service-api.md) — issuer/verifier SPI contracts and factory options.
- Module guides under `docs/modules/` summarise additional utilities exposed by each artifact.

## How do I enforce stricter verification policies?

Use **`trustWeave.verify { }`** with **`VerificationBuilder`** (and optional **`VerificationOptions`** when calling **`CredentialService.verify`** directly); see [Verification Policies](advanced/verification-policies.md). You can enable expiration checks, proof-purpose enforcement, schema validation, revocation lookups, and issuer trust via **`TrustEvaluator`** / **`TrustRegistry`**, and you receive a sealed **`VerificationResult`** (`org.trustweave.credential.results`).

## How do I handle errors in TrustWeave?

**Credential flows** (`issue`, `verify`, `presentationResult`, batches) return **sealed results**—handle them with **`when`** (e.g. **`IssuanceResult.Failure.AdapterNotReady`**). For **`VerificationResult.Invalid`**, **`IssuanceResult.Failure`**, and **`PresentationResult.Failure`**, use **`allErrors`** for a single list of messages (logging, APIs). **DID creation** returns **`DidCreationResult`**, not a bare DID. **Unwrapping:** **`getOrThrowDid()`** and most credential **`getOrThrow()`** throw **`IllegalStateException`**; **`PresentationResult.getOrThrow()`** throws **`TrustWeaveException.InvalidState`** (`PRESENTATION_*`). Prefer **`when`** on sealed results in production.

```kotlin
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.testkit.services.*

val trustWeave = TrustWeave.build {
    keys { provider(IN_MEMORY); algorithm(ED25519) }
    did { method(KEY) { algorithm(ED25519) } }
}

when (val dr = trustWeave.createDid { method(KEY) }) {
    is DidCreationResult.Success -> println("Created: ${dr.did}")
    is DidCreationResult.Failure.MethodNotRegistered -> {
        println("Method not registered: ${dr.method}")
        println("Available: ${dr.availableMethods}")
    }
    is DidCreationResult.Failure.InvalidConfiguration -> println("Invalid config: ${dr.reason}")
    is DidCreationResult.Failure.KeyGenerationFailed -> println("Key generation: ${dr.reason}")
    is DidCreationResult.Failure.DocumentCreationFailed -> println("Document: ${dr.reason}")
    is DidCreationResult.Failure.Other -> println("Other: ${dr.reason}")
}
```

**Note:** Wallet and some other APIs may still throw **`TrustWeaveException`**. See the [API contract table](getting-started/api-patterns.md#api-contract-results-vs-exceptions).

See [Error Handling](advanced/error-handling.md) for detailed patterns and [API Patterns](getting-started/api-patterns.md).

## Where do I log issues or request features?

Open an issue in the GitHub repository or contact Geoknoesis LLC via [www.geoknoesis.com](https://www.geoknoesis.com). When contributing code or docs, follow the workflow outlined in the [Contributing Guide](contributing/README.md).

