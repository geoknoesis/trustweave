# TrustWeave BBS-2023 Proof Engine

W3C Data Integrity BBS Cryptosuite 2023 proof engine for TrustWeave, providing selective-disclosure
verifiable credentials over the BLS12-381 curve.

## Overview

This module implements the `ProofEngine` SPI for the BBS Cryptosuite 2023 (`bbs-2023`) so that
TrustWeave can issue and verify W3C Verifiable Credentials whose proofs are BBS+ signatures, and
holders can derive zero-knowledge presentations that disclose only a subset of the original claims.

The engine plugs into the standard TrustWeave credential pipeline:

- Issuance produces a `CredentialProof.LinkedDataProof` of type `DataIntegrityProof` with the
  `cryptosuite` property set to `bbs-2023`.
- Selective-disclosure presentations are derived with the `bbs-2023-derived` cryptosuite tag and
  carry only the disclosed claims in the credential subject.

Source files:

- [`Bbs2023ProofEngine.kt`](src/main/kotlin/org/trustweave/credential/bbs/Bbs2023ProofEngine.kt)
- [`Bbs2023ProofEngineProvider.kt`](src/main/kotlin/org/trustweave/credential/bbs/Bbs2023ProofEngineProvider.kt)
- [`BbsCryptoSuite.kt`](src/main/kotlin/org/trustweave/credential/bbs/BbsCryptoSuite.kt)
- [`Bls12381KeyPair.kt`](src/main/kotlin/org/trustweave/credential/bbs/Bls12381KeyPair.kt)

## Key Concepts

**BBS+ signatures** sign a list of messages (the credential claims) with a single short signature.
Anyone holding the signature and the public key can derive a new proof that reveals only a chosen
subset of those messages while still being verifiable.

**Selective disclosure** is implemented by encoding each credential claim as a `key=value` message
(claims are sorted by key for determinism). When the holder creates a presentation, the engine
calls `BbsCryptoSuite.deriveProof` over the disclosed indices and returns a new
`LinkedDataProof` whose `cryptosuite` is `bbs-2023-derived`.

**Derived proofs** never carry the original BBS+ signature — they are fresh randomised commitments
bound to the public key and the disclosed messages.

## Supported Algorithm

| Property        | Value                                                |
|-----------------|------------------------------------------------------|
| Format          | `ProofSuiteId.BBS_2023` (`bbs-2023`)                 |
| Format name     | `W3C Data Integrity BBS Cryptosuite 2023`            |
| Curve           | BLS12-381 G2                                         |
| Public key size | 96 bytes                                             |
| Secret key size | 32 bytes                                             |
| Signature size  | 96 bytes (`BbsCryptoSuite.SIGNATURE_SIZE`)           |
| Derived proof   | 208 bytes (`BbsCryptoSuite.DERIVED_PROOF_SIZE`)      |

Engine capabilities exposed via `ProofEngineCapabilities`:

- `selectiveDisclosure = true`
- `zeroKnowledge = true`
- `revocation = true`
- `presentation = true`
- `predicates = false`

## Usage

### Adding the Dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-bbs:0.6.0")
}
```

### Key Generation

`BbsCryptoSuite.generateKeyPair` produces a `Bls12381KeyPair` carrying the 96-byte public key, the
32-byte secret key, and a caller-supplied `keyId`.

```kotlin
import org.trustweave.credential.bbs.BbsCryptoSuite

val keyPair = BbsCryptoSuite.generateKeyPair("urn:example:bbs:issuer-key-1")

println("Public key (base64url): ${keyPair.publicKeyBase64Url}")
println("Key id: ${keyPair.keyId}")
```

### Issuance

Wire the key pair into a `ProofEngineConfig` and call `issue`. If no key pair is configured the
engine generates an ephemeral one on first use and logs a warning (handy for tests, not for
production).

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.bbs.Bbs2023ProofEngine
import org.trustweave.credential.bbs.BbsCryptoSuite
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.spi.proof.ProofEngineConfig

fun main() = runBlocking {
    val issuerDid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    val subjectDid = "did:key:z6MkiTBz1ymuepAQ4HEHYSF1H8quG5GLVVQR3djdX3mDooWp"

    val keyPair = BbsCryptoSuite.generateKeyPair("urn:example:bbs:issuer-key-1")
    val engine = Bbs2023ProofEngine(
        ProofEngineConfig(properties = mapOf("keyPair" to keyPair)),
    )

    val request = IssuanceRequest(
        format = ProofSuiteId.BBS_2023,
        issuer = Issuer.from(issuerDid),
        credentialSubject = CredentialSubject.fromIri(
            iri = subjectDid,
            claims = mapOf(
                "givenName" to JsonPrimitive("Alice"),
                "familyName" to JsonPrimitive("Smith"),
                "degree" to JsonPrimitive("BachelorOfScience"),
                "gpa" to JsonPrimitive("3.8"),
            ),
        ),
        type = listOf(
            CredentialType.VerifiableCredential,
            CredentialType.Custom("UniversityDegreeCredential"),
        ),
    )

    val credential = engine.issue(request)
    println("Issued VC id=${credential.id?.value}")
}
```

### Verification

`verify` returns a `VerificationResult` sealed type. Use `assertIs` / `when` to branch.

```kotlin
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult

val result = engine.verify(credential, VerificationOptions())
when (result) {
    is VerificationResult.Valid -> println("OK from ${result.issuerIri.value}")
    is VerificationResult.Invalid -> println(result.allErrors.joinToString())
}
```

The engine recovers the BLS12-381 public key from the proof's `verificationMethod` URL. Two forms
are accepted:

1. A fragment matching the engine's active key pair (`...#bbs-<base64url-pk>` written by `issue`).
2. Any `#bbs-<base64url>` or raw `#<base64url>` fragment that decodes to a 96-byte key.

### Selective Disclosure / Proof Derivation

`createPresentation` takes a `PresentationRequest.disclosedClaims` set; passing `null` discloses
everything. Non-disclosed claims are stripped from the presented credential's subject and the
proof is replaced with a derived ZK proof (`cryptosuite = "bbs-2023-derived"`).

```kotlin
import org.trustweave.credential.requests.PresentationRequest

val presentation = engine.createPresentation(
    credentials = listOf(credential),
    request = PresentationRequest(disclosedClaims = setOf("givenName", "degree")),
)

val disclosed = presentation.verifiableCredential.first().credentialSubject.claims.keys
println("Disclosed: $disclosed") // [givenName, degree]
```

You can also drive `BbsCryptoSuite` directly for lower-level work:

```kotlin
import org.trustweave.credential.bbs.BbsCryptoSuite

val messages = listOf("name=Alice".toByteArray(), "age=30".toByteArray())
val signature = BbsCryptoSuite.sign(
    secretKey = keyPair.secretKeyBytes!!,
    publicKey = keyPair.publicKeyBytes,
    messages = messages,
)

val derived = BbsCryptoSuite.deriveProof(
    signature = signature,
    publicKey = keyPair.publicKeyBytes,
    messages = messages,
    disclosed = setOf(0), // disclose only "name=Alice"
)
val ok = BbsCryptoSuite.verifyDerivedProof(
    publicKey = keyPair.publicKeyBytes,
    derivedProof = derived,
    disclosedMessages = derived.disclosedMessages,
)
```

## SPI Auto-Registration

`Bbs2023ProofEngineProvider` is registered via
[`META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider`](src/main/resources/META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider),
so simply having the JAR on the classpath makes the engine discoverable by TrustWeave's
`ProofEngineRegistry`. The provider exposes `name = "bbs-2023"` and
`supportedFormatIds = [ProofSuiteId.BBS_2023]`, and accepts the following options on `create`:

| Key            | Type                  | Description                                          |
|----------------|-----------------------|------------------------------------------------------|
| `keyPair`      | `Bls12381KeyPair`     | BLS12-381 key pair used for signing and derivation.  |
| `didResolver`  | `DidResolver`         | Resolver consulted during verification (optional).   |

```kotlin
import org.trustweave.credential.bbs.Bbs2023ProofEngineProvider
import org.trustweave.credential.bbs.BbsCryptoSuite

val provider = Bbs2023ProofEngineProvider()
val engine = provider.create(mapOf("keyPair" to BbsCryptoSuite.generateKeyPair("k1")))!!
```

## Limitations

- BouncyCastle 1.84 does not expose a high-level BBS+/pairing API. `BbsCryptoSuite` is therefore a
  **spec-aligned HMAC-based emulation**: wire formats (96-byte signatures, 96-byte public keys,
  208-byte derived proofs) match the specification, but the construction is cryptographically
  weaker than real BBS+. Swap in a validated BLS12-381 / BBS+ JVM library before production use.
- Claim canonicalisation is a simple sorted `key=value` UTF-8 encoding — not full JSON-LD
  canonicalisation. Two credentials with semantically equivalent but textually different claim
  values will not produce the same signature.
- `predicates = false`: range/comparison predicates over disclosed claims are not yet supported.
- Public-key resolution falls back to a base64url fragment embedded in `verificationMethod`; a
  full DID-document resolver path is not wired in this engine.

## References

- [W3C Data Integrity BBS Cryptosuites](https://w3c.github.io/vc-di-bbs/)
- [BBS Signatures (IETF draft)](https://datatracker.ietf.org/doc/draft-irtf-cfrg-bbs-signatures/)
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model/)
