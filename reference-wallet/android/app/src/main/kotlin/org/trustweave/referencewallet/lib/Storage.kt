package org.trustweave.referencewallet.lib

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Encrypted local storage for the wallet — holder identity + credentials.
 *
 * Phase 2.5b: HolderIdentity now records which backend stores the signing key
 * (`keySource = "keystore"` on API 33+ vs `"software"` on older). When keystore-backed,
 * `softwarePrivateKey` is null and the actual key material lives in AndroidKeyStore
 * under [keystoreAlias]; we only keep the public key here for display + did:key
 * derivation. On the software path the private key seed continues to sit in
 * EncryptedSharedPreferences (Phase 2 baseline).
 *
 * Schema: still v2 (the HolderIdentity additions are additive with safe defaults).
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

    init {
        // v1 → v2 migration: old shape had `vcJwt` instead of `credential`+`format`.
        // Wipe and start fresh; acceptable for a demo wallet.
        val existingVersion = prefs.getString(KEY_SCHEMA_VERSION, null)
        if (existingVersion == null) {
            prefs.edit().putString(KEY_SCHEMA_VERSION, CURRENT_VERSION.toString()).apply()
        } else if (existingVersion.toIntOrNull() != CURRENT_VERSION) {
            prefs.edit().clear().putString(KEY_SCHEMA_VERSION, CURRENT_VERSION.toString()).apply()
        }
    }

    @Serializable
    data class HolderIdentity(
        val did: String,
        val publicKey: String,                         // base64url — always present
        /** "software" (Phase 2 + API 29-32) or "keystore" (API 33+ Keystore-bound). */
        val keySource: String = "software",
        /** base64url. Present only when keySource="software". */
        val softwarePrivateKey: String? = null,
        /** Keystore alias. Present only when keySource="keystore". */
        val keystoreAlias: String? = null,
        val createdAt: String,
    )

    @Serializable
    data class StoredCredential(
        val id: String,
        val format: String,
        val credential: String,
        val receivedAt: String,
        val issuerDid: String,
        val subjectDid: String,
        val type: List<String>,
        val previewTitle: String,
        val previewSubtitle: String? = null,
        val selectivelyDisclosable: List<String> = emptyList(),
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

    fun addCredential(cred: StoredCredential) { saveCredentials(loadCredentials() + cred) }
    fun deleteCredential(id: String) { saveCredentials(loadCredentials().filterNot { it.id == id }) }
    fun reset() { prefs.edit().clear().apply() }

    companion object {
        private const val KEY_HOLDER = "holder"
        private const val KEY_CREDENTIALS = "credentials"
        private const val KEY_SCHEMA_VERSION = "schema-version"
        private const val CURRENT_VERSION = 2
    }
}
