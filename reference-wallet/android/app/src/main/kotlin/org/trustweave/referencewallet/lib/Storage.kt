package org.trustweave.referencewallet.lib

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Encrypted local storage for the wallet — holder identity + credentials.
 *
 * Backed by EncryptedSharedPreferences, which derives its wrapping key from Android
 * Keystore. The on-disk SharedPreferences file is unreadable without the device's
 * Keystore-bound key, which requires device unlock to access.
 *
 * Phase 2 baseline. Phase 2.5 binds the holder signing key DIRECTLY to Keystore (as a
 * non-extractable Ed25519 key on API 33+, or a wrapped Ed25519 seed below that), at
 * which point the private-key blob leaves this storage.
 */
class Storage(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "trustweave-wallet",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    data class HolderIdentity(
        val did: String,
        val publicKey: String,   // base64url
        val privateKey: String,  // base64url
        val createdAt: String,
    )

    @Serializable
    data class StoredCredential(
        val id: String,
        val vcJwt: String,
        val receivedAt: String,
        val issuerDid: String,
        val subjectDid: String,
        val type: List<String>,
        val previewTitle: String,
        val previewSubtitle: String? = null,
    )

    fun loadHolder(): HolderIdentity? =
        prefs.getString(KEY_HOLDER, null)?.let { json.decodeFromString(HolderIdentity.serializer(), it) }

    fun saveHolder(holder: HolderIdentity) {
        prefs.edit().putString(KEY_HOLDER, json.encodeToString(HolderIdentity.serializer(), holder)).apply()
    }

    fun loadCredentials(): List<StoredCredential> =
        prefs.getString(KEY_CREDENTIALS, null)?.let {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(StoredCredential.serializer()), it)
        } ?: emptyList()

    fun saveCredentials(creds: List<StoredCredential>) {
        prefs.edit().putString(
            KEY_CREDENTIALS,
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(StoredCredential.serializer()), creds),
        ).apply()
    }

    fun addCredential(cred: StoredCredential) {
        saveCredentials(loadCredentials() + cred)
    }

    fun deleteCredential(id: String) {
        saveCredentials(loadCredentials().filterNot { it.id == id })
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_HOLDER = "holder"
        private const val KEY_CREDENTIALS = "credentials"
    }
}
