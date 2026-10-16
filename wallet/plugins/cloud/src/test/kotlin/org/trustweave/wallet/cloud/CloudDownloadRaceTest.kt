package org.trustweave.wallet.cloud

import com.azure.storage.blob.BlobServiceClientBuilder
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A blob deleted between listing and download is skipped, not a failure; Azure construction does no I/O. */
class CloudDownloadRaceTest {
    private fun credentialBytes(id: String) =
        Json
            .encodeToString(
                VerifiableCredential.serializer(),
                VerifiableCredential(
                    id = CredentialId(id),
                    type = listOf(CredentialType.Custom("Employee")),
                    issuer = Issuer.fromDid(Did("did:key:issuer")),
                    credentialSubject = CredentialSubject.fromIri("did:key:holder"),
                ),
            ).toByteArray()

    @Test
    fun `azure download maps a 404 after a successful listing to a skipped record`() =
        runBlocking<Unit> {
            val goneKey = "wallets/test/credentials/urn:gone.json"
            val keepKey = "wallets/test/credentials/urn:keep.json"
            val keep = credentialBytes("urn:keep")
            val requests = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                requests.incrementAndGet()
                val query = exchange.requestURI.rawQuery.orEmpty()
                exchange.responseHeaders.add("x-ms-request-id", "race")
                exchange.responseHeaders.add("x-ms-version", "2021-12-02")
                exchange.responseHeaders.add("Last-Modified", "Wed, 01 Jan 2025 00:00:00 GMT")
                exchange.responseHeaders.add("ETag", "\"race\"")
                exchange.responseHeaders.add("x-ms-blob-type", "BlockBlob")
                val path = exchange.requestURI.rawPath
                when {
                    query.contains("comp=list") -> {
                        val blobs =
                            listOf(goneKey, keepKey).joinToString("") {
                                "<Blob><Name>$it</Name><Properties><Content-Length>1</Content-Length><BlobType>BlockBlob</BlobType></Properties></Blob>"
                            }
                        val body =
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?><EnumerationResults><Blobs>$blobs</Blobs><NextMarker/></EnumerationResults>"
                                .toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/xml")
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    exchange.requestMethod == "HEAD" -> {
                        exchange.responseHeaders.add("Content-Length", keep.size.toString())
                        exchange.sendResponseHeaders(200, -1)
                    }
                    path.contains("urn:gone") -> {
                        exchange.responseHeaders.add("x-ms-error-code", "BlobNotFound")
                        exchange.sendResponseHeaders(404, -1)
                    }
                    else -> {
                        exchange.sendResponseHeaders(200, keep.size.toLong())
                        exchange.responseBody.use { it.write(keep) }
                    }
                }
                exchange.close()
            }
            server.start()
            try {
                val service = BlobServiceClientBuilder().endpoint("http://127.0.0.1:${server.address.port}/account").buildClient()
                val wallet = AzureBlobWallet("test", "did:key:w", "did:key:h", "bucket", "wallets/test", service)
                assertEquals(0, requests.get(), "constructing the wallet must not touch the network")
                assertEquals(listOf("urn:keep"), wallet.listRecords().map { it.storageId })
            } finally {
                server.stop(0)
            }
        }

    @Test
    fun `gcs download maps a 404 to null`() =
        runBlocking<Unit> {
            val storage =
                Proxy.newProxyInstance(Storage::class.java.classLoader, arrayOf(Storage::class.java)) { _, method, _ ->
                    when (method.name) {
                        "readAllBytes", "get" -> throw StorageException(404, "Not Found")
                        "toString" -> "fake"
                        else -> throw UnsupportedOperationException(method.name)
                    }
                } as Storage
            val wallet = GoogleCloudStorageWallet("w", "did:key:w", "did:key:h", "bucket", "p", storage)
            assertNull(wallet.get("urn:gone"))
        }
}
