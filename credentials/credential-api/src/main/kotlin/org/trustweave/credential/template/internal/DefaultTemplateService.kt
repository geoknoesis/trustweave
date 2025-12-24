package org.trustweave.credential.template.internal

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.template.CredentialTemplate
import org.trustweave.credential.template.TemplateService
import kotlinx.datetime.Instant
import kotlin.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of TemplateService.
 * 
 * Thread-safe in-memory template storage.
 */
internal class DefaultTemplateService : TemplateService {
    private val templates = ConcurrentHashMap<String, CredentialTemplate>()

    override suspend fun createTemplate(template: CredentialTemplate) {
        templates[template.id] = template
    }

    override suspend fun getTemplate(templateId: String): CredentialTemplate? {
        return templates[templateId]
    }

    override suspend fun createIssuanceRequest(
        templateId: String,
        format: ProofSuiteId,
        issuer: Issuer,
        credentialSubject: CredentialSubject,
        issuedAt: Instant,
        validUntil: Instant?
    ): IssuanceRequest {
        val template = templates[templateId]
            ?: throw IllegalArgumentException("Template not found: $templateId")

        // Validate required fields in credentialSubject claims
        for (field in template.requiredFields) {
            if (!credentialSubject.claims.containsKey(field)) {
                throw IllegalArgumentException("Required field '$field' is missing in claims")
            }
        }

        // Calculate expiration date if template default is set
        val expiration = validUntil ?: template.defaultValidity?.let {
            val kotlinDuration = Duration.parse(it.toString())
            issuedAt.plus(kotlinDuration)
        }

        return IssuanceRequest(
            format = format,
            issuer = issuer,
            credentialSubject = credentialSubject,
            type = template.type,
            issuedAt = issuedAt,
            validUntil = expiration
        )
    }

    override suspend fun listTemplates(): List<CredentialTemplate> {
        return templates.values.toList()
    }

    override suspend fun deleteTemplate(templateId: String): Boolean {
        return templates.remove(templateId) != null
    }

    override suspend fun clear() {
        templates.clear()
    }
}

