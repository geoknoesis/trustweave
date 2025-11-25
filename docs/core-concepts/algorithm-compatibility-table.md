---
title: "Cryptographic Algorithm Compatibility: DIDs, VCs, AWS KMS, and Azure Key Vault"
---

# Cryptographic Algorithm Compatibility: DIDs, VCs, AWS KMS, and Azure Key Vault

This document provides a comprehensive comparison of cryptographic algorithms supported by Decentralized Identifiers (DIDs), Verifiable Credentials (VCs), AWS Key Management Service (KMS), and Azure Key Vault.

## Algorithm Support Comparison Table

| Algorithm | Description | DID/VC Support | AWS KMS Support | Azure Key Vault Support | Usage & Use Cases |
|-----------|-------------|----------------|-----------------|------------------------|-------------------|
| **Ed25519** | Edwards-curve Digital Signature Algorithm using Curve25519. High-performance elliptic curve signature algorithm with 32-byte public keys and 64-byte signatures. | ✅ **Widely Supported**<br/>- Default in TrustWeave<br/>- Used in `did:key`<br/>- `Ed25519Signature2020` proof type | ✅ **Supported**<br/>(Added Nov 2025)<br/>- Key spec: `ECC_Ed25519`<br/>- Signing: EdDSA | ❌ **Not Supported**<br/>- No native Ed25519 support<br/>- Workaround: Use secp256k1 or P-256 | **DID/VC Usage:**<br/>- Most common in DID ecosystems<br/>- Recommended for TrustWeave<br/>- Compact keys ideal for mobile/IoT<br/>- Fast signing/verification<br/><br/>**When to Use:**<br/>- General-purpose DID/VC signing<br/>- Mobile and IoT applications<br/>- When interoperability is key |
| **secp256k1** | Elliptic curve used by Bitcoin and Ethereum. 256-bit curve with good performance. | ✅ **Widely Supported**<br/>- Used in `did:ethr`<br/>- `did:polygonid`<br/>- Blockchain-based DIDs<br/>- `JsonWebSignature2020` with ES256K | ✅ **Supported**<br/>- Key spec: `ECC_SECG_P256K1`<br/>- Signing: ES256K | ✅ **Supported**<br/>- Key type: `EC-P256K`<br/>- Signing: ES256K | **DID/VC Usage:**<br/>- Blockchain-based DIDs<br/>- Ethereum ecosystem<br/>- Bitcoin-related applications<br/><br/>**When to Use:**<br/>- Integrating with Ethereum/Bitcoin<br/>- Blockchain-anchored credentials<br/>- Web3 applications |
| **P-256 (NIST)** | NIST P-256 elliptic curve (also known as secp256r1). FIPS 140-2 compliant. | ✅ **Supported**<br/>- Enterprise/government use<br/>- `JsonWebSignature2020` with ES256<br/>- FIPS-compliant deployments | ✅ **Supported**<br/>- Key spec: `ECC_NIST_P256`<br/>- Signing: ES256 | ✅ **Supported**<br/>- Key type: `EC-P256`<br/>- Signing: ES256 | **DID/VC Usage:**<br/>- Government/enterprise deployments<br/>- FIPS 140-2 compliance requirements<br/>- Healthcare/financial services<br/><br/>**When to Use:**<br/>- Regulatory compliance needed<br/>- Government contracts<br/>- Enterprise security policies |
| **P-384 (NIST)** | NIST P-384 elliptic curve. Higher security level than P-256. FIPS 140-2 compliant. | ✅ **Supported**<br/>- Higher security requirements<br/>- `JsonWebSignature2020` with ES384<br/>- Government/defense use | ✅ **Supported**<br/>- Key spec: `ECC_NIST_P384`<br/>- Signing: ES384 | ✅ **Supported**<br/>- Key type: `EC-P384`<br/>- Signing: ES384 | **DID/VC Usage:**<br/>- High-security applications<br/>- Defense/government systems<br/>- Long-term credential validity<br/><br/>**When to Use:**<br/>- Higher security requirements<br/>- Long-term credential storage<br/>- Defense/government systems |
| **P-521 (NIST)** | NIST P-521 elliptic curve. Highest security level among NIST curves. FIPS 140-2 compliant. | ✅ **Supported**<br/>- Maximum security requirements<br/>- `JsonWebSignature2020` with ES512<br/>- Specialized high-security use | ✅ **Supported**<br/>- Key spec: `ECC_NIST_P521`<br/>- Signing: ES512 | ✅ **Supported**<br/>- Key type: `EC-P521`<br/>- Signing: ES512 | **DID/VC Usage:**<br/>- Maximum security applications<br/>- Long-term archival<br/>- Critical infrastructure<br/><br/>**When to Use:**<br/>- Maximum security requirements<br/>- Long-term credential archival<br/>- Critical systems |
| **RSA-2048** | Rivest-Shamir-Adleman algorithm with 2048-bit keys. Widely used but larger key sizes. | ✅ **Supported**<br/>- Legacy systems<br/>- `JsonWebSignature2020` with RS256/RS384/RS512<br/>- Backward compatibility | ✅ **Supported**<br/>- Key spec: `RSA_2048`<br/>- Signing: RS256/RS384/RS512<br/>- Encryption: RSAES_OAEP_SHA_1/256 | ✅ **Supported**<br/>- Key type: `RSA`<br/>- Key size: 2048 bits<br/>- Signing: RS256/RS384/RS512 | **DID/VC Usage:**<br/>- Legacy system integration<br/>- Backward compatibility<br/>- Enterprise systems<br/><br/>**When to Use:**<br/>- Legacy system requirements<br/>- Backward compatibility<br/>- Enterprise integrations |
| **RSA-3072** | RSA with 3072-bit keys. Higher security than RSA-2048. | ✅ **Supported**<br/>- Higher security RSA<br/>- `JsonWebSignature2020` with RS256/RS384/RS512 | ✅ **Supported**<br/>- Key spec: `RSA_3072`<br/>- Signing: RS256/RS384/RS512<br/>- Encryption: RSAES_OAEP_SHA_1/256 | ✅ **Supported**<br/>- Key type: `RSA`<br/>- Key size: 3072 bits<br/>- Signing: RS256/RS384/RS512 | **DID/VC Usage:**<br/>- Higher security RSA requirements<br/>- Long-term credentials<br/><br/>**When to Use:**<br/>- Higher security RSA needs<br/>- Long-term credential validity |
| **RSA-4096** | RSA with 4096-bit keys. Maximum security for RSA. | ✅ **Supported**<br/>- Maximum RSA security<br/>- `JsonWebSignature2020` with RS256/RS384/RS512 | ✅ **Supported**<br/>- Key spec: `RSA_4096`<br/>- Signing: RS256/RS384/RS512<br/>- Encryption: RSAES_OAEP_SHA_1/256 | ✅ **Supported**<br/>- Key type: `RSA`<br/>- Key size: 4096 bits<br/>- Signing: RS256/RS384/RS512 | **DID/VC Usage:**<br/>- Maximum RSA security<br/>- Critical systems<br/><br/>**When to Use:**<br/>- Maximum RSA security<br/>- Critical infrastructure |
| **BLS12-381** | BLS (Boneh-Lynn-Shacham) signature scheme on BLS12-381 curve. Used for BBS+ signatures. | ✅ **Supported**<br/>- `BbsBlsSignature2020` proof type<br/>- Selective disclosure<br/>- Zero-knowledge proofs | ❌ **Not Supported**<br/>- No native BLS support<br/>- Requires specialized KMS | ❌ **Not Supported**<br/>- No native BLS support<br/>- Requires specialized KMS | **DID/VC Usage:**<br/>- Selective disclosure<br/>- Zero-knowledge proofs<br/>- Privacy-preserving credentials<br/><br/>**When to Use:**<br/>- Privacy requirements<br/>- Selective disclosure<br/>- ZK-proof applications |

## TrustWeave Algorithm Support

Based on the TrustWeave codebase:

### Supported Algorithms in TrustWeave

```48:62:TrustWeave-did/src/main/kotlin/com/geoknoesis/TrustWeave/did/DidCreationOptions.kt
    enum class KeyAlgorithm(val algorithmName: String) {
        /** Ed25519 signature algorithm (recommended) */
        ED25519("Ed25519"),
        
        /** secp256k1 (Bitcoin/Ethereum curve) */
        SECP256K1("secp256k1"),
        
        /** P-256 (NIST curve) */
        P256("P-256"),
        
        /** P-384 (NIST curve) */
        P384("P-384"),
        
        /** P-521 (NIST curve) */
        P521("P-521");
```

### Supported Proof Types in TrustWeave

```32:36:TrustWeave-trust/src/main/kotlin/com/geoknoesis/TrustWeave/credential/dsl/TypeSafeHelpers.kt
object ProofTypes {
    const val ED25519 = "Ed25519Signature2020"
    const val JWT = "JsonWebSignature2020"
    const val BBS_BLS = "BbsBlsSignature2020"
}
```

## Proof Type to Algorithm Mapping

| Proof Type | Signature Algorithm | KMS Algorithm Required | AWS KMS Compatible | Azure Key Vault Compatible |
|------------|-------------------|----------------------|-------------------|---------------------------|
| `Ed25519Signature2020` | Ed25519 (EdDSA) | Ed25519 | ✅ Yes (Nov 2025+) | ❌ No |
| `JsonWebSignature2020` | JWS (flexible) | ES256K, ES256, ES384, ES512, RS256, RS384, RS512, EdDSA | ✅ Yes (varies by algorithm) | ✅ Yes (varies by algorithm) |
| `BbsBlsSignature2020` | BBS+ (BLS12-381) | BLS12-381 | ❌ No | ❌ No |

## Cloud KMS Compatibility Summary

### AWS KMS
- ✅ **Fully Compatible:** secp256k1, P-256, P-384, P-521, RSA (all sizes), Ed25519 (Nov 2025+)
- ⚠️ **Partial Compatibility:** BLS12-381 (not supported)
- ✅ **FIPS 140-3 Level 3 Validated:** P-256, P-384, P-521, RSA (all sizes), secp256k1 (blockchain only) - [Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884)
- ⚠️ **FIPS Status:** Ed25519 not in current FIPS certificate (added Nov 2025, may use non-FIPS path)
- **Best For:** Production deployments requiring FIPS 140-3 Level 3 compliance, Ed25519 support

### Azure Key Vault
- ✅ **Fully Compatible:** secp256k1, P-256, P-384, P-521, RSA (all sizes)
- ❌ **Not Compatible:** Ed25519, BLS12-381
- **Best For:** Azure ecosystem deployments, NIST-compliant algorithms, JWK native format

## Recommendations

### For General DID/VC Use Cases
1. **Ed25519** (if AWS KMS available) - Best performance, compact keys, widely supported
2. **secp256k1** - Good alternative, supported by both cloud KMS providers
3. **P-256** - Enterprise/government compliance requirements

### For Blockchain Integration
- **secp256k1** - Required for Ethereum/Bitcoin-based DIDs

### For Enterprise/Government
- **P-256/P-384/P-521** - FIPS 140-3 Level 3 validated (AWS KMS Certificate #4884), supported by both cloud providers
- **RSA-2048/3072/4096** - FIPS 140-3 Level 3 validated (AWS KMS Certificate #4884)

### For Privacy-Preserving Credentials
- **BLS12-381** - Requires specialized KMS (not available in AWS/Azure)

## Notes

1. **Ed25519 Support:** AWS KMS added Ed25519 support in November 2025. Azure Key Vault does not yet support Ed25519. Note: Ed25519 is not listed in AWS KMS's current FIPS 140-3 certificate (#4884) and may use a non-FIPS validated cryptographic path.

2. **FIPS 140-3 Compliance:** AWS KMS uses FIPS 140-3 Level 3 validated HSMs ([Certificate #4884](https://csrc.nist.gov/projects/cryptographic-module-validation-program/certificate/4884), validated 11/18/2024). The certificate approves ECDSA (FIPS 186-4) on NIST curves, ECDSA secp256k1 (blockchain use only), and RSA (FIPS 186-4) for all key sizes.

3. **Algorithm Selection:** Choose algorithms based on:
   - Interoperability requirements
   - Regulatory compliance needs
   - Cloud provider constraints
   - Performance requirements

4. **TrustWeave Default:** TrustWeave defaults to Ed25519 for DID creation and credential signing, which is the recommended algorithm for most use cases.

5. **Hybrid Approaches:** For Ed25519 requirements with Azure Key Vault, consider:
   - Using secp256k1 or P-256 as alternatives
   - Hybrid KMS approach (Azure for some keys, local/other KMS for Ed25519)
   - Using AWS KMS for Ed25519 support

## Related Documentation

- [Key Management Guide](./key-management.md)
- [Verifiable Credentials Guide](./verifiable-credentials.md)
- [DID Guide](./dids.md)
- [Architecture Overview](../introduction/architecture-overview.md)


