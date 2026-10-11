package org.trustweave.wallet.cloud

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.services.WalletCreationOptions
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudWalletLayoutTest {
    private class SharedBucketWallet(
        id: String,
        basePath: String,
        val objects: ConcurrentHashMap<String, ByteArray>,
    ) : CloudWallet(id, "did:key:w-$id", "did:key:h", "bucket", basePath) {
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
            proof = null,
        )

    @Test
    fun `two wallets configured with the same storagePath do not see each other`() =
        runBlocking<Unit> {
            val objects = ConcurrentHashMap<String, ByteArray>()
            val base = resolveCloudLocation("aws", "s3://bucket/wallets", "wallet-a", false)
            val a = SharedBucketWallet("wallet-a", base.basePath, objects)
            val b = SharedBucketWallet("wallet-b", resolveCloudLocation("aws", "s3://bucket/wallets", "wallet-b", false).basePath, objects)

            a.store(credential("urn:a"))
            assertTrue(b.listRecords().isEmpty())
            assertNull(b.get("urn:a"))
            assertTrue(!b.delete("urn:a"))
            assertEquals(1, a.listRecords().size)
            b.store(credential("urn:b"))
            assertEquals(listOf("urn:a"), a.list().map { it.id?.value })
            assertEquals(listOf("urn:b"), b.list().map { it.id?.value })
        }

    @Test
    fun `a wallet id that looks like a storage folder cannot reach another wallet`() {
        val tricky = resolveCloudLocation("aws", "s3://bucket/w", "../x/credentials", false).basePath
        assertEquals(2, tricky.split("/").size, tricky)
        assertTrue(tricky.split("/").none { it == ".." || it == "." }, tricky)
        assertNotEquals(
            resolveCloudLocation("aws", "s3://bucket/w", "a/b", false).basePath,
            resolveCloudLocation("aws", "s3://bucket/w", "a%2Fb", false).basePath,
        )
    }

    @Test
    fun `slashes in the storage path are normalised for every provider`() {
        assertEquals("b", resolveCloudLocation("aws", "s3://b//x///y/", "w", false).bucket)
        assertEquals("x/y/wallet-w", resolveCloudLocation("aws", "s3://b//x///y/", "w", false).basePath)
        assertEquals("x/wallet-w", resolveCloudLocation("gcs", "gs://b/x/", "w", false).basePath)
        assertEquals("c", resolveCloudLocation("azure", "https://acc.blob.core.windows.net//c//p/", "w", false).bucket)
        assertEquals("p/wallet-w", resolveCloudLocation("azure", "https://acc.blob.core.windows.net//c//p/", "w", false).basePath)
        assertEquals("wallet-w", resolveCloudLocation("aws", "s3://b", "w", false).basePath)
    }

    @Test
    fun `empty or traversing storage paths are rejected`() {
        for (path in listOf(
            "",
            "  ",
            "s3://",
            "s3:///x",
            "gs://",
            "gs:///p",
            "https://acc.blob.core.windows.net",
            "https://acc.blob.core.windows.net/",
        )) {
            val provider =
                if (path.startsWith("gs")) {
                    "gcs"
                } else if (path.startsWith("https")) {
                    "azure"
                } else {
                    "aws"
                }
            assertFailsWith<IllegalArgumentException>(path) { resolveCloudLocation(provider, path, "w", false) }
        }
        assertFailsWith<IllegalArgumentException> { resolveCloudLocation("aws", "s3://b/../other", "w", false) }
        assertFailsWith<IllegalArgumentException> { resolveCloudLocation("aws", "s3://b/./x", "w", false) }
    }

    @Test
    fun `legacy layout is an explicit opt-in that keeps the old prefix`() {
        assertEquals("wallets/old", resolveCloudLocation("aws", "s3://b/wallets/old/", "w", true).basePath)
    }

    @Test
    fun `factory rejects an empty path before any client is built`() =
        runBlocking<Unit> {
            assertFailsWith<IllegalArgumentException> {
                CloudWalletFactory().create(
                    "cloud",
                    walletId = "w",
                    holderDid = "did:key:h",
                    options = WalletCreationOptions(storagePath = "s3://", additionalProperties = mapOf("provider" to "aws")),
                )
            }
        }

    @Test
    fun `factory refuses an encryptionKey it cannot honour`() =
        runBlocking<Unit> {
            for (options in listOf(
                WalletCreationOptions(storagePath = "s3://b/p", encryptionKey = "secret"),
                WalletCreationOptions(storagePath = "s3://b/p", additionalProperties = mapOf("encryptionKey" to "secret")),
            )) {
                val error =
                    assertFailsWith<IllegalArgumentException> {
                        CloudWalletFactory().create("cloud", walletId = "w", holderDid = "did:key:h", options = options)
                    }
                assertTrue(error.message!!.contains("encryptionKey"))
                assertTrue(!error.message!!.contains("secret"))
            }
        }
}
