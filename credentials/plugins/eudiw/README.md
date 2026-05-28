# TrustWeave EUDIW Plugin

EU Digital Identity Wallet (EUDIW) Architecture and Reference Framework (ARF) 1.x profile overlays for TrustWeave, providing the EU PID credential model, OID4VCI and OID4VP profile validators, wallet trust evidence schema, and an `eudiw` credential exchange protocol that enforces ARF compliance on top of OpenID4VP flows.

## Overview

This module brings the EUDIW ecosystem requirements into TrustWeave. It does not replace the underlying OID4VCI/OID4VP plugins — it layers a set of profile constraints on top so that issuers, wallets, and verifiers can be checked against the EUDIW *High Assurance Interoperability Profile*.

What you get:

- A type-safe [EuPidCredential](src/main/kotlin/org/trustweave/credential/eudiw/EuPidCredential.kt) data class for the EU Person Identification Data credential (eIDAS 2.0 Annex V).
- Issuance and presentation profile validators ([EuPidIssuanceProfile](src/main/kotlin/org/trustweave/credential/eudiw/EuPidIssuanceProfile.kt), [EudiwOid4VciProfile](src/main/kotlin/org/trustweave/credential/eudiw/EudiwOid4VciProfile.kt), [EudiwOid4VpProfile](src/main/kotlin/org/trustweave/credential/eudiw/EudiwOid4VpProfile.kt)).
- A [WalletTrustEvidence](src/main/kotlin/org/trustweave/credential/eudiw/WalletTrustEvidence.kt) schema for Wallet Provider attestations (eIDAS 2.0 Article 5c).
- An [EudiwExchangeProtocol](src/main/kotlin/org/trustweave/credential/eudiw/exchange/EudiwExchangeProtocol.kt) auto-registered via [EudiwExchangeProtocolProvider](src/main/kotlin/org/trustweave/credential/eudiw/exchange/spi/EudiwExchangeProtocolProvider.kt) under the `eudiw` protocol name.

## Key concepts

- **PID (Person Identification Data)** — The minimum data set defined in eIDAS 2.0 Annex V that every EUDI Wallet must be able to obtain from a national PID Provider. The mandatory claims are `family_name`, `given_name`, `birth_date`, `issuing_authority`, `issuing_country`, `issuance_date`, and `expiry_date`.
- **PCT (Pseudonymous Credential Type) / attribute credentials** — Additional credentials a wallet may hold alongside the PID. This module focuses on the PID; the constants in [EudiwConstants](src/main/kotlin/org/trustweave/credential/eudiw/EudiwConstants.kt) (formats, doctypes, namespaces) are reused for any EUDIW-conformant credential.
- **Wallet attestations** — A wallet instance proves its security posture by presenting a Wallet Trust Evidence signed by a certified Wallet Provider. Verifiers and Issuers consume it to determine the wallet's assurance level (`high`, `substantial`, `low`).
- **ARF profile** — A set of *additional* constraints over OID4VCI/OID4VP: only `vc+sd-jwt` and `mso_mdoc` credential formats; only `ES256`/`ES384`/`ES512`/`EdDSA` signing algorithms; only `direct_post` / `direct_post.jwt` response modes; only the four supported `client_id_scheme` values.

## What's included

### EU PID Credential

[EuPidCredential](src/main/kotlin/org/trustweave/credential/eudiw/EuPidCredential.kt) is a `@Serializable` data class that captures the mandatory and optional claims from the EU PID Rule Book. It is format-neutral — the same object can be encoded as an SD-JWT VC (`vc+sd-jwt`) or as an ISO mDoc (`mso_mdoc`). Field names are mapped to the `eu.europa.ec.eudiw.pid.1` namespace via `@SerialName`.

`EuPidCredential.toClaims()` produces a flat `Map<String, Any>` suitable for handing to an SD-JWT or mDoc encoder. Only non-null fields are included.

### OID4VCI Profile

[EudiwOid4VciProfile](src/main/kotlin/org/trustweave/credential/eudiw/EudiwOid4VciProfile.kt) holds the issuance-side constraints from ARF §6.3 and exposes three validators:

- `validateFormat(format: String)` — rejects anything other than `vc+sd-jwt` or `mso_mdoc`.
- `validateAlgorithm(algorithm: String)` — rejects RSA-based JWAs; only `ES256`/`ES384`/`ES512`/`EdDSA` are allowed.
- `validateCredentialOffer(offerUri: String)` — rejects offers that do not use the `openid-credential-offer://` custom URI scheme.

It also exposes `SUPPORTED_FORMATS`, `SUPPORTED_PROOF_TYPES`, `SUPPORTED_ALGORITHMS`, `REQUIRED_CLIENT_AUTH_METHODS`, and `MAX_OFFER_VALIDITY_SECONDS` (24 hours) as read-only constants.

### OID4VP Profile

[EudiwOid4VpProfile](src/main/kotlin/org/trustweave/credential/eudiw/EudiwOid4VpProfile.kt) covers the presentation-side constraints from ARF §6.4:

- `validateResponseMode(responseMode: String?)` — only `direct_post` and `direct_post.jwt` are accepted.
- `validateClientIdScheme(scheme: String?)` — only `did`, `x509_san_dns`, `x509_san_uri`, and `verifier_attestation` are accepted.

`MAX_NONCE_AGE_SECONDS` (300 seconds) is exposed for wallets that want to enforce nonce freshness.

### Wallet Trust Evidence

[WalletTrustEvidence](src/main/kotlin/org/trustweave/credential/eudiw/WalletTrustEvidence.kt) is the serialized payload a Wallet Provider signs to attest that a particular wallet instance has been certified. It carries:

- `walletInstanceId`, `walletProviderId`, and the bound `walletAttestationKey` (a JWK).
- A `securityLevel` (`HIGH` / `SUBSTANTIAL` / `LOW`, serialized as `"high"` / `"substantial"` / `"low"`).
- The `certificationAuthority` DID, `certificationScheme` (e.g. `"EUDI-CC"`), and optional `certificationRef`.
- A nested [WalletCapabilities](src/main/kotlin/org/trustweave/credential/eudiw/WalletTrustEvidence.kt) record describing supported formats, cryptographic suites, key storage mechanisms, proof types, and user-authentication methods.
- `iat` / `exp` epoch-second timestamps.

### Exchange Protocol

[EudiwExchangeProtocol](src/main/kotlin/org/trustweave/credential/eudiw/exchange/EudiwExchangeProtocol.kt) implements `CredentialExchangeProtocol` under the `ExchangeProtocolName("eudiw")` name. It supports `REQUEST_PROOF` and `PRESENT_PROOF` (issuance operations throw `TrustWeaveException.InvalidOperation` — defer to OID4VCI for actual issuance).

`requestProof()` reads `response_mode` and `client_id_scheme` from `request.options.metadata`, runs them through `EudiwOid4VpProfile`, and throws `EUDIW_PROFILE_VIOLATION` if either is missing or non-conformant. On success it emits an `ExchangeMessageEnvelope` with the negotiated values.

`presentProof()` echoes the presented `VerifiablePresentation` back inside a `ProofPresentation` envelope tagged `profile=eudiw`.

## Usage

### Gradle dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-eudiw:0.6.0")
}
```

The plugin requires `credentials:credential-api`, `did:did-core`, and `credentials:plugins:openid-federation` on the classpath (already transitive).

### Building and validating a PID credential

```kotlin
import org.trustweave.credential.eudiw.EuPidCredential
import org.trustweave.credential.eudiw.EuPidIssuanceProfile

val pid = EuPidCredential(
    familyName = "Smith",
    givenName = "Alice",
    birthDate = "1990-06-15",
    issuingAuthority = "Ministry of Interior",
    issuingCountry = "DE",
    issuanceDate = "2024-01-01",
    expiryDate = "2029-01-01",
    ageOver18 = true,
    nationality = "DE",
)

val claims = pid.toClaims()

val result = EuPidIssuanceProfile.validateClaims(claims)
if (!result.valid) {
    error("PID is not issuable: ${result.violations.joinToString("; ")}")
}
```

The canonical W3C `type` and `@context` arrays for an SD-JWT VC encoding are available as `EuPidIssuanceProfile.PID_VC_TYPES` and `EuPidIssuanceProfile.PID_VC_CONTEXTS`.

### Applying the OID4VCI profile overlay

```kotlin
import org.trustweave.credential.eudiw.EudiwConstants
import org.trustweave.credential.eudiw.EudiwOid4VciProfile

// Before serving a Credential Offer URI from your issuer:
val offer = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Foffer%2F123"
require(EudiwOid4VciProfile.validateCredentialOffer(offer).valid)

// Before configuring credential_configurations_supported on the issuer metadata:
require(EudiwOid4VciProfile.validateFormat(EudiwConstants.CREDENTIAL_FORMAT_SD_JWT_VC).valid)
require(EudiwOid4VciProfile.validateAlgorithm("ES256").valid)
```

### Applying the OID4VP profile overlay via the exchange protocol

```kotlin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.exchange.options.ExchangeOptions
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.request.ProofRequest
import org.trustweave.credential.eudiw.exchange.EudiwExchangeProtocol
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.did.identifiers.Did

fun main() = runBlocking {
    val protocol = EudiwExchangeProtocol()

    val verifierDid = Did("did:web:verifier.example.com")
    val proverDid = Did("did:key:z6Mki...")

    val options = ExchangeOptions(
        metadata = mapOf(
            "response_mode" to JsonPrimitive("direct_post.jwt"),
            "client_id_scheme" to JsonPrimitive("verifier_attestation"),
        ),
    )

    val envelope = protocol.requestProof(
        ProofExchangeRequest.Request(
            protocolName = ExchangeProtocolName("eudiw"),
            verifierDid = verifierDid,
            proverDid = proverDid,
            proofRequest = ProofRequest(
                name = "EU PID verification",
                requestedAttributes = emptyMap(),
            ),
            options = options,
        ),
    )

    println("Sent EUDIW proof request: ${envelope.messageData}")
}
```

If the metadata violates the profile (missing or wrong `response_mode`, unknown `client_id_scheme`), `requestProof()` throws `TrustWeaveException.InvalidOperation` with code `EUDIW_PROFILE_VIOLATION` and a `violations` entry in its `context` map.

### Wallet trust evidence

```kotlin
import kotlinx.serialization.json.Json
import org.trustweave.credential.eudiw.EudiwConstants
import org.trustweave.credential.eudiw.WalletCapabilities
import org.trustweave.credential.eudiw.WalletSecurityLevel
import org.trustweave.credential.eudiw.WalletTrustEvidence

val wte = WalletTrustEvidence(
    walletInstanceId = "wallet-instance-001",
    walletProviderId = "did:web:wallet.example.eu",
    walletAttestationKey = """{"kty":"EC","crv":"P-256","x":"...","y":"..."}""",
    securityLevel = WalletSecurityLevel.HIGH,
    certificationAuthority = "did:web:ca.example.eu",
    certificationScheme = "EUDI-CC",
    walletCapabilities = WalletCapabilities(
        formats = listOf(
            EudiwConstants.CREDENTIAL_FORMAT_SD_JWT_VC,
            EudiwConstants.CREDENTIAL_FORMAT_MSO_MDOC,
        ),
        cryptographicSuites = listOf("ES256", "EdDSA"),
        keyStorage = listOf("hardware_key_storage"),
        proofTypes = listOf("jwt", "cwt"),
        userAuthentication = listOf("biometric", "system_pin"),
    ),
    issuedAt = 1_700_000_000L,
    expiresAt = 1_800_000_000L,
)

val json = Json { encodeDefaults = false }.encodeToString(WalletTrustEvidence.serializer(), wte)
```

The WTE schema is transport-neutral. Wrap it in a signed JWT or CWT with whatever KMS your Wallet Provider uses.

## SPI auto-registration

[EudiwExchangeProtocolProvider](src/main/kotlin/org/trustweave/credential/eudiw/exchange/spi/EudiwExchangeProtocolProvider.kt) is registered in [META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider](src/main/resources/META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider). When this JAR is on the classpath, the TrustWeave credential service discovers it via `ServiceLoader` and resolves `ExchangeProtocolName("eudiw")` to a fresh `EudiwExchangeProtocol` instance. No manual wiring is required.

## Limitations

- The `EudiwExchangeProtocol` only enforces ARF profile validation; the actual OID4VP transport (Authorization Request signing, `direct_post` HTTP round-trip, response encryption) is delegated to the OID4VP plugin. Use this protocol *together with* `credentials:plugins:oidc4vp` (and `credentials:plugins:oidc4vci` for issuance) or your verifier framework, not as a replacement.
- `EuPidCredential` covers the most commonly used PID claims but is not exhaustive — exotic claims from the EU PID Rule Book (e.g. `family_name_birth`, `resident_street`) are present as constants in `EudiwConstants` but not yet typed as `EuPidCredential` fields. Add them to a custom claims map if you need them.
- Issuance operations (`offer`, `request`, `issue`) on `EudiwExchangeProtocol` are intentionally unsupported and throw `OPERATION_NOT_SUPPORTED`. Use an OID4VCI-capable protocol for issuance and validate the offer/format with `EudiwOid4VciProfile`.
- Wallet trust evidence is provided as a schema only — signing, verification, and trust-list lookup are out of scope for this module.
- `mso_mdoc` encoding/decoding is not provided here; pair this plugin with an mdoc encoder when you need ISO 18013-5 output.

## References

- [EUDIW Architecture and Reference Framework](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework)
- [EU PID Rule Book](https://github.com/eu-digital-identity-wallet/eudi-doc-pid-rule-book)
- [eIDAS 2.0 Regulation (EU) 2024/1183](https://eur-lex.europa.eu/eli/reg/2024/1183/oj) — Annex V (PID minimum data set), Article 5c (Wallet Trust Evidence)
- [OpenID for Verifiable Credential Issuance (OID4VCI)](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OpenID for Verifiable Presentations (OID4VP)](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [ISO/IEC 18013-5 — Mobile driving licence (mDL)](https://www.iso.org/standard/69084.html)
