package com.geoknoesis.vericore.qrcode

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * QR code generator for credentials and presentations.
 * 
 * Generates QR codes containing credential/presentation data or deep links.
 * 
 * **Example Usage:**
 * ```kotlin
 * val generator = QrCodeGenerator()
 * 
 * // Generate QR code for credential
 * val qrCodeData = generator.generateForCredential(credential)
 * 
 * // Generate QR code for presentation
 * val qrCodeData = generator.generateForPresentation(presentation)
 * 
 * // Generate QR code with deep link
 * val qrCodeData = generator.generateDeepLink("https://example.com/verify", credential)
 * ```
 */
interface QrCodeGenerator {
    /**
     * Generate QR code data for a verifiable credential.
     * 
     * @param credential The credential to encode
     * @param format Output format (JSON, JWT, or URL)
     * @return QR code data (string that can be encoded as QR code)
     */
    suspend fun generateForCredential(
        credential: VerifiableCredential,
        format: QrCodeFormat = QrCodeFormat.JSON
    ): String
    
    /**
     * Generate QR code data for a verifiable presentation.
     * 
     * @param presentation The presentation to encode
     * @param format Output format
     * @return QR code data
     */
    suspend fun generateForPresentation(
        presentation: VerifiablePresentation,
        format: QrCodeFormat = QrCodeFormat.JSON
    ): String
    
    /**
     * Generate QR code with a deep link URL.
     * 
     * @param baseUrl Base URL for the deep link
     * @param credential Optional credential to include in URL
     * @param presentation Optional presentation to include in URL
     * @return QR code data (URL string)
     */
    suspend fun generateDeepLink(
        baseUrl: String,
        credential: VerifiableCredential? = null,
        presentation: VerifiablePresentation? = null
    ): String
}

/**
 * QR code output format.
 */
enum class QrCodeFormat {
    JSON,  // Full JSON credential/presentation
    JWT,   // JWT format if available
    URL    // Deep link URL
}

/**
 * Simple QR code generator implementation.
 * 
 * Note: This generates the data string for QR codes. Actual QR code image generation
 * should be done by a QR code library (e.g., ZXing) in the application layer.
 */
class SimpleQrCodeGenerator(
    private val json: Json = Json { prettyPrint = false; encodeDefaults = false }
) : QrCodeGenerator {
    
    override suspend fun generateForCredential(
        credential: VerifiableCredential,
        format: QrCodeFormat
    ): String {
        return when (format) {
            QrCodeFormat.JSON -> json.encodeToString(VerifiableCredential.serializer(), credential)
            QrCodeFormat.JWT -> {
                // Try to extract JWT if available
                credential.proof?.jws ?: json.encodeToString(VerifiableCredential.serializer(), credential)
            }
            QrCodeFormat.URL -> {
                val encoded = URLEncoder.encode(
                    json.encodeToString(VerifiableCredential.serializer(), credential),
                    StandardCharsets.UTF_8.toString()
                )
                "vericore://credential?data=$encoded"
            }
        }
    }
    
    override suspend fun generateForPresentation(
        presentation: VerifiablePresentation,
        format: QrCodeFormat
    ): String {
        return when (format) {
            QrCodeFormat.JSON -> json.encodeToString(VerifiablePresentation.serializer(), presentation)
            QrCodeFormat.JWT -> {
                // Try to extract JWT if available
                presentation.proof?.jws ?: json.encodeToString(VerifiablePresentation.serializer(), presentation)
            }
            QrCodeFormat.URL -> {
                val encoded = URLEncoder.encode(
                    json.encodeToString(VerifiablePresentation.serializer(), presentation),
                    StandardCharsets.UTF_8.toString()
                )
                "vericore://presentation?data=$encoded"
            }
        }
    }
    
    override suspend fun generateDeepLink(
        baseUrl: String,
        credential: VerifiableCredential?,
        presentation: VerifiablePresentation?
    ): String {
        val params = mutableListOf<String>()
        
        credential?.let {
            val encoded = URLEncoder.encode(
                json.encodeToString(VerifiableCredential.serializer(), it),
                StandardCharsets.UTF_8.toString()
            )
            params.add("credential=$encoded")
        }
        
        presentation?.let {
            val encoded = URLEncoder.encode(
                json.encodeToString(VerifiablePresentation.serializer(), it),
                StandardCharsets.UTF_8.toString()
            )
            params.add("presentation=$encoded")
        }
        
        val queryString = params.joinToString("&")
        return if (queryString.isNotEmpty()) {
            "$baseUrl?$queryString"
        } else {
            baseUrl
        }
    }
}

