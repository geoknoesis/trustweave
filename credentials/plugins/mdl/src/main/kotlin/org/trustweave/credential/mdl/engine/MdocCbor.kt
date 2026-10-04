package org.trustweave.credential.mdl.engine

import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import org.trustweave.credential.mdl.MdocException
import org.trustweave.credential.mdl.model.DeviceAuth
import org.trustweave.credential.mdl.model.DeviceKeyInfo
import org.trustweave.credential.mdl.model.DeviceSigned
import org.trustweave.credential.mdl.model.IssuerSigned
import org.trustweave.credential.mdl.model.IssuerSignedItem
import org.trustweave.credential.mdl.model.MobileDocument
import org.trustweave.credential.mdl.model.MobileSecurityObject
import org.trustweave.credential.mdl.model.ValidityInfo
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.time.Instant

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
    fun decodeIssuerSignedItem(bytes: ByteArray): IssuerSignedItem =
        decoding("IssuerSignedItem") {
            val map = CBORObject.DecodeFromBytes(bytes)
            IssuerSignedItem(
                digestId = required(map, "digestID").AsInt32(),
                random = required(map, "random").GetByteString(),
                elementIdentifier = required(map, "elementIdentifier").AsString(),
                elementValue = fromCborValue(required(map, "elementValue")),
            )
        }

    /** Compute SHA-256 digest of an encoded IssuerSignedItem. */
    fun digestItem(
        itemBytes: ByteArray,
        algorithm: String = "SHA-256",
    ): ByteArray = MessageDigest.getInstance(algorithm).digest(itemBytes)

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
        dki["deviceKey"] =
            if (mso.deviceKeyInfo.deviceKey.isEmpty()) {
                CBORObject.NewOrderedMap() // empty COSE_Key placeholder when no device key is bound
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
    fun decodeMso(bytes: ByteArray): MobileSecurityObject =
        decoding("MobileSecurityObject") {
            val map = CBORObject.DecodeFromBytes(bytes)
            val vd = required(map, "valueDigests")
            val valueDigests = mutableMapOf<String, Map<Int, ByteArray>>()
            vd.entries.forEach { entry ->
                val ns = entry.key.AsString()
                val digestMap = mutableMapOf<Int, ByteArray>()
                entry.value.entries.forEach { d ->
                    digestMap[d.key.AsInt32()] = d.value.GetByteString()
                }
                valueDigests[ns] = digestMap
            }
            val dki = required(map, "deviceKeyInfo")
            val deviceKey = required(dki, "deviceKey").EncodeToBytes()
            val vi = required(map, "validityInfo")
            MobileSecurityObject(
                version = required(map, "version").AsString(),
                digestAlgorithm = required(map, "digestAlgorithm").AsString(),
                valueDigests = valueDigests,
                deviceKeyInfo = DeviceKeyInfo(deviceKey = deviceKey),
                docType = required(map, "docType").AsString(),
                validityInfo =
                    ValidityInfo(
                        signed = decodeInstant(required(vi, "signed")),
                        validFrom = decodeInstant(required(vi, "validFrom")),
                        validUntil = decodeInstant(required(vi, "validUntil")),
                        expectedUpdate = vi["expectedUpdate"]?.let { decodeInstant(it) },
                    ),
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
        val issuerSignedMap = CBORObject.NewOrderedMap()
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
        issuerSignedMap["nameSpaces"] = nsMap
        issuerSignedMap["issuerAuth"] = CBORObject.DecodeFromBytes(doc.issuerSigned.issuerAuth)
        map["issuerSigned"] = issuerSignedMap

        doc.deviceSigned?.let { ds ->
            val deviceSignedMap = CBORObject.NewOrderedMap()
            deviceSignedMap["nameSpaces"] = CBORObject.DecodeFromBytes(ds.nameSpaces)
            val da = CBORObject.NewOrderedMap()
            ds.deviceAuth.deviceSignature?.let { da["deviceSignature"] = CBORObject.DecodeFromBytes(it) }
            ds.deviceAuth.deviceMac?.let { da["deviceMac"] = CBORObject.DecodeFromBytes(it) }
            deviceSignedMap["deviceAuth"] = da
            map["deviceSigned"] = deviceSignedMap
        }

        return map.EncodeToBytes()
    }

    /** Decode a [MobileDocument] from CBOR bytes. */
    fun decodeMobileDocument(bytes: ByteArray): MobileDocument =
        decoding("MobileDocument") {
            val map = CBORObject.DecodeFromBytes(bytes)
            val docType = required(map, "docType").AsString()
            val issuerSignedMap = required(map, "issuerSigned")
            val nsMap = required(issuerSignedMap, "nameSpaces")
            val nameSpaces = mutableMapOf<String, List<IssuerSignedItem>>()
            nsMap.entries.forEach { entry ->
                val ns = entry.key.AsString()
                val items =
                    (0 until entry.value.size()).map { i ->
                        val taggedItem = entry.value[i]
                        val itemBytes =
                            if (taggedItem.HasMostOuterTag(TAG_ENCODED_CBOR)) {
                                taggedItem.GetByteString()
                            } else {
                                taggedItem.EncodeToBytes()
                            }
                        decodeIssuerSignedItem(itemBytes)
                    }
                nameSpaces[ns] = items
            }
            val issuerAuthBytes = required(issuerSignedMap, "issuerAuth").EncodeToBytes()
            val issuerSigned = IssuerSigned(nameSpaces = nameSpaces, issuerAuth = issuerAuthBytes)

            val deviceSigned =
                map["deviceSigned"]?.let { ds ->
                    val da = ds["deviceAuth"]
                    DeviceSigned(
                        nameSpaces = ds["nameSpaces"].EncodeToBytes(),
                        deviceAuth =
                            DeviceAuth(
                                deviceSignature = da["deviceSignature"]?.EncodeToBytes(),
                                deviceMac = da["deviceMac"]?.EncodeToBytes(),
                            ),
                    )
                }
            MobileDocument(docType = docType, issuerSigned = issuerSigned, deviceSigned = deviceSigned)
        }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /** Runs a decoder, turning any structural failure (missing field, wrong type, bad CBOR) into a typed [MdocException]. */
    private inline fun <T> decoding(
        what: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: MdocException) {
            throw e
        } catch (e: RuntimeException) {
            throw MdocException.malformed(what, e)
        }

    /** The value at [key]; a missing field is an error, never a silent null. */
    private fun required(
        map: CBORObject,
        key: String,
    ): CBORObject {
        if (map.type != CBORType.Map) throw IllegalStateException("expected a CBOR map when reading '$key'")
        return map[key] ?: throw IllegalStateException("missing required field '$key'")
    }

    private fun toCborValue(value: Any): CBORObject =
        when (value) {
            is String -> CBORObject.FromObject(value)
            is Int -> CBORObject.FromObject(value)
            is Long -> CBORObject.FromObject(value)
            is Boolean -> CBORObject.FromObject(value)
            is ByteArray -> CBORObject.FromObject(value)
            is Double -> CBORObject.FromObject(value)
            is Float -> CBORObject.FromObject(value)
            else -> CBORObject.FromObject(value.toString())
        }

    private fun fromCborValue(obj: CBORObject): Any =
        when {
            obj.type == CBORType.TextString -> obj.AsString()
            obj.type == CBORType.Integer -> obj.AsInt64Value()
            obj.type == CBORType.Boolean -> obj.AsBoolean()
            obj.type == CBORType.ByteString -> obj.GetByteString()
            obj.type == CBORType.FloatingPoint -> obj.AsDouble()
            else -> obj.ToJSONString()
        }

    // ISO 8601 full-date encoding: CBOR tdate (tag 0) or full-date string
    private fun encodeInstant(instant: Instant): CBORObject =
        CBORObject.FromObjectAndTag(instant.toString(), 0) // tag 0 = ISO 8601 datetime string

    private fun decodeInstant(obj: CBORObject): Instant =
        Instant.parse(if (obj.HasMostOuterTag(0)) obj.Untag().AsString() else obj.AsString())
}
