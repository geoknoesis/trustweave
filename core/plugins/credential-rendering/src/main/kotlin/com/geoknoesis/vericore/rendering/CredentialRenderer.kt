package com.geoknoesis.vericore.rendering

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rendering format.
 */
enum class RenderingFormat {
    HTML,
    PDF,
    JSON,
    TEXT
}

/**
 * Credential renderer interface.
 * 
 * Renders credentials and presentations in various formats for display.
 * 
 * **Example Usage:**
 * ```kotlin
 * val renderer = CredentialRenderer()
 * 
 * // Render credential as HTML
 * val html = renderer.renderCredential(credential, RenderingFormat.HTML)
 * 
 * // Render presentation as PDF
 * val pdf = renderer.renderPresentation(presentation, RenderingFormat.PDF)
 * ```
 */
interface CredentialRenderer {
    /**
     * Render a credential.
     * 
     * @param credential Credential to render
     * @param format Output format
     * @return Rendered content
     */
    suspend fun renderCredential(
        credential: VerifiableCredential,
        format: RenderingFormat
    ): String
    
    /**
     * Render a presentation.
     * 
     * @param presentation Presentation to render
     * @param format Output format
     * @return Rendered content
     */
    suspend fun renderPresentation(
        presentation: VerifiablePresentation,
        format: RenderingFormat
    ): String
}

/**
 * Simple credential renderer implementation.
 */
class SimpleCredentialRenderer(
    private val json: Json = Json { prettyPrint = true; encodeDefaults = false }
) : CredentialRenderer {
    
    override suspend fun renderCredential(
        credential: VerifiableCredential,
        format: RenderingFormat
    ): String = withContext(Dispatchers.IO) {
        when (format) {
            RenderingFormat.HTML -> renderCredentialHtml(credential)
            RenderingFormat.PDF -> renderCredentialPdf(credential)
            RenderingFormat.JSON -> json.encodeToString(VerifiableCredential.serializer(), credential)
            RenderingFormat.TEXT -> renderCredentialText(credential)
        }
    }
    
    override suspend fun renderPresentation(
        presentation: VerifiablePresentation,
        format: RenderingFormat
    ): String = withContext(Dispatchers.IO) {
        when (format) {
            RenderingFormat.HTML -> renderPresentationHtml(presentation)
            RenderingFormat.PDF -> renderPresentationPdf(presentation)
            RenderingFormat.JSON -> json.encodeToString(VerifiablePresentation.serializer(), presentation)
            RenderingFormat.TEXT -> renderPresentationText(presentation)
        }
    }
    
    private fun renderCredentialHtml(credential: VerifiableCredential): String {
        val subject = credential.credentialSubject.jsonObject
        val subjectHtml = subject.entries.joinToString("\n") { (key, value) ->
            "<tr><td><strong>$key</strong></td><td>$value</td></tr>"
        }
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Verifiable Credential</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                .credential { border: 1px solid #ddd; padding: 20px; border-radius: 5px; }
                table { width: 100%; border-collapse: collapse; }
                td { padding: 8px; border-bottom: 1px solid #eee; }
            </style>
        </head>
        <body>
            <div class="credential">
                <h1>Verifiable Credential</h1>
                <p><strong>Issuer:</strong> ${credential.issuer}</p>
                <p><strong>Issued:</strong> ${credential.issuanceDate}</p>
                ${credential.expirationDate?.let { "<p><strong>Expires:</strong> $it</p>" } ?: ""}
                <h2>Credential Subject</h2>
                <table>
                    $subjectHtml
                </table>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun renderCredentialPdf(credential: VerifiableCredential): String {
        // Simplified - in production would use a PDF library
        return renderCredentialHtml(credential) // Return HTML as placeholder
    }
    
    private fun renderCredentialText(credential: VerifiableCredential): String {
        val subject = credential.credentialSubject.jsonObject
        val subjectText = subject.entries.joinToString("\n") { (key, value) ->
            "$key: $value"
        }
        
        return """
        Verifiable Credential
        ====================
        Issuer: ${credential.issuer}
        Issued: ${credential.issuanceDate}
        ${credential.expirationDate?.let { "Expires: $it" } ?: ""}
        
        Credential Subject:
        $subjectText
        """.trimIndent()
    }
    
    private fun renderPresentationHtml(presentation: VerifiablePresentation): String {
        val credentialsHtml = presentation.verifiableCredential.joinToString("\n") { credential ->
            "<div style='border: 1px solid #ccc; padding: 10px; margin: 10px 0;'>" +
            renderCredentialHtml(credential) +
            "</div>"
        }
        
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Verifiable Presentation</title>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
            </style>
        </head>
        <body>
            <h1>Verifiable Presentation</h1>
            <p><strong>Holder:</strong> ${presentation.holder}</p>
            $credentialsHtml
        </body>
        </html>
        """.trimIndent()
    }
    
    private fun renderPresentationPdf(presentation: VerifiablePresentation): String {
        return renderPresentationHtml(presentation)
    }
    
    private fun renderPresentationText(presentation: VerifiablePresentation): String {
        val credentialsText = presentation.verifiableCredential.joinToString("\n\n") { credential ->
            renderCredentialText(credential)
        }
        
        return """
        Verifiable Presentation
        =======================
        Holder: ${presentation.holder}
        
        Credentials:
        $credentialsText
        """.trimIndent()
    }
}

