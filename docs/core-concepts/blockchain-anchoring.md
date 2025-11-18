# Blockchain Anchoring

Anchoring creates an immutable audit trail for important events or payloads by writing a compact reference to a blockchain. VeriCore standardises the experience so you can leverage tamper evidence without becoming a chain expert.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
}
```

**Result:** Adds the anchoring registry and clients to your project so the examples below compile.

## Why anchor data?

- **Integrity** – recompute a digest and compare it to the anchor; any change breaks the link.  
- **Provenance** – the anchor’s block height or timestamp proves when the information existed.  
- **Portability** – `AnchorRef` structures capture chain ID, transaction hash, optional contract/app ID, and metadata that verifiers can consume.

Anchoring complements verifiable credentials: you can notarise VC digests, presentation receipts, workflow checkpoints—anything that needs an immutable trail.

## How VeriCore anchors payloads

| Step | Implementation |
|------|----------------|
| 1. Choose a chain | Register a `BlockchainAnchorClient` (in-memory, Algorand, Polygon, Indy, or your own adapter). Chains use CAIP-2 IDs such as `algorand:testnet`. |
| 2. Canonicalise payload | Kotlinx Serialization + JSON Canonicalization Scheme ensure deterministic bytes. |
| 3. Submit | `writePayload` stores the digest on chain and returns `AnchorResult` with an `AnchorRef`. |
| 4. Verify | `readPayload` rehydrates the JSON, or recompute the digest locally and compare to the stored reference. |

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*
import kotlinx.serialization.json.Json

// Using VeriCore facade (recommended)
val vericore = VeriCore.create()
val anchorResult = vericore.anchor(
    data = credential,
    serializer = VerifiableCredential.serializer(),
    chainId = "algorand:testnet"
).getOrThrow()
println("Anchored tx: ${anchorResult.ref.txHash}")

// With error handling
val result = vericore.anchor(data, serializer, chainId)
result.fold(
    onSuccess = { anchor -> println("Anchored tx: ${anchor.ref.txHash}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
            }
            else -> println("Anchoring error: ${error.message}")
        }
    }
)
```

## Configuring clients

- **In-memory** – perfect for tutorials and unit tests.  
- **Algorand** – use `AlgorandBlockchainAnchorClientOptions` (`algodUrl`, `algodToken`, optional private key).  
- **Polygon / Ganache** – specify RPC endpoints, signer keys, and (optionally) contract addresses.  
- **Indy** – configure pool parameters and wallet credentials for permissioned ledgers.  
- **Custom** – implement `BlockchainAnchorClient` or `BlockchainAnchorClientProvider` (discovered via SPI).

## Reading and verifying

```kotlin
import com.geoknoesis.vericore.VeriCore
import com.geoknoesis.vericore.core.*

// Using VeriCore facade (recommended)
val vericore = VeriCore.create()
val data = vericore.readAnchor<VerifiableCredential>(
    ref = anchorRef,
    serializer = VerifiableCredential.serializer()
).getOrThrow()
println("Read credential: ${data.id}")

// With error handling
val result = vericore.readAnchor<VerifiableCredential>(ref, serializer)
result.fold(
    onSuccess = { data -> println("Read: ${data.id}") },
    onFailure = { error ->
        when (error) {
            is VeriCoreError.ChainNotRegistered -> {
                println("Chain not registered: ${error.chainId}")
            }
            else -> println("Read error: ${error.message}")
        }
    }
)
```

- `readAnchor` returns `Result<T>` with the deserialized data.  
- For higher assurance, recompute the digest from the canonical payload and compare it to the data stored on chain.  
- Keep connection credentials (RPC tokens, private keys) in a secret store for production deployments.
- All anchoring operations return `Result<T>` for consistent error handling.

## Practical usage tips

- **Persist AnchorRefs with credentials** so verifiers can revalidate without bespoke lookups.  
- **Retry-friendly anchoring** – public chains may require exponential back-off; design idempotent submissions.  
- **Integrate with revocation** – anchor revocation lists or proofs to create audit trails for credential status changes.  
- **Testing** – use the in-memory client or spin up Ganache/Testnet clients for end-to-end tests.
- **Error handling** – all anchoring operations return `Result<T>` with structured `VeriCoreError` types. See [Error Handling](../advanced/error-handling.md).
- **Input validation** – VeriCore automatically validates chain ID format and registration before anchoring.

## See also

- [Quick Start – Step 5](../getting-started/quick-start.md#step-5-verify-and-optionally-anchor) for an end-to-end example.  
- [Wallet API Reference – Anchoring helpers](../api-reference/wallet-api.md#anchors) for wallet-integrated flows.  
- [Architecture Overview](../introduction/architecture-overview.md) for the DID ➜ credential ➜ anchor flow.  
- [Verifiable Credentials](verifiable-credentials.md) to understand what you may want to anchor.
# Blockchain Anchoring

Anchoring creates an immutable audit trail for important events or payloads by writing a compact reference to a blockchain. VeriCore standardizes the experience so you can take advantage of tamper evidence without having to become a chain expert.

## Why Anchor?

- **Integrity** – prove a payload was not modified after anchoring by recomputing its digest and comparing it to the on-chain reference.
- **Provenance** – demonstrate when information existed by referencing the block height or timestamp of the anchor transaction.
- **Portability** – exchangeable `AnchorRef` models capture chain, transaction hash, optional contract, and any custom metadata.

Anchoring is complementary to verifiable credentials: you can anchor raw JSON, credential digests, presentation receipts, or any other data you want to notarize.

## How VeriCore Anchoring Works

1. **Choose a chain** – VeriCore ships with in-memory clients for testing and adapters for Algorand, Polygon, Indy, and community providers. Chains are identified using CAIP-2 strings (for example `algorand:testnet`).
2. **Serialize the payload** – the SDK serializes your Kotlin data using Kotlinx Serialization before hashing.
3. **Submit** – the registered `BlockchainAnchorClient` stores the digest on-chain and returns an `AnchorResult` containing the `AnchorRef` (transaction hash, contract/app ID, chain).
4. **Verify** – later you can `readPayload` or independently recompute the digest to confirm the payload matches the anchor reference.

```kotlin
val anchorClient = anchorRegistry.get("algorand:testnet")
val result = anchorClient?.writePayload(jsonPayload)
println("Anchored tx: ${result?.ref?.txHash}")
```

## Configuring Clients

- **In-memory** – great for tests. Register with `BlockchainAnchorRegistry().register("inmemory:anchor", InMemoryBlockchainAnchorClient("inmemory:anchor"))`.
- **Algorand** – configure `AlgorandBlockchainAnchorClientOptions` (`algodUrl`, `algodToken`, optional private key for signing).
- **Polygon / Ganache** – supply RPC URLs, contract addresses, and private keys via typed options.
- **Indy** – connect to Hyperledger Indy pools using pool endpoints, wallet names, and DIDs.

All clients share a common template (`AbstractBlockchainAnchorClient`) for fallbacks, metadata, and error handling. You can implement your own by extending the base class or providing an SPI adapter discovered via `META-INF/services`.

## Reading and Verifying

```kotlin
val anchorRef: AnchorRef = result.ref
val stored = anchorClient?.readPayload(anchorRef)
println("Stored mediaType=${stored?.mediaType} payload=${stored?.payload}")
```

- The payload is returned as a `JsonElement`; you can re-hydrate it using your serializer.
- Anchoring with in-memory fallbacks works without private keys, which makes it ideal for unit tests and demos.
- For production you should secure credentials (RPC URLs, tokens, private keys) using your secrets management system.

## When to Use Anchoring

- Credential issuance receipts or revocation records.
- Supply-chain checkpoints or sensor readings.
- Publication timestamps for news or research.
- Any workflow where you need evidence that “this existed in this exact form at this time.”

## See Also

- [Quick Start – Step 5](../getting-started/quick-start.md#step-5-verify-and-optionally-anchor)
- [Blockchain Anchor Registry API](../api-reference/wallet-api.md) *(for wallet anchoring integration)*
- [VeriCore Anchor module](../modules/vericore-core.md) for implementation details.

