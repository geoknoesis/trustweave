package com.geoknoesis.vericore.credential.wallet

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import com.geoknoesis.vericore.credential.PresentationOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Comprehensive tests for CredentialPresentation API using mock implementations.
 */
class CredentialPresentationTest {

    @Test
    fun `test create presentation`() = runBlocking {
        val wallet = createMockPresentationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val presentation = wallet.createPresentation(
            credentialIds = listOf(credentialId),
            holderDid = "did:key:holder",
            options = PresentationOptions(holderDid = "did:key:holder")
        )
        
        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential.size)
        assertEquals("did:key:holder", presentation.holder)
    }

    @Test
    fun `test create presentation with multiple credentials`() = runBlocking {
        val wallet = createMockPresentationWallet()
        val credential1 = createTestCredential(id = "cred-1")
        val credential2 = createTestCredential(id = "cred-2")
        val id1 = wallet.store(credential1)
        val id2 = wallet.store(credential2)
        
        val presentation = wallet.createPresentation(
            credentialIds = listOf(id1, id2),
            holderDid = "did:key:holder",
            options = PresentationOptions(holderDid = "did:key:holder")
        )
        
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test create presentation fails when credential not found`() = runBlocking {
        val wallet = createMockPresentationWallet()
        
        assertFailsWith<IllegalArgumentException> {
            wallet.createPresentation(
                credentialIds = listOf("nonexistent"),
                holderDid = "did:key:holder",
                options = PresentationOptions(holderDid = "did:key:holder")
            )
        }
    }

    @Test
    fun `test create selective disclosure`() = runBlocking {
        val wallet = createMockPresentationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val presentation = wallet.createSelectiveDisclosure(
            credentialIds = listOf(credentialId),
            disclosedFields = listOf("credentialSubject.name"),
            holderDid = "did:key:holder",
            options = PresentationOptions(holderDid = "did:key:holder")
        )
        
        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential.size)
    }

    @Test
    fun `test create selective disclosure fails when credential not found`() = runBlocking {
        val wallet = createMockPresentationWallet()
        
        assertFailsWith<IllegalArgumentException> {
            wallet.createSelectiveDisclosure(
                credentialIds = listOf("nonexistent"),
                disclosedFields = listOf("name"),
                holderDid = "did:key:holder",
                options = PresentationOptions(holderDid = "did:key:holder")
            )
        }
    }

    @Test
    fun `test create presentation with options`() = runBlocking {
        val wallet = createMockPresentationWallet()
        val credential = createTestCredential()
        val credentialId = wallet.store(credential)
        
        val presentation = wallet.createPresentation(
            credentialIds = listOf(credentialId),
            holderDid = "did:key:holder",
            options = PresentationOptions(
                holderDid = "did:key:holder",
                challenge = "challenge-123",
                domain = "example.com"
            )
        )
        
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
    }

    private fun createMockPresentationWallet(): MockPresentationWallet {
        return object : MockPresentationWallet {
            private val credentials = mutableMapOf<String, VerifiableCredential>()
            
            override val walletId = UUID.randomUUID().toString()
            override val capabilities = WalletCapabilities(
                createPresentation = true,
                selectiveDisclosure = true
            )
            
            override suspend fun store(credential: VerifiableCredential): String {
                val id = credential.id ?: UUID.randomUUID().toString()
                credentials[id] = credential
                return id
            }
            override suspend fun get(credentialId: String) = credentials[credentialId]
            override suspend fun list(filter: CredentialFilter?) = credentials.values.toList()
            override suspend fun delete(credentialId: String) = credentials.remove(credentialId) != null
            override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> {
                val builder = CredentialQueryBuilder()
                builder.query()
                val predicate = builder.createPredicate()
                return credentials.values.filter(predicate)
            }
            override suspend fun getStatistics() = WalletStatistics(credentials.size, 0, 0)
            
            override suspend fun createPresentation(
                credentialIds: List<String>,
                holderDid: String,
                options: PresentationOptions
            ): VerifiablePresentation {
                val vcs = credentialIds.map { credentials[it] ?: throw IllegalArgumentException("Credential not found: $it") }
                return VerifiablePresentation(
                    id = UUID.randomUUID().toString(),
                    type = listOf("VerifiablePresentation"),
                    verifiableCredential = vcs,
                    holder = holderDid,
                    proof = null,
                    challenge = options.challenge,
                    domain = options.domain
                )
            }
            
            override suspend fun createSelectiveDisclosure(
                credentialIds: List<String>,
                disclosedFields: List<String>,
                holderDid: String,
                options: PresentationOptions
            ): VerifiablePresentation {
                // Placeholder: return regular presentation
                return createPresentation(credentialIds, holderDid, options)
            }
        }
    }
    
    private interface MockPresentationWallet : Wallet, CredentialPresentation

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString()
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate
        )
    }
}

