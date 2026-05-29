package org.trustweave.referencewallet.lib

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Encrypted local storage for the wallet — holder identity + credentials.
 *
 * Phase 2 baseline: EncryptedSharedPreferences derives its wrapping key from Android
 * Keystore, so the on-disk file is non-extractable without device unlock. Phase 2.5
 * binds the holder signing key DIRECTLY to Keystore.
 *
 * Phase 2.5: storage shape upgraded to v2 — credentials carry their format
 * (`vc+jwt` vs `vc+sd-jwt`) and the issuer-declared list of selectively-disclosable
 * claim names (for the present-screen UI).
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
        // v1 → v2 migration: if we detect old-shape data, wipe it. Acceptable for a
        // demo; a real wallet would migrate in place. The v1 schema had `vcJwt`
        // instead of `credential` + `format`; an older serialized cred deserialised
        // through the v2 schema would either fail or load with empty fields.
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
        val publicKey: String,   // base64url
        val privateKey: String,  // base64url
        val createdAt: String,
    )

    @Serializable
    data class StoredCredential(
        val id: String,
        val format: String,  // "vc+jwt" or "vc+sd-jwt"
        val credential: String,  // VC-JWT or SD-JWT VC compact form
        val receivedAt: String,
        val issuerDid: String,
        val subjectDid: String,
        val type: List<String>,
        val previewTitle: String,
        val previewSubtitle: String? = null,
        /** SD-JWT VC only: claim names the issuer marked selectively-disclosable. */
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
