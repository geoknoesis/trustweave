---
title: Frequently Asked Questions
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

- [Wallet API Reference](api-reference/wallet-api.md) — wallet capabilities, typed option builders, and extension helpers.  
- [Credential Service API Reference](api-reference/credential-service-api.md) — issuer/verifier SPI contracts and factory options.  
- Module guides under `docs/modules/` summarise additional utilities exposed by each artifact.

## How do I enforce stricter verification policies?

Configure `CredentialVerificationOptions` (see [Verification Policies](advanced/verification-policies.md)). You can enable expiration checks, proof-purpose enforcement, anchoring requirements, revocation lookups, and domain/audience validation—all while receiving a structured `CredentialVerificationResult`.

## How do I handle errors in TrustWeave?

All `TrustLayer` methods throw `TrustWeaveError` exceptions on failure. Always wrap operations in try-catch blocks:

```kotlin
import com.trustweave.trust.TrustLayer
import com.trustweave.core.TrustWeaveError

val trustLayer = TrustLayer.build {
    keys { provider("inMemory"); algorithm("Ed25519") }
    did { method("key") { algorithm("Ed25519") } }
}

try {
    val did = trustLayer.createDid { method("key") }
    println("Created: $did")
} catch (error: TrustWeaveError) {
    when (error) {
        is TrustWeaveError.DidMethodNotRegistered -> {
            println("Method not registered: ${error.method}")
            println("Available methods: ${error.availableMethods}")
        }
        else -> println("Error: ${error.message}")
    }
}
```

**Note:** Some lower-level APIs return `Result<T>` directly. Check the method signature for each operation.

See [Error Handling](advanced/error-handling.md) for detailed error handling patterns and [API Patterns](getting-started/api-patterns.md) for correct API usage.

## Where do I log issues or request features?

Open an issue in the GitHub repository or contact Geoknoesis LLC via [www.geoknoesis.com](https://www.geoknoesis.com). When contributing code or docs, follow the workflow outlined in the [Contributing Guide](contributing/README.md).

