# TrustWeave Indy Anchor

`anchors:plugins:indy` implements `BlockchainAnchorClient` on top of Hyperledger Indy.

It writes the SHA-256 digest of a payload (plus the payload itself, when it fits) to
an Indy ledger using the **ATTRIB** transaction type and reads it back through
**GET_ATTRIB**. All wire requests are constructed in Kotlin and submitted to an
[`indy-vdr-proxy`](https://github.com/hyperledger/indy-vdr/tree/main/indy-vdr-proxy)
HTTP endpoint, so the plugin works against any Indy pool (Sovrin Mainnet/Staging,
BCovrin, von-network, …) without pulling in native binaries.

## Chain IDs

Format: `indy:<network>:<pool-name>`

| Chain ID                          | Pool                    |
|-----------------------------------|-------------------------|
| `indy:mainnet:sovrin`             | Sovrin Mainnet          |
| `indy:testnet:sovrin-staging`     | Sovrin Staging          |
| `indy:testnet:bcovrin`            | BCovrin Testnet         |

Custom pools are accepted as long as `poolEndpoint` is supplied in the options.

## Configuration

| Option           | Required for writes | Description                                                             |
|------------------|---------------------|-------------------------------------------------------------------------|
| `poolEndpoint`   | yes                 | Base URL of an `indy-vdr-proxy` instance                               |
| `did`            | yes                 | Submitter DID (identifier on the ATTRIB request)                       |
| `signingKeySeed` | yes                 | Base58-encoded 32-byte Ed25519 seed                                    |
| `targetDid`      | no (default = `did`)| DID the attribute is attached to (`operation.dest`)                    |
| `signingKey`     | optional alias      | 64-byte Base58 signing key (`seed || pubkey`) emitted by `indy-cli`    |

Without `signingKeySeed`/`signingKey` the client falls back to in-memory storage so
that examples and unit tests can run without a live pool.

## Usage

```kotlin
import org.trustweave.anchor.indy.IndyBlockchainAnchorClient
import org.trustweave.anchor.options.IndyOptions

val client = IndyBlockchainAnchorClient(
    chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
    options = mapOf(
        "poolEndpoint" to "http://localhost:9000",
        "did" to "V4SGRU86Z58d6TV7PBUe6f",
        "signingKeySeed" to "DvFAY1mNkUu1vSEzQyT9bAg6QqfZqWdMSCJ4Bc2pY3iU"
    )
)
val result = client.writePayload(buildJsonObject { put("digest", "abc") })
println("ledger seqNo = ${result.ref.txHash}")
```

The plugin registers itself via SPI; calling `IndyIntegration.discoverAndRegister()`
will wire it into a `BlockchainAnchorRegistry` alongside other adapters.

## Wire format

ATTRIB write request, signed with Ed25519 over the canonical signing payload:

```json
{
  "operation": {
    "type": "100",
    "dest": "<submitter-did>",
    "raw": "{\"digest\":\"…\",\"mediaType\":\"application/json\",\"payload\":{…}}"
  },
  "identifier": "<submitter-did>",
  "reqId": 1700000000000001,
  "protocolVersion": 2,
  "signature": "<base58-ed25519>"
}
```

GET_ATTRIB is sent unsigned and returns a `data` field with the same JSON string
verbatim, which the plugin parses back into a `JsonElement`.

## Running locally

Start a local Indy network with [von-network](https://github.com/bcgov/von-network):

```bash
docker run --rm -d --name vonnetwork \
  -p 9000:9000 -p 9701-9708:9701-9708 \
  bcgovimages/von-network-base:latest \
  ./scripts/start_webserver.sh
```

Then `POST /register` (e.g. `curl -X POST http://localhost:9000/register -d '{"seed":"00000000000000000000000000000000","role":"ENDORSER"}'`)
to mint a Steward DID and feed the returned DID + seed into the plugin options.

## Testing

```bash
./gradlew :anchors:plugins:indy:test
./gradlew :anchors:plugins:indy:integrationTest   # requires Docker
```

Unit tests use WireMock to mock `indy-vdr-proxy` and verify the on-the-wire JSON
matches expectations. Integration tests bring up `bcgovimages/von-network-base` via
TestContainers and exercise the real ATTRIB/GET_ATTRIB round-trip; they skip cleanly
when Docker is not available.

## References

- [Indy transactions](https://github.com/hyperledger/indy-node/blob/main/docs/source/transactions.md)
- [Indy requests](https://github.com/hyperledger/indy-node/blob/main/docs/source/requests.md)
- [indy-vdr-proxy](https://github.com/hyperledger/indy-vdr/tree/main/indy-vdr-proxy)
- [von-network](https://github.com/bcgov/von-network)
