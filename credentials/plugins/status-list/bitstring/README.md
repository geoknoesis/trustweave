# TrustWeave Bitstring Status List

W3C Bitstring Status List v1.0 implementation for TrustWeave, providing a space-efficient
credential status mechanism backed by a JDBC `DataSource`.

## Overview

This module implements the [`CredentialRevocationManager`](../../../credential-api/src/main/kotlin/org/trustweave/credential/revocation/CredentialRevocationManager.kt)
SPI on top of the [W3C Bitstring Status List v1.0](https://www.w3.org/TR/vc-bitstring-status-list/)
specification. Each credential is assigned an integer index into a long bitstring; flipping the
bit at that index revokes (or suspends) the credential. The bitstring itself is GZIP-compressed,
base64url-encoded, and published as a Verifiable Credential of type `BitstringStatusListCredential`
that any verifier can fetch. A single 16 KB bitstring (the default 131,072 entries) can carry
status for ~131k credentials in one HTTP response, which is why this scheme is the recommended
baseline status mechanism for W3C VCs.

Source files:

- [`BitstringStatusListManager.kt`](src/main/kotlin/org/trustweave/revocation/bitstring/BitstringStatusListManager.kt)
- [`BitstringStatusListManagerFactory.kt`](src/main/kotlin/org/trustweave/revocation/bitstring/BitstringStatusListManagerFactory.kt)
- [`BitstringStatusListManagerProvider.kt`](src/main/kotlin/org/trustweave/revocation/bitstring/spi/BitstringStatusListManagerProvider.kt)
- [`V1__create_bitstring_status_list.sql`](src/main/resources/db/migration/V1__create_bitstring_status_list.sql)

## Key Concepts

**Bitstring indexing.** Every credential issued under a given status list is assigned an integer
`statusListIndex`. To check the credential, a verifier downloads the bitstring, decompresses it,
and reads the bit at that index. The first credential lives at index 0, the second at index 1, and
so on.

**`statusPurpose`.** A single bitstring serves one purpose at a time. Standard purposes are
`revocation` (permanent withdrawal), `suspension` (temporary hold; the bit can be cleared later),
and `message` (application-defined; not implemented here — use a separate list). This module
honours `StatusPurpose.REVOCATION` and `StatusPurpose.SUSPENSION` from
[`CredentialTypes.kt`](../../../credential-api/src/main/kotlin/org/trustweave/credential/model/CredentialTypes.kt).

**Encoding pipeline.** Bits → little-endian byte array (bit 0 of the first byte = entry 0) →
GZIP → base64url without padding. The encoded string appears as `encodedList` in the published
status list VC.

**`bitsPerEntry`.** With `bitsPerEntry = 1` (default) each credential occupies one bit and the list
serves a single purpose. With `bitsPerEntry = 2`, each credential occupies two adjacent bits — even
is the revocation flag, odd is the suspension flag — allowing both states in a single list.

**The status list IS a credential.** `buildStatusListVc(statusListId)` produces a signed
`VerifiableCredential` whose `type` is `["VerifiableCredential", "BitstringStatusListCredential"]`
and whose `credentialSubject` carries `type = "BitstringStatusList"`, `statusPurpose`, and
`encodedList`. This is the artefact verifiers fetch over HTTP.

## Storage

The manager initialises three tables on construction using `CREATE TABLE IF NOT EXISTS` (works with
H2, PostgreSQL, and MySQL via HikariCP): `bitstring_status_lists`, `bitstring_credential_indices`,
`bitstring_next_index`. The DDL is identical to
[`V1__create_bitstring_status_list.sql`](src/main/resources/db/migration/V1__create_bitstring_status_list.sql).

## Usage

### Adding the Dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-status-list-bitstring:0.6.0")
}
```

### Creating a Manager via the Factory

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.trustweave.revocation.bitstring.BitstringStatusListManagerFactory
import org.trustweave.testkit.kms.InMemoryKeyManagementService

val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/trustweave"
    username = "trustweave"
    password = System.getenv("DB_PASSWORD")
    maximumPoolSize = 5
})

val kms = InMemoryKeyManagementService() // swap for AWS/Azure KMS in production
val issuerDid = "did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSdiHrSyu6jpJUMK33MK"

val manager = BitstringStatusListManagerFactory.create(
    dataSource = dataSource,
    kms = kms,
    issuerDid = issuerDid,
    bitsPerEntry = 1 // 1 for single-purpose lists, 2 for combined revocation + suspension
)
```

### Allocating an Index for a Newly Issued Credential

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.StatusPurpose

runBlocking {
    val statusListId = manager.createStatusList(
        issuerDid = issuerDid,
        purpose = StatusPurpose.REVOCATION,
        size = 131072 // default — 16 KB bitstring
    )

    val credentialId = "urn:uuid:9c4f6f0e-1d3d-4b8a-9e2c-7e8d1a2b3c4d"
    val index: Int = manager.assignCredentialIndex(credentialId, statusListId)

    println("Assigned index $index in list $statusListId")
}
```

Indices are sequential and auto-assigned. Passing an explicit `index` will fail with
`IllegalArgumentException` if that slot is already taken by another credential. To look up a
credential's existing index without mutating state, use `getCredentialIndex(credentialId, statusListId)`.

### Setting Status (Revoke / Suspend)

```kotlin
runBlocking {
    // Revoke a single credential
    val revoked: Boolean = manager.revokeCredential(credentialId, statusListId)
    check(revoked) { "Revocation failed (status list not found or wrong purpose)" }

    // Undo a revocation
    manager.unrevokeCredential(credentialId, statusListId)

    // Batch revoke
    val results: Map<String, Boolean> = manager.revokeCredentials(
        credentialIds = listOf("urn:uuid:cred-a", "urn:uuid:cred-b"),
        statusListId = statusListId
    )
}
```

`suspendCredential` / `unsuspendCredential` behave the same way but require
`StatusPurpose.SUSPENSION` (when `bitsPerEntry = 1`). With `bitsPerEntry = 2` both pairs of methods
work on any list.

### Generating (and Signing) the Status List Credential

```kotlin
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.VerifiableCredential

runBlocking {
    val statusVc: VerifiableCredential = manager.buildStatusListVc(statusListId)

    val encodedList = statusVc.credentialSubject.claims["encodedList"]
    val statusPurpose = statusVc.credentialSubject.claims["statusPurpose"]
    val proofType = (statusVc.proof as? CredentialProof.LinkedDataProof)?.type

    println("type            = ${statusVc.type.map { it.value }}")
    println("issuer          = ${statusVc.issuer.id.value}")
    println("statusPurpose   = $statusPurpose")
    println("encodedList[..] = ${encodedList?.toString()?.take(40)}...")
    println("proof.type      = $proofType")
}
```

The manager generates an Ed25519 signing key via the injected
[`KeyManagementService`](../../../../kms/kms-core/src/main/kotlin/org/trustweave/kms/KeyManagementService.kt)
and attaches an `Ed25519Signature2020` `LinkedDataProof`. The signed JSON form is also persisted to
the `status_list_vc` column so it can be served from a static endpoint without re-signing.

### Looking Up Status by Index or Credential ID

```kotlin
import org.trustweave.credential.revocation.RevocationStatus

runBlocking {
    val byIndex: RevocationStatus = manager.checkStatusByIndex(statusListId, index = 42)
    val byId: RevocationStatus = manager.checkStatusByCredentialId(credentialId, statusListId)

    println("isValid  = ${byId.isValid}")
    println("revoked  = ${byId.revoked}")
    println("suspended= ${byId.suspended}")
}
```

For verifiers holding a full `VerifiableCredential` whose `credentialStatus` field points at this
list, call `manager.checkRevocationStatus(credential)` and the manager will pull both the
`statusListCredential` URL and the `statusListIndex` directly from the credential.

### Statistics and Lifecycle

```kotlin
runBlocking {
    val stats = manager.getStatusListStatistics(statusListId)
    println("used=${stats?.usedIndices}/${stats?.totalCapacity}  revoked=${stats?.revokedCount}")

    manager.listStatusLists(issuerDid = issuerDid)
    manager.expandStatusList(statusListId, additionalSize = 65536)
    manager.deleteStatusList(statusListId)
}
```

## SPI Auto-Registration

The module ships a [`BitstringStatusListManagerProvider`](src/main/kotlin/org/trustweave/revocation/bitstring/spi/BitstringStatusListManagerProvider.kt)
discovered via `java.util.ServiceLoader` through
[`META-INF/services/org.trustweave.revocation.services.StatusListRegistryFactory`](src/main/resources/META-INF/services/org.trustweave.revocation.services.StatusListRegistryFactory).
The provider name is `"bitstring"`.

```kotlin
import java.util.ServiceLoader
import kotlinx.coroutines.runBlocking
import org.trustweave.revocation.bitstring.spi.BitstringStatusListManagerProvider
import org.trustweave.revocation.services.StatusListRegistryFactory

runBlocking {
    val factory = ServiceLoader.load(StatusListRegistryFactory::class.java)
        .filterIsInstance<BitstringStatusListManagerProvider>()
        .first()
        .apply { kms = InMemoryKeyManagementService() } // must be injected before create()

    val manager = factory.create(BitstringStatusListManagerProvider.PROVIDER_NAME)
}
```

The provider reads the following system properties (with sensible defaults for tests):

| Property                                  | Default                                                                |
|-------------------------------------------|------------------------------------------------------------------------|
| `trustweave.statuslist.jdbc.url`          | `jdbc:h2:mem:bitstring_status;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`       |
| `trustweave.statuslist.jdbc.username`     | `sa`                                                                   |
| `trustweave.statuslist.jdbc.password`     | (empty)                                                                |
| `trustweave.statuslist.issuer.did`        | `did:key:default`                                                      |

The provider throws `IllegalStateException` if `kms` is not set before `create(...)` is called.

## Integration with `CredentialStatusChecker`

This manager produces and reads status data but does not itself implement the verifier-side
[`CredentialStatusChecker`](../../../credential-api/src/main/kotlin/org/trustweave/credential/spi/status/CredentialStatusChecker.kt)
SPI used by proof engines to gate `VerificationResult`. A thin adapter is enough:

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.credential.spi.status.CredentialStatusCheckResult

class BitstringStatusChecker(
    private val manager: CredentialRevocationManager
) : CredentialStatusChecker {
    override suspend fun checkStatus(
        credential: VerifiableCredential
    ): CredentialStatusCheckResult {
        if (credential.credentialStatus == null) return CredentialStatusCheckResult.NoStatus
        val status = manager.checkRevocationStatus(credential)
        return when {
            status.revoked -> CredentialStatusCheckResult.Revoked(status.reason)
            status.suspended -> CredentialStatusCheckResult.Suspended(status.reason)
            else -> CredentialStatusCheckResult.Valid
        }
    }
}
```

Register the adapter under the `"statusChecker"` key in
`ProofEngineConfig.properties` so the surrounding proof engine consults it during verification.

## Limitations

- `buildStatusListVc` generates a fresh Ed25519 key via the configured `KeyManagementService` on
  every call. Wire your KMS to return the issuer's signing key deterministically, or cache the
  signed VC (the manager persists it to the `status_list_vc` column) and serve that.
- `bitsPerEntry` must be `1` or `2`; arbitrary message-bit widths from the W3C `statusMessage`
  profile are not supported. Use a separate list per status type.
- Single-bit lists reject mutations whose direction does not match the list's `statusPurpose`
  (e.g. `suspendCredential` on a `REVOCATION` list returns `false`).
- The manager performs no HTTP I/O; serving the signed VC at the URL referenced by
  `credentialStatus.statusListCredential` is the caller's responsibility.

## References

- [W3C Bitstring Status List v1.0](https://www.w3.org/TR/vc-bitstring-status-list/)
- [W3C Verifiable Credentials Data Model v1.1](https://www.w3.org/TR/vc-data-model/)
- [`CredentialRevocationManager`](../../../credential-api/src/main/kotlin/org/trustweave/credential/revocation/CredentialRevocationManager.kt)
  — the SPI implemented by this module.
- [`CredentialStatusChecker`](../../../credential-api/src/main/kotlin/org/trustweave/credential/spi/status/CredentialStatusChecker.kt)
  — the verifier-side SPI to which this manager is adapted.
- [`BitstringStatusListManagerTest`](src/test/kotlin/org/trustweave/revocation/bitstring/BitstringStatusListManagerTest.kt)
  — runnable examples of every public API call.
