# TrustWeave IETF Token Status List Plugin

JWT-based credential status list implementation for TrustWeave, following the IETF draft
[`draft-ietf-oauth-status-list`](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/).
Designed for SD-JWT VC and ISO mdoc credential profiles as a lightweight alternative to the
W3C Bitstring Status List.

**Version:** 0.6.0
**Artifact:** `org.trustweave.credentials:credentials-plugins-status-list-token:0.6.0`

## Overview

This module implements [`CredentialRevocationManager`](../../../credential-api/src/main/kotlin/org/trustweave/credential/revocation/CredentialRevocationManager.kt)
on top of a JDBC datastore and produces signed status list **JWTs** with the JOSE type
`statuslist+jwt`. Verifiers dereference the `sub` URI, validate the JWT signature, decode
the `status_list.lst` bitstring, and look up a credential's index to determine if it has
been revoked or suspended.

Compared with the W3C Bitstring Status List, Token Status List is JOSE-native (compact
JWT, with CWT also defined in the draft), free of JSON-LD context and VC envelope, and
referenced from credentials via a compact `status.status_list` claim carrying `idx` and
`uri`.

## Key concepts

| Concept | Description |
|---|---|
| `status_list` claim | JWT payload claim with `bits` (1 or 2) and `lst` (base64url byte array, no padding) |
| `typ = "statuslist+jwt"` | JOSE header type identifying the token |
| `bitsPerEntry = 1` | Single-purpose list: 8 credentials per byte, bit 0 of each entry encodes revoked **or** suspended |
| `bitsPerEntry = 2` | Combined list: 4 credentials per byte, bit 0 = revoked, bit 1 = suspended |
| `idx` | Zero-based entry index assigned to a credential at issuance time |
| `uri` | Public HTTPS URL where the verifier fetches the latest signed token |

A credential references the list with a `status` claim in its SD-JWT VC body, for example:

```json
{
  "status": {
    "status_list": {
      "idx": 42,
      "uri": "https://issuer.example.com/statuslists/employees"
    }
  }
}
```

The verifier resolves the URI, validates the returned JWT, reads byte `floor(42 / 8)` of
the decoded `lst`, and inspects bit `42 % 8` to determine the credential's status.

## Usage

### Gradle dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-status-list-token:0.6.0")
}
```

The module pulls in HikariCP plus PostgreSQL, MySQL, and H2 drivers, so any JDBC
`DataSource` you provide will work out of the box.

### Creating a manager via the factory

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.trustweave.revocation.token.TokenStatusListManagerFactory
import org.trustweave.testkit.kms.InMemoryKeyManagementService

val dataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = "jdbc:h2:mem:statuslist;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        username = "sa"
        password = ""
        maximumPoolSize = 5
    }
)

val kms = InMemoryKeyManagementService()

val manager = TokenStatusListManagerFactory.create(
    dataSource = dataSource,
    kms = kms,
    issuerDid = "did:key:z6MkEmployerIssuer",
    statusListUri = "https://issuer.example.com/statuslists/employees",
    bitsPerEntry = 1
)
```

The constructor auto-creates three tables (`token_status_lists`, `token_credential_indices`,
`token_next_index`) the first time it runs. A Flyway-compatible script is also shipped
under [`V1__create_token_status_list.sql`](src/main/resources/db/migration/V1__create_token_status_list.sql).

### Allocating an index and setting status

All status-mutating operations are suspend functions and must run inside a coroutine
scope. Use `runBlocking { ... }` for one-shot scripts or call from an existing suspend
context in production code.

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.model.StatusPurpose

runBlocking {
    val listId = manager.createStatusList(
        issuerDid = "did:key:z6MkEmployerIssuer",
        purpose = StatusPurpose.REVOCATION,
        size = 1024
    )

    val index = manager.assignCredentialIndex(
        credentialId = "urn:uuid:employee-credential-001",
        statusListId = listId
    )
    println("Allocated entry index $index in list $listId")

    // Later, revoke the credential
    val revoked = manager.revokeCredential(
        credentialId = "urn:uuid:employee-credential-001",
        statusListId = listId
    )
    check(revoked)
}
```

For combined revocation + suspension on a single list, build the manager with
`bitsPerEntry = 2` and use `revokeCredential`/`suspendCredential` independently — each
flips its own bit.

### Producing a signed Token Status List JWT

`buildStatusListToken` encodes the current bit array, signs it with a freshly generated
key from the injected `KeyManagementService`, persists the JWT in
`token_status_lists.status_list_token`, and returns it as a
[`TokenStatusListToken`](src/main/kotlin/org/trustweave/revocation/token/TokenStatusListToken.kt):

```kotlin
runBlocking {
    val token = manager.buildStatusListToken(
        statusListId = listId,
        ttlSeconds = 3600L // optional; adds `exp` and `ttl` claims
    )

    println("Compact JWT: ${token.jwt}")
    println("Serve at:    ${token.uri}")
}
```

The signing key currently defaults to `Algorithm.Ed25519` and the JWS header advertises
`alg = "EdDSA"` plus `typ = "statuslist+jwt"`. Publish `token.jwt` at `token.uri` over
HTTPS with an `application/statuslist+jwt` content type.

### Validating status

Verifiers typically check status during credential verification, but you can also query
the manager directly:

```kotlin
runBlocking {
    val status = manager.checkStatusByCredentialId(
        credentialId = "urn:uuid:employee-credential-001",
        statusListId = listId
    )
    when {
        status.revoked   -> println("Credential is revoked")
        status.suspended -> println("Credential is suspended")
        else             -> println("Credential is active")
    }

    // Or look up purely by index (the verifier flow after decoding the JWT)
    val byIndex = manager.checkStatusByIndex(listId, index = 0)
    println("Entry 0 revoked? ${byIndex.revoked}")
}
```

Batch operations (`revokeCredentials`, `updateStatusListBatch`) and reporting helpers
(`getStatusListStatistics`, `listStatusLists`, `expandStatusList`, `deleteStatusList`) are
also available — see [`TokenStatusListManager`](src/main/kotlin/org/trustweave/revocation/token/TokenStatusListManager.kt)
for the full surface.

## SPI auto-registration

The module ships a Java `ServiceLoader` provider so a `TokenStatusListManager` can be
discovered from any classpath that includes this JAR.

- Provider class: [`TokenStatusListManagerProvider`](src/main/kotlin/org/trustweave/revocation/token/spi/TokenStatusListManagerProvider.kt)
- Provider name: `"token"`
- Registration file: [`META-INF/services/org.trustweave.revocation.services.StatusListRegistryFactory`](src/main/resources/META-INF/services/org.trustweave.revocation.services.StatusListRegistryFactory)

```kotlin
import java.util.ServiceLoader
import kotlinx.coroutines.runBlocking
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.revocation.token.spi.TokenStatusListManagerProvider

val factory = ServiceLoader.load(StatusListRegistryFactory::class.java)
    .filterIsInstance<TokenStatusListManagerProvider>()
    .first()
    .apply { kms = InMemoryKeyManagementService() }

val manager = runBlocking { factory.create(TokenStatusListManagerProvider.PROVIDER_NAME) }
```

The provider reads optional JVM system properties; when absent it falls back to an H2
in-memory datasource:

| System property | Default | Purpose |
|---|---|---|
| `trustweave.statuslist.jdbc.url` | `jdbc:h2:mem:token_status;…` | JDBC URL |
| `trustweave.statuslist.jdbc.username` | `sa` | DB user |
| `trustweave.statuslist.jdbc.password` | _empty_ | DB password |
| `trustweave.statuslist.issuer.did` | `did:key:default` | Issuer DID (`iss` claim) |
| `trustweave.statuslist.token.uri` | `https://example.com/statuslists/default` | Public URI (`sub` claim) |

## Integration with `CredentialStatusChecker`

The credential verification pipeline talks to status lists through the
[`CredentialStatusChecker`](../../../credential-api/src/main/kotlin/org/trustweave/credential/spi/status/CredentialStatusChecker.kt)
SPI, which returns a typed `CredentialStatusCheckResult`. A thin adapter over
`TokenStatusListManager` keeps both sides decoupled:

```kotlin
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.revocation.token.TokenStatusListManager

class TokenStatusChecker(
    private val manager: TokenStatusListManager
) : CredentialStatusChecker {

    override suspend fun checkStatus(
        credential: VerifiableCredential
    ): CredentialStatusCheckResult {
        val entry = credential.credentialStatus
            ?: return CredentialStatusCheckResult.NoStatus

        val listRef = entry.statusListCredential ?: entry.id
        val status = manager.checkRevocationStatus(credential)

        return when {
            status.revoked   -> CredentialStatusCheckResult.Revoked()
            status.suspended -> CredentialStatusCheckResult.Suspended()
            else             -> CredentialStatusCheckResult.Valid
        }
    }
}
```

Inject the resulting checker through `ProofEngineConfig.properties["statusChecker"]` so
proof engines can gate `VerificationResult` on live status data.

## How this complements the Bitstring plugin

| | Token Status List (this module) | Bitstring Status List (`credentials:plugins:status-list:bitstring`) |
|---|---|---|
| Spec | IETF `draft-ietf-oauth-status-list` | W3C Bitstring Status List v1.0 |
| Envelope | Compact JWT (`statuslist+jwt`) | Verifiable Credential (JSON-LD) |
| Encoding | Raw base64url, optional DEFLATE (not applied here) | GZIP-compressed base64 inside a VC |
| Best fit | SD-JWT VC, ISO mdoc, OAuth flows | W3C VC Data Model 2.0 issuance |
| Provider name | `"token"` | `"bitstring"` |

Both plugins implement the same `CredentialRevocationManager` interface and can coexist
on the classpath. Pick the format that matches the credentials you issue, or run both
behind a router if you mint multiple credential formats.

## Limitations

- DEFLATE compression of `lst` is not applied (the draft marks it OPTIONAL).
- CWT output is not implemented; only the JWT variant is produced.
- `buildStatusListToken` generates a new signing key from the KMS on every call — wire in
  your own key strategy if you need stable `kid` references.
- No HTTP server is bundled; pair with `credentials:plugins:status-list:publishing` or
  your own service to host the token at `statusListUri`.

## References

- [IETF Token Status List draft](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/) — `draft-ietf-oauth-status-list`
- [SD-JWT VC](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/) — `draft-ietf-oauth-sd-jwt-vc`
- [`CredentialRevocationManager`](../../../credential-api/src/main/kotlin/org/trustweave/credential/revocation/CredentialRevocationManager.kt) — SPI implemented by this module
- [`CredentialStatusChecker`](../../../credential-api/src/main/kotlin/org/trustweave/credential/spi/status/CredentialStatusChecker.kt) — verifier-side SPI
- [`TokenStatusListManager`](src/main/kotlin/org/trustweave/revocation/token/TokenStatusListManager.kt) — main implementation
- [`TokenStatusListManagerFactory`](src/main/kotlin/org/trustweave/revocation/token/TokenStatusListManagerFactory.kt) — recommended entry point
- [`TokenStatusListManagerProvider`](src/main/kotlin/org/trustweave/revocation/token/spi/TokenStatusListManagerProvider.kt) — ServiceLoader provider
- [`TokenStatusListManagerTest`](src/test/kotlin/org/trustweave/revocation/token/TokenStatusListManagerTest.kt) — usage examples in test form
