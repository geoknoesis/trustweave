# TrustWeave Reference Wallet

The working demo for the TrustWeave Wallet SDK. **This is not a product** — see
[`docs/.internal/reference-wallet-design.md`](../docs/.internal/reference-wallet-design.md)
for the full design and intent.

## What it does

End-to-end credential holder flow in your browser:

1. **First run** — generates a `did:key` Ed25519 identity for the holder, stores the
   private key in browser localStorage (Phase 1 only; Phase 2 mobile uses Secure Enclave).
2. **Receive** — calls the in-repo demo issuer and stores a Bachelor of Science
   Verifiable Credential.
3. **Browse** — lists held credentials as cards.
4. **Present** — wraps a selected credential in a Verifiable Presentation, signed by the
   holder, and sends it to the in-repo demo verifier.
5. **Verify** — demo verifier checks the VC signature against the issuer's `did:key`,
   the VP signature against the holder's `did:key`, and reports success/failure.

## Run it

```bash
cd reference-wallet
npm install
npm run dev
```

Open http://localhost:3000.

## What's inside

```
reference-wallet/
├── app/
│   ├── page.tsx                  Home — wallet identity + credentials list
│   ├── receive/page.tsx          Receive a demo credential
│   ├── present/page.tsx          Present a held credential to the verifier
│   ├── api/
│   │   ├── demo-issuer/          Mock OID4VCI-style issuer endpoint
│   │   └── demo-verifier/        Mock OID4VP-style verifier endpoint
│   ├── layout.tsx
│   └── globals.css
├── lib/
│   ├── wallet.ts                 Wallet facade: identity, store, list, present
│   ├── crypto.ts                 Ed25519 keygen, did:key encoding/resolution, JWS
│   ├── storage.ts                localStorage adapter (Phase 1)
│   └── server-keys.ts            Server-side demo issuer + verifier keys
├── package.json
├── tsconfig.json
├── next.config.mjs
└── README.md
```

## What's deliberately NOT here (Phase 1)

- SD-JWT VC and selective disclosure (Phase 1.1)
- ISO 18013-5 mDoc / NFC / Bluetooth (Phase 2 / Phase 2)
- Full OID4VCI / OID4VP wire-format compliance (the demo uses a simplified direct flow
  with the same conceptual shape)
- Mobile / KMM build (Phase 2)
- Settings, diagnostics, search, tags (Phase 3)
- IndexedDB, push notifications, multi-wallet (out of scope per design)
- App Store distribution (deliberately never)

See the design doc's §3 In/Out scope and §10 implementation plan.

## Hooking it up to real TrustWeave

The current build uses `jose` directly for crypto and a minimal in-house OID4VCI/OID4VP
flow. A future iteration replaces this with a TypeScript port of the wallet SDK that
mirrors the Kotlin `wallet-core-mp` capability surface. The lib layer's shape
(`createWallet`, `wallet.store`, `wallet.list`, `wallet.present`) already aligns with
the Kotlin SDK contracts, so swapping the implementation later is a focused refactor.

## License

Apache 2.0 — fork freely, rebrand, submit under your own developer accounts.
