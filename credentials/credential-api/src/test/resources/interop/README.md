# External interoperability test vectors

Fixtures used by `CanonicalizationKnownAnswerTest` and `Ed25519Signature2020InteropTest`.
None of these vectors were produced by TrustWeave: they exercise the JSON-LD
canonicalization (RDFC-1.0/URDNA2015) and Ed25519Signature2020 verification pipeline
against independent implementations.

## w3c-vc-di-eddsa/ — W3C specification test vectors

Source: W3C "Data Integrity EdDSA Cryptosuites v1.0" (vc-di-eddsa), section
*"A.4 Representation: Ed25519Signature2020"* — <https://www.w3.org/TR/vc-di-eddsa/#representation-ed25519signature2020>.
Retrieved: 2026-06-11. Extracted verbatim from the `<pre>` example blocks of the
published Recommendation (HTML entities unescaped; a single trailing newline ensured —
the spec's SHA-256 hashes are computed over the canonical N-Quads **with** a trailing
newline, which was confirmed by recomputing both hashes from these files).

| File | Content |
|---|---|
| `unsecured-credential.json` | Example 41: the credential before securing |
| `canonical-credential.nq` | Example 42: canonical (RDFC-1.0) N-Quads of the credential; SHA-256 = `517744132ae165a5349155bef0bb0cf2258fff99dfe1dbd914b938d775a36017` |
| `proof-options.json` | Example 44: proof options document (proof node without `proofValue`, carrying the secured document's `@context`) |
| `canonical-proof-options.nq` | Example 45: canonical N-Quads of the proof options; SHA-256 = `04e14bcf5727cba0c0aa04a04d22a56fef915d5f8f7756bb92ae67cb1d0c4847` |
| `signed-credential.json` | Example 50: the fully signed credential (`proofValue` is multibase base58-btc) |

Key pair published in the same section:
`publicKeyMultibase = z6MkrJVnaZkeFzdQyMZu1cgjg7k1pZZ6pvBQ7XJPt4swbTQ2`,
`secretKeyMultibase = z3u2en7t5LR2WtQH5PfFqMqwVHBeXouLzo6haApm8XHqvjxq`.
The spec's signature (`z57Mm1vboMtZiCyJ4aReZsv8co4Re64Y8GEjL1ZARzMbXZgkARFLqFs1P345NpPGG2hgCrS4nNdvJhpwnrNyG3kEF`)
was independently re-verified (python `cryptography`) over
`SHA-256(canonical proof options) || SHA-256(canonical document)` before being committed
here.

Note: the spec's credential uses a non-DID issuer (`https://vc.example/issuers/5678`),
so it cannot exercise the engine-level DID-document resolution path; it is used for
byte-exact canonicalization known-answer tests and a payload-level signature
verification against the spec's public key.

## digitalbazaar/ — cross-stack signed credential

`signed-credential.json` was produced by the **Digital Bazaar JavaScript stack** — the
reference Ed25519Signature2020 implementation, which shares no code with the
titanium-json-ld / titanium-rdfc stack used by this module:

- `@digitalbazaar/vc` 6.3.0
- `@digitalbazaar/ed25519-signature-2020` 5.4.0 (with `jsonld-signatures` 11.6.0)
- `@digitalbazaar/ed25519-verification-key-2020` 4.2.0
- `jsonld` (jsonld.js) 9.0.0, `rdf-canonize` 5.0.0

Generated on 2026-06-11 with `generate-vector.mjs` (kept here verbatim for
reproducibility), Node.js v24.15.0. The signing key is the key pair published in the
W3C vc-di-eddsa test vectors (above), giving the issuer
`did:key:z6MkrJVnaZkeFzdQyMZu1cgjg7k1pZZ6pvBQ7XJPt4swbTQ2`. The credential was verified
with the same JS stack (`vc.verifyCredential` → `verified: true`) before being committed.

A did:key issuer was chosen (instead of the spec's `https:` issuer) because the engine's
verification path resolves the proof's verification method from the **issuer's** DID
document and enforces the `assertionMethod` relationship there; the corresponding DID
document fixture is constructed in `Ed25519Signature2020InteropTest`.

## contexts/

| File | Source |
|---|---|
| `credentials-examples-v2.jsonld` | Official W3C context `https://www.w3.org/ns/credentials/examples/v2`, fetched 2026-06-11 with `Accept: application/ld+json` (byte-identical) |
| `trustweave-alumni-test-v1.jsonld` | Test-only claim vocabulary served as `https://example.org/contexts/alumni/v1`; the **same bytes** were served to the Digital Bazaar stack by `generate-vector.mjs` when signing `digitalbazaar/signed-credential.json` |

The W3C VC v1.1/v2 base contexts and the `ed25519-2020/v1` suite context are not
duplicated here; the official copies bundled with the main source set
(`org/trustweave/credential/contexts/`) are used — `generate-vector.mjs` served those
exact files to the JS stack as well, guaranteeing context-byte equality across stacks.
