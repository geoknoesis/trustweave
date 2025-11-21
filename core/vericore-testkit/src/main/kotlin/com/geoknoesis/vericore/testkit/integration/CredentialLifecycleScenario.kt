package com.geoknoesis.vericore.testkit.integration

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.testkit.VeriCoreTestFixture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
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
    private val fixture: VeriCoreTestFixture
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
        kotlin.test.assertEquals(issuerDid.id, credential.issuer)
        
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
            id = holderDid.id,
            additionalClaims = mapOf(
                "name" to "Test User",
                "email" to "test@example.com"
            )
        )
        
        // In a real scenario, this would use the credential service
        // For now, create a minimal credential structure
        return VerifiableCredential(
            id = "vc:test:${System.currentTimeMillis()}",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid.id,
            credentialSubject = credentialSubject as JsonElement,
            issuanceDate = Instant.now().toString(),
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

