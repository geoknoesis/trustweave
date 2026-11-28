package org.trustweave.referencewallet.lib

/**
 * Pure Kotlin NFC Forum RTD-URI and RTD-Text payload decoder.
 * Mirrors `reference-wallet/expo/lib/ndefUri.ts` for Android NDEF tags.
 */
object NdefParser {

    private val URI_PREFIXES = arrayOf(
        "",
        "http://www.",
        "https://www.",
        "http://",
        "https://",
        "tel:",
        "mailto:",
        "ftp://anonymous:anonymous@",
        "ftp://ftp.",
        "ftps://",
        "sftp://",
        "smb://",
        "nfs://",
        "ftp://",
        "dav://",
        "news:",
        "telnet://",
        "imap:",
        "rtsp://",
        "urn:",
        "pop:",
        "sip:",
        "sips:",
        "tftp:",
        "btspp://",
        "btl2cap://",
        "btgoep://",
        "tcpobex://",
        "irdaobex://",
        "file://",
        "urn:epc:id:",
        "urn:epc:tag:",
        "urn:epc:pat:",
        "urn:epc:raw:",
        "urn:epc:",
        "urn:nfc:",
    )

    fun decodeUriPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = if (prefixCode in URI_PREFIXES.indices) URI_PREFIXES[prefixCode] else ""
        val rest = String(payload, 1, payload.size - 1, Charsets.UTF_8).trim()
        val combined = "$prefix$rest"
        return combined.takeIf { it.isNotBlank() }
    }

    fun decodeTextPayload(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = (status and 0x80) != 0
        val langLen = status and 0x3F
        if (1 + langLen >= payload.size) return null
        val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, 1 + langLen, payload.size - 1 - langLen, charset).trim().takeIf { it.isNotBlank() }
    }
}
