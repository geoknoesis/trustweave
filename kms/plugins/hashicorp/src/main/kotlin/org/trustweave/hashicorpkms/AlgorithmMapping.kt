package org.trustweave.hashicorpkms

import org.trustweave.kms.Algorithm
import org.trustweave.kms.JwkKeyTypes
import org.trustweave.kms.JwkKeys
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Utilities for mapping between TrustWeave Algorithm types and HashiCorp Vault Transit key types.
 */
object AlgorithmMapping {
    /**
     * Maps TrustWeave Algorithm to Vault Transit key type.
     *
     * @param algorithm TrustWeave algorithm
     * @return Vault Transit key type string
     * @throws IllegalArgumentException if algorithm is not supported by Vault Transit
     */
    fun toVaultKeyType(algorithm: Algorithm): String =
        when (algorithm) {
            is Algorithm.Ed25519 -> "ed25519"
            is Algorithm.Secp256k1 -> "ecdsa-p256k1"
            is Algorithm.P256 -> "ecdsa-p256"
            is Algorithm.P384 -> "ecdsa-p384"
            is Algorithm.P521 -> "ecdsa-p521"
            is Algorithm.RSA -> {
                when (algorithm.keySize) {
                    2048 -> "rsa-2048"
                    3072 -> "rsa-3072"
                    4096 -> "rsa-4096"
                    else -> throw IllegalArgumentException("Unsupported RSA key size: ${algorithm.keySize}")
                }
            }
            else -> throw IllegalArgumentException("Algorithm ${algorithm.name} is not supported by Vault Transit")
        }

    /**
     * Parses Vault Transit key type to TrustWeave Algorithm.
     *
     * @param keyType Vault Transit key type string
     * @return TrustWeave Algorithm, or null if not recognized
     */
    fun fromVaultKeyType(keyType: String): Algorithm? =
        when (keyType.lowercase()) {
            "ed25519" -> Algorithm.Ed25519
            "ecdsa-p256k1" -> Algorithm.Secp256k1
            "ecdsa-p256" -> Algorithm.P256
            "ecdsa-p384" -> Algorithm.P384
            "ecdsa-p521" -> Algorithm.P521
            "rsa-2048" -> Algorithm.RSA.RSA_2048
            "rsa-3072" -> Algorithm.RSA.RSA_3072
            "rsa-4096" -> Algorithm.RSA.RSA_4096
            else -> null
        }

    /**
     * Maps TrustWeave Algorithm to Vault Transit hash algorithm for signing.
     *
     * @param algorithm TrustWeave algorithm
     * @return Vault Transit hash algorithm string
     */
    fun toVaultHashAlgorithm(algorithm: Algorithm): String =
        when (algorithm) {
            is Algorithm.Ed25519 -> "sha2-256" // Ed25519 uses SHA-256 internally
            is Algorithm.Secp256k1 -> "sha2-256"
            is Algorithm.P256 -> "sha2-256"
            is Algorithm.P384 -> "sha2-384"
            is Algorithm.P521 -> "sha2-512"
            is Algorithm.RSA -> "sha2-256"
            else -> "sha2-256"
        }

    /**
     * Resolves a key identifier to a Vault Transit key name.
     *
     * Vault Transit uses key names (not IDs) to identify keys.
     * The key name should be URL-safe and descriptive.
     *
     * @param keyId Key identifier (can be key name or full path)
     * @param config Vault configuration
     * @return Resolved key name for Vault API
     */
    fun resolveKeyName(
        keyId: String,
        config: VaultKmsConfig,
    ): String {
        // If keyId already contains the transit path, extract just the key name
        val transitPrefix = "${config.transitPath}/keys/"
        val transitPrefixWithSlash = "/${config.transitPath}/keys/"

        return when {
            keyId.startsWith(transitPrefix) -> keyId.substringAfter(transitPrefix)
            keyId.startsWith(transitPrefixWithSlash) -> keyId.substringAfter(transitPrefixWithSlash)
            keyId.startsWith("/") -> keyId.substring(1)
            else -> keyId
        }
    }

    /**
     * Converts a Vault Transit public key to JWK format.
     *
     * Accepts a PEM block (`PUBLIC KEY`; also `EC PUBLIC KEY` for EC keys) or bare base64, with
     * any whitespace or line endings (`\n`, `\r\n`) in the body, and verifies the decoded key is
     * of the expected [algorithm]: for EC, an ecPublicKey with that curve's OID and a point on the
     * curve; for RSA, an RSA key of the expected modulus size; for Ed25519, the raw 32-byte key or
     * its exact RFC 8410 SubjectPublicKeyInfo.
     *
     * @param publicKeyPem PEM-encoded (or bare base64) public key from Vault
     * @param algorithm The algorithm the key must be
     * @return JWK map representation
     * @throws IllegalArgumentException if the key cannot be parsed or is not of [algorithm]
     */
    fun publicKeyPemToJwk(
        publicKeyPem: String,
        algorithm: Algorithm,
    ): Map<String, Any?> {
        val b64url = Base64.getUrlEncoder().withoutPadding()
        try {
            when (algorithm) {
                is Algorithm.Ed25519 -> {
                    val keyBytes = PemPublicKeys.der(publicKeyPem, setOf("PUBLIC KEY"))
                    // Transit returns raw Ed25519 bytes. Also accept the exact RFC 8410 SPKI
                    // representation; never silently truncate an arbitrary DER/key type.
                    val prefix =
                        java.util.HexFormat
                            .of()
                            .parseHex("302a300506032b6570032100")
                    val rawKey =
                        when {
                            keyBytes.size == 32 -> keyBytes
                            keyBytes.size == prefix.size + 32 && keyBytes.copyOfRange(0, prefix.size).contentEquals(prefix) ->
                                keyBytes.copyOfRange(prefix.size, keyBytes.size)
                            else -> throw IllegalArgumentException("Invalid Ed25519 public-key encoding")
                        }

                    return mapOf(
                        JwkKeys.KTY to JwkKeyTypes.OKP,
                        JwkKeys.CRV to Algorithm.Ed25519.curveName,
                        JwkKeys.X to b64url.encodeToString(rawKey),
                    )
                }
                is Algorithm.Secp256k1, is Algorithm.P256, is Algorithm.P384, is Algorithm.P521 -> {
                    val curve =
                        when (algorithm) {
                            is Algorithm.Secp256k1 -> PemPublicKeys.SECP256K1
                            is Algorithm.P256 -> PemPublicKeys.P256
                            is Algorithm.P384 -> PemPublicKeys.P384
                            else -> PemPublicKeys.P521
                        }
                    val curveName =
                        algorithm.curveName
                            ?: throw IllegalArgumentException("Unsupported EC algorithm: ${algorithm.name}")
                    val spki = PemPublicKeys.der(publicKeyPem, setOf("PUBLIC KEY", "EC PUBLIC KEY"))
                    val (x, y) = PemPublicKeys.ecPoint(spki, curve)

                    return mapOf(
                        JwkKeys.KTY to JwkKeyTypes.EC,
                        JwkKeys.CRV to curveName,
                        JwkKeys.X to b64url.encodeToString(x),
                        JwkKeys.Y to b64url.encodeToString(y),
                    )
                }
                is Algorithm.RSA -> {
                    val keyBytes = PemPublicKeys.der(publicKeyPem, setOf("PUBLIC KEY"))
                    // KeyFactory("RSA") rejects any SubjectPublicKeyInfo that is not rsaEncryption.
                    val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes)) as RSAPublicKey
                    require(publicKey.modulus.bitLength() == algorithm.keySize) {
                        "Expected a ${algorithm.keySize}-bit RSA key, got ${publicKey.modulus.bitLength()} bits"
                    }

                    // Convert BigInteger to unsigned byte array
                    fun toUnsignedByteArray(bigInt: java.math.BigInteger): ByteArray {
                        val signed = bigInt.toByteArray()
                        if (signed.isNotEmpty() && signed[0] == 0.toByte()) {
                            return signed.sliceArray(1 until signed.size)
                        }
                        return signed
                    }

                    return mapOf(
                        JwkKeys.KTY to JwkKeyTypes.RSA,
                        JwkKeys.N to b64url.encodeToString(toUnsignedByteArray(publicKey.modulus)),
                        JwkKeys.E to b64url.encodeToString(toUnsignedByteArray(publicKey.publicExponent)),
                    )
                }
                else -> throw IllegalArgumentException("Unsupported algorithm for JWK conversion: ${algorithm.name}")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to convert Vault public key to JWK: ${e.message}", e)
        }
    }
}
