# TrustWeave Cardano Anchor

Cardano blockchain adapter for TrustWeave. Anchors arbitrary JSON payloads in
**CIP-20 transaction metadata** (label `674` by default) via **Blockfrost**, and reads
them back through the `/txs/{hash}/metadata` endpoint.

Supported networks (CAIP-2 chain ids):

| Chain id | Network | Blockfrost base URL |
|---|---|---|
| `cardano:mainnet` | Mainnet | `https://cardano-mainnet.blockfrost.io/api/v0` |
| `cardano:preview` | Preview testnet | `https://cardano-preview.blockfrost.io/api/v0` |
| `cardano:preprod` | Pre-production testnet | `https://cardano-preprod.blockfrost.io/api/v0` |

## How it works

1. The payload (a `kotlinx.serialization.json.JsonElement`) is canonicalised to a JSON
   string and split into ≤ 64-byte UTF-8 chunks. Cardano metadata strings have a 64-byte
   per-string ceiling.
2. The chunks are placed under the CIP-20 shape:
   ```cbor
   { 674: { "msg": [ "chunk-1", "chunk-2", ... ] } }
   ```
3. A self-payment of 1 ADA is built with [bloxbean cardano-client-lib]'s `QuickTxBuilder`,
   the metadata is attached, the transaction is signed with the submitter wallet, and
   submitted via Blockfrost. We then block until confirmation.
4. The returned `AnchorRef.txHash` is the canonical 64-hex-char Cardano transaction id.

## Configuration

```kotlin
val config = CardanoAnchorConfig(
    blockfrostProjectId   = "previewABC123",          // Blockfrost project id
    network               = CardanoNetwork.Preview,    // mainnet / preview / preprod
    submitterMnemonic     = "abandon abandon ...",     // BIP-39 (15 or 24 words)
    // OR
    submitterSecretKey    = "<hex-encoded extended sk>",
    metadataLabel         = 674L,                      // CIP-20 default
    confirmationTimeoutSeconds = 120,
)
```

Configuration can also be supplied via the generic `BlockchainAnchorClientProvider.create`
map API. Recognised keys:

- `blockfrostProjectId` (required for any real-network use)
- `submitterMnemonic` or `submitterSecretKey` (required to **write**)
- `network` (`"Mainnet" | "Preview" | "Preprod"`) — usually derived from chain id
- `metadataLabel` (defaults to 674)
- `blockfrostBaseUrl` (override for self-hosted Blockfrost or test mocks)
- `confirmationTimeoutSeconds` (default 90)

The SPI provider also honours these environment variables when matching options are absent:

- `CARDANO_BLOCKFROST_PROJECT_ID`
- `CARDANO_SUBMITTER_MNEMONIC`
- `CARDANO_SUBMITTER_SECRET_KEY`

## Usage

```kotlin
import org.trustweave.anchor.cardano.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val config = CardanoAnchorConfig(
        blockfrostProjectId = System.getenv("CARDANO_BLOCKFROST_PROJECT_ID"),
        network             = CardanoNetwork.Preview,
        submitterMnemonic   = System.getenv("CARDANO_SUBMITTER_MNEMONIC"),
    )
    CardanoBlockchainAnchorClient(CardanoBlockchainAnchorClient.PREVIEW, config).use { client ->
        val anchored = client.writePayload(buildJsonObject {
            put("vc", JsonPrimitive("vc-12345"))
            put("digest", JsonPrimitive("urn:sha-256:abc..."))
        })
        println("anchored at tx ${anchored.ref.txHash}")
        val read = client.readPayload(anchored.ref)
        println("payload back: ${read.payload}")
    }
}
```

## Tests

```bash
# Unit tests (no network) — verify CBOR encoding, Blockfrost JSON parsing, SPI wiring.
./gradlew :anchors:plugins:cardano:test
```

Unit tests use `okhttp3.mockwebserver` to stand in for Blockfrost and round-trip CBOR
metadata through bloxbean's `CBORMetadata.deserialize` /
`MetadataToJsonNoSchemaConverter.cborBytesToJson` — no external service required.

### Integration test (real Blockfrost, Preview network)

`CardanoBlockfrostIntegrationTest` round-trips a real anchor against the Preview
testnet. It is **only run** when both environment variables are set:

```bash
export CARDANO_BLOCKFROST_PROJECT_ID=preview...     # from blockfrost.io
export CARDANO_SUBMITTER_MNEMONIC="abandon abandon ..."  # Preview wallet with ≥ 5 tADA
./gradlew :anchors:plugins:cardano:test --tests "*CardanoBlockfrostIntegrationTest*"
```

Preview faucet: <https://docs.cardano.org/cardano-testnet/tools/faucet/>. The test
submits a small transaction (≈ 0.17 ADA fee plus 1 ADA self-payment) and waits for
confirmation (default 5 min).

## References

- CIP-20 transaction message: <https://cips.cardano.org/cips/cip20/>
- Blockfrost API: <https://docs.blockfrost.io/>
- bloxbean cardano-client-lib: <https://github.com/bloxbean/cardano-client-lib>
- Cardano metadata limits: <https://docs.cardano.org/explore-cardano/network-tutorials/local-cardano-testnet/transaction-metadata/>
