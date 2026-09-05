package org.trustweave.wallet.cloud

import com.azure.storage.blob.BlobServiceClientBuilder
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Exercises the actual Azure SDK's HTTP request construction and response decoding. */
class AzureListingContractTest {
    @Test
    fun `flat prefix listing discovers credential IDs containing slash characters`() =
        runBlocking {
            val id = "https://issuer.example/credentials/123"
            val key = "wallets/test/credentials/$id.json"
            val bytes =
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
            var listing: Map<String, String> = emptyMap()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                val query =
                    exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
                        val (name, value) = it.split('=', limit = 2)
                        URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
                    }
                exchange.responseHeaders.add("x-ms-request-id", "contract-request")
                exchange.responseHeaders.add("x-ms-version", "2021-12-02")
                exchange.responseHeaders.add("Last-Modified", "Wed, 01 Jan 2025 00:00:00 GMT")
                exchange.responseHeaders.add("ETag", "\"contract\"")
                exchange.responseHeaders.add("x-ms-blob-type", "BlockBlob")
                if (exchange.requestMethod == "HEAD") {
                    exchange.responseHeaders.add("Content-Length", bytes.size.toString())
                    exchange.sendResponseHeaders(200, -1)
                } else {
                    val body =
                        if (query["comp"] == "list") {
                            listing = query
                            val entry =
                                if (query.containsKey("delimiter")) {
                                    "<BlobPrefix><Name>nested/</Name></BlobPrefix>"
                                } else {
                                    "<Blob><Name>$key</Name><Properties><Content-Length>${bytes.size}</Content-Length><BlobType>BlockBlob</BlobType></Properties></Blob>"
                                }
                            exchange.responseHeaders.add("Content-Type", "application/xml")
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?><EnumerationResults><Blobs>$entry</Blobs><NextMarker/></EnumerationResults>"
                                .toByteArray()
                        } else {
                            bytes
                        }
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                exchange.close()
            }
            server.start()
            try {
                val service = BlobServiceClientBuilder().endpoint("http://127.0.0.1:${server.address.port}/account").buildClient()
                val wallet = AzureBlobWallet("test", "did:key:w", "did:key:h", "bucket", "wallets/test", service)
                assertEquals(id, wallet.listRecords().single().storageId)
                assertEquals("wallets/test/credentials/", listing["prefix"])
                assertFalse(listing.containsKey("delimiter"))
            } finally {
                server.stop(0)
            }
        }
}
