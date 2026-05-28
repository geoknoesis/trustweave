# TrustWeave mDL / mDoc Plugin

ISO/IEC 18013-5 Mobile Driving Licence (mDL) and mDoc credential support for TrustWeave.

## Overview

This module provides a [`ProofEngine`](../../credential-api/src/main/kotlin/org/trustweave/credential/spi/proof/ProofEngine.kt)
implementation that issues and verifies ISO/IEC 18013-5 mobile documents (mdocs)
using CBOR encoding and COSE_Sign1 signatures (RFC 8152). The engine is registered
under the [`ProofSuiteId.MDOC`](../../credential-api/src/main/kotlin/org/trustweave/credential/format/ProofSuiteId.kt)
proof format (`mso_mdoc`) and is auto-discovered through Java SPI.

mDoc credentials are signed by the issuer over a Mobile Security Object (MSO) that
records SHA-256 digests of each individually-salted claim. This structure enables
selective disclosure: a holder can present a subset of claims while the verifier
still validates each disclosed claim against the original issuer signature.

## Key concepts

The data model lives in [`MdocModels.kt`](src/main/kotlin/org/trustweave/credential/mdl/model/MdocModels.kt).

- **`MobileDocument`** — top-level mdoc; carries the `docType`, the
  `IssuerSigned` block, and an optional `DeviceSigned` block produced at
  presentation time.
- **`IssuerSigned`** — namespace map of `IssuerSignedItem` claims plus the
  CBOR-encoded `issuerAuth` (a COSE_Sign1 over the MSO).
- **`IssuerSignedItem`** — a single claim entry containing a `digestId`, a
  16-byte random salt, the `elementIdentifier`, and the `elementValue`. The
  salt is what makes per-claim selective disclosure possible.
- **`MobileSecurityObject` (MSO)** — the signed payload. Holds the
  `valueDigests` table (namespace -> digestId -> SHA-256 of the encoded
  `IssuerSignedItem`), the `deviceKeyInfo` for holder binding, the `docType`,
  and a `ValidityInfo` window.
- **`DeviceSigned` / `DeviceAuth`** — present in presentations only; carries
  a COSE_Sign1 device signature (or COSE_Mac0) over a session-bound
  `DeviceAuthentication` structure.

CBOR encoding and digesting is implemented in
[`MdocCbor.kt`](src/main/kotlin/org/trustweave/credential/mdl/engine/MdocCbor.kt)
using Peter Occil's `cbor-java` library, which handles tagged values and
indefinite-length items as required by ISO 18013-5. COSE_Sign1 produce/verify
lives in [`MdocCoseSign.kt`](src/main/kotlin/org/trustweave/credential/mdl/engine/MdocCoseSign.kt)
and uses Bouncy Castle for ECDSA and Ed25519 primitives.

The wrapping `VerifiableCredential` carries the mdoc bytes inside a
[`CredentialProof.MdocProof`](../../credential-api/src/main/kotlin/org/trustweave/credential/model/vc/CredentialProof.kt),
whose `deviceResponse` field holds the CBOR-encoded `MobileDocument` and whose
`docType` mirrors the mdoc document type.

## Supported namespaces

Constants are defined in [`MdlNamespace.kt`](src/main/kotlin/org/trustweave/credential/mdl/MdlNamespace.kt):

| Constant | Value | Purpose |
|---|---|---|
| `MdlNamespace.ISO_18013_5_1` | `org.iso.18013.5.1` | ISO mDL claim namespace (default) |
| `MdlNamespace.AAMVA` | `org.iso.18013.5.1.aamva` | AAMVA mDL extensions |
| `MdlNamespace.EUDIW_PID` | `eu.europa.ec.eudiw.pid.1` | EU Digital Identity Wallet Person Identification Data |
| `MdlNamespace.MDL_DOC_TYPE` | `org.iso.18013.5.1.mDL` | Mobile driving licence docType |
| `MdlNamespace.EU_PID_DOC_TYPE` | `eu.europa.ec.eudiw.pid.1` | EU PID docType |

Supported signing algorithms (selected via the `algorithm` proof option):
`P-256` / `ES256`, `P-384` / `ES384`, `P-521` / `ES512`, and `Ed25519` / `EdDSA`.
The default is `P-256`.

## Usage

### Gradle dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-mdl:0.6.0")
}
```

### Issuing an mDoc credential

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.mdl.MdlNamespace
import org.trustweave.credential.mdl.engine.MdocProofEngine
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService

fun main() = runBlocking {
    val kms = InMemoryKeyManagementService()
    val engine = MdocProofEngine(kms)

    val keyResult = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "issuer-key"))
    val issuerKeyId = (keyResult as GenerateKeyResult.Success).keyHandle.id

    val issuerDid = Did("did:example:issuer")
    val holderDid = Did("did:example:holder")

    val request = IssuanceRequest(
        format = ProofSuiteId.MDOC,
        issuer = Issuer.fromDid(issuerDid),
        issuerKeyId = VerificationMethodId(issuerDid, issuerKeyId),
        credentialSubject = CredentialSubject.fromDid(
            holderDid,
            claims = mapOf(
                "family_name" to JsonPrimitive("Smith"),
                "given_name" to JsonPrimitive("John"),
                "age_over_18" to JsonPrimitive(true)
            )
        ),
        type = listOf(
            CredentialType.fromString("VerifiableCredential"),
            CredentialType.fromString(MdlNamespace.MDL_DOC_TYPE)
        ),
        proofOptions = ProofOptions(
            additionalOptions = mapOf(
                "namespace" to MdlNamespace.ISO_18013_5_1,
                "docType" to MdlNamespace.MDL_DOC_TYPE,
                "algorithm" to "Ed25519"
                // Optional: "validUntilSeconds" to 365L * 24 * 3600
                // Optional: "deviceKey" to <CBOR-encoded COSE_Key bytes>
            )
        )
    )

    val credential = engine.issue(request)
    println("Issued mdoc with docType: ${(credential.proof as org.trustweave.credential.model.vc.CredentialProof.MdocProof).docType}")
}
```

### Verifying an mDoc credential

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult

fun verify(engine: MdocProofEngine, credential: org.trustweave.credential.model.vc.VerifiableCredential) = runBlocking {
    when (val result = engine.verify(credential, VerificationOptions())) {
        is VerificationResult.Valid ->
            println("Valid mdoc from ${result.issuerIri}, expires at ${result.expiresAt}")
        is VerificationResult.Invalid.Expired ->
            println("Expired at ${result.expiredAt}")
        is VerificationResult.Invalid.NotYetValid ->
            println("Not yet valid until ${result.validFrom}")
        is VerificationResult.Invalid ->
            println("Invalid: ${result.allErrors.joinToString()}")
    }
}
```

To enable device authentication verification (ISO 18013-5 section 9.1.3), pass
the reader-side `SessionTranscript` CBOR bytes via
`VerificationOptions.additionalOptions["sessionTranscript"]`. If omitted, the
engine verifies the issuer signature only.

### Encoding and decoding CBOR (module-internal)

The CBOR helpers in [`MdocCbor`](src/main/kotlin/org/trustweave/credential/mdl/engine/MdocCbor.kt)
are declared `internal object` and are used by `MdocProofEngine` to encode and
decode `IssuerSignedItem`, `MobileSecurityObject`, and `MobileDocument` values.
They are not part of the public API; callers outside this module should drive
encoding through `MdocProofEngine.issue` / `verify` / `createPresentation` and
read the resulting bytes from `CredentialProof.MdocProof.deviceResponse`.

### Selective disclosure during presentation

`createPresentation` filters the namespaces in the encoded `MobileDocument`
without re-signing the issuer COSE_Sign1 — digests for omitted items are
simply absent from the disclosed view, while the issuer signature still
binds the full `valueDigests` table.

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.requests.PresentationRequest

runBlocking {
    val presentation = engine.createPresentation(
        credentials = listOf(credential),
        request = PresentationRequest(disclosedClaims = setOf("family_name"))
    )
}
```

## SPI auto-registration

The plugin registers itself through Java's `ServiceLoader` via
[`MdocProofEngineProvider`](src/main/kotlin/org/trustweave/credential/mdl/spi/MdocProofEngineProvider.kt)
and the service descriptor at
[`META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider`](src/main/resources/META-INF/services/org.trustweave.credential.spi.proof.ProofEngineProvider).

Once this artifact is on the classpath, the provider is discovered automatically
and can be constructed by passing a `KeyManagementService` in the options map:

```kotlin
val provider = MdocProofEngineProvider()
val engine = provider.create(mapOf("kms" to kms))
```

The provider's `supportedFormatIds` is `[ProofSuiteId.MDOC]` and its `name`
is `mso_mdoc` — matching the OID4VCI / OID4VP format identifier.

## Limitations

The current implementation focuses on issuance and verification of the issuer
side of mdocs. The following are not yet supported and will be added in
subsequent releases:

- **Holder-side device signing.** `MdocProofEngine.issue` writes the
  `deviceKey` into the MSO when provided, and `verify` can validate a
  `DeviceAuth` signature given a `sessionTranscript`, but there is no
  helper for *producing* the `DeviceSigned` block. Callers must construct
  and sign the `DeviceAuthentication` CBOR themselves.
- **COSE_Mac0 device authentication.** The model has a `deviceMac` slot
  but verification only handles `deviceSignature` (COSE_Sign1).
- **Proximity / OID4VP transport.** The engine produces and consumes mdoc
  bytes; the BLE, NFC, Wi-Fi Aware, and OID4VP transports defined by ISO
  18013-5 and OpenID4VP are out of scope for this module.
- **Age attestation and other derived predicates.** The engine surfaces
  `age_over_NN` claims if the issuer includes them, but it does not derive
  predicates from a date of birth.
- **AAMVA-specific claim validation.** `MdlNamespace.AAMVA` is declared
  but the engine performs no AAMVA-specific structural checks.
- **X.509 issuer certificate chains.** Issuer trust is delegated to the
  `KeyManagementService` (the verifier key is resolved from the credential's
  issuer IRI); the engine does not parse `x5chain` headers from the
  COSE_Sign1 unprotected map.
- **Status list lookup.** Revocation is delegated to an optional
  `CredentialStatusChecker` and only runs when the credential carries a
  `credentialStatus`.

## References

- ISO/IEC 18013-5:2021 — Personal identification: ISO-compliant driving licence,
  Part 5: Mobile driving licence (mDL) application — <https://www.iso.org/standard/69084.html>
- RFC 8152 — CBOR Object Signing and Encryption (COSE) — <https://www.rfc-editor.org/rfc/rfc8152>
- RFC 8949 — Concise Binary Object Representation (CBOR) — <https://www.rfc-editor.org/rfc/rfc8949>
- OpenID for Verifiable Presentations (OID4VP) `mso_mdoc` format
