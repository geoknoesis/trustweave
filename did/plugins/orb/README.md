# did:orb plugin

Implementation of the [did:orb](https://trustbloc.github.io/did-method-orb/) DID method for TrustWeave.

Orb is a TrustBloc / DIF DID method built on top of the [Sidetree protocol](https://identity.foundation/sidetree/spec/).
Operations (create / update / recover / deactivate) are submitted to an Orb node, batched, anchored, and
published via ActivityPub. Each Orb node exposes a Sidetree REST API:

| Endpoint                                 | Purpose                                         |
| ---------------------------------------- | ----------------------------------------------- |
| `POST {baseUrl}/sidetree/v1/operations`  | Submit a Sidetree create/update/recover/deactivate |
| `GET  {baseUrl}/sidetree/v1/identifiers/{did}` | Resolve a DID (short-form or long-form) |

## DID format

- Short-form: `did:orb:<unique-suffix>` — anchored.
- Long-form: `did:orb:<unique-suffix>:<base64url-of-create-request>` — resolvable immediately
  before anchoring, since the resolution is deterministic.

The unique suffix is `base64url(SHA-256(JCS(suffixData)))`, per Sidetree §6.2.1.

## Configuration

```kotlin
val cfg = OrbDidConfig(
    baseUrl = "https://orb.example.com",     // required
    namespace = "did:orb",                    // optional, default did:orb
    authHeader = "Authorization" to "Bearer …", // optional
    timeoutSeconds = 30L,                     // optional, default 30
)

val method = OrbDidMethod(kms, cfg)
```

SPI users supply the same fields via `DidCreationOptions.additionalProperties`:

| Key                 | Required | Notes                                                            |
| ------------------- | -------- | ---------------------------------------------------------------- |
| `baseUrl`           | yes      | Orb node base URL                                                |
| `namespace`         | no       | Override the DID namespace (e.g. `did:orb:my-private-namespace`) |
| `operationsPath`    | no       | Override the operations endpoint path                            |
| `identifiersPath`   | no       | Override the identifiers endpoint path                           |
| `authHeaderName`    | no       | Together with `authHeaderValue` sets a custom auth header        |
| `authHeaderValue`   | no       | See above                                                        |
| `timeoutSeconds`    | no       | HTTP client timeout                                              |

The provider also reads `ORB_BASE_URL` from the environment if `baseUrl` is not supplied.

## Operations

| Method          | What it does                                                                 |
| --------------- | ---------------------------------------------------------------------------- |
| `createDid`     | Generates a signing key, builds a Sidetree create operation, POSTs it.       |
| `resolveDid`    | GETs the identifiers endpoint; falls back to the in-memory cache on 404.     |
| `updateDid`     | Builds a Sidetree update operation, POSTs it.                                |
| `deactivateDid` | Builds a Sidetree deactivate operation, POSTs it.                            |

Cryptographic primitives (commitments, deltaHash, JCS canonicalisation) follow
Sidetree §6 exactly: `base64url(SHA-256(JCS(...)))`.

## Running an Orb sandbox locally

TrustBloc publishes an Orb image as part of the
[`trustbloc/orb`](https://github.com/trustbloc/orb) repository. The
all-in-one sandbox lives in `test/bdd/fixtures/orb` and brings up an Orb node
with witnesses, CAS, and ActivityPub. The recommended quick start is:

```bash
git clone https://github.com/trustbloc/orb
cd orb/test/bdd/fixtures
docker compose up -d
export ORB_BASE_URL=https://localhost:48326
```

The Orb sandbox uses self-signed TLS certs by default; set
`-Djavax.net.ssl.trustStore=...` or run Orb behind a reverse proxy with a
trusted certificate for production-style integration tests.

There is no first-party `ghcr.io/trustbloc/orb:latest` image at the time of
writing; the integration test uses `ORB_BASE_URL` from the environment and is
skipped when the variable is unset.

## Running tests

Unit tests (run by default, no external services required):

```bash
./gradlew :did:plugins:orb:test
```

Integration tests against a real Orb node — set `ORB_BASE_URL`:

```bash
export ORB_BASE_URL=https://localhost:48326
./gradlew :did:plugins:orb:test --tests "*Integration*"
```

## References

- [did:orb method spec](https://trustbloc.github.io/did-method-orb/)
- [Sidetree v1 spec](https://identity.foundation/sidetree/spec/)
- [TrustBloc Orb on GitHub](https://github.com/trustbloc/orb)
