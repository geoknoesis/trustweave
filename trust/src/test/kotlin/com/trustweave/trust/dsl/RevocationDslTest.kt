package com.trustweave.trust.dsl

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.revocation.CredentialRevocationManager
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.did.identifiers.Did
import com.trustweave.kms.results.SignResult
import kotlinx.datetime.Instant
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.testkit.services.TestkitDidMethodFactory
import com.trustweave.testkit.services.TestkitStatusListRegistryFactory
import com.trustweave.trust.dsl.TrustWeaveConfig
import com.trustweave.trust.dsl.trustWeave
import com.trustweave.trust.dsl.credential.DidMethods
import com.trustweave.trust.dsl.credential.KeyAlgorithms
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for RevocationDsl.kt
 */
class RevocationDslTest {

    private lateinit var trustWeave: TrustWeaveConfig

    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()

        trustWeave = trustWeave {
            factories(
                didMethodFactory = TestkitDidMethodFactory(),
                statusListRegistryFactory = TestkitStatusListRegistryFactory()
            )
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(com.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            revocation {
                provider("inMemory")
            }
        }

        // Manually set status list manager in context for testing
        // In real usage, this would be configured via TrustWeaveConfig
    }

    @Test
    fun `test createStatusList`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val statusList = trustWeave.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()

        assertNotNull(statusList)
        // Verify we can retrieve the status list metadata
        val metadata = trustWeave.revocation {
            statusList(statusList.value)
        }.getStatusList()
        assertNotNull(metadata)
        assertEquals(issuerDid, metadata?.issuerDid)
        assertEquals(StatusPurpose.REVOCATION, metadata?.purpose)
    }

    @Test
    fun `test createStatusList without issuer throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.revocation {
                purpose(StatusPurpose.REVOCATION)
            }.createStatusList()
        }
    }

    @Test
    fun `test revoke credential`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val credentialId = "cred-123"

        val statusList = trustWeave.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()

        val revoked = trustWeave.revoke {
            credential(credentialId)
            statusList(statusList.value)
        }

        assertTrue(revoked)
    }

    @Test
    fun `test revoke credential without credentialId throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.revoke {
                statusList("list-id")
            }
        }
    }

    @Test
    fun `test check revocation status`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val credentialId = "cred-123"

        val statusList = trustWeave.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()

        trustWeave.revoke {
            credential(credentialId)
            statusList(statusList.value)
        }

        val credential = VerifiableCredential(
            id = CredentialId(credentialId),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialStatus = CredentialStatus(
                id = StatusListId("urn:statuslist:${statusList.value}#0"),
                type = "StatusList2021Entry",
                statusListCredential = statusList
            )
        )

        val status = trustWeave.revocation {
            statusList(statusList.value)
        }.check(credential)

        assertTrue(status.revoked)
    }

    @Test
    fun `test suspend credential`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val credentialId = "cred-123"

        val statusList = trustWeave.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.SUSPENSION)
        }.createStatusList()

        val suspended = trustWeave.revocation {
            credential(credentialId)
            statusList(statusList.value)
        }.suspend()

        assertTrue(suspended)
    }

    @Test
    fun `test getStatusList`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val createdList = trustWeave.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
        }.createStatusList()

        val retrievedList = trustWeave.revocation {
            statusList(createdList.value)
        }.getStatusList()

        assertNotNull(retrievedList)
        assertEquals(createdList, retrievedList?.id)
    }

    @Test
    fun `test createStatusList with custom size`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val statusList = trustWeave.revocation {
            forIssuer(issuerDid)
            purpose(StatusPurpose.REVOCATION)
            size(65536)
        }.createStatusList()

        assertNotNull(statusList)
        // Verify we can retrieve the status list metadata
        val metadata = trustWeave.revocation {
            statusList(statusList.value)
        }.getStatusList()
        assertNotNull(metadata)
        assertEquals(issuerDid, metadata?.issuerDid)
    }

    @Test
    fun `test revoke credential without statusListId throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.revoke {
                credential("cred-123")
            }
        }
    }

    @Test
    fun `test getStatusList without statusListId throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.revocation { }.getStatusList()
        }
    }
}


