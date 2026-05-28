package org.trustweave.credential.mdl.engine

import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import org.trustweave.credential.mdl.model.*
import kotlinx.datetime.Instant
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * CBOR encoding/decoding for ISO 18013-5 mDoc structures.
 *
 * Uses Peter Occil's cbor-java library which correctly handles tagged values,
 * indefinite-length encoding, and full CBOR tag compliance required by ISO 18013-5.
 */
internal object MdocCbor {

    private val random = SecureRandom()

    // CBOR tag 6 = CBOR-encoded data item (bstr-wrapped CBOR)
    private const val TAG_ENCODED_CBOR = 24

    // ---------------------------------------------------------------------------
    // IssuerSignedItem encoding
    // ---------------------------------------------------------------------------

    /** Encode a single [IssuerSignedItem] to CBOR bytes (for digest computation). */
    fun encodeIssuerSignedItem(item: IssuerSignedItem): ByteArray {
        val map = CBORObject.NewOrderedMap()
        map["digestID"] = CBORObject.FromObject(item.digestId)
        map["random"] = CBORObject.FromObject(item.random)
        map["elementIdentifier"] = CBORObject.FromObject(item.elementIdentifier)
        map["elementValue"] = toCborValue(item.elementValue)
        return map.EncodeToBytes()
    }

    /** Decode CBOR bytes back to an [IssuerSignedItem]. */
    fun decodeIssuerSignedItem(bytes: ByteArray): IssuerSignedItem {
        val map = CBORObject.DecodeFromBytes(bytes)
        return IssuerSignedItem(
            digestId = map["digestID"].AsInt32(),
            random = map["random"].GetByteString(),
            elementIdentifier = map["elementIdentifier"].AsString(),
            elementValue = fromCborValue(map["elementValue"])
        )
    }

    /** Compute SHA-256 digest of an encoded IssuerSignedItem. */
    fun digestItem(itemBytes: ByteArray, algorithm: String = "SHA-256"): ByteArray {
        return MessageDigest.getInstance(algorithm).digest(itemBytes)
    }

    /** Generate a 16-byte random salt for an IssuerSignedItem. */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    // ---------------------------------------------------------------------------
    // MSO encoding
    // ---------------------------------------------------------------------------

    /** Encode an [MobileSecurityObject] to CBOR bytes (the payload of COSE_Sign1). */
    fun encodeMso(mso: MobileSecurityObject): ByteArray {
        val map = CBORObject.NewOrderedMap()
        map["version"] = CBORObject.FromObject(mso.version)
        map["digestAlgorithm"] = CBORObject.FromObject(mso.digestAlgorithm)

        // valueDigests: namespace → { digestId → bstr }
        val vd = CBORObject.NewOrderedMap()
        mso.valueDigests.forEach { (ns, digests) ->
            val nsMap = CBORObject.NewOrderedMap()
            digests.forEach { (id, digest) ->
                nsMap[CBORObject.FromObject(id)] = CBORObject.FromObject(digest)
            }
            vd[ns] = nsMap
        }
        map["valueDigests"] = vd

        // deviceKeyInfo
        val dki = CBORObject.NewOrderedMap()
        dki["deviceKey"] = if (mso.deviceKeyInfo.deviceKey.isEmpty()) {
            CBORObject.NewOrderedMap()  // empty COSE_Key placeholder when no device key is bound
        } else {
            CBORObject.DecodeFromBytes(mso.deviceKeyInfo.deviceKey)
        }
        map["deviceKeyInfo"] = dki

        map["docType"] = CBORObject.FromObject(mso.docType)

        // validityInfo
        val vi = CBORObject.NewOrderedMap()
        vi["signed"] = encodeInstant(mso.validityInfo.signed)
        vi["validFrom"] = encodeInstant(mso.validityInfo.validFrom)
        vi["validUntil"] = encodeInstant(mso.validityInfo.validUntil)
        mso.validityInfo.expectedUpdate?.let { vi["expectedUpdate"] = encodeInstant(it) }
        map["validityInfo"] = vi

        return map.EncodeToBytes()
    }

    /** Decode an [MobileSecurityObject] from CBOR bytes. */
    fun decodeMso(bytes: ByteArray): MobileSecurityObject {
        val map = CBORObject.DecodeFromBytes(bytes)
        val vd = map["valueDigests"]
        val valueDigests = mutableMapOf<String, Map<Int, ByteArray>>()
        vd.entries.forEach { entry ->
            val ns = entry.key.AsString()
            val digestMap = mutableMapOf<Int, ByteArray>()
            entry.value.entries.forEach { d ->
                digestMap[d.key.AsInt32()] = d.value.GetByteString()
            }
            valueDigests[ns] = digestMap
        }
        val dki = map["deviceKeyInfo"]
        val deviceKey = dki["deviceKey"].EncodeToBytes()
        val vi = map["validityInfo"]
        return MobileSecurityObject(
            version = map["version"].AsString(),
            digestAlgorithm = map["digestAlgorithm"].AsString(),
            valueDigests = valueDigests,
            deviceKeyInfo = DeviceKeyInfo(deviceKey = deviceKey),
            docType = map["docType"].AsString(),
            validityInfo = ValidityInfo(
                signed = decodeInstant(vi["signed"]),
                validFrom = decodeInstant(vi["validFrom"]),
                validUntil = decodeInstant(vi["validUntil"]),
                expectedUpdate = vi["expectedUpdate"]?.let { decodeInstant(it) }
            )
        )
    }

    // ---------------------------------------------------------------------------
    // MobileDocument encoding
    // ---------------------------------------------------------------------------

    /** Encode a [MobileDocument] to CBOR bytes (the DeviceResponse document entry). */
    fun encodeMobileDocument(doc: MobileDocument): ByteArray {
        val map = CBORObject.NewOrderedMap()
        map["docType"] = CBORObject.FromObject(doc.docType)

        // issuerSigned
        val is_ = CBORObject.NewOrderedMap()
        val nsMap = CBORObject.NewOrderedMap()
        doc.issuerSigned.nameSpaces.forEach { (ns, items) ->
            val arr = CBORObject.NewArray()
            items.forEach { item ->
                // Each item is a tagged bstr (tag 24 = CBOR-encoded item)
                val itemBytes = encodeIssuerSignedItem(item)
                arr.Add(CBORObject.FromObjectAndTag(itemBytes, TAG_ENCODED_CBOR))
            }
            nsMap[ns] = arr
        }
        is_["nameSpaces"] = nsMap
        is_["issuerAuth"] = CBORObject.DecodeFromBytes(doc.issuerSigned.issuerAuth)
        map["issuerSigned"] = is_

        doc.deviceSigned?.let { ds ->
            val ds_ = CBORObject.NewOrderedMap()
            ds_["nameSpaces"] = CBORObject.DecodeFromBytes(ds.nameSpaces)
            val da = CBORObject.NewOrderedMap()
            ds.deviceAuth.deviceSignature?.let { da["deviceSignature"] = CBORObject.DecodeFromBytes(it) }
            ds.deviceAuth.deviceMac?.let { da["deviceMac"] = CBORObject.DecodeFromBytes(it) }
            ds_["deviceAuth"] = da
            map["deviceSigned"] = ds_
        }

        return map.EncodeToBytes()
    }

    /** Decode a [MobileDocument] from CBOR bytes. */
    fun decodeMobileDocument(bytes: ByteArray): MobileDocument {
        val map = CBORObject.DecodeFromBytes(bytes)
        val docType = map["docType"].AsString()
        val is_ = map["issuerSigned"]
        val nsMap = is_["nameSpaces"]
        val nameSpaces = mutableMapOf<String, List<IssuerSignedItem>>()
        nsMap.entries.forEach { entry ->
            val ns = entry.key.AsString()
            val items = (0 until entry.value.size()).map { i ->
                val taggedItem = entry.value[i]
                val itemBytes = if (taggedItem.HasMostOuterTag(TAG_ENCODED_CBOR)) {
                    taggedItem.GetByteString()
                } else {
                    taggedItem.EncodeToBytes()
                }
                decodeIssuerSignedItem(itemBytes)
            }
            nameSpaces[ns] = items
        }
        val issuerAuthBytes = is_["issuerAuth"].EncodeToBytes()
        val issuerSigned = IssuerSigned(nameSpaces = nameSpaces, issuerAuth = issuerAuthBytes)

        val deviceSigned = map["deviceSigned"]?.let { ds ->
            val da = ds["deviceAuth"]
            DeviceSigned(
                nameSpaces = ds["nameSpaces"].EncodeToBytes(),
                deviceAuth = DeviceAuth(
                    deviceSignature = da["deviceSignature"]?.EncodeToBytes(),
                    deviceMac = da["deviceMac"]?.EncodeToBytes()
                )
            )
        }
        return MobileDocument(docType = docType, issuerSigned = issuerSigned, deviceSigned = deviceSigned)
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun toCborValue(value: Any): CBORObject = when (value) {
        is String -> CBORObject.FromObject(value)
        is Int -> CBORObject.FromObject(value)
        is Long -> CBORObject.FromObject(value)
        is Boolean -> CBORObject.FromObject(value)
        is ByteArray -> CBORObject.FromObject(value)
        is Double -> CBORObject.FromObject(value)
        is Float -> CBORObject.FromObject(value)
        else -> CBORObject.FromObject(value.toString())
    }

    private fun fromCborValue(obj: CBORObject): Any = when {
        obj.type == CBORType.TextString -> obj.AsString()
        obj.type == CBORType.Integer -> obj.AsInt64Value()
        obj.type == CBORType.Boolean -> obj.AsBoolean()
        obj.type == CBORType.ByteString -> obj.GetByteString()
        obj.type == CBORType.FloatingPoint -> obj.AsDouble()
        else -> obj.ToJSONString()
    }

    // ISO 8601 full-date encoding: CBOR tdate (tag 0) or full-date string
    private fun encodeInstant(instant: Instant): CBORObject =
        CBORObject.FromObjectAndTag(instant.toString(), 0)  // tag 0 = ISO 8601 datetime string

    private fun decodeInstant(obj: CBORObject): Instant =
        Instant.parse(if (obj.HasMostOuterTag(0)) obj.Untag().AsString() else obj.AsString())
}
