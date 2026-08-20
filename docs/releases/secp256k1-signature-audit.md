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

The defect is fixed on `main` as of commit `cbd1e616`. **Upgrading the library does not repair
signatures that were already persisted before the fix.** A corrupted signature is permanently
invalid; there is no way to derive the correct one from it after the fact (the original high-`s`
value that got partially overwritten is gone). Every corrupted signature must be identified and
re-signed from the underlying data.

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

val toResign = summary.outcomes.filter {
    it.verdict == org.trustweave.kms.util.Secp256k1AuditVerdict.CORRUPTED_BY_PADDING_DEFECT
}
```

If you have a very large corpus and want a cheap first pass before wiring up public keys and
digests for every record, `Secp256k1SignatureAudit.canBeVictimOfPaddingDefect(signature)` takes
only the signature bytes and rules out (with certainty) anything that structurally cannot be a
victim of this bug — see "How the pre-filter works" below. It will not tell you whether a
surviving candidate actually *is* corrupted; only `classify`/`auditBatch` can do that.

### Interpreting each outcome

| Verdict | What it means | What to do |
|---|---|---|
| `VALID` | The signature verifies against the given key and digest. | Nothing — this signature was never affected, including any that happen to be in non-canonical high-`s` form (that's a policy convention, not a validity requirement). |
| `CORRUPTED_BY_PADDING_DEFECT` | The signature does not verify, **and** reconstructing what the pre-fix code would have overwritten yields a signature that *does* verify against this key and digest. | Re-sign. See "Confidence" below for exactly what this verdict does and doesn't establish. |
| `INVALID_OTHER` | The signature does not verify, and it's either outside the numeric range this defect can produce, or reconstruction didn't yield anything that verifies. | Investigate before re-signing — this is not necessarily "safe." It covers wrong keys, tampered payloads, truncated export rows, and any corruption from a cause other than this specific defect. `Secp256k1AuditOutcome.detail` is populated when the record itself was malformed (e.g. wrong-length signature) rather than a well-formed signature that failed to verify. |

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
- **A corrupted signature cannot be repaired after the fact.** The original high-`s` value that
  got partially overwritten is gone; there is nothing to recover it from. The only remedy is
  re-signing from the underlying data.
- **Performance is offline-tool-grade, not hot-path-grade.** Verification uses affine-coordinate
  BigInteger arithmetic, chosen for correctness and auditability over raw speed. Expect roughly
  single-digit milliseconds per record; a corpus in the tens of thousands should complete in well
  under a minute, but this is not meant to run inline on a request path.
- **Re-signing is a separate step.** This tool identifies which records are corrupt; it does not
  re-sign anything (it never has access to a private key). Re-sign each `CORRUPTED_BY_PADDING_DEFECT`
  record through your normal issuance/anchoring path once you've upgraded past `cbd1e616`.
