---
title: DIDComm V2 Integration Guide
redirect_from:
  - /features/credential-exchange-protocols/didcomm-integration-guide/
parent: Feature Reference
grand_parent: API Reference
---

# DIDComm V2 Integration Guide

## Overview

The TrustWeave DIDComm plugin integrates **[didcomm-java](https://github.com/sicpa-dlab/didcomm-java)** (`org.didcommx:didcomm:0.3.2`) through **`DidCommCryptoDidcomm`**. Packing and unpacking use `DIDComm.packEncrypted` / `UnpackParams` with **`forward(false)`** so messages are not wrapped for mediators unless you add that layer yourself.

## What you must provide

1. **`suspend (String) -> DidDocument?`** — Resolve each DID to a W3C-shaped `DidDocument` that includes **key agreement** verification methods compatible with the keys you use for encryption (see didcomm-java curve matching).
2. **`org.didcommx.didcomm.secret.SecretResolver`** — Resolve **private** key material for every `kid` involved (sender agreement key and recipient agreement key). For tests, use **`org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver`** with `org.didcommx.didcomm.secret.Secret` entries (`VerificationMethodType.JSON_WEB_KEY_2020` + JWK string including `d` is typical).
3. **`BlockingDidDocResolver`** is used internally: your resolver runs on **`Dispatchers.IO`** via `runBlocking` with a **per-call timeout** (default 30s) because didcomm-java’s `DIDDocResolver` API is synchronous. Do not call back into DIDComm pack/unpack from the resolver on the same thread (deadlock risk).

## Factory methods

| Goal | API |
|------|-----|
| Interoperable service | `DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)` |
| Interoperable packer | `DidCommFactory.createPacker(kms, resolveDid, secretResolver)` |
| Unsafe placeholder (dev only) | `DidCommFactory.createInMemoryServiceWithPlaceholderCrypto(...)` / `createPackerWithPlaceholderCrypto(...)` |

## SPI (`DidCommExchangeProtocolProvider`)

Options map:

- **`useProductionCrypto`**: defaults to **`false`** (placeholder crypto, no `SecretResolver`). Set to **`true`** only with **`secretResolver`**; otherwise the provider throws **`IllegalArgumentException`**.
- **`secretResolver`**: `SecretResolver` — required when `useProductionCrypto == true` for didcomm-java.

## DID document mapping

`TrustWeaveDidDocMapper` converts TrustWeave `DidDocument` → didcomm **`DIDDoc`**:

- Verification methods with **`publicKeyJwk`** → **`JSON_WEB_KEY_2020`** (JWK serialized as a string).
- **`keyAgreement`** and **`authentication`** lists use **full verification method IDs** (`did:…#fragment`) as in didcomm-java.

## Tests

See **`DidCommDidcommRoundTripTest`** in the plugin: X25519 `OctetKeyPair` (Nimbus), `JsonWebKey2020` in DID docs, `MapSecretResolver`, then `DidCommFactory.createPacker` → `pack` / `unpack`.

## References

- [didcomm-java](https://github.com/sicpa-dlab/didcomm-java)
- [DIDComm V2 book](https://didcomm.org/book/v2/)
