# TrustWeave Status List Publishing

Cloud and local-file publishers for the signed status list credentials produced by the TrustWeave bitstring and token status-list managers.

## Overview

W3C [Bitstring Status List](https://www.w3.org/TR/vc-bitstring-status-list/) and IETF [Token Status List](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/) work by having issuers publish a signed status document at a stable, publicly resolvable URL; verifiers then fetch that document and inspect a single bit (or two) to learn whether a credential is revoked or suspended.

This module is the **publication side** of that flow. The bitstring and token status-list managers (see [bitstring](../bitstring/) and [token](../token/)) produce the signed bytes — a `VerifiableCredential` for bitstring, a `statuslist+jwt` for token. This module hands those bytes to object storage and returns the URL verifiers will use.

It provides the [`StatusListPublisher`](src/main/kotlin/org/trustweave/revocation/publishing/StatusListPublisher.kt) interface plus four concrete backends:

| Backend | Class | Use case |
|---|---|---|
| Local file | [`LocalFileStatusListPublisher`](src/main/kotlin/org/trustweave/revocation/publishing/LocalFileStatusListPublisher.kt) | Tests, self-hosted deployments behind any web server |
| AWS S3 | [`S3StatusListPublisher`](src/main/kotlin/org/trustweave/revocation/publishing/S3StatusListPublisher.kt) | Buckets fronted by S3 website hosting or CloudFront |
| Azure Blob | [`AzureBlobStatusListPublisher`](src/main/kotlin/org/trustweave/revocation/publishing/AzureBlobStatusListPublisher.kt) | Public blob containers, optionally fronted by Azure CDN |
| Google Cloud Storage | [`GcsStatusListPublisher`](src/main/kotlin/org/trustweave/revocation/publishing/GcsStatusListPublisher.kt) | Public GCS buckets, optionally fronted by Cloud CDN |

All four implementations are bundled in the same artifact; pick whichever one matches your hosting and only that backend's cloud client will be exercised at runtime.

## Interface

```kotlin
package org.trustweave.revocation.publishing

interface StatusListPublisher {
    suspend fun publish(statusListId: String, content: ByteArray, contentType: String): String
    suspend fun delete(statusListId: String)
    suspend fun getUrl(statusListId: String): String?
}
```

- `publish` uploads `content` under the given `statusListId` with the specified MIME type and returns the **public URL** verifiers should resolve. Use `application/json` for W3C bitstring status list credentials (which is what `BitstringStatusListManager.buildStatusListVc` produces, serialized to JSON) and `application/statuslist+jwt` for IETF token status lists.
- `delete` removes a previously published status list. Implementations are idempotent: deleting a non-existent ID does not throw.
- `getUrl` returns the public URL for an already-published status list, or `null` if the ID is unknown. Useful when you have already issued credentials that point at a URL and want to confirm the storage still holds the object.

All three methods are `suspend` and safe to call from any coroutine context; each backend dispatches blocking I/O on `Dispatchers.IO` (or uses the cloud SDK's async client where one is available).

## Backends

### Local file

Writes each status list as a file in a directory you control, named after the `statusListId`. Whatever URL pattern you supply is templated by replacing `{id}` with the status list identifier — there is no built-in HTTP server, so you point a real web server (Nginx, S3-static, GitHub Pages, etc.) at the directory.

```kotlin
data class LocalFilePublisherConfig(
    val directory: Path,
    val publicUrlPattern: String, // contains "{id}" placeholder
)
```

Use it for unit tests, integration tests, and self-hosted deployments where you already operate a static file server.

### AWS S3

Backed by the AWS SDK v2 [`S3AsyncClient`](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/S3AsyncClient.html); operations are non-blocking and bridged into coroutines via `kotlinx-coroutines-jdk8`'s `.await()`.

```kotlin
data class S3PublisherConfig(
    val bucket: String,
    val region: String,
    val keyPrefix: String = "status-lists/",
    val publicUrlPattern: String,
)
```

Credentials are resolved by the standard AWS SDK chain (env vars, profile, instance/container metadata). The bucket itself must be configured for public read or fronted by CloudFront/another CDN — this module does **not** set ACLs or bucket policies for you.

Use it when your verifiers will hit `https://<bucket>.s3.<region>.amazonaws.com/...` or a CloudFront distribution backed by S3.

### Azure Blob

Backed by the Azure SDK [`BlobServiceClient`](https://learn.microsoft.com/en-us/java/api/com.azure.storage.blob.blobserviceclient). The Azure SDK is synchronous, so all calls are dispatched on `Dispatchers.IO`.

```kotlin
data class AzureBlobPublisherConfig(
    val connectionString: String,
    val containerName: String,
    val publicUrlPattern: String,
)
```

The container must be configured for public blob access; the publisher uploads, then sets the `Content-Type` HTTP header so verifiers receive the correct MIME type on GET.

Use it when verifiers will hit `https://<account>.blob.core.windows.net/<container>/...` or an Azure Front Door / CDN endpoint backed by the container.

### Google Cloud Storage

Backed by the Google Cloud Storage Java client. Like Azure, the GCS client is synchronous and operations run on `Dispatchers.IO`.

```kotlin
data class GcsPublisherConfig(
    val projectId: String,
    val bucketName: String,
    val publicUrlPattern: String,
)
```

Credentials are resolved by Application Default Credentials (`GOOGLE_APPLICATION_CREDENTIALS`, gcloud login, or workload identity). Object ACLs / bucket-level IAM must grant `allUsers` (or your CDN) read access — this module does not change permissions.

Use it when verifiers will hit `https://storage.googleapis.com/<bucket>/...` or a Cloud CDN endpoint.

## Usage

### Gradle dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-status-list-publishing:0.6.0")
}
```

The module pulls in the AWS S3, Azure Blob Storage, and GCS client libraries (via their respective BOMs declared in [build.gradle.kts](build.gradle.kts)). If artifact size matters you can exclude the SDKs you don't use and the class for that backend will simply be unusable.

### Local file

```kotlin
import org.trustweave.revocation.publishing.LocalFilePublisherConfig
import org.trustweave.revocation.publishing.LocalFileStatusListPublisher
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main() = runBlocking {
    val publisher = LocalFileStatusListPublisher(
        LocalFilePublisherConfig(
            directory = Path.of("/var/www/status-lists"),
            publicUrlPattern = "https://example.com/status-lists/{id}",
        ),
    )

    val url = publisher.publish(
        statusListId = "sl-001",
        content = """{"type":"BitstringStatusListCredential"}""".toByteArray(),
        contentType = "application/json",
    )
    println("Published at: $url") // https://example.com/status-lists/sl-001
}
```

### AWS S3

```kotlin
import org.trustweave.revocation.publishing.S3PublisherConfig
import org.trustweave.revocation.publishing.S3StatusListPublisher
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val publisher = S3StatusListPublisher(
        S3PublisherConfig(
            bucket = "my-status-lists",
            region = "us-east-1",
            keyPrefix = "status-lists/",
            publicUrlPattern = "https://my-status-lists.s3.amazonaws.com/status-lists/{id}",
        ),
    )

    val url = publisher.publish("sl-001", statusListBytes, "application/json")
    println("Published at: $url")
}
```

To inject a pre-configured client (custom credentials provider, endpoint override, etc.), pass an `S3AsyncClient` as the second constructor argument.

### Azure Blob

```kotlin
import org.trustweave.revocation.publishing.AzureBlobPublisherConfig
import org.trustweave.revocation.publishing.AzureBlobStatusListPublisher
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val publisher = AzureBlobStatusListPublisher(
        AzureBlobPublisherConfig(
            connectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING"),
            containerName = "status-lists",
            publicUrlPattern = "https://myaccount.blob.core.windows.net/status-lists/{id}",
        ),
    )

    val url = publisher.publish("sl-001", statusListBytes, "application/json")
    println("Published at: $url")
}
```

### Google Cloud Storage

```kotlin
import org.trustweave.revocation.publishing.GcsPublisherConfig
import org.trustweave.revocation.publishing.GcsStatusListPublisher
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val publisher = GcsStatusListPublisher(
        GcsPublisherConfig(
            projectId = "my-gcp-project",
            bucketName = "my-status-lists",
            publicUrlPattern = "https://storage.googleapis.com/my-status-lists/{id}",
        ),
    )

    val url = publisher.publish("sl-001", statusListBytes, "application/json")
    println("Published at: $url")
}
```

## Tying it into the status list managers

This module is intentionally narrow: it knows nothing about bitsets, JWTs, or signing. Compose it with the manager that builds your status documents.

### Bitstring (W3C Bitstring Status List)

```kotlin
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.revocation.bitstring.BitstringStatusListManager
import org.trustweave.revocation.publishing.StatusListPublisher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

suspend fun publishBitstring(
    manager: BitstringStatusListManager,
    publisher: StatusListPublisher,
    statusListId: StatusListId,
): String {
    // Manager produces and signs the status list VC...
    val signedVc = manager.buildStatusListVc(statusListId)

    // ...and the publisher persists it where verifiers can fetch it.
    val bytes = Json.encodeToString(signedVc).toByteArray()
    return publisher.publish(
        statusListId = statusListId.toString(),
        content = bytes,
        contentType = "application/json",
    )
}
```

The returned URL should be the same value you use as `credentialStatus.statusListCredential` on every credential you mint against this status list. Make sure `publicUrlPattern` resolves to that exact URL.

### Token (IETF Token Status List)

```kotlin
import org.trustweave.revocation.publishing.StatusListPublisher
import org.trustweave.revocation.token.TokenStatusListToken
import kotlinx.coroutines.runBlocking

suspend fun publishTokenList(
    publisher: StatusListPublisher,
    token: TokenStatusListToken,
): String =
    publisher.publish(
        statusListId = token.statusListId,
        content = token.jwt.toByteArray(),
        contentType = "application/statuslist+jwt",
    )
```

The `TokenStatusListToken.uri` field is the URL verifiers will hit; configure your publisher's `publicUrlPattern` so that `publish` returns the same value the token was minted with.

## Limitations

- **No caching headers.** The publishers set `Content-Type` only. If you want `Cache-Control`, `ETag`, or signed-URL expiry, configure them at the storage layer or via a CDN in front.
- **No bucket / container provisioning.** The bucket, container, ACLs, and public-read policies are out of scope; create them with Terraform, the cloud console, or `aws`/`az`/`gcloud` CLI before pointing the publisher at them.
- **`publicUrlPattern` is not validated.** If the template does not resolve to the actual stored object, verifiers will get a 404 — there is no end-to-end probe.
- **No SPI auto-registration.** Unlike DID method or KMS plugins, the publisher is not discovered via `META-INF/services/`; instantiate the implementation you need directly and pass it to your status list manager / issuer code.
- **Cloud SDK transitive footprint.** All three cloud SDKs are pulled in even if you only use one backend. Exclude unused SDKs from your build if size matters.
- **Only the local file backend has tests in this module.** S3, Azure Blob, and GCS implementations are thin wrappers over their SDKs and are exercised in integration tests that live elsewhere; verify against real credentials before relying on them in production.

## References

- [W3C Bitstring Status List v1.0](https://www.w3.org/TR/vc-bitstring-status-list/)
- [IETF Token Status List (draft)](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/)
- [AWS SDK for Java v2 — S3](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html)
- [Azure SDK for Java — Blob Storage](https://learn.microsoft.com/en-us/azure/storage/blobs/storage-blobs-introduction)
- [Google Cloud Storage Java client](https://cloud.google.com/storage/docs/reference/libraries#client-libraries-install-java)
- Companion modules: [bitstring](../bitstring/), [token](../token/), [database](../database/)
