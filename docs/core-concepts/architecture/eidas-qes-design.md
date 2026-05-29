---
redirect_from:
  - /architecture/eidas-qes-design/
parent: Architecture
grand_parent: Core Concepts
---

# eIDAS QES Design (TrustWeave 0.7.x)

**Status:** This is a forward-looking design document for adding eIDAS 2.0 qualified-electronic-signature
(QES) support to TrustWeave. None of the modules, packages, types, or wiring described below exist
in the repository yet. The document is intended to guide the implementation work targeted for the
TrustWeave 0.7.x release line. References to existing code (e.g. `KeyManagementService`,
`CredentialProof`, the `credentials/credential-api` engine registry) have been validated against
the current `main` branch and link to the actual sources where applicable.

---

## 1. Goals

- **Native eIDAS 2.0 qualified signatures.** Produce signatures that, by construction, satisfy the
  Advanced Electronic Signature (AdES) requirements of eIDAS 2.0 (EU 2024/1183). When the signing
  key is bound to a Qualified Signature Creation Device (QSCD) and the signing certificate is
  issued by a Qualified Trust Service Provider (QTSP) listed on a Member-State Trusted List, the
  same code path yields a Qualified Electronic Signature (QES).
- **JAdES as the primary format.** ETSI TS 119 182-1 (JAdES) is the JSON-native AdES profile and
  is the only profile that wraps cleanly around the credential formats TrustWeave already issues
  (W3C VC-JWT, IETF SD-JWT VC, OID4VCI JWT VC). Implementing JAdES first lets a single signature
  envelope cover every JWT/JSON-LD-shaped credential the platform produces.
- **PKCS#11 binding for QSCDs.** A new `kms:plugins:pkcs11` module implements the existing
  [`KeyManagementService`](../../kms/kms-core/src/main/kotlin/org/trustweave/kms/KeyManagementService.kt)
  SPI on top of `sun.security.pkcs11.SunPKCS11`. Any standards-conformant QSCD or HSM is then
  usable as a signing backend without device-specific code in the credentials or signatures
  domains.
- **LoTL/TSL-based trust resolution.** Discovery of qualified-TSP certificates happens against the
  EU List of Trusted Lists (LoTL) and per-Member-State Trusted Service Lists (TSLs) as defined by
  ETSI TS 119 612, so that the trust anchor for a QES verification is the official European trust
  graph, not a hand-curated allow-list.
- **No commercial dependencies for the QES code path.** All new modules build only on Bouncy
  Castle, Nimbus JOSE+JWT, the JDK `SunPKCS11` provider, and the existing TrustWeave SPIs.

## 2. Non-goals (MVP)

- CAdES (ETSI EN 319 122-1), XAdES (ETSI EN 319 132-1), PAdES (ETSI EN 319 142-1). Out of scope —
  the MVP targets JSON payloads only.
- The B-LT and B-LTA JAdES profiles. Long-term-validation material (validation data references,
  archival timestamps) is deferred until B-B + B-T are stable.
- Full conformance to ETSI EN 319 102-1 *Procedures for Creation and Validation of AdES Digital
  Signatures*. The MVP provides cryptographic validation and trust-anchor resolution; the full
  validation procedure (signature policy evaluation, time-of-signing certificate validity,
  PoE-driven status determination) is a follow-up.
- Self-verification of the LoTL XAdES signature. The MVP treats the LoTL XML as a pre-verified
  trust-anchor input supplied by the operator.
- Conversion of any module to Kotlin Multiplatform. TrustWeave remains JVM-only (Kotlin 2.3.21,
  JVM 21) for this work.

## 3. Standards in scope

| Standard | Version / Date | What TrustWeave needs from it |
|---|---|---|
| eIDAS 2.0 — Regulation (EU) 2024/1183 | OJ L 2024/1183, 30 Apr 2024 | Legal definitions (AES, QES, QSCD, QTSP); Annex VII (qualified certificate content); justifies the trust-list-driven validation model. |
| ETSI EN 319 122-1 | v1.3.1 (2023-06) | CAdES base — referenced only because JAdES uses several CAdES attribute semantics (`signing-certificate-v2`, signing-time). Out of scope for code. |
| ETSI EN 319 132-1 | v1.3.1 (2023-06) | XAdES — out of scope for the QES code path, but the LoTL XML uses XAdES signatures (deferred LoTL self-verification will depend on this). |
| **ETSI TS 119 182-1 (JAdES)** | v1.2.1 (2023-06) | **Primary.** Defines JAdES header parameters (`sigT`, `x5t#S256`, `x5c`, `sigPSt`, `sigPId`, `etsiU`, `xVals`, `rVals`, `arcTst`), the B-B and B-T profiles, and the canonical serialization rules used for both signing input and verification. |
| ETSI EN 319 102-1 | v1.4.1 (2023-06) | Validation procedure model. MVP implements only the building-block "Basic Signature Validation" path; "Validation with Time" and "Validation with Time and Validation Data" are deferred. |
| ETSI TS 119 612 | v2.3.1 (2024-05) | Trusted List format. Provides the schema for LoTL and per-MS TSL XML — used by `trust-lists` to parse `TrustServiceProvider`, `TSPService`, and `ServiceDigitalIdentity` structures. |
| RFC 3161 | Aug 2001 | Time-Stamp Protocol. Wire format for the `TimeStampReq` and `TimeStampResp` messages exchanged with a TSA. Required by JAdES B-T. |
| RFC 5652 | Sep 2009 | CMS — underlying ASN.1 format of `TimeStampToken` returned inside a `TimeStampResp`. We do not implement CMS ourselves; Bouncy Castle's `org.bouncycastle.tsp` and `org.bouncycastle.cms` packages handle parsing. |
| PKCS#11 v2.40 (with v3.0 awareness) | OASIS, 2015 / 2020 | Cryptoki interface used through `SunPKCS11`. We standardize on a v2.40 mechanism subset (CKM_ECDSA, CKM_SHA256/384/512_RSA_PKCS_PSS, CKM_ECDSA_SHA256 etc.). |
| EN 419 221-5 | v1.0 (2018-04) | Protection Profile for cryptographic modules of trust services — informative; constrains which HSMs qualify as QSCDs. Not implemented, but the design must not block operators from binding to such devices. |
| W3C XML Signature Syntax | Second Edition (2008) | Only relevant for eventually verifying the LoTL signature. The MVP treats LoTL as pre-verified input, so this is touched only minimally (XML namespace handling during TSL parsing). |

## 4. Module layout

The QES work introduces a sixth top-level domain, `signatures/`, alongside the existing five
(`did/`, `wallet/`, `kms/`, `anchors/`, `credentials/`).

```
signatures/                          [NEW DOMAIN]
  tsa-core/                          RFC 3161 TSA client + TimeStampToken model
  trust-lists/                       LoTL + TSL parser, trust-anchor resolver
  jades/                             JAdES B-B and B-T engines + verifier

kms/plugins/pkcs11/                  [NEW]  QSCD / HSM binding via SunPKCS11

credentials/credential-api/          [MODIFIED]
                                     - new CredentialProof.JAdES variant
                                     - new JAdESProofEngine in the engine registry
                                     - IssuanceBuilder / VerificationBuilder DSL hooks
```

### 4.1 `signatures:tsa-core`

- **Purpose:** Issue RFC 3161 time-stamp requests against any RFC 3161 TSA endpoint, return a
  validated `TimeStampToken` value object. Pure HTTP + ASN.1.
- **Public package:** `org.trustweave.signatures.tsa`
- **Key dependencies:** Bouncy Castle (`bcprov-jdk18on`, `bcpkix-jdk18on` — already in classpath
  via `gradle/libs.versions.toml`), OkHttp (already in classpath), no Nimbus.

### 4.2 `signatures:trust-lists`

- **Purpose:** Parse the EU LoTL and the per-Member-State TSLs that the LoTL references. Build
  an in-memory trust graph of QTSPs and their qualified service certificates. Expose a
  `TrustAnchorResolver` that, given a signing-certificate's issuer + key identifier, answers
  whether the certificate chains to a trusted QTSP and what qualifier types apply
  (`http://uri.etsi.org/TrstSvc/Svctype/CA/QC`,
  `http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCWithSSCD`, etc.).
- **Public package:** `org.trustweave.signatures.trustlists`
- **Key dependencies:** JDK XML (`javax.xml.parsers`, `javax.xml.namespace`), Bouncy Castle for
  X.509 parsing. No JAXB — the parser is hand-rolled against the TS 119 612 schema using
  StAX/DOM, to keep the artifact small and Java 21 friendly.

### 4.3 `signatures:jades`

- **Purpose:** Build and verify JAdES B-B and B-T signatures. Composes `kms:kms-core`
  (for signing operations), `signatures:tsa-core` (for B-T's signature-time-stamp), and
  `signatures:trust-lists` (for signer-certificate validation against the trust graph).
- **Public package:** `org.trustweave.signatures.jades`
- **Key dependencies:** Nimbus JOSE+JWT for JSON Serialization framing and JWS primitives,
  Bouncy Castle for certificate path validation, kotlinx.serialization for header value
  modelling.

### 4.4 `kms:plugins:pkcs11`

- **Purpose:** Implement the existing
  [`KeyManagementService`](../../kms/kms-core/src/main/kotlin/org/trustweave/kms/KeyManagementService.kt)
  SPI on top of a PKCS#11 device, using `sun.security.pkcs11.SunPKCS11`. Slot + token + PIN are
  externally configured. The implementation is generic — no device-specific code paths — so any
  standards-conformant QSCD (SafeNet Luna, Utimaco CryptoServer, Thales ProtectServer,
  SoftHSM2 for testing, YubiHSM 2 for development) plugs in without changes to JAdES or
  credentials code.
- **Public package:** `org.trustweave.kms.pkcs11`
- **Key dependencies:** JDK only (`sun.security.pkcs11.SunPKCS11` reflective load — works on
  every JDK 17+ build that bundles the provider, which includes Temurin, Liberica, Zulu, and
  Oracle JDK).

### 4.5 `credentials/credential-api` modifications

- New `CredentialProof.JAdES` sealed-class variant (see
  [`CredentialProof.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/model/vc/CredentialProof.kt)
  for the existing variants).
- New `JAdESProofEngine` implementing
  [`ProofEngine`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/spi/proof/ProofEngine.kt),
  registered via the same SPI route used by `VcLdProofEngine` and `SdJwtProofEngine`.
- New `ProofSuiteId.JADES` enum entry alongside the existing values in
  [`ProofSuiteId.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/format/ProofSuiteId.kt).
- DSL hooks on
  [`IssuanceRequestBuilder`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/requests/IssuanceRequestBuilder.kt)
  and `VerificationBuilder`.

## 5. Public API surfaces

### 5.1 `signatures:tsa-core`

```kotlin
package org.trustweave.signatures.tsa

import kotlinx.datetime.Instant

/**
 * RFC 3161 Time-Stamp Authority client.
 *
 * One instance per TSA endpoint. Implementations MUST be thread-safe.
 * MVP keeps this small: synchronous request/response, one digest algorithm per call.
 */
interface TsaClient {

    /**
     * Request a time-stamp token for the given message digest.
     *
     * @param digest       The digest bytes (already hashed; not the cleartext).
     * @param hashAlgorithm The hash algorithm that produced [digest] (must match the digest length).
     * @param nonce         Optional client-side nonce; if non-null, the TSA's response MUST echo it.
     * @return A parsed and structurally validated TimeStampToken.
     * @throws TsaException on network failure, non-2xx TSA response, or invalid token.
     */
    suspend fun requestTimeStamp(
        digest: ByteArray,
        hashAlgorithm: TsaHashAlgorithm,
        nonce: ByteArray? = null
    ): TimeStampToken
}

enum class TsaHashAlgorithm(val oid: String, val jcaName: String) {
    SHA_256("2.16.840.1.101.3.4.2.1", "SHA-256"),
    SHA_384("2.16.840.1.101.3.4.2.2", "SHA-384"),
    SHA_512("2.16.840.1.101.3.4.2.3", "SHA-512")
}

/**
 * Validated RFC 3161 time-stamp token.
 *
 * The [encoded] field is the DER-encoded TimeStampToken (CMS SignedData) as returned by the TSA;
 * it is what gets placed inside a JAdES `sigTst` header. The accessor fields are eagerly parsed
 * for verifier convenience.
 */
data class TimeStampToken(
    val encoded: ByteArray,
    val genTime: Instant,
    val tsaSubject: String,                 // RFC 2253 subject DN of the TSA cert
    val messageImprintAlgorithm: TsaHashAlgorithm,
    val messageImprint: ByteArray,
    val serialNumber: ByteArray,
    val policyOid: String?
) {
    override fun equals(other: Any?): Boolean =
        other is TimeStampToken && encoded.contentEquals(other.encoded)
    override fun hashCode(): Int = encoded.contentHashCode()
}

/**
 * Configuration for a single TSA endpoint.
 *
 * In production, multiple [TsaConfig] instances are typically composed behind a
 * round-robin or failover decorator that the application supplies.
 */
data class TsaConfig(
    val endpointUrl: String,
    val requestTimeoutMs: Long = 10_000,
    val username: String? = null,                  // for HTTP Basic on commercial TSAs
    val password: String? = null,
    val expectedPolicyOid: String? = null,         // require this policy OID in the response
    val trustedSignerCertificates: List<ByteArray> = emptyList()  // pin TSA signer cert(s)
)

class TsaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
```

The default implementation `BouncyCastleTsaClient` builds the request via
`org.bouncycastle.tsp.TimeStampRequestGenerator`, posts it as
`application/timestamp-query`, parses the response with `TimeStampResponse`, and validates the
returned token's signature against `trustedSignerCertificates` (if any) before constructing the
returned value object.

### 5.2 `signatures:trust-lists`

```kotlin
package org.trustweave.signatures.trustlists

import java.security.cert.X509Certificate

/**
 * Parsed European trust graph: one root LoTL plus N per-Member-State TSLs.
 */
data class TrustList(
    val schemeOperator: String,                    // "European Commission" for the LoTL
    val sequenceNumber: Int,
    val issuedAt: kotlinx.datetime.Instant,
    val nextUpdateAt: kotlinx.datetime.Instant?,
    val memberStateLists: List<MemberStateTsl>
)

data class MemberStateTsl(
    val territory: String,                         // ISO 3166-1 alpha-2 (e.g. "DE", "FR")
    val schemeOperator: String,
    val sequenceNumber: Int,
    val issuedAt: kotlinx.datetime.Instant,
    val trustedTsps: List<TrustedTSP>
)

data class TrustedTSP(
    val name: String,
    val tradeName: String?,
    val services: List<TspService>
)

data class TspService(
    val serviceName: String,
    val serviceType: TspServiceType,
    val status: TspServiceStatus,
    val statusStartingTime: kotlinx.datetime.Instant,
    val serviceCertificates: List<X509Certificate>,
    val qualifierUris: List<String>                // e.g. URIs under http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/
)

enum class TspServiceType {
    CA_FOR_QUALIFIED_CERTIFICATES,                 // CA/QC
    QUALIFIED_TIMESTAMP,                           // TSA/QTST
    QUALIFIED_VALIDATION_SERVICE,                  // QVS
    OTHER
}

enum class TspServiceStatus { GRANTED, WITHDRAWN, RECOGNISEDATNATIONALLEVEL, OTHER }

/**
 * Parses LoTL + TSL XML documents. The MVP accepts pre-fetched, pre-verified XML bytes
 * (signature verification of the LoTL itself is deferred — see section 12).
 */
interface TrustListParser {
    /** Parse the LoTL pointer file and the TSL XML documents it references. */
    fun parse(lotlXml: ByteArray, tslXmlByTerritory: Map<String, ByteArray>): TrustList
}

/**
 * Trust-anchor resolution against a parsed [TrustList].
 *
 * Used by the JAdES verifier to decide whether a signer certificate is qualified.
 */
interface TrustAnchorResolver {
    /**
     * Resolve a signing certificate against the trust graph.
     *
     * Returns [TrustAnchorMatch.NotTrusted] if no chain to a CA/QC service is found.
     */
    fun resolve(signerCert: X509Certificate, chain: List<X509Certificate>): TrustAnchorMatch
}

sealed class TrustAnchorMatch {
    /** Signer chains to a qualified CA AND that CA is currently `GRANTED`. */
    data class QualifiedActive(
        val tspName: String,
        val territory: String,
        val service: TspService,
        val qcWithSscd: Boolean,                   // true => QES rather than only AES
        val qcForESig: Boolean                     // QCForESig vs QCForESeal vs QCForWSA
    ) : TrustAnchorMatch()

    /** Chains to a qualified CA but the service status is `WITHDRAWN` as of the signing time. */
    data class QualifiedWithdrawn(val tspName: String, val withdrawnAt: kotlinx.datetime.Instant) : TrustAnchorMatch()

    /** No chain found. */
    object NotTrusted : TrustAnchorMatch()
}
```

### 5.3 `signatures:jades`

```kotlin
package org.trustweave.signatures.jades

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyManagementService
import org.trustweave.signatures.tsa.TsaClient
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import kotlinx.serialization.json.JsonElement

/**
 * JAdES profile level. MVP supports B-B (basic) and B-T (basic + signature-time-stamp).
 * See ETSI TS 119 182-1 §5.
 */
enum class JadesProfile {
    /** Basic signature — JWS + JAdES protected header parameters. */
    B_B,
    /** Basic signature + at least one signature-time-stamp (`sigTst`) attribute. */
    B_T
}

/**
 * JAdES protected header — the union of JWS RFC 7515 `protected` and JAdES TS 119 182-1 §5.2 parameters.
 *
 * The verifier reconstructs this from the wire bytes; the signer builds it during issuance.
 */
data class JadesHeader(
    // JWS
    val alg: String,                       // e.g. "ES256", "PS256", "EdDSA"
    val kid: String? = null,
    val typ: String? = null,               // e.g. "JAdES", "vc+ld+json+jwt"
    val cty: String? = null,               // payload content type
    val crit: List<String>? = null,
    // JAdES (TS 119 182-1)
    val sigT: String,                      // ISO 8601 claimed signing time, e.g. "2026-05-28T10:15:00Z"
    val x5tS256: String,                   // base64url(SHA-256(signer cert)) — JSON key "x5t#S256"
    val x5c: List<String>? = null,         // X.5c chain (base64 DER, signer first)
    val sigPSt: SignaturePolicyStore? = null,
    val sigPId: SignaturePolicyIdentifier? = null,
    val sigD: SignatureDetached? = null,   // when the payload is detached (NOT used in MVP)
    val srCms: List<String>? = null,       // commitment-type-indication
    val srAts: SignerAttributes? = null,
    /** Free-form headers we don't model individually but still must include in the protected JSON. */
    val additional: Map<String, JsonElement> = emptyMap()
)

data class SignaturePolicyIdentifier(val id: String, val digestAlgorithm: String, val digestB64: String)
data class SignaturePolicyStore(val spDocSpec: String, val spDocB64: String)
data class SignatureDetached(val pars: List<String>, val hashM: String, val hashV: String, val ctys: List<String>?)
data class SignerAttributes(val claimed: List<JsonElement> = emptyList(), val certified: List<JsonElement> = emptyList())

/**
 * Unsigned properties — JAdES `etsiU` array entries. Populated for B-T (sigTst) and beyond.
 */
data class JadesUnsignedProperties(
    val sigTst: List<EncodedTimeStampToken> = emptyList()
)

data class EncodedTimeStampToken(val tstTokensB64: List<String>, val canonAlg: String? = null)

/**
 * Signer.
 *
 * Builds a JAdES JSON Serialization (flattened) signature over a JSON payload.
 * The signing key is resolved via the existing TrustWeave KMS SPI — no key material is held inside this module.
 */
interface JadesSigner {
    suspend fun sign(
        payloadJson: JsonElement,
        request: JadesSigningRequest
    ): JadesSignature
}

data class JadesSigningRequest(
    val profile: JadesProfile,
    val keyId: KeyId,                                  // points at a key in the configured KMS
    val signerCertificateChain: List<ByteArray>,       // DER X.509, signer first; required for x5c / x5t#S256
    val headerOverrides: Map<String, JsonElement> = emptyMap(),
    val signaturePolicyId: SignaturePolicyIdentifier? = null,
    val signingTime: kotlinx.datetime.Instant? = null, // defaults to Clock.System.now()
    val tsaConfig: org.trustweave.signatures.tsa.TsaConfig? = null  // required iff profile == B_T
)

/**
 * A produced JAdES signature, in JWS JSON Serialization (flattened) form.
 *
 * The `compact` accessor returns the JWS Compact Serialization variant when the unsigned
 * properties block is empty (i.e. strict B-B without `etsiU`).
 */
data class JadesSignature(
    val protectedHeaderB64u: String,
    val payloadB64u: String,
    val signatureB64u: String,
    val unsigned: JadesUnsignedProperties = JadesUnsignedProperties(),
    val serializedFlattened: String                    // full JSON-flattened JWS string
) {
    fun compact(): String? =
        if (unsigned.sigTst.isEmpty()) "$protectedHeaderB64u.$payloadB64u.$signatureB64u" else null
}

/**
 * Verifier.
 *
 * Pure verification — does not fetch trust lists or contact TSAs. The caller supplies
 * pre-resolved [TrustAnchorResolver] and (for B-T) the verifier reads the embedded
 * `sigTst` tokens directly without re-stamping.
 */
interface JadesVerifier {
    suspend fun verify(
        jadesSerialized: String,
        options: JadesVerificationOptions
    ): JadesValidationResult
}

data class JadesVerificationOptions(
    val requiredProfile: JadesProfile,
    val trustAnchorResolver: TrustAnchorResolver,
    val acceptedAlgorithms: Set<String> = setOf("ES256", "PS256", "ES384", "PS384", "ES512", "PS512", "EdDSA"),
    val allowExpiredCertificateAtSigningTime: Boolean = false,
    val maxClockSkew: kotlin.time.Duration = kotlin.time.Duration.parse("PT5M")
)

sealed class JadesValidationResult {
    data class Valid(
        val header: JadesHeader,
        val payload: JsonElement,
        val trust: org.trustweave.signatures.trustlists.TrustAnchorMatch,
        val signingTime: kotlinx.datetime.Instant,
        val signatureTimeStamp: kotlinx.datetime.Instant?     // null for B-B
    ) : JadesValidationResult()

    sealed class Invalid : JadesValidationResult() {
        data class BadSignature(val reason: String) : Invalid()
        data class UntrustedSigner(val cert: java.security.cert.X509Certificate) : Invalid()
        data class WrongProfile(val found: JadesProfile, val required: JadesProfile) : Invalid()
        data class MissingTimeStamp(val reason: String) : Invalid()
        data class TimeStampMismatch(val reason: String) : Invalid()
        data class CertificateExpired(val notAfter: kotlinx.datetime.Instant) : Invalid()
        data class Malformed(val reason: String) : Invalid()
    }
}
```

### 5.4 `kms:plugins:pkcs11`

```kotlin
package org.trustweave.kms.pkcs11

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.SignResult

/**
 * PKCS#11-backed KeyManagementService.
 *
 * Implements the same SPI used by [InMemoryKeyManagementService], [AwsKeyManagementService], etc.
 * Devices are not detected at compile time — the library path is supplied at construction.
 *
 * See [KeyManagementService.kt](../../../kms/kms-core/src/main/kotlin/org/trustweave/kms/KeyManagementService.kt)
 * for the contract this class satisfies.
 */
class Pkcs11KeyManagementService(
    private val config: Pkcs11Config
) : KeyManagementService {

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> { /* dynamic via C_GetMechanismInfo */ }
    override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): GenerateKeyResult { /* ... */ }
    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult { /* ... */ }
    override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): SignResult { /* ... */ }
    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult { /* ... */ }
}

/**
 * Pkcs11 provider configuration.
 *
 * Constructs a per-instance SunPKCS11 provider; multiple instances may coexist for multiple devices.
 */
data class Pkcs11Config(
    val name: String,                                   // friendly name, becomes part of the SunPKCS11 provider name
    val libraryPath: String,                            // absolute path to the PKCS#11 .so / .dll / .dylib
    val slot: SlotSelector,
    val pinStrategy: PinStrategy,
    val keyIdEncoding: KeyIdEncoding = KeyIdEncoding.HEX_OBJECT_HANDLE,
    val mechanismAllowList: Set<String>? = null         // optional: restrict to a curated CK_MECHANISM set
)

sealed class SlotSelector {
    data class ById(val slotId: Long) : SlotSelector()
    data class ByLabel(val tokenLabel: String) : SlotSelector()
    object FirstAvailable : SlotSelector()
}

sealed class PinStrategy {
    /** PIN supplied as plaintext; useful for tests with SoftHSM2. */
    data class Plain(val pin: CharArray) : PinStrategy()
    /** PIN sourced from an environment variable name at first-use. */
    data class FromEnv(val variableName: String) : PinStrategy()
    /** PIN supplied by an externally provided callback (e.g. a vault, an OS keychain). */
    data class Callback(val provider: suspend () -> CharArray) : PinStrategy()
}

enum class KeyIdEncoding {
    /** [KeyId] value carries the hex-encoded CK_OBJECT_HANDLE. Fast, but handles aren't stable across sessions. */
    HEX_OBJECT_HANDLE,
    /** [KeyId] value carries the CKA_LABEL of the key object. Stable; recommended for long-lived QES keys. */
    LABEL
}
```

### 5.5 `credentials/credential-api` additions

Extension of the existing `CredentialProof` sealed class (current shape in
[`CredentialProof.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/model/vc/CredentialProof.kt)):

```kotlin
package org.trustweave.credential.model.vc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

sealed class CredentialProof {
    // ...existing variants: LinkedDataProof, JwtProof, SdJwtVcProof, MdocProof...

    /**
     * JAdES (ETSI TS 119 182-1) — qualified-grade signature envelope.
     *
     * Wraps any JWT/JSON-LD VC payload. `value` is the JWS JSON Serialization (flattened) form;
     * `signedHeaderRefs` mirrors the protected header back into the proof object so consumers can
     * inspect `sigT`, `x5t#S256`, `sigPId`, etc. without re-decoding the JWS.
     */
    @Serializable
    data class JAdES(
        val value: String,
        val signedHeaderRefs: Map<String, JsonElement> = emptyMap()
    ) : CredentialProof()
}
```

`CredentialProofSerializer` (see
[`CredentialProofSerializer.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/model/vc/CredentialProofSerializer.kt))
gains one branch for the new `@type` discriminator value `JAdES`.

`ProofSuiteId` (see
[`ProofSuiteId.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/format/ProofSuiteId.kt))
gains an entry:

```kotlin
JADES("jades")
```

A new `JAdESProofEngine` implements
[`ProofEngine`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/spi/proof/ProofEngine.kt):

```kotlin
package org.trustweave.credential.proof.internal.engines

class JAdESProofEngine(
    private val kms: org.trustweave.kms.KeyManagementService,
    private val jadesSigner: org.trustweave.signatures.jades.JadesSigner,
    private val jadesVerifier: org.trustweave.signatures.jades.JadesVerifier,
    private val trustAnchorResolver: org.trustweave.signatures.trustlists.TrustAnchorResolver
) : org.trustweave.credential.spi.proof.ProofEngine {
    override val format = org.trustweave.credential.format.ProofSuiteId.JADES
    override val formatName = "JAdES (ETSI TS 119 182-1)"
    override val formatVersion = "1.2.1"
    override val capabilities = org.trustweave.credential.spi.proof.ProofEngineCapabilities(
        selectiveDisclosure = false,
        zeroKnowledge = false,
        revocation = true,
        presentation = false,
        predicates = false
    )

    override suspend fun issue(request: org.trustweave.credential.requests.IssuanceRequest):
        org.trustweave.credential.model.vc.VerifiableCredential { /* ... */ }

    override suspend fun verify(
        credential: org.trustweave.credential.model.vc.VerifiableCredential,
        options: org.trustweave.credential.requests.VerificationOptions
    ): org.trustweave.credential.results.VerificationResult { /* ... */ }
}
```

## 6. Key data models

The full data-model relationships for the new domain:

```kotlin
//  signatures:tsa-core
TsaClient.requestTimeStamp(digest, hashAlgorithm) -> TimeStampToken

//  signatures:trust-lists
TrustListParser.parse(lotlXml, tslByTerritory) -> TrustList
TrustList -> List<MemberStateTsl> -> List<TrustedTSP> -> List<TspService>

TrustAnchorResolver.resolve(signerCert, chain) -> TrustAnchorMatch
                              \_______________________________________
                                                                      \
//  signatures:jades                                                   v
JadesSigner.sign(payloadJson, JadesSigningRequest)  -> JadesSignature
JadesVerifier.verify(serialized, JadesVerificationOptions) -> JadesValidationResult
                              ^                          ^
                              |                          |
                              +-- uses TsaClient (B-T) --+
                              +-- uses TrustAnchorResolver

//  credentials:credential-api
CredentialProof.JAdES(value, signedHeaderRefs)
JAdESProofEngine : ProofEngine
```

A minimal end-to-end issuance sequence:

```kotlin
val kms: KeyManagementService = Pkcs11KeyManagementService(pkcs11Config)
val tsa: TsaClient = BouncyCastleTsaClient(TsaConfig("https://freetsa.org/tsr"))
val trustList: TrustList = TrustListParser.parse(lotlBytes, perTerritoryTslBytes)
val resolver: TrustAnchorResolver = TrustAnchorResolver.of(trustList)

val signer: JadesSigner = JadesSignerImpl(kms, tsa)
val verifier: JadesVerifier = JadesVerifierImpl(tsaTrustedSignerCerts = listOf(/* ... */))

val engine = JAdESProofEngine(kms, signer, verifier, resolver)
```

## 7. Integration points

### 7.1 KMS — `KeyManagementService` SPI

The QES code path uses
[`KeyManagementService`](../../kms/kms-core/src/main/kotlin/org/trustweave/kms/KeyManagementService.kt)
unchanged. `JadesSigner` calls `kms.sign(keyId, signingInput, algorithm)` and never sees a private
key. For QES, the application wires `Pkcs11KeyManagementService` as the active KMS; for AES-only
operation or local tests, the existing `InMemoryKeyManagementService` works without any JAdES code
change.

### 7.2 CredentialProof — sealed-class extension

`CredentialProof.JAdES` is added as a new sibling of `LinkedDataProof`, `JwtProof`, `SdJwtVcProof`,
and `MdocProof` in
[`CredentialProof.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/model/vc/CredentialProof.kt).
The custom `@type` discriminator in
[`CredentialProofSerializer.kt`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/model/vc/CredentialProofSerializer.kt)
gains one branch.

### 7.3 IssuanceBuilder DSL — `withJadesProfile`

The existing
[`IssuanceRequestBuilder`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/requests/IssuanceRequestBuilder.kt)
DSL gets one new configurator. Usage:

```kotlin
val request = issuanceRequest(ProofSuiteId.JADES) {
    issuer(issuerDid)
    subject(subjectDid) {
        "givenName" to "Ada"
        "familyName" to "Lovelace"
    }
    type("EuQualifiedCredential")
    withJadesProfile(JadesProfile.B_T, tsaConfig = TsaConfig("https://qtsp.example.eu/tsa"))
    expiresIn(365.days)
}
```

Signature:

```kotlin
fun IssuanceRequestBuilder.withJadesProfile(
    profile: JadesProfile,
    tsaConfig: TsaConfig? = null,
    signaturePolicy: SignaturePolicyIdentifier? = null
): IssuanceRequestBuilder
```

The builder stashes these in the `proofOptions` map already used by the SD-JWT and VC-LD engines;
`JAdESProofEngine.issue` reads them back during proof construction. When `profile == B_T`,
`tsaConfig` is required and the builder eagerly validates that.

### 7.4 VerificationBuilder DSL — `requireJadesProfile`

The verification side, currently driven by
[`VerificationOptions`](https://github.com/geoknoesis/trustweave/blob/main/credentials/credential-models-mp/src/commonMain/kotlin/org/trustweave/credential/requests/VerificationOptions.kt),
gains a parallel configurator:

```kotlin
fun VerificationBuilder.requireJadesProfile(profile: JadesProfile): VerificationBuilder
fun VerificationBuilder.requireQualifiedSigner(): VerificationBuilder    // QES gate
```

`JAdESProofEngine.verify` reads these (carried inside the existing
`VerificationOptions.formatMetadata` map) and refuses to return `Valid` if the
`TrustAnchorMatch` is anything other than `QualifiedActive` (when `requireQualifiedSigner` is set)
or the parsed profile is below the required level.

### 7.5 Cross-domain plug-in registration

`signatures:jades` does *not* introduce a new SPI namespace. The `JAdESProofEngine` registers
itself via the same SPI route that the existing engines use — see
[`VcLdProofEngineProvider.kt`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/VcLdProofEngineProvider.kt)
and
[`SdJwtProofEngineProvider.kt`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/SdJwtProofEngineProvider.kt)
for the pattern. The JAdES engine provider lives in the new `signatures:jades` module so that
applications that don't pull in `signatures:jades` never load the engine.

## 8. Phased implementation plan

| Phase | Module(s) | Ships | Depends on | Est. LOC | Est. timeline |
|---|---|---|---|---|---|
| A | `signatures:tsa-core` | `TsaClient` + `BouncyCastleTsaClient`, `TimeStampToken` model, `TsaConfig`, FreeTSA + BC-in-process tests | `common`, BC, OkHttp (all already in classpath) | ~700 | 1 week |
| B | `signatures:trust-lists` | `TrustListParser`, `TrustList`/`TrustedTSP` models, `TrustAnchorResolver`, fixture-based tests against a snapshotted LoTL | `common`, JDK XML | ~1,500 | 2 weeks |
| C | `signatures:jades` (B-B only) | `JadesSigner`/`JadesVerifier` for the B-B profile, JWS JSON Serialization framing, header model | A (only for the verifier-side `sigTst` decode path) + `kms:kms-core` | ~1,800 | 2 weeks |
| D | `signatures:jades` (B-T) | B-T sign + verify, embedding RFC 3161 tokens in `etsiU.sigTst`, signature-time-stamp validation against trust list | A, B, C | ~700 | 1 week |
| E | `kms:plugins:pkcs11` | `Pkcs11KeyManagementService`, `Pkcs11Config`, SoftHSM2-on-CI tests, manual smoke against SafeNet Luna and YubiHSM 2 | `kms:kms-core` only — runnable in parallel with A-D | ~1,400 | 2 weeks |
| F | `credentials/credential-api` wiring | `CredentialProof.JAdES` + serializer branch, `ProofSuiteId.JADES`, `JAdESProofEngine`, DSL hooks, EUDI Reference Wallet interop fixtures | C, D | ~900 | 1 week |

Total: ~7,000 LOC of production code, ~9 calendar weeks with the phases as drawn. E can begin in
parallel with A, compressing the calendar to ~7 weeks if two engineers are available.

## 9. Test strategy

### 9.1 Unit tests (every module, pure JVM, no externals)

- `signatures:tsa-core`: tests against a BouncyCastle-in-process TSA constructed with
  `org.bouncycastle.tsp.TimeStampTokenGenerator`. Covers SHA-256/384/512, nonce echo,
  policy-OID mismatch, malformed `TimeStampResp`, HTTP error mapping.
- `signatures:trust-lists`: fixture-driven parsing of a frozen 2026-01 snapshot of the LoTL plus
  five Member-State TSLs (DE, FR, ES, IT, BE). Tests cover all status values, `QCWithSSCD`
  qualifier detection, withdrawn-after-signing-time logic, certificate-chain matching.
- `signatures:jades`: golden-file tests for B-B and B-T signatures generated by the engine and
  re-verified by the engine itself; plus negative tests (tampered payload, wrong `x5t#S256`,
  algorithm not in allow-list, missing `sigT`).
- `kms:plugins:pkcs11`: tests against SoftHSM2 (see 9.4). Covers slot-by-label, slot-by-id,
  PIN-from-env, algorithm advertisement via `C_GetMechanismInfo`, error mapping when the device
  is offline or the PIN is wrong.
- `credentials/credential-api`: the new `JAdESProofEngine` is tested against the same fixture
  matrix used by `VcLdProofEngine` and `SdJwtProofEngine` (see
  [`VcLdProofEngine.kt`](../../credentials/credential-api/src/main/kotlin/org/trustweave/credential/proof/internal/engines/VcLdProofEngine.kt)).

### 9.2 Interop tests with the EUDI Reference Wallet

The EU Digital Identity Wallet reference implementations
(https://github.com/eu-digital-identity-wallet) ship sample VCs and PIDs as part of their test
suite. We vendor a frozen subset of those as test resources under
`signatures/jades/src/test/resources/eudi-fixtures/` and assert that:

1. JAdES B-B signatures produced by TrustWeave validate inside the EUDI verifier sample app
   (one-way export check, run manually before each release; not in CI).
2. JAdES B-B and B-T signatures produced by the EUDI reference signer validate inside
   `JadesVerifier` (both-way; CI-resident as static fixtures).

### 9.3 TSA testing

- **CI:** Bouncy Castle in-process TSA — deterministic, no network. Provides the bulk of unit
  coverage.
- **Nightly CI job:** one test run against the public FreeTSA endpoint (https://freetsa.org/tsr)
  marked `@Tag("network")` and excluded from PR builds, to detect drift in real-world TSA
  responses.

### 9.4 PKCS#11 testing

- **CI:** SoftHSM2 in a Docker container (`opendnssec/softhsm2:latest`). The CI workflow
  initializes one token, generates an RSA-2048 and an ECDSA-P-256 key, and runs
  the `Pkcs11KeyManagementService` test suite against it.
- **Manual:** documented smoke-test scripts for SafeNet Luna (PCI-E), Utimaco
  CryptoServer, and YubiHSM 2 (USB development device). These live under
  `kms/plugins/pkcs11/MANUAL_TESTING.md` and are run by the release engineer
  before each major version bump.

### 9.5 LoTL test fixtures

The EU LoTL is published at https://ec.europa.eu/tools/lotl/eu-lotl.xml. The CI pipeline must NOT
live-fetch this URL — the fixture would change under our feet and CI failures would be
indistinguishable from regressions. Instead, we snapshot:

- `eu-lotl-2026-01-15.xml`
- per-MS TSLs for `DE`, `FR`, `ES`, `IT`, `BE`, `NL`, `PL` as referenced from that LoTL snapshot

These are stored in `signatures/trust-lists/src/test/resources/lotl-snapshot/` and refreshed
manually on a quarterly cadence as part of routine maintenance.

## 10. Dependencies

| Dependency | Status | Notes |
|---|---|---|
| `org.bouncycastle:bcprov-jdk18on` | Already on classpath via `gradle/libs.versions.toml` | Used for CMS, TSP, X.509 parsing. |
| `org.bouncycastle:bcpkix-jdk18on` | Already on classpath | Provides `org.bouncycastle.tsp.*`. |
| `com.nimbusds:nimbus-jose-jwt` | Already on classpath | JWS JSON Serialization framing. |
| `com.squareup.okhttp3:okhttp` | Already on classpath | HTTP transport for `TsaClient`. |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | Already on classpath | JAdES header modelling and `signedHeaderRefs`. |
| `org.jetbrains.kotlinx:kotlinx-datetime` | Already on classpath | `Instant` everywhere consistent with the rest of the codebase. |
| `sun.security.pkcs11.SunPKCS11` | JDK-provided | **No Gradle dep.** Provider class is loaded reflectively; available in every JDK 17+ distribution we target (Temurin, Liberica, Zulu, Oracle). Note the API change in JDK 17+: `Provider.configure(String)` replaces the deprecated constructor-with-config-file pattern from JDK 8/11. Code must use the new path. |
| `softhsm2` (Docker `opendnssec/softhsm2`) | CI-only | Container is started by GitHub Actions / `Dockerfile.softhsm` before the PKCS#11 test job. |
| EUDI Reference Wallet fixtures | CI-only (vendored) | Downloaded once, committed under `signatures/jades/src/test/resources/eudi-fixtures/`. License attribution recorded in `THIRD_PARTY_NOTICES.md`. |

No new Gradle dependencies are introduced for the production code path. The only additions are
test-scope fixtures and a CI container image.

## 11. External constraints & risks

- **SunPKCS11 portability.** `SunPKCS11` is part of OpenJDK and is present in every mainstream
  distribution we target, but it is technically an internal JDK package. If a future JDK
  restricts access (similar to the JEP 403 strong-encapsulation tightening), we have a fallback:
  the IAIK PKCS#11 wrapper (https://github.com/mw-software-engineering/iaikpkcs11wrapper), an
  Apache-2.0 alternative. Switching would mean a new dependency but no API change in
  `Pkcs11KeyManagementService`.
- **JAdES vs SD-JWT VC layering.** These are not alternatives; they layer. SD-JWT VC is the
  *credential format* — it defines the claim layout, the selective-disclosure mechanism, and the
  holder-binding rules. JAdES is an *outer signature envelope* — it can wrap an SD-JWT VC (or a
  W3C VC-JWT, or a JSON-LD VC, or any other JSON payload) and provide the eIDAS legal-grade
  signature on top. The two coexist: an issuer producing an EU PID will typically issue an
  SD-JWT VC and then wrap that SD-JWT VC inside a JAdES B-T signature for non-repudiation. The
  TrustWeave API surfaces both via independent `ProofSuiteId` values (`SD_JWT_VC`, `JADES`)
  and engines, and the application picks which (or both) to apply.
- **LoTL refresh policy.** The MVP treats the LoTL as immutable input. In production it must be
  refreshed periodically (the LoTL itself carries `NextUpdate`, typically 6 months out, but
  per-MS TSLs refresh more frequently — sometimes weekly). The follow-up adds a
  `TrustListRefreshScheduler` that polls `NextUpdate`, re-verifies the LoTL signature, and
  atomically swaps the in-memory `TrustList` instance.
- **ETSI EN 319 102-1 conformance.** Full conformance requires implementing the
  ETSI EN 319 102-1 building blocks (signature policy evaluation, validation context
  initialization, X.509 validation constraints, signing-time-of-cert validity, PoE-driven
  status determination). The MVP gives basic *mathematical* validation plus trust-anchor
  resolution; we explicitly mark `JadesValidationResult.Valid` as "cryptographically valid and
  trust-anchored" rather than "ETSI EN 319 102-1 conformant". Marketing language and API
  documentation must respect this distinction.

## 12. Open questions

- **Result vs throw conventions.** The `kms:` domain returns sealed `*Result` types
  (see [`SignResult.kt`](../../kms/kms-core/src/main/kotlin/org/trustweave/kms/results/SignResult.kt));
  the credentials API throws (`ProofEngine.issue` is documented to throw). The new
  `signatures:` modules straddle both. **Recommendation:** match the `kms:` pattern —
  `JadesValidationResult` is already a sealed type, and `JadesSigner.sign` should likewise
  return a `JadesSignResult` rather than throw. This is consistent with the existing
  observation in the codebase that crypto operations should never throw across module
  boundaries.
- **LoTL cache location.** Once refresh is implemented, where does the cached LoTL XML live?
  Candidates: (a) a configurable directory (default `~/.trustweave/lotl/`); (b) an injectable
  `TrustListStore` SPI mirroring the existing `WalletStorage` pattern. (b) is preferred — it
  matches the rest of the codebase and is testable.
- **Detached vs enveloping signatures.** JAdES supports both via the `sigD` header. The MVP
  ships enveloping only (`sigD = null`). Detached mode is needed for very large payloads and
  for the future PAdES work where the signature lives outside the PDF body. Decision deferred.
- **Selective disclosure interaction.** When JAdES wraps an SD-JWT VC, does selective
  disclosure happen before or after the JAdES wrapping? **Tentative answer:** disclosures are
  carried alongside the SD-JWT VC payload (after the `~` separator), and the JAdES signature
  covers only the SD-JWT issuer-signed JWT — not the disclosures. This matches IETF SD-JWT VC
  draft semantics. To be confirmed against the EUDI Reference Wallet fixtures.

## 13. Out-of-MVP roadmap

In suggested priority order:

1. **JAdES B-LT and B-LTA profiles.** Adds validation-data references (`xVals`, `rVals`,
   `axVals`, `arVals`) for long-term validation and the archival time-stamp (`arcTst`) for
   archival validation. Required for documents that must remain verifiable beyond the
   signer-cert lifetime.
2. **Self-verification of the LoTL XAdES signature.** Brings the trust-list ingestion in-band
   so operators no longer need an out-of-band trust path to the LoTL XML. Pulls in an XML
   Signature implementation (Santuario via the JDK is sufficient).
3. **Full ETSI EN 319 102-1 validation pipeline.** Signature policy evaluation, validation
   context initialization, PoE-driven status determination, time-of-signing certificate
   validity. Required for formal conformance claims.
4. **CAdES (ETSI EN 319 122-1) and PAdES (ETSI EN 319 142-1).** Needed once TrustWeave is used
   to sign binary documents (CMS containers) and PDFs.
5. **Periodic LoTL/TSL refresh.** `TrustListRefreshScheduler` + `TrustListStore` SPI.
6. **eIDAS-compliant audit logging.** ETSI EN 319 421 sets audit requirements for QTSP
   operations. The signing-side audit trail needs to be reproducible, non-repudiable, and
   immutable — likely implemented on top of `anchors/` for blockchain-anchored audit log
   integrity.

## 14. References

| Reference | URL |
|---|---|
| Regulation (EU) 2024/1183 (eIDAS 2.0) | https://eur-lex.europa.eu/eli/reg/2024/1183/oj |
| ETSI TS 119 182-1 (JAdES) | https://www.etsi.org/deliver/etsi_ts/119100_119199/11918201/ |
| ETSI EN 319 102-1 (AdES signature procedures) | https://www.etsi.org/deliver/etsi_en/319100_319199/31910201/ |
| ETSI TS 119 612 (Trusted Lists) | https://www.etsi.org/deliver/etsi_ts/119600_119699/119612/ |
| ETSI EN 319 122-1 (CAdES) | https://www.etsi.org/deliver/etsi_en/319100_319199/31912201/ |
| ETSI EN 319 132-1 (XAdES) | https://www.etsi.org/deliver/etsi_en/319100_319199/31913201/ |
| RFC 3161 (Time-Stamp Protocol) | https://datatracker.ietf.org/doc/html/rfc3161 |
| RFC 5652 (CMS) | https://datatracker.ietf.org/doc/html/rfc5652 |
| PKCS#11 v2.40 (OASIS) | https://docs.oasis-open.org/pkcs11/pkcs11-base/v2.40/pkcs11-base-v2.40.html |
| EUDI Wallet Architecture Reference Framework | https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework |
| EUDI Reference Wallet (issuer / wallet / verifier samples) | https://github.com/eu-digital-identity-wallet |
| EU LoTL XML (production endpoint) | https://ec.europa.eu/tools/lotl/eu-lotl.xml |
| walt.id Identity (comparison reference) | https://github.com/walt-id/waltid-identity |

---

*End of document.*
