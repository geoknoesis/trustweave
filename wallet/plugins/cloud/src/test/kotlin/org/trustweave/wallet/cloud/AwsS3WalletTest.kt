package org.trustweave.wallet.cloud

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [AwsS3Wallet] (and the [CloudWallet] base behavior) against an
 * in-memory fake [S3Client].
 *
 * The AWS SDK v2 service interfaces declare every operation as a default method
 * throwing UnsupportedOperationException, so a hand-rolled fake overriding only
 * the operations used by the wallet is sufficient — no mocking library needed.
 */
class AwsS3WalletTest {

    private val issuerDid = "did:key:z6MkTestIssuer"
    private val basePath = "wallets/wallet-test"

    private fun wallet(s3: S3Client): AwsS3Wallet =
        AwsS3Wallet(
            walletId = "wallet-test",
            walletDid = "did:key:z6MkWallet",
            holderDid = "did:key:z6MkHolder",
            bucketName = "test-bucket",
            basePath = basePath,
            s3Client = s3
        )

    private fun credential(id: String? = "urn:uuid:${UUID.randomUUID()}"): VerifiableCredential =
        VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkTestSubject"),
            issuanceDate = Clock.System.now(),
            proof = null
        )

    // ========== store() id handling (P1: ClassCastException on CredentialId) ==========

    @Test
    fun `store returns the credential id string and keys the object by it`() {
        runBlocking {
            val fake = FakeS3Client()
            val wallet = wallet(fake)
            val credentialId = "urn:uuid:test-credential"

            val returnedId = wallet.store(credential(id = credentialId))

            assertEquals(credentialId, returnedId)
            assertTrue(fake.objects.containsKey("$basePath/credentials/$credentialId.json"))
            // The S3 key must embed the raw id value, never CredentialId.toString()
            assertTrue(fake.objects.keys.none { it.contains("CredentialId") })
        }
    }

    @Test
    fun `store without credential id generates an id and round-trips`() {
        runBlocking {
            val fake = FakeS3Client()
            val wallet = wallet(fake)

            val id = wallet.store(credential(id = null))

            assertTrue(id.isNotBlank())
            assertTrue(fake.objects.containsKey("$basePath/credentials/$id.json"))
        }
    }

    @Test
    fun `store initializes metadata exactly once`() {
        runBlocking {
            val fake = FakeS3Client()
            val wallet = wallet(fake)
            val credentialId = "urn:uuid:meta-once"

            wallet.store(credential(id = credentialId))
            val metadataKey = "$basePath/metadata/$credentialId.json"
            assertTrue(fake.objects.containsKey(metadataKey))
            val firstMetadata = fake.objects[metadataKey]!!

            wallet.store(credential(id = credentialId))
            assertTrue(firstMetadata.contentEquals(fake.objects[metadataKey]!!))
        }
    }

    // ========== download() stream handling (P1: leaked ResponseInputStream) ==========

    @Test
    fun `get closes the S3 object stream after reading`() {
        runBlocking {
            val fake = FakeS3Client()
            val wallet = wallet(fake)
            val id = wallet.store(credential())

            val loaded = wallet.get(id)

            assertNotNull(loaded)
            assertEquals(id, loaded.id?.value)
            assertTrue(fake.openStreams.isNotEmpty())
            assertTrue(
                fake.openStreams.all { it.closed },
                "Every ResponseInputStream returned by getObject must be closed"
            )
        }
    }

    @Test
    fun `get returns null for missing keys`() {
        runBlocking {
            val wallet = wallet(FakeS3Client())
            assertEquals(null, wallet.get("does-not-exist"))
        }
    }

    // ========== listKeys() pagination (P1: 1000-key truncation) ==========

    @Test
    fun `list follows continuation tokens across pages`() {
        runBlocking {
            val fake = FakeS3Client().apply { listPageSize = 2 }
            val wallet = wallet(fake)
            val storedIds = (1..5).map { wallet.store(credential(id = "urn:uuid:page-cred-$it")) }

            val listed = wallet.list(null)

            assertEquals(storedIds.toSet(), listed.map { it.id?.value }.toSet())
            assertEquals(3, fake.listCallCount, "5 keys at page size 2 require 3 ListObjectsV2 calls")
        }
    }

    // ========== deleteFromStorage() error mapping (P1: auth failures swallowed) ==========

    @Test
    fun `delete propagates unexpected S3 failures instead of returning false`() {
        runBlocking {
            val fake = FakeS3Client().apply {
                deleteFailure = S3Exception.builder().message("Access Denied").statusCode(403).build()
            }
            val wallet = wallet(fake)

            val exception = assertFailsWith<RuntimeException> {
                wallet.delete("some-credential")
            }
            assertTrue(exception.message!!.contains("Failed to delete from S3"))
        }
    }

    @Test
    fun `delete maps NoSuchKeyException to false`() {
        runBlocking {
            val fake = FakeS3Client().apply {
                deleteFailure = NoSuchKeyException.builder().message("no such key").build()
            }
            val wallet = wallet(fake)

            assertFalse(wallet.delete("missing-credential"))
        }
    }

    // ========== query() tag/collection filters (P1: silent no-op) ==========

    @Test
    fun `query with byTag throws instead of silently returning all credentials`() {
        runBlocking {
            val wallet = wallet(FakeS3Client())
            wallet.store(credential())

            val exception = assertFailsWith<UnsupportedOperationException> {
                wallet.query { byTag("important") }
            }
            assertTrue(exception.message!!.contains("byTag"))
        }
    }

    @Test
    fun `query with byCollection throws instead of silently returning all credentials`() {
        runBlocking {
            val wallet = wallet(FakeS3Client())
            wallet.store(credential())

            assertFailsWith<UnsupportedOperationException> {
                wallet.query { byCollection("collection-1") }
            }
        }
    }

    @Test
    fun `query without tag or collection filters still works`() {
        runBlocking {
            val wallet = wallet(FakeS3Client())
            val id = wallet.store(credential())

            val results = wallet.query { byIssuer(issuerDid) }
            assertEquals(listOf(id), results.map { it.id?.value })

            assertTrue(wallet.query { byIssuer("did:key:someoneElse") }.isEmpty())
        }
    }

    // ========== Fakes ==========

    private class CloseTrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        @Volatile
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }

    /**
     * Minimal in-memory S3 fake. Pagination is simulated with numeric continuation
     * tokens over a sorted key listing, mirroring S3's page-by-page contract.
     */
    private class FakeS3Client : S3Client {
        val objects = LinkedHashMap<String, ByteArray>()
        val openStreams = mutableListOf<CloseTrackingInputStream>()
        var listPageSize = 1000
        var listCallCount = 0
        var deleteFailure: RuntimeException? = null

        override fun serviceName(): String = "s3"

        override fun close() {}

        override fun putObject(request: PutObjectRequest, requestBody: RequestBody): PutObjectResponse {
            objects[request.key()] = requestBody.contentStreamProvider().newStream().use { it.readAllBytes() }
            return PutObjectResponse.builder().build()
        }

        override fun getObject(request: GetObjectRequest): ResponseInputStream<GetObjectResponse> {
            val bytes = objects[request.key()]
                ?: throw NoSuchKeyException.builder().message("No such key: ${request.key()}").build()
            val stream = CloseTrackingInputStream(bytes)
            openStreams.add(stream)
            return ResponseInputStream(GetObjectResponse.builder().build(), AbortableInputStream.create(stream))
        }

        override fun deleteObject(request: DeleteObjectRequest): DeleteObjectResponse {
            deleteFailure?.let { throw it }
            objects.remove(request.key())
            return DeleteObjectResponse.builder().build()
        }

        override fun listObjectsV2(request: ListObjectsV2Request): ListObjectsV2Response {
            listCallCount++
            val matching = objects.keys.filter { it.startsWith(request.prefix().orEmpty()) }.sorted()
            val startIndex = request.continuationToken()?.toInt() ?: 0
            val page = matching.drop(startIndex).take(listPageSize)
            val nextIndex = startIndex + page.size
            val truncated = nextIndex < matching.size
            val builder = ListObjectsV2Response.builder()
                .contents(page.map { key -> S3Object.builder().key(key).build() })
                .isTruncated(truncated)
            if (truncated) {
                builder.nextContinuationToken(nextIndex.toString())
            }
            return builder.build()
        }
    }
}
