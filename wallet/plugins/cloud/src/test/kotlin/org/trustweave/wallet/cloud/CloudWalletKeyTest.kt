package org.trustweave.wallet.cloud

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CloudWalletKeyTest {
    private class MapWallet : CloudWallet("id", "did:key:w", "did:key:h", "bucket", "wallet") {
        val objects = ConcurrentHashMap<String, ByteArray>()

        override suspend fun upload(
            key: String,
            data: ByteArray,
        ) {
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

    @Test
    fun `hostile credential ids cannot escape or collide in object keys`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            val hostile = listOf("../../other/secret", "a/b", "..", "urn:x%2Fy", "urn:x/y", "urn:q%3Fr", "urn:sp ace")
            val handles = hostile.map { wallet.store(credential(it)) }

            assertEquals(hostile.size, wallet.objects.keys.count { it.startsWith("wallet/credentials/") })
            for (key in wallet.objects.keys) {
                val rest = key.removePrefix("wallet/").substringAfter('/')
                assertTrue('/' !in rest && rest != "..json", "key $key must be a single path segment")
            }
            for ((id, handle) in hostile.zip(handles)) {
                assertNotNull(wallet.get(handle), "round trip for $id")
            }
            assertEquals(hostile.toSet(), wallet.listRecords().map { it.storageId }.toSet())
            assertTrue(wallet.delete("a/b"))
            assertEquals(hostile.size - 1, wallet.list().size)
        }

    @Test
    fun `ordinary ids keep their historical object keys`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            wallet.store(credential("urn:uuid:1234"))
            assertTrue("wallet/credentials/urn:uuid:1234.json" in wallet.objects.keys)
        }

    @Test
    fun `re-storing a credential refreshes updatedAt and keeps createdAt`() =
        runBlocking<Unit> {
            val wallet = MapWallet()
            val id = wallet.store(credential("urn:uuid:meta"))
            val key = "wallet/metadata/urn:uuid:meta.json"
            val first = Json.parseToJsonElement(String(wallet.objects.getValue(key))).jsonObject
            Thread.sleep(20)
            wallet.store(credential(id))
            val second = Json.parseToJsonElement(String(wallet.objects.getValue(key))).jsonObject

            assertEquals(first.getValue("createdAt"), second.getValue("createdAt"))
            val before = Instant.parse(first.getValue("updatedAt").jsonPrimitive.content)
            val after = Instant.parse(second.getValue("updatedAt").jsonPrimitive.content)
            assertTrue(after > before, "updatedAt must move forward on re-store")
        }
}
