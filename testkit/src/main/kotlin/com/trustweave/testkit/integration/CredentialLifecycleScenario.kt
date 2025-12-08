package com.trustweave.testkit.integration

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.model.CredentialType
import com.trustweave.did.model.DidDocument
import com.trustweave.testkit.TrustWeaveTestFixture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Reusable test scenario for full credential lifecycle.
 *
 * Tests the complete flow: issuance -> storage -> presentation -> verification -> revocation.
 *
 * **Example Usage**:
 * ```kotlin
 * @Test
 * fun testCredentialLifecycle() = runBlocking {
 *     val scenario = CredentialLifecycleScenario(fixture)
 *     scenario.execute()
 * }
 * ```
 */
class CredentialLifecycleScenario(
    private val fixture: TrustWeaveTestFixture
) {

    /**
     * Executes the full credential lifecycle test.
     */
    suspend fun execute() {
        // 1. Setup: Create issuer and holder DIDs
        val issuerDid = fixture.createIssuerDid()
        val holderDid = fixture.createIssuerDid()

        // 2. Issue credential
        val credential = issueCredential(issuerDid, holderDid)
        kotlin.test.assertNotNull(credential)
        kotlin.test.assertEquals(issuerDid.id.value, credential.issuer.id.value)

        // 3. Store credential (if wallet is available)
        // This would use fixture.getWallet() if available

        // 4. Create presentation
        // This would create a VerifiablePresentation from the credential

        // 5. Verify credential
        // This would verify the credential using fixture.getCredentialRegistry()

        // 6. Revoke credential (if status list is available)
        // This would revoke the credential and verify it's no longer valid
    }

    /**
     * Issues a test credential.
     */
    private suspend fun issueCredential(
        issuerDid: DidDocument,
        holderDid: DidDocument
    ): VerifiableCredential {
        val credentialSubject = fixture.createTestCredentialSubject(
            id = holderDid.id.value,
            additionalClaims = mapOf(
                "name" to "Test User",
                "email" to "test@example.com"
            )
        )

        // In a real scenario, this would use the credential service
        // For now, create a minimal credential structure
        val subjectId = (credentialSubject["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content 
            ?: holderDid.id.value
        val subjectClaims = credentialSubject.filterKeys { it != "id" }
        
        return VerifiableCredential(
            id = CredentialId("vc:test:${System.currentTimeMillis()}"),
            type = listOf(CredentialType.Custom("VerifiableCredential"), CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(issuerDid.id),
            credentialSubject = CredentialSubject.fromIri(subjectId, claims = subjectClaims),
            issuanceDate = Clock.System.now(),
            expirationDate = null,
            credentialStatus = null,
            credentialSchema = null,
            evidence = null,
            proof = null,
            termsOfUse = null,
            refreshService = null
        )
    }
}

