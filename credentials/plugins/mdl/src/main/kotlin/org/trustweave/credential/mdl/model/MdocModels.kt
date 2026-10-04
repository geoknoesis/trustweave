package org.trustweave.credential.mdl.model

import kotlin.time.Instant

/**
 * Top-level ISO 18013-5 mobile document.
 *
 * Contains issuer-signed data (MSO + namespace claims) and optional device-signed data
 * produced during a presentation.
 */
data class MobileDocument(
    val docType: String,
    val issuerSigned: IssuerSigned,
    val deviceSigned: DeviceSigned? = null,
    val errors: Map<String, Int>? = null,
)

/**
 * Issuer-signed portion of a mobile document.
 *
 * @property nameSpaces Map of namespace → list of issuer-signed claim items.
 * @property issuerAuth CBOR-encoded COSE_Sign1 structure containing the signed MSO.
 */
data class IssuerSigned(
    val nameSpaces: Map<String, List<IssuerSignedItem>>,
    val issuerAuth: ByteArray, // COSE_Sign1 bytes
) {
    override fun equals(other: Any?): Boolean =
        other is IssuerSigned &&
            nameSpaces == other.nameSpaces &&
            issuerAuth.contentEquals(other.issuerAuth)

    override fun hashCode(): Int = 31 * nameSpaces.hashCode() + issuerAuth.contentHashCode()
}

/**
 * A single claim within an IssuerSigned namespace.
 *
 * Per ISO 18013-5, each item is individually salted so selective disclosure is possible.
 */
data class IssuerSignedItem(
    val digestId: Int,
    val random: ByteArray, // 16-byte random salt
    val elementIdentifier: String, // claim name, e.g. "family_name"
    val elementValue: Any, // claim value (String, Int, Boolean, ByteArray, etc.)
) {
    override fun equals(other: Any?): Boolean =
        other is IssuerSignedItem &&
            digestId == other.digestId &&
            random.contentEquals(other.random) &&
            elementIdentifier == other.elementIdentifier &&
            valuesEqual(elementValue, other.elementValue)

    override fun hashCode(): Int {
        var result = digestId
        result = 31 * result + random.contentHashCode()
        result = 31 * result + elementIdentifier.hashCode()
        result = 31 * result + (if (elementValue is ByteArray) elementValue.contentHashCode() else elementValue.hashCode())
        return result
    }

    // ByteArray values compare by content, not identity.
    private fun valuesEqual(
        a: Any,
        b: Any,
    ): Boolean = if (a is ByteArray && b is ByteArray) a.contentEquals(b) else a == b
}

/**
 * Mobile Security Object — the data signed by the issuer.
 *
 * Contains SHA-256 digests of each IssuerSignedItem so verifiers can check integrity
 * without needing the raw claim bytes.
 */
data class MobileSecurityObject(
    val version: String = "1.0",
    val digestAlgorithm: String = "SHA-256",
    /** namespace → digestId → SHA-256 digest of the IssuerSignedItem CBOR bytes */
    val valueDigests: Map<String, Map<Int, ByteArray>>,
    val deviceKeyInfo: DeviceKeyInfo,
    val docType: String,
    val validityInfo: ValidityInfo,
)

/** Device public key information embedded in the MSO. */
data class DeviceKeyInfo(
    val deviceKey: ByteArray, // CBOR-encoded COSE_Key of the device's public key
    val keyAuthorizations: Map<String, Any>? = null,
    val keyInfo: Map<String, Any>? = null,
) {
    override fun equals(other: Any?): Boolean = other is DeviceKeyInfo && deviceKey.contentEquals(other.deviceKey)

    override fun hashCode(): Int = deviceKey.contentHashCode()
}

/** Validity period for the MSO. */
data class ValidityInfo(
    val signed: Instant,
    val validFrom: Instant,
    val validUntil: Instant,
    val expectedUpdate: Instant? = null,
)

/** Device-signed portion (produced during presentation). */
data class DeviceSigned(
    val nameSpaces: ByteArray, // CBOR-encoded device namespace bytes
    val deviceAuth: DeviceAuth,
) {
    override fun equals(other: Any?): Boolean =
        other is DeviceSigned &&
            nameSpaces.contentEquals(other.nameSpaces) &&
            deviceAuth == other.deviceAuth

    override fun hashCode(): Int = 31 * nameSpaces.contentHashCode() + deviceAuth.hashCode()
}

/** Device authentication structure (COSE_Sign1 or COSE_Mac0). */
data class DeviceAuth(
    val deviceSignature: ByteArray? = null, // COSE_Sign1 bytes
    val deviceMac: ByteArray? = null, // COSE_Mac0 bytes
) {
    override fun equals(other: Any?): Boolean =
        other is DeviceAuth &&
            deviceSignature.contentEquals(other.deviceSignature) &&
            deviceMac.contentEquals(other.deviceMac)

    override fun hashCode(): Int = 31 * (deviceSignature?.contentHashCode() ?: 0) + (deviceMac?.contentHashCode() ?: 0)
}
