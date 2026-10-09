package org.trustweave.wallet.cloud

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class CloudWalletLegacyKeyTest {
    private class MapWallet : CloudWallet("id", "did:key:w", "did:key:h", "bucket", "wallet") {
        val objects = ConcurrentHashMap<String, ByteArray>()

        override suspend fun upload(key: String, data: ByteArray) {
            objects[key] = data
        }

        override suspend fun download(key: String): ByteArray? = objects[key]

        override suspend fun deleteFromStorage(key: String) = objects.remove(key) != null

        override suspend fun listKeys(prefix: String) = objects.keys.filter { it.startsWith(prefix) }
    }

    private fun credential(id: String) =
        VerifiableCredential(
            id = CredentialId(id),
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did("did:key:z6MkIssuer")),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkSubject"),
            issuanceDate = Clock.System.now(),
            proof = null,
        )

    private fun bytes(c: VerifiableCredential) =
        Json.encodeToString(VerifiableCredential.serializer(), c).toByteArray()

    @Test
    fun `objects stored under legacy raw keys stay reachable`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            wallet.objects["wallet/credentials/https://ex.com/a/b.json"] = bytes(credential("https://ex.com/a/b"))
            wallet.objects["wallet/credentials/urn:legacy:1.json"] = bytes(credential("urn:legacy:1"))

            assertNotNull(wallet.get("https://ex.com/a/b"))
            assertNotNull(wallet.get("urn:legacy:1"))
            val records = wallet.listRecords()
            assertEquals(setOf("https://ex.com/a/b", "urn:legacy:1"), records.map { it.storageId }.toSet())
            for (r in records) assertNotNull(wallet.get(r.storageId), "handle ${r.storageId} must round-trip")
            assertTrue(wallet.delete("https://ex.com/a/b"))
            assertNull(wallet.get("https://ex.com/a/b"))
            assertTrue("wallet/credentials/https://ex.com/a/b.json" !in wallet.objects.keys)
        }

    @Test
    fun `a legacy id containing percent signs round-trips through listRecords`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            wallet.objects["wallet/credentials/https://ex.com/a%2Fb.json"] = bytes(credential("https://ex.com/a%2Fb"))

            val record = wallet.listRecords().single()
            assertNotNull(wallet.get(record.storageId))
            assertTrue(wallet.delete(record.storageId))
            assertTrue(wallet.objects.keys.none { it.startsWith("wallet/credentials/") })
        }

    @Test
    fun `legacy fallback cannot be used to read outside the credentials prefix`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            wallet.objects["wallet/other/secret.json"] = bytes(credential("secret"))

            assertNull(wallet.get("../other/secret"))
            assertTrue(!wallet.delete("../other/secret"))
            assertTrue("wallet/other/secret.json" in wallet.objects.keys)
        }

    @Test
    fun `corrupt objects are skipped by listRecords list and getStatistics`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            wallet.store(credential("urn:good"))
            wallet.objects["wallet/credentials/urn:bad.json"] = "not json".toByteArray()

            assertEquals(listOf("urn:good"), wallet.listRecords().map { it.storageId })
            assertEquals(1, wallet.list().size)
            assertEquals(1, wallet.getStatistics().totalCredentials)
            // Recovery still reports the corrupt object rather than hiding it.
            assertEquals(1, wallet.recoverRecords().failures.size)
        }
}
