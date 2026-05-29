package org.trustweave.referencewallet.lib

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

/**
 * Phase 2.5b: holder signing-key abstraction.
 *
 * Two backends:
 *  - [Keystore] (API 33+): Ed25519 key is generated INSIDE Android Keystore. The
 *    private key never enters userspace memory; signing happens inside the Keystore
 *    process. This is the "Keystore-bound holder key" the design doc calls for.
 *  - [Software]: classic Ed25519 raw-byte private key, generated and signed via
 *    Bouncy Castle in process. Stored encrypted in EncryptedSharedPreferences.
 *    Used on API 29-32 (no Ed25519-in-Keystore support), and as a fallback when
 *    Keystore Ed25519 generation fails on 33+ devices that disabled it.
 *
 * The Wallet facade calls only [HolderKey.publicKey] / [HolderKey.sign]; it
 * doesn't know or care which backend is in use.
 */
sealed interface HolderKey {
    /** Raw 32-byte Ed25519 public key. */
    val publicKey: ByteArray

    /** Sign [data] with the holder's Ed25519 private key. Returns raw 64-byte signature. */
    fun sign(data: ByteArray): ByteArray

    /** Keystore-bound Ed25519 (API 33+). Private key never enters userspace memory. */
    data class Keystore internal constructor(
        override val publicKey: ByteArray,
        private val alias: String,
    ) : HolderKey {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun sign(data: ByteArray): ByteArray {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = ks.getEntry(alias, null) as KeyStore.PrivateKeyEntry
            // No explicit "BC" provider — AndroidKeyStore handles Ed25519 natively on API 33+.
            val sig = Signature.getInstance("Ed25519")
            sig.initSign(entry.privateKey)
            sig.update(data)
            return sig.sign()
        }

        // Manual equals/hashCode because Kotlin doesn't generate them correctly for ByteArray.
        override fun equals(other: Any?): Boolean =
            other is Keystore && publicKey.contentEquals(other.publicKey) && alias == other.alias
        override fun hashCode(): Int = publicKey.contentHashCode() * 31 + alias.hashCode()
    }

    /** Software Ed25519 — raw private key bytes loaded from encrypted storage. */
    data class Software internal constructor(
        override val publicKey: ByteArray,
        private val privateKey: ByteArray,
    ) : HolderKey {
        override fun sign(data: ByteArray): ByteArray =
            Crypto.signEd25519(data, privateKey)

        override fun equals(other: Any?): Boolean =
            other is Software && publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
        override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "trustweave-wallet-holder-ed25519"

        /**
         * Generate a fresh holder key. Prefers Keystore-bound Ed25519 on API 33+;
         * falls back to software on older devices or if Keystore generation fails
         * (some 33+ vendors disable Ed25519 in their Keystore implementation).
         */
        fun generate(alias: String = DEFAULT_ALIAS): GeneratedKey {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { generateKeystore(alias) }
                    .onSuccess { return GeneratedKey(it, source = "keystore", softwarePrivateKey = null, keystoreAlias = alias) }
            }
            val sw = Crypto.generateEd25519()
            return GeneratedKey(
                key = Software(publicKey = sw.publicKey, privateKey = sw.privateKey),
                source = "software",
                softwarePrivateKey = sw.privateKey,
                keystoreAlias = null,
            )
        }

        /**
         * Restore a previously-stored holder key. Caller decides the source
         * (typically from the Storage.HolderIdentity).
         */
        fun restoreKeystore(publicKey: ByteArray, alias: String): HolderKey = Keystore(publicKey, alias)
        fun restoreSoftware(publicKey: ByteArray, privateKey: ByteArray): HolderKey = Software(publicKey, privateKey)

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun generateKeystore(alias: String): Keystore {
            // KeyProperties.KEY_ALGORITHM_ED25519 is on the constant table only from
            // API 35 (compileSdk 34 here), but the underlying algorithm string is
            // accepted by AndroidKeyStore on some API 33+ devices. Use the literal so
            // we can compile against SDK 34 and let runtime decide. The outer
            // runCatching in generate() handles devices where it's unsupported by
            // falling back to software.
            val kpg = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
            kpg.initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY).build(),
            )
            val kp = kpg.generateKeyPair()
            val publicRaw = extractRawPublic(kp.public.encoded)
            return Keystore(publicRaw, alias)
        }

        /**
         * Extract the raw 32-byte Ed25519 public key from a JCA-encoded SubjectPublicKeyInfo.
         * For Ed25519 the SPKI is short (12-byte header + 32-byte key); the trailing 32 bytes
         * are the raw point encoding.
         */
        private fun extractRawPublic(spki: ByteArray): ByteArray {
            require(spki.size >= 32) { "SPKI shorter than expected: ${spki.size}" }
            return spki.copyOfRange(spki.size - 32, spki.size)
        }
    }

    /** Tuple returned from [generate] so the caller can persist the right metadata. */
    data class GeneratedKey(
        val key: HolderKey,
        val source: String,
        val softwarePrivateKey: ByteArray?,
        val keystoreAlias: String?,
    )
}
