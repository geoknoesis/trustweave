# secp256k1 signature audit (padding defect fixed in `cbd1e616`)

## What happened

`EcdsaSignatureCodec.normalizeSecp256k1LowS` (`kms:kms-core`) converts a high-`s` secp256k1
signature to canonical low-`s` form by writing `n - s` over a copy of the original 64-byte P1363
signature. The helper it used to write that value, `writeFixedWidth`, right-aligned the new bytes
into the 32-byte `s` field but did not zero the leading pad first — it assumed the destination was
already zero, which was true everywhere else it was called from, but not here: the destination was
a copy of the *original* signature, so the field still held the old high `s`.

Whenever `n - s` needed **fewer than 32 bytes** to represent (i.e. it had a leading zero byte),
the unzeroed leading bytes of the old `s` survived. The result was not a merely non-canonical
(high-s) signature — it was a **cryptographically invalid** one: `r` unchanged, `s` neither the
original value nor `n - s`, verifying against nothing.

This condition — `n - s` having a leading zero byte — occurs for roughly **1 in 256** signatures
(precisely `2^248 / n ≈ 0.3906%`; measured 63 corrupt out of 16,000 real signatures during
diagnosis). It affected **every** secp256k1 signing path in this codebase, because they all route
through the same codec: `aws`, `azure`, `cyberark`, `fortanix`, `google`, `hashicorp`, `ibm`,
`inmemory`, `thales`, `waltid`, and `testkit`. P-256 and Ed25519 signing paths were never affected
— `derToP1363` (the DER transcoder) writes into a freshly-allocated, already-zero array and was
never exposed to this bug, and non-secp256k1 algorithms never call `normalizeSecp256k1LowS` at
all.

The defect is fixed on `main` as of commit `cbd1e616`. **Upgrading the library does not
retroactively touch signatures that were already persisted before the fix — but for this specific
defect, they do not need re-signing.** The corruption is a deterministic byte-level transformation,
not a loss of information: the correct low-`s` value is recoverable from the corrupted bytes with
no private key and no re-signing. `Secp256k1SignatureAudit.repair()` computes it, and only ever
returns it after independently re-verifying it against the record's public key and digest — see
"Repairing a corrupted signature" below. Every corrupted signature this tool identifies should be
**repaired**, not re-signed; re-signing from the underlying data is the fallback only for records
the audit could not repair (`INVALID_OTHER`).

## Are you affected?

You are potentially affected if, before upgrading past `cbd1e616`, you:

- Issued or anchored **secp256k1** credentials, proofs, or transactions through any of the KMS
  backends listed above.
- Used `EcdsaSignatureCodec.normalize()` or `normalizeSecp256k1LowS()` directly.

You are **not** affected if you only ever used P-256, Ed25519, or non-EC algorithms (RSA, BLS), or
if every secp256k1 signature you have was produced by a version of the library that already
includes `cbd1e616`.

### Where the exposure actually is: off-chain vs on-chain

This defect's practical risk is **not evenly spread** across use cases — where you look first
matters:

- **Off-chain and detached signatures are the real exposure.** A stored VC proof
  (`EcdsaSecp256k1Signature2019`-style), a detached JWS, or a persisted anchoring signature that
  nobody re-verifies immediately is written once and trusted from then on. If it was corrupted at
  signing time, **nothing fails at the time** — the corrupted bytes are simply persisted as if they
  were valid. The failure is silent and deferred: it only surfaces the next time someone tries to
  verify that specific credential or proof, which may be months later, by a relying party you have
  no visibility into. This is the category you need to actively sweep for.

- **On-chain (EVM) submissions were largely self-limiting.** An Ethereum node validates that a
  submitted transaction's signature recovers to the claimed sender address *before* accepting it.
  A corrupted signature fails that recovery check, so the transaction is **rejected at submission
  time** — you get an immediate, visible error, not a silently-persisted bad record. The
  corruption still happened (at the same ~1-in-256 rate), but it announced itself as a failed
  submission rather than as a defect you'd have to go looking for later.

  This does **not** mean on-chain paths need no attention — it means the artifact to look for is
  different. Check your anchoring operation logs for **errored anchoring operations that were
  never retried**. A submission that failed due to signature corruption, got logged as an error,
  and was never resubmitted leaves a credential or DID operation in a permanently un-anchored
  state even though nothing about the *data* is wrong — only the one-time signature was. Re-running
  the anchoring operation (a fresh sign-and-submit, not a patch) resolves these; they do not need
  the byte-level audit below, just a search through your anchoring error logs for the affected time
  window.

Prioritize the sweep below for anything off-chain and detached. For on-chain paths, prioritize a
log search for failed-and-unretried anchoring operations instead — the audit tool can still confirm
a specific stored signature is bad if you want byte-level certainty, but the failed submission
already told you it was.

## Where TrustWeave persists secp256k1 signatures

Before you can sweep anything you need to know where to point the export. This is the concrete
list of places this codebase writes a secp256k1 P1363 signature, traced from the source:

1. **Verifiable Credential / Presentation proofs — `Proof.proofValue`.** This is the primary
   off-chain exposure and where most sweeps should start. Two encodings appear depending on the
   proof suite, both produced in `credentials/credential-api`:
   - **`JsonWebSignature2020`**: `proofValue` is a *detached JWS compact serialization*
     (`<header>..<signature>`, ES256K when the signing key is secp256k1). Produced/verified in
     `credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/VcLdProofEngine.kt`
     and
     `credentials/credential-api/src/main/kotlin/org/trustweave/credential/internal/infrastructure/DefaultJsonWebSignature2020Adapter.kt`.
     The JWS signature segment, base64url-decoded, is the 64-byte P1363 signature to feed the
     audit; the JWS payload (document + proof-options digest) is what you need to hash to get
     `messageDigest`.
   - **Data-Integrity-style suites** (raw signature bytes, not a JWS): produced via
     `EcdsaSignatureCodec` directly and multibase/base64url-encoded into `proofValue` — see
     `credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/ProofEngineUtils.kt`.

   These `proofValue` strings live wherever the VC/VP JSON itself is stored:
   - **Wallet (holder-side) storage** — `wallet:wallet-core`'s `CredentialStorage.store()` /
     `get()` (`wallet/wallet-core/src/main/kotlin/org/trustweave/wallet/CredentialStorage.kt`),
     backed by whichever storage plugin is configured. If you use `wallet:plugins:database`, the
     concrete location is the `credentials` table's `credential_data` column (see
     `wallet/plugins/database/src/main/kotlin/org/trustweave/wallet/database/DatabaseWallet.kt`) —
     `SELECT id, credential_data FROM credentials` and parse `proof.proofValue` out of each row.
     `wallet:plugins:file` and `wallet:plugins:cloud` store the same VC JSON in file- or
     cloud-object form instead of a SQL column.
   - **Status list credentials**, which are themselves ordinary VCs signed through the same proof
     engines: `credentials/plugins/status-list/bitstring/src/main/kotlin/org/trustweave/revocation/bitstring/BitstringStatusListManager.kt`
     and `credentials/plugins/status-list/token/src/main/kotlin/org/trustweave/revocation/token/TokenStatusListManager.kt`.
   - **Issuer-side records and exchange logs** — anywhere your own issuance service persisted the
     credential JSON after calling into TrustWeave (a database, an OIDC4VCI issuance log, a
     DIDComm message store, a presentation-exchange transcript). TrustWeave does not mandate a
     specific issuer-side schema, so this list item is a pointer, not a table name — check your own
     issuance pipeline for where it kept a copy of what it issued.

2. **On-chain secp256k1 transactions — EVM anchoring and `did:ethr` operations.** Signed in
   `anchors/plugins/evm-base/src/main/kotlin/org/trustweave/anchor/evm/AbstractEvmAnchorClient.kt`
   (shared by the `ethereum`, `polygon`, `arbitrum`, `optimism`, `zksync`, and `ganache` anchor
   plugins) and by `did/plugins/ethr`. TrustWeave does not persist these signatures separately as
   standalone `r || s` bytes — the signature is embedded in the raw transaction it submits to the
   chain. As covered above, a corrupted signature here fails submission immediately rather than
   silently persisting, so this is lower priority for the byte-level sweep; if you keep your own
   pre-submission outbox/audit log of raw signed transactions, that log is the only place a
   corrupted-but-never-submitted signature could be sitting, and it is not something TrustWeave
   defines the schema for.

**Confirmed not affected (do not need sweeping):** the Indy anchor plugin signs exclusively with
Ed25519 (`anchors/plugins/indy/src/main/kotlin/org/trustweave/anchor/indy/IndySigner.kt`), and the
SD-JWT-VC proof engine hardcodes `JWSAlgorithm.EdDSA`
(`credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/SdJwtProofEngine.kt`)
— neither path can produce a secp256k1 signature regardless of what key algorithm is configured
elsewhere.

**Could not be determined from source in this pass:** whether the `bitcoin`, `algorand`,
`cardano`, and `starknet` anchor plugins route their signing through `EcdsaSignatureCodec` (and
therefore could carry this defect) or sign through a separate path (e.g. `bitcoinj`'s own signer)
that never touched the buggy code. Bitcoin uses secp256k1 by protocol, so if its plugin does share
the codec it belongs in this list; Algorand and Cardano use Ed25519 by protocol and are likely
unaffected the same way Indy is, but this was not verified against their source in this pass. If
you anchor through any of these four, check the relevant plugin's signing path directly before
concluding either way — do not assume from this document alone.

## The audit tool

`org.trustweave.kms.util.Secp256k1SignatureAudit` (`kms:kms-core`, alongside `EcdsaSignatureCodec`)
lets you sweep an export of persisted signatures and get back exactly which ones this defect
corrupted. It is a pure, offline, read-only tool:

- No KMS provider, no JCA security provider, no network access — ECDSA verification is done with
  plain `BigInteger` arithmetic (`Secp256k1Curve`), matching the dependency-free style of
  `EcdsaSignatureCodec` itself.
- It never touches a private key. It only needs, per signature: the signature bytes, the public
  key, and the message digest that was signed.

### Running the sweep

For each persisted signature you want to check, gather:

1. **The signature** — 64-byte P1363 (`r || s`).
2. **The public key** — SEC 1-encoded point, either 65-byte uncompressed (`0x04 || X || Y`) or
   33-byte compressed (`0x02`/`0x03 || X`). Decode it from whatever you have on hand (a JWK's `x`/
   `y`, a `did:key` multibase string, etc.) into these raw bytes before calling the audit.
3. **The message digest** — the 32-byte hash the signature was actually computed over. **This is
   not the raw message** — you must hash it yourself with whatever algorithm was used at signing
   time. This codebase uses SHA-256 for VC/JWS-style proofs and Keccak-256 for Ethereum-anchored
   transactions; they are not interchangeable, and using the wrong one will make a perfectly valid
   signature look invalid.

Then, per record:

```kotlin
import org.trustweave.kms.util.Secp256k1AuditRecord
import org.trustweave.kms.util.Secp256k1SignatureAudit

val records = exportedRows.map { row ->
    Secp256k1AuditRecord(
        id = row.id,                 // whatever correlates back to your record — not interpreted
        signature = row.signatureBytes,
        publicKey = row.publicKeyBytes,
        messageDigest = row.digestBytes,
    )
}

val summary = Secp256k1SignatureAudit.auditBatch(records)
println("total=${summary.total} verified=${summary.verified} " +
    "corrupted=${summary.corruptedByPaddingDefect} other=${summary.invalidOther}")

val toRepair = summary.outcomes.filter {
    it.verdict == org.trustweave.kms.util.Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT
}
val toResign = summary.outcomes.filter {
    it.verdict == org.trustweave.kms.util.Secp256k1AuditVerdict.INVALID_OTHER
}
```

If you have a very large corpus and want a cheap first pass before wiring up public keys and
digests for every record, `Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(signature)` takes
only the signature bytes and rules out (with certainty) anything that structurally cannot be a
victim of this bug — see "How the pre-filter works" below. It will not tell you whether a
surviving candidate actually *is* corrupted; only `classify`/`auditBatch` can do that.

### Repairing a corrupted signature (do this first — re-signing is the fallback)

Every `CORRUPTED_BY_PADDING_DEFECT` outcome already carries its own fix:
`Secp256k1AuditOutcome.repairedSignature` is the 64-byte P1363 signature to write back in place of
the corrupted one. It requires **no private key and no re-signing** — write the bytes directly:

```kotlin
for (outcome in toRepair) {
    val fixedBytes = outcome.repairedSignature!! // non-null: verdict is CORRUPTED_BY_PADDING_DEFECT
    writeBackSignature(outcome.id, fixedBytes)    // your own storage update, not part of this tool
}
```

Or, if you don't need the full `auditBatch` summary, call `repair()` directly on one record:

```kotlin
val fixed = Secp256k1SignatureAudit.repair(row.signatureBytes, row.publicKeyBytes, row.digestBytes)
if (fixed != null) {
    writeBackSignature(row.id, fixed)
}
```

**Why this is safe to apply without a second verification pass.** `repair()` (and
`repairedSignature`) never return bytes speculatively — the tool has already run
`Secp256k1Curve.verifySignature` against the returned bytes, this record's own public key, and this
record's own digest, and only returns non-null because that check passed. A non-null return is a
signature already proven to verify, not a plausible-looking guess; see
`Secp256k1SignatureAudit`'s KDoc ("Repair, not just detection") for the proof that the value found
is unconditionally the canonical low-s signature — the same bytes
`EcdsaSignatureCodec.normalizeSecp256k1LowS` would have produced from the original, pre-corruption
high-s signature, bit for bit. `repair()` returning `null` is not a maybe — it means no candidate
verified, so there is nothing to write back and the record needs `INVALID_OTHER`'s treatment
(investigate, then re-sign) instead.

**Re-signing from the underlying data is the fallback**, used only for records that come back
`INVALID_OTHER` after investigation confirms they are genuinely unrecoverable this way (wrong key,
tampered payload, or corruption from something other than this defect) — never as the first
response to a `CORRUPTED_BY_PADDING_DEFECT` verdict.

### Interpreting each outcome

| Verdict | What it means | What to do |
|---|---|---|
| `VALID` | The signature verifies against the given key and digest. | Nothing — this signature was never affected, including any that happen to be in non-canonical high-`s` form (that's a policy convention, not a validity requirement). |
| `CORRUPTED_BY_PADDING_DEFECT` | The signature does not verify, **and** reconstructing what the pre-fix code would have overwritten yields a signature that *does* verify against this key and digest. | **Repair** — write back `outcome.repairedSignature`. See "Repairing a corrupted signature" above and "Confidence" below. |
| `INVALID_OTHER` | The signature does not verify, and it's either outside the numeric range this defect can produce, or reconstruction didn't yield anything that verifies. | Investigate before re-signing — this is not necessarily "safe." It covers wrong keys, tampered payloads, truncated export rows, and any corruption from a cause other than this specific defect. `Secp256k1AuditOutcome.detail` is populated when the record itself was malformed (e.g. wrong-length signature) rather than a well-formed signature that failed to verify. If investigation rules out a repairable cause, this is where **re-signing** applies. |

`verified + corruptedByPaddingDefect + invalidOther == total` always holds.

### Confidence: what a `CORRUPTED_BY_PADDING_DEFECT` verdict does and does not prove

The classifier doesn't stop at "this signature is invalid and its `s` value looks like it's in the
right numeric range." It goes further: it reconstructs the exact byte pattern the pre-fix code
would have overwritten and re-verifies *that*. If the reconstruction verifies, the reconstructed
`(r, s)` pair is **itself a genuine, valid ECDSA signature** over the given key and digest. For an
unrelated failure (wrong key, altered message, coincidental garbage) to also produce a
reconstruction that verifies would require that reconstruction to be a forged valid signature —
as hard as breaking ECDSA outright. So in practice, this verdict is about as conclusive as
signature verification ever gets.

What it does **not** prove: that this *specific* defect, as opposed to some other corruption
mechanism that happens to produce bit-for-bit identical output, is what actually happened. The
tool only ever sees the corrupted signature, never the original signing call, so it cannot
distinguish "matches this defect's fingerprint and a valid reconstruction exists" from a
hypothetical alternative explanation with an identical footprint. Treat the verdict as strong,
practically-conclusive evidence — not a courtroom-grade causal proof of *this exact bug* as
opposed to any conceivable equivalent one.

Likewise, `INVALID_OTHER` only rules out *this* defect. It is not a certificate that the signature
is safe or explainable by something benign — it means this particular fingerprint wasn't found.

**The repair guarantee is stronger than the causal-attribution point above, and does not depend on
it.** The paragraph above is about *why* the bytes are shaped the way they are — a question the
tool cannot answer with certainty because it never observes the original signing call. Whether the
*returned repair is valid* is a different, fully decidable question, and the answer is
unconditional: ECDSA has at most two `s` values that verify for a given `(r, digest, key)` (the
malleability pair `s0`/`n - s0`), every candidate the reconstruction loop can construct is too
small in magnitude to ever equal the high one, and the loop only returns a candidate after
`Secp256k1Curve.verifySignature` confirms it against this record's own key and digest. So a
returned `repairedSignature` is never "probably the fix, contingent on this being the padding
defect" — it is a signature the tool has already proven verifies, full stop. See
`Secp256k1SignatureAudit`'s KDoc for the complete argument.

### How the pre-filter works

The bug only fires when `n - s` (computed from the *original* high-`s` value at signing time) has
a leading zero byte — i.e. needs fewer than 32 bytes. That's a fact about signing time, but it has
a provable, purely-structural consequence for a signature that's already been corrupted and
persisted: `n - s_stored` also always has a leading zero byte, for every persisted victim, with no
exceptions. `canBeVictimOfPaddingDefect` checks exactly that (one subtraction, no key or message
needed). If it returns `false`, the signature is not a victim of this bug, full stop — no
verification required.

It is a **necessary, not sufficient** condition: some genuinely valid, never-corrupted high-`s`
signatures also happen to land in the same numeric range purely by chance, at roughly the same
~1-in-256 rate the bug itself occurs at. A `true` result only means the record is worth spending a
public key, digest, and full verification on — `classify`/`auditBatch` make the actual
determination.

## Limitations to know before you run this

- **You need the public key and message digest for every record you classify.** The pre-filter
  works from signature bytes alone, but a real verdict (verified / corrupted / invalid-other)
  requires reconstructing what was actually signed.
- **Hash algorithm mismatches produce false `INVALID_OTHER` results.** If you pass a SHA-256
  digest for a signature that was actually made over a Keccak-256 digest (or vice versa), a
  genuinely valid signature will come back `INVALID_OTHER`. Confirm which hash your signing path
  used before trusting a large batch of `INVALID_OTHER` results.
- **`CORRUPTED_BY_PADDING_DEFECT` records repair in place; they do not need re-signing.** This
  reverses what earlier drafts of this runbook said. The corrupted `s` value is not the original
  and is not simply lost — it is a deterministic, reversible transformation of the correct one, and
  `repair()` / `Secp256k1AuditOutcome.repairedSignature` recovers it with no private key involved.
  Re-signing is the fallback for `INVALID_OTHER` records only, after investigation confirms the
  cause is not something this tool can repair.
- **The repair is a byte-level substitution you still have to apply.** This tool computes the
  correct bytes; it does not have access to (or opinions about) your storage layer, so writing
  `repairedSignature` back over the corrupted value in place of the original — updating the row,
  re-serializing the credential, whatever your storage requires — is on you. See "Repairing a
  corrupted signature" above.
- **Performance is offline-tool-grade, not hot-path-grade.** Verification uses affine-coordinate
  BigInteger arithmetic, chosen for correctness and auditability over raw speed. Expect roughly
  single-digit milliseconds per record; a corpus in the tens of thousands should complete in well
  under a minute, but this is not meant to run inline on a request path.
