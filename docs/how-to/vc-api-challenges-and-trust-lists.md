---
title: VC API Challenges and Trust Lists
nav_order: 150
parent: How-To Guides
keywords:
  - vc api
  - presentation
  - challenge
  - holder binding
  - trust list
  - lotl
  - tsl
  - eidas
---

# VC API Challenges and Trust Lists

Two verifier-side controls that are easy to get wrong: the one-time presentation challenge of the VC API
server, and signature-verified loading of the EU trust lists.

## Presentation challenge flow (`credentials:vc-api-server`)

`POST /presentations/verify` enforces holder binding and a one-time, server-issued challenge by default.

1. The verifier calls `POST /presentations/challenge`. The server answers `201 Created` with a
   `ChallengeResponse` (`challenge`, and `expiresAt` as an ISO-8601 instant). The challenge is 32 random
   bytes, base64url without padding.
2. The holder signs that challenge into the presentation proof.
3. The verifier posts the presentation to `POST /presentations/verify` with the value in
   `options.challenge`. The server consumes the challenge **before** verifying, so a challenge works exactly
   once: a second request, an expired challenge or one this server never issued is answered with a rejected
   result and never reaches the verifier. With the default policy a request that carries no challenge is
   rejected too.

If challenge issuing is disabled the challenge endpoint answers 404; if the store is full it answers
"unavailable" instead of evicting a live challenge.

Holder binding means every credential's `credentialSubject.id` must equal the presentation holder and the
proof key must belong to the holder.

### Configuring `VcApiVerificationPolicy`

```kotlin
import org.trustweave.credential.vcapi.InMemoryVcApiChallengeStore
import org.trustweave.credential.vcapi.VcApiVerificationPolicy
import kotlin.time.Duration.Companion.minutes

val policy =
    VcApiVerificationPolicy(
        enforceHolderBinding = true,
        challengeStore = InMemoryVcApiChallengeStore(ttl = 2.minutes, capacity = 5_000),
        requireChallenge = true,
    )
// server.withVerificationPolicy(policy) before the server is started
```

| Parameter | Default | Meaning |
|---|---|---|
| `enforceHolderBinding` | `true` | Subject must equal the holder and the key must belong to the holder. Set `false` only for bearer credentials or third-party subjects. |
| `challengeStore` | `InMemoryVcApiChallengeStore()` (5 minutes, capacity 10,000) | Where challenges are issued and consumed. `null` turns the one-time challenge off; a caller-chosen challenge is then only compared with the presentation. |
| `requireChallenge` | `true` | With a store, reject a verify request without a challenge. |

`InMemoryVcApiChallengeStore` is per process. If more than one instance can receive the same transaction,
implement `VcApiChallengeStore` (`issue(): IssuedChallenge` and an atomic, single-use
`consume(challenge: String): Boolean`) on shared storage.

## Loading the EU trust lists (`signatures:trust-lists`)

`VerifiedTrustListLoader` loads the List of Trusted Lists (LoTL) and Member-State Trusted Service Lists (TSLs)
without assuming the XML was verified beforehand:

1. The LoTL signature is verified with `DefaultLotlSignatureVerifier` against the pinned signer certificates
   you pass as `lotlSigningCerts`.
2. Each TSL is verified with `DefaultTslSignatureVerifier` against the certificates published by **its own
   LoTL pointer**. A TSL for a territory the LoTL does not list is rejected.
3. The `SchemeTerritory` declared in a TSL must equal the key it was supplied under.
4. `NextUpdate` is enforced, a list issued in the future (beyond 5 minutes of skew) is refused, and a sequence
   number lower than the one you saw before is a rollback and is refused.

Both verifiers require exactly one enveloped signature over the whole document and judge the signer
certificate at the validation time (the injected `Clock`), not the claimed `SigningTime`.

```kotlin
import org.trustweave.signatures.trustlists.TrustListLoadOptions
import org.trustweave.signatures.trustlists.TrustListLoadResult
import org.trustweave.signatures.trustlists.VerifiedTrustListLoader
import java.security.cert.X509Certificate

fun loadTrustList(
    lotlXml: ByteArray,
    tslXmlByTerritory: Map<String, ByteArray>,
    pinnedLotlSigners: List<X509Certificate>,
    previousLotlSequence: Int?,
    previousTslSequences: Map<String, Int>,
): TrustListLoadResult =
    VerifiedTrustListLoader().load(
        lotlXml = lotlXml,
        tslXmlByTerritory = tslXmlByTerritory,
        lotlSigningCerts = pinnedLotlSigners,
        options =
            TrustListLoadOptions(
                allowStale = false,
                previousLotlSequence = previousLotlSequence,
                previousTslSequences = previousTslSequences,
            ),
    )
```

### Handling the result

`TrustListLoadResult.Loaded` carries the `trustList` and `staleLists`, the labels (`"LoTL"` or a territory
code) accepted only because `allowStale = true`; it is empty in strict mode. `TrustListLoadResult.Rejected`
carries a `TrustListRejection` (`LOTL_SIGNATURE`, `TSL_SIGNATURE`, `MALFORMED`, `TERRITORY_NOT_IN_LOTL`,
`TERRITORY_MISMATCH`, `EXPIRED`, `MISSING_NEXT_UPDATE`, `ROLLBACK`, `ISSUED_IN_FUTURE`), a message and the
offending territory (null for the LoTL).

- **`allowStale`** defaults to `false` because a stale list may omit later withdrawals. Use it only offline or
  in tests, and show the user which lists were stale. It also accepts a list with no `NextUpdate`.
- **Rollback protection is the caller's job.** The loader keeps no state. After each successful load, persist
  `trustList.sequenceNumber` (and each territory's sequence number) and pass them back as
  `previousLotlSequence` and `previousTslSequences` (upper-case ISO code keys).
- **Signature failures** surface as `LotlSignatureValidationResult.Invalid`. `SignerCertificateNotValid`
  means the pinned certificate is outside its validity window (or the signature claims a future signing time):
  rotate the pin or fix the clock. It is not a tampering signal, unlike `SignatureCryptoFailed`. It is also not
  the same as the CAdES/JAdES `SignerCertificateInvalid`, which reports a signer certificate that may not be
  used for signing (a CA certificate or a forbidding key usage).
