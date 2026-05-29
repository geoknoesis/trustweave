package org.trustweave.referencewallet.lib

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey

/**
 * Ed25519 + did:key + JWS helpers.
 *
 * Mirrors the TypeScript `reference-wallet/lib/crypto.ts` so the Android and web wallets
 * stay conceptually aligned. Uses Bouncy Castle for raw Ed25519 because the Android JCA
 * Ed25519 support is uneven across API levels (added in API 33).
 *
 * did:key spec: https://w3c-ccg.github.io/did-method-key/
 * Ed25519 multicodec prefix: 0xED 0x01, then 32-byte raw pubkey, then base58btc.
 */
object Crypto {

    private const val ED25519_OID = "1.3.101.112"  // RFC 8410

    data class KeyPairBytes(val publicKey: ByteArray, val privateKey: ByteArray) {
        // Equals/hashCode generated for value semantics over the byte arrays.
        override fun equals(other: Any?): Boolean =
            other is KeyPairBytes &&
                publicKey.contentEquals(other.publicKey) &&
                privateKey.contentEquals(other.privateKey)
        override fun hashCode(): Int =
            publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
    }

    /** Generate a new Ed25519 key pair. */
    fun generateEd25519(): KeyPairBytes {
        val kpg = KeyPairGenerator.getInstance("Ed25519", "BC")
        val kp = kpg.generateKeyPair()
        return KeyPairBytes(
            publicKey = extractRawPublic(kp.public),
            privateKey = extractRawPrivate(kp.private),
        )
    }

    /** Encode a raw 32-byte Ed25519 public key as did:key per W3C method-key spec. */
    fun publicKeyToDidKey(publicKey: ByteArray): String {
        require(publicKey.size == 32) {
            "Ed25519 public key must be 32 bytes, got ${publicKey.size}"
        }
        val prefixed = ByteArray(2 + 32)
        prefixed[0] = 0xed.toByte()
        prefixed[1] = 0x01.toByte()
        System.arraycopy(publicKey, 0, prefixed, 2, 32)
        return "did:key:z${Base58.encode(prefixed)}"
    }

    /** Resolve a did:key back to its raw 32-byte Ed25519 public key. */
    fun didKeyToPublicKey(did: String): ByteArray {
        require(did.startsWith("did:key:z")) { "Not a did:key: $did" }
        val decoded = Base58.decode(did.substring("did:key:z".length))
        require(decoded.size == 34 && decoded[0] == 0xed.toByte() && decoded[1] == 0x01.toByte()) {
            "Not an Ed25519 did:key: $did"
        }
        return decoded.copyOfRange(2, 34)
    }

    /** Sign with raw Ed25519 private key bytes. Returns raw 64-byte signature. */
    fun signEd25519(payload: ByteArray, privateKey: ByteArray): ByteArray {
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initSign(rawPrivateToKey(privateKey))
        sig.update(payload)
        return sig.sign()
    }

    /** Verify raw Ed25519 signature. */
    fun verifyEd25519(signature: ByteArray, payload: ByteArray, publicKey: ByteArray): Boolean {
        val sig = Signature.getInstance("Ed25519", "BC")
        sig.initVerify(rawPublicToKey(publicKey))
        sig.update(payload)
        return sig.verify(signature)
    }

    /** Base64url (RFC 4648 §5) without padding. */
    fun b64uEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun b64uDecode(s: String): ByteArray =
        Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun b64uEncodeString(s: String): String = b64uEncode(s.toByteArray(Charsets.UTF_8))
    fun b64uDecodeString(s: String): String = String(b64uDecode(s), Charsets.UTF_8)

    /**
     * Build a compact JWS (Ed25519, alg=EdDSA) over a JSON payload string.
     *
     * Caller is responsible for producing the JSON serialisation — that keeps this
     * function transport-neutral and lets the wallet facade choose how it serialises
     * its VP-JWT payload (kotlinx.serialization preferred).
     */
    fun signJwsCompact(
        jsonPayload: String,
        privateKey: ByteArray,
        kid: String,
    ): String {
        val header = """{"alg":"EdDSA","typ":"JWT","kid":"$kid"}"""
        val encodedHeader = b64uEncodeString(header)
        val encodedPayload = b64uEncodeString(jsonPayload)
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = signEd25519(signingInput.toByteArray(Charsets.UTF_8), privateKey)
        return "$signingInput.${b64uEncode(signature)}"
    }

    // ----- internal: raw <-> JCA key conversions -----

    private fun extractRawPublic(key: PublicKey): ByteArray {
        // BCEdDSAPublicKey exposes raw 32-byte form via getPointEncoding.
        if (key is BCEdDSAPublicKey) {
            // Try the public API first.
            val raw = (key as? BCEdDSAPublicKey)?.let { runCatching { it.pointEncoding }.getOrNull() }
            if (raw != null && raw.size == 32) return raw
        }
        // Fallback: parse the SubjectPublicKeyInfo encoded form.
        val spki = SubjectPublicKeyInfo.getInstance(key.encoded)
        return spki.publicKeyData.bytes
    }

    private fun extractRawPrivate(key: PrivateKey): ByteArray {
        if (key is BCEdDSAPrivateKey) {
            val raw = runCatching { key.encoded }.getOrNull()
            if (raw != null) {
                // The PrivateKeyInfo wraps the seed in an OCTET STRING inside an OCTET STRING.
                val pki = PrivateKeyInfo.getInstance(raw)
                val inner = pki.privateKey.octets
                // inner is itself a DER OCTET STRING (0x04 0x20 <32 bytes>)
                if (inner.size == 34 && inner[0] == 0x04.toByte() && inner[1] == 0x20.toByte()) {
                    return inner.copyOfRange(2, 34)
                }
                if (inner.size == 32) return inner
            }
        }
        throw IllegalStateException("Could not extract raw Ed25519 private key")
    }

    private fun rawPublicToKey(raw: ByteArray): PublicKey {
        require(raw.size == 32) { "Ed25519 public key must be 32 bytes" }
        val params = Ed25519PublicKeyParameters(raw, 0)
        val spki = SubjectPublicKeyInfo(AlgorithmIdentifier(ASN1ObjectIdentifier(ED25519_OID)), params.encoded)
        val kf = KeyFactory.getInstance("Ed25519", "BC")
        return kf.generatePublic(java.security.spec.X509EncodedKeySpec(spki.encoded))
    }

    private fun rawPrivateToKey(raw: ByteArray): PrivateKey {
        require(raw.size == 32) { "Ed25519 private key must be 32 bytes" }
        val params = Ed25519PrivateKeyParameters(raw, 0)
        // Inner DER OCTET STRING per RFC 8410.
        val inner = byteArrayOf(0x04, 0x20) + params.encoded
        val pki = PrivateKeyInfo(AlgorithmIdentifier(ASN1ObjectIdentifier(ED25519_OID)), org.bouncycastle.asn1.DEROctetString(inner).toASN1Primitive())
        val kf = KeyFactory.getInstance("Ed25519", "BC")
        return kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(pki.encoded))
    }
}
