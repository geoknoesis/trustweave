---
title: DIDComm Crypto Implementation Notes
redirect_from:
  - /features/credential-exchange-protocols/didcomm-crypto-implementation-notes/
parent: Feature Reference
grand_parent: API Reference
---

# DIDComm Crypto Implementation Notes

## Interoperable path (default factory)

**`DidCommCryptoDidcomm`** delegates pack/unpack to **`org.didcommx.didcomm.DIDComm`** with:

- **`PackEncryptedParams`**: `forward(false)` (no mediator wrapping in this integration).
- **`BlockingDidDocResolver`**: synchronous didcomm resolver backed by your suspend `resolveDid`, with a per-call timeout (default 30s) on `Dispatchers.IO`.
- **`TrustWeaveDidDocMapper`**: TrustWeave `DidDocument` → didcomm `DIDDoc` (JWK-based verification methods map to `JSON_WEB_KEY_2020`).

You must supply a **`SecretResolver`** with private key material for each `kid` used in AuthCrypt.

## Placeholder path

**`DidCommCrypto`** remains for **non-interoperable** local experiments (dummy ciphertext). Do not use it for production or cross-vendor interop.

## Further reading

- [DIDComm plugin overview](didcomm.md)
- [Integration guide](didcomm-integration-guide.md)
