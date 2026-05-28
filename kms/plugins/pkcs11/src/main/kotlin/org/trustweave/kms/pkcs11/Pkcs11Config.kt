package org.trustweave.kms.pkcs11

/**
 * Configuration for [Pkcs11KeyManagementService].
 *
 * This is the MVP configuration shape used by `kms:plugins:pkcs11`. The richer
 * configuration shape described in `docs/architecture/eidas-qes-design.md` §5.4
 * (SlotSelector / PinStrategy / KeyIdEncoding) is intentionally deferred — for
 * the MVP, keys are addressed by PKCS#11 label (mapped to [KeyId.value]).
 *
 * @property libraryPath Absolute path to the vendor PKCS#11 shared library
 *                       (e.g. `/usr/lib/softhsm/libsofthsm2.so` on Linux or
 *                       `C:\\SoftHSM2\\lib\\softhsm2-x64.dll` on Windows).
 * @property slot PKCS#11 slot index. Default `0` matches the first available slot.
 * @property providerName Friendly name suffix for the SunPKCS11 provider; multiple
 *                       `Pkcs11KeyManagementService` instances may coexist as long as
 *                       their provider names differ.
 * @property pin PKCS#11 user PIN, passed to `KeyStore.load(null, pin)`. May be `null`
 *              for tokens that do not require login.
 * @property enableSoftDelete When `true`, [Pkcs11KeyManagementService.deleteKey] returns
 *                            [org.trustweave.kms.results.DeleteKeyResult.Deleted] even when
 *                            the underlying provider fails to delete (many production HSMs
 *                            forbid programmatic key destruction). When `false` (default),
 *                            failures surface as
 *                            [org.trustweave.kms.results.DeleteKeyResult.Failure.Error].
 */
data class Pkcs11Config(
    val libraryPath: String,
    val slot: Int = 0,
    val providerName: String = "TrustWeave-PKCS11",
    val pin: CharArray? = null,
    val enableSoftDelete: Boolean = false,
) {
    init {
        require(libraryPath.isNotBlank()) {
            "Pkcs11Config.libraryPath must not be blank"
        }
    }

    /**
     * Equality is structural; PIN comparison uses [contentEquals] because [CharArray]
     * does not override [equals].
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Pkcs11Config) return false
        if (libraryPath != other.libraryPath) return false
        if (slot != other.slot) return false
        if (providerName != other.providerName) return false
        if (pin == null) {
            if (other.pin != null) return false
        } else {
            if (other.pin == null) return false
            if (!pin.contentEquals(other.pin)) return false
        }
        if (enableSoftDelete != other.enableSoftDelete) return false
        return true
    }

    override fun hashCode(): Int {
        var result = libraryPath.hashCode()
        result = 31 * result + slot
        result = 31 * result + providerName.hashCode()
        result = 31 * result + (pin?.contentHashCode() ?: 0)
        result = 31 * result + enableSoftDelete.hashCode()
        return result
    }

    /**
     * String form deliberately omits the PIN to avoid accidental leakage in logs.
     */
    override fun toString(): String =
        "Pkcs11Config(libraryPath='$libraryPath', slot=$slot, providerName='$providerName', " +
            "pin=${if (pin == null) "null" else "***"}, enableSoftDelete=$enableSoftDelete)"
}
