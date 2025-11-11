# Frequently Asked Questions

## How do I run the quick-start sample?

Clone the repo, then execute:

```bash
./gradlew :vericore-examples:runQuickStartSample
```

The sample issues a credential, verifies it (with proper error handling), and anchors the digest using the in-memory client. Use it as a template for your own experiments.

## How do I add a new DID method?

1. Implement `DidMethod` (and optionally `DidMethodProvider` for SPI).  
2. Register it with `DidMethodRegistry` in your `VeriCoreConfig`.  
3. Update wallets or services that create DIDs to reference the new method.  

See [DIDs](core-concepts/dids.md) and the [Wallet API Reference – DidManagement](api-reference/wallet-api.md#didmanagement).

## What licence applies to VeriCore?

VeriCore uses a dual licence: open source for non-commercial and educational use, and a commercial licence from Geoknoesis LLC for production deployments. Details live in the [Licensing Overview](licensing/README.md).

## How do I test without a blockchain or external KMS?

Use `vericore-testkit` which provides in-memory DID methods, KMS, and anchor clients. They are deterministic, making unit tests and CI runs lightweight.

## Where can I find API signatures and parameters?

- [Wallet API Reference](api-reference/wallet-api.md) for wallet capabilities and helper extensions.  
- [Credential Service API Reference](api-reference/credential-service-api.md) for issuer/verifier SPI details.  
- Module-level READMEs under `docs/modules/` describe additional utilities.

## How do I enforce stricter verification policies?

Use `CredentialVerificationOptions` (see [Verification Policies](advanced/verification-policies.md)). You can enforce expiration, revocation, audience, and anchoring in addition to signature checks.

## Where do I log issues or request features?

Open an issue in the GitHub repository or reach out to the Geoknoesis team via [www.geoknoesis.com](https://www.geoknoesis.com). Contributions follow the [Contributing Guide](contributing/README.md).

