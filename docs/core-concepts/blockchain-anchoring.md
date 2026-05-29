---
title: Blockchain Anchoring
nav_order: 4
parent: Core Concepts
keywords:
  - blockchain
  - anchoring
  - tamper evidence
  - integrity
  - timestamping
  - algorand
  - polygon
  - ethereum
---

# Blockchain Anchoring

Anchoring creates an immutable audit trail for important events or payloads by writing a compact reference to a blockchain. TrustWeave standardises the experience so you can leverage tamper evidence without becoming a chain expert.

```kotlin
dependencies {
    implementation("org.trustweave:anchors-anchor-core:0.6.0")
    implementation("org.trustweave:common:0.6.0")
}
```

**Result:** Adds the anchoring registry and clients to your project so the examples below compile.

## Why anchor data?

- **Integrity** – recompute a digest and compare it to the anchor; any change breaks the link.
- **Provenance** – the anchor’s block height or timestamp proves when the information existed.
- **Portability** – `AnchorRef` structures capture chain ID, transaction hash, optional contract/app ID, and metadata that verifiers can consume.

Anchoring complements verifiable credentials: you can notarise VC digests, presentation receipts, workflow checkpoints—anything that needs an immutable trail.

## How TrustWeave anchors payloads

| Step | Implementation |
|------|----------------|
| 1. Choose a chain | Register a `BlockchainAnchorClient` (in-memory, [Algorand](../how-to/integrations/algorand.md), [Polygon](../how-to/integrations/README.md#blockchain-anchor-integrations), [Ethereum](../how-to/integrations/ethereum-anchor.md), [Base](../how-to/integrations/base-anchor.md), [Arbitrum](../how-to/integrations/arbitrum-anchor.md), Indy, or your own adapter). Chains use CAIP-2 IDs such as `algorand:testnet`. |
| 2. Canonicalise payload | Kotlinx Serialization + JSON Canonicalization Scheme ensure deterministic bytes. |
| 3. Submit | `writePayload` stores the digest on chain and returns `AnchorResult` with an `AnchorRef`. |
| 4. Verify | `readPayload` rehydrates the JSON, or recompute the digest locally and compare to the stored reference. |

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.anchor.exceptions.BlockchainException

runBlocking {
    val trustWeave = TrustWeave.build { /* configure kms, did, anchor, ... */ }
    val anchorResult = trustWeave.blockchains.anchor(
        data = credential,
        serializer = VerifiableCredential.serializer(),
        chainId = "algorand:testnet"
    )
    println("Anchored tx: ${anchorResult.ref.txHash}")

    try {
        val anchor = trustWeave.blockchains.anchor(credential, VerifiableCredential.serializer(), "algorand:testnet")
        println("Anchored tx: ${anchor.ref.txHash}")
    } catch (e: BlockchainException.ChainNotRegistered) {
        println("Chain not registered: ${e.chainId}; available: ${e.availableChains}")
    }
}
```

## Configuring clients

- **In-memory** – perfect for tutorials and unit tests.
- **Algorand** – use `AlgorandBlockchainAnchorClientOptions` (`algodUrl`, `algodToken`, optional private key).
- **Polygon / Ganache** – specify RPC endpoints, signer keys, and (optionally) contract addresses.
- **Indy** – configure pool parameters and wallet credentials for permissioned ledgers.
- **Custom** – implement `BlockchainAnchorClient` or `BlockchainAnchorClientProvider` (discovered via SPI).

## Reading and verifying

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.anchor.exceptions.BlockchainException

runBlocking {
    val trustWeave = TrustWeave.build { /* configure kms, did, anchor, ... */ }
    val data = trustWeave.blockchains.read(
        ref = anchorRef,
        serializer = VerifiableCredential.serializer()
    )
    println("Read credential: ${data.id}")

    try {
        val payload = trustWeave.blockchains.read(anchorRef, VerifiableCredential.serializer())
        println("Read: ${payload.id}")
    } catch (e: BlockchainException.ChainNotRegistered) {
        println("Chain not registered: ${e.chainId}")
    }
}
```

- `blockchains.read` returns the deserialized value or throws `BlockchainException` (for example if the chain is not registered).
- For higher assurance, recompute the digest from the canonical payload and compare it to the data stored on chain.
- Keep connection credentials (RPC tokens, private keys) in a secret store for production deployments.

## Practical usage tips

- **Persist AnchorRefs with credentials** so verifiers can revalidate without bespoke lookups.
- **Retry-friendly anchoring** – public chains may require exponential back-off; design idempotent submissions.
- **Integrate with revocation** – anchor revocation lists or proofs to create audit trails for credential status changes.
- **Testing** – use the in-memory client or spin up Ganache/Testnet clients for end-to-end tests.
- **Error handling** – anchoring uses **`AnchorResult`** on success and throws **`BlockchainException`** on many failures. See [Error handling](../api-reference/advanced/error-handling.md).
- **Input validation** – TrustWeave automatically validates chain ID format and registration before anchoring.

## See also

- Blockchain Anchor Integration Guides](../integrations/README.md#blockchain-anchor-integrations) – Implementation guides for Algorand, Ethereum, Base, Arbitrum, Polygon, and Ganache
- Quick Start – Step 5](../getting-started/quick-start.md#step-5-verify-and-optionally-anchor) for an end-to-end example.
- Wallet API Reference – Anchoring helpers](../api-reference/wallet-api.md#anchors) for wallet-integrated flows.
- Architecture Overview](../introduction/architecture-overview.md) for the DID ➜ credential ➜ anchor flow.
- Verifiable Credentials](verifiable-credentials.md) to understand what you may want to anchor.
- Blockchain-Anchored Revocation](blockchain-anchored-revocation.md) for anchoring credential revocation status lists.

## Related How-To Guides

- **[Anchor to Blockchain](../how-to/blockchain-anchoring.md)** - Step-by-step guide for anchoring data to blockchains

**Explore integrations:**
- Blockchain Integrations](../integrations/README.md#blockchain-anchoring) - Algorand, Polygon, Ethereum, and more
- Use Case Scenarios](../scenarios/README.md) - Real-world anchoring examples

