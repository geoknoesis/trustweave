# TrustWeave OpenID Federation Plugin

OpenID Federation 1.0 trust establishment for TrustWeave: host an Entity Configuration, resolve and verify trust chains, and plug federation-based verifier checks into the credential exchange layer.

## Overview

[OpenID Federation 1.0](https://openid.net/specs/openid-federation-1_0.html) is a hierarchical trust establishment protocol for OpenID-style entities (OpenID Providers, Relying Parties, and Federation Entities). Each entity publishes a self-signed **Entity Configuration** at a well-known URL; intermediate authorities and trust anchors sign **Subordinate Statements** about the entities below them. A consumer assembles these signed statements into a **trust chain** that proves a leaf entity is reachable from a trust anchor it already trusts.

This plugin gives TrustWeave a federation-based alternative (or complement) to the trust-registry pattern used elsewhere in the SDK:

- Issuers and verifiers can be identified by an HTTPS entity identifier rather than (or in addition to) a DID.
- Trust is rooted in a small set of trust anchors instead of an enumerated registry.
- The same machinery (entity statements + chains) carries metadata, policy, and trust marks.

The plugin focuses on the verifier/relying-party side. It can fetch and verify trust chains, and it ships a [`CredentialExchangeProtocol`](../../credential-api/src/main/kotlin/org/trustweave/credential/exchange/CredentialExchangeProtocol.kt) implementation that enforces federation trust during proof presentation.

## Key concepts

- **Entity statement** — a signed JWT (`application/entity-statement+jwt`) describing an entity. Modeled as [`EntityStatement`](src/main/kotlin/org/trustweave/credential/federation/FederationModels.kt) with `iss`, `sub`, `iat`, `exp`, `jwks`, optional `metadata`, `authority_hints`, `constraints`, `trust_marks`, and `metadata_policy`.
- **Entity Configuration** — the self-signed entity statement (`iss == sub`) published at `{entityId}/.well-known/openid-federation`. The well-known path is exposed as [`EntityConfigurationEndpoint.WELL_KNOWN_PATH`](src/main/kotlin/org/trustweave/credential/federation/EntityConfigurationEndpoint.kt).
- **Subordinate statement** — an entity statement where `iss != sub`, issued by an authority about one of its subordinates and fetched from the authority's `federation_fetch_endpoint`.
- **Intermediate authority** — a federation entity that sits between leaves and the trust anchor; it issues subordinate statements and may impose policy via `constraints` and `metadata_policy`.
- **Trust anchor** — a federation entity whose public keys the relying party already trusts out-of-band. Represented at runtime as an entity-identifier string passed in `trustedAnchorIds`.
- **Trust chain** — an ordered list of signed entity-statement JWTs going from the leaf's Entity Configuration up to a subordinate statement issued by the trust anchor. Modeled as [`TrustChain`](src/main/kotlin/org/trustweave/credential/federation/FederationModels.kt), with results wrapped in [`TrustChainResolutionResult`](src/main/kotlin/org/trustweave/credential/federation/FederationModels.kt).

## What's included

- **Models** — [`FederationModels.kt`](src/main/kotlin/org/trustweave/credential/federation/FederationModels.kt): `EntityStatement`, `FederationJwkSet`, `FederationJwk`, `EntityMetadata` (`OpenIdProviderMetadata`, `OpenIdRelyingPartyMetadata`, `FederationEntityMetadata`), `PolicyConstraints`, `NamingConstraints`, `TrustMark`, `MetadataPolicy`, `PolicyOperator`, `TrustChain`, and `TrustChainResolutionResult`. All wire formats use the spec's snake_case via `@SerialName`.
- **Entity Configuration helper** — [`EntityConfigurationEndpoint`](src/main/kotlin/org/trustweave/credential/federation/EntityConfigurationEndpoint.kt) builds canonical well-known URLs (`getUrl(entityId)`), stripping trailing slashes from the entity identifier.
- **Entity Statement JWT processing** — [`EntityStatementJwtProcessor`](src/main/kotlin/org/trustweave/credential/federation/EntityStatementJwtProcessor.kt) parses, verifies, and signs entity statement JWTs using Nimbus JOSE + JWT. Supports EC (`ES256`/`ES384`/`ES512`) and RSA (`RS256`/`RS384`/`RS512`, `PS256`/`PS384`/`PS512`); EdDSA is not yet covered.
- **Trust chain resolution** — [`TrustChainResolver`](src/main/kotlin/org/trustweave/credential/federation/TrustChainResolver.kt) walks `authority_hints` from a leaf up to a trusted anchor over HTTP, fetches subordinate statements via each authority's `federation_fetch_endpoint`, and verifies the resulting chain (signature chaining, expiry with clock-skew tolerance, and `max_path_length` constraint propagation).
- **Federation exchange protocol** — [`FederationExchangeProtocol`](src/main/kotlin/org/trustweave/credential/federation/exchange/FederationExchangeProtocol.kt) implements `CredentialExchangeProtocol` for the `openid-federation` protocol name. It supports the `REQUEST_PROOF` and `PRESENT_PROOF` operations and rejects presentations whose credential issuers cannot be resolved to a configured trust anchor. Issuance operations are intentionally out of scope.
- **SPI provider** — [`FederationExchangeProtocolProvider`](src/main/kotlin/org/trustweave/credential/federation/exchange/spi/FederationExchangeProtocolProvider.kt), registered via `META-INF/services` for ServiceLoader auto-discovery.

## Usage

### Adding the dependency

```kotlin
dependencies {
    implementation("org.trustweave.credentials:credentials-plugins-openid-federation:0.6.0")
}
```

Requires Java 21+ and Kotlin 2.3.x (matches the rest of the TrustWeave SDK).

### Hosting an Entity Configuration

The plugin does not embed an HTTP server, but it gives you the well-known path and the signer. Wire the result into whatever Ktor/Spring/Javalin endpoint you already expose:

```kotlin
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.Curve
import org.trustweave.credential.federation.EntityConfigurationEndpoint
import org.trustweave.credential.federation.EntityMetadata
import org.trustweave.credential.federation.EntityStatement
import org.trustweave.credential.federation.EntityStatementJwtProcessor
import org.trustweave.credential.federation.FederationEntityMetadata
import org.trustweave.credential.federation.FederationJwk
import org.trustweave.credential.federation.FederationJwkSet

fun publishEntityConfiguration(): Pair<String, String> {
    val entityId = "https://leaf.example.com"

    // 1. Mint (or load) the entity's signing key.
    val signingKey: ECKey = ECKeyGenerator(Curve.P_256)
        .keyID("leaf-key-1")
        .generate()
    val publicJwk = signingKey.toPublicJWK()

    // 2. Build the EntityStatement payload.
    val now = System.currentTimeMillis() / 1000L
    val statement = EntityStatement(
        iss = entityId,
        sub = entityId,
        iat = now,
        exp = now + 3600L,
        jwks = FederationJwkSet(
            keys = listOf(
                FederationJwk(
                    kty = publicJwk.keyType.value,
                    use = "sig",
                    kid = publicJwk.keyID,
                    crv = publicJwk.curve.name,
                    x = publicJwk.x.toString(),
                    y = publicJwk.y.toString(),
                    alg = "ES256",
                ),
            ),
        ),
        metadata = EntityMetadata(
            federationEntity = FederationEntityMetadata(
                organizationName = "Example Leaf Org",
                homepageUri = "https://leaf.example.com",
            ),
        ),
        authorityHints = listOf("https://anchor.example.com"),
    )

    // 3. Sign and serialize the JWT.
    val processor = EntityStatementJwtProcessor()
    val jwt = processor.sign(
        statement = statement,
        privateKeyJwk = signingKey.toJSONString(),
        algorithm = "ES256",
    )

    // Serve `jwt` from your HTTP layer at this URL with
    // Content-Type: application/entity-statement+jwt
    val url = EntityConfigurationEndpoint.getUrl(entityId)
    return url to jwt
}
```

### Resolving and verifying a trust chain

```kotlin
import kotlinx.coroutines.runBlocking
import org.trustweave.credential.federation.TrustChainResolutionResult
import org.trustweave.credential.federation.TrustChainResolver

fun main() = runBlocking {
    val resolver = TrustChainResolver(
        maxChainLength = 5,
        clockSkewSeconds = 30L,
    )

    val leafEntityId = "https://leaf.example.com"
    val trustedAnchors = setOf("https://anchor.example.com")

    when (val result = resolver.resolve(leafEntityId, trustedAnchors)) {
        is TrustChainResolutionResult.Success -> {
            val chain = result.chain
            println("Chain length: ${chain.statements.size}")
            println("Trust anchor: ${chain.trustAnchorId}")

            if (resolver.verifyChain(chain)) {
                println("Trust chain is valid (verified at ${result.verifiedAt})")
            } else {
                println("Trust chain failed verification (signature/expiry/constraints)")
            }
        }
        is TrustChainResolutionResult.Failure -> {
            println("Failed to resolve ${result.entityId}: ${result.reason}")
        }
    }
}
```

`TrustChainResolver.verifyChain` checks that the chain is non-empty, that no statement has expired (within `clockSkewSeconds` tolerance), that each statement's `jwks` actually verifies the next statement's signature, and that any intermediate's `max_path_length` constraint is honored.

To inspect an individual JWT without verifying it (for debugging or for reading `metadata` claims), use `resolver.parseEntityStatement(jwt)` or the lower-level `EntityStatementJwtProcessor.parse(jwt)`.

## SPI auto-registration

The plugin registers [`FederationExchangeProtocolProvider`](src/main/kotlin/org/trustweave/credential/federation/exchange/spi/FederationExchangeProtocolProvider.kt) via `META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`. Once this JAR is on the classpath, the `openid-federation` protocol is discovered automatically by the credential-exchange registry.

The provider accepts the following options:

| Option              | Type                                      | Default       | Description                                                                   |
|---------------------|-------------------------------------------|---------------|-------------------------------------------------------------------------------|
| `trustedAnchorIds`  | `Set<String>` / `Collection<String>` / comma-separated `String` | empty | Entity identifiers of trust anchors the verifier accepts. Required to verify presentations. |
| `httpClient`        | `OkHttpClient`                            | `OkHttpClient()` | HTTP client used for entity-configuration and subordinate-statement fetches. |
| `maxChainLength`    | `Int`                                     | `5`           | Maximum recursion depth when walking `authority_hints`.                       |

When a holder calls `presentProof`, the protocol resolves a trust chain for every distinct issuer in the VP. If any chain fails to resolve or fails `verifyChain`, the call throws `TrustWeaveException.InvalidOperation` with code `FEDERATION_TRUST_CHAIN_FAILED` or `FEDERATION_TRUST_CHAIN_INVALID`. The `requestProof` call returns an `ExchangeMessageEnvelope` whose `messageData` advertises the configured `trustedAnchors` and the `verifierDid` so the holder knows which federations are acceptable.

Manual construction is also straightforward when you do not want SPI discovery:

```kotlin
import org.trustweave.credential.federation.TrustChainResolver
import org.trustweave.credential.federation.exchange.FederationExchangeProtocol

val protocol = FederationExchangeProtocol(
    resolver = TrustChainResolver(),
    trustedAnchorIds = setOf("https://anchor.example.com"),
)
```

## Limitations

- **EdDSA not yet supported** — `EntityStatementJwtProcessor` handles EC and RSA keys only; Ed25519/Ed448 entity statements will fail to sign or verify.
- **Trust anchor key bootstrap is out of band** — `verifyChain` walks the signature chain but does not itself verify the top statement against a pre-configured anchor key. You must independently establish that the trust anchor's keys are the ones you expect (typically by pinning its Entity Configuration or its JWK Set).
- **Issuance operations are unsupported** — `offer`, `request`, and `issue` throw `TrustWeaveException.InvalidOperation` with code `OPERATION_NOT_SUPPORTED`. Use a dedicated issuance protocol (for example OID4VCI) for credential issuance.
- **No metadata-policy merging yet** — `MetadataPolicy` and `PolicyOperator` are modeled and serialized, but the resolver does not currently merge policy onto the leaf's metadata. Trust mark JWTs are likewise carried through but not verified.
- **No caching layer** — every `resolve` call re-fetches each entity configuration and subordinate statement. For production verifier deployments, wrap the supplied `OkHttpClient` with a caching client or front the resolver with your own short-lived cache.
- **HTTP only** — only `https://` (or `http://`) entity identifiers are supported; DID-based entity identifiers are not yet recognized as resolvable hints.

## References

- [OpenID Federation 1.0 specification](https://openid.net/specs/openid-federation-1_0.html)
- TrustWeave credential exchange SPI: [`CredentialExchangeProtocol`](../../credential-api/src/main/kotlin/org/trustweave/credential/exchange/CredentialExchangeProtocol.kt), [`CredentialExchangeProtocolProvider`](../../credential-api/src/main/kotlin/org/trustweave/credential/spi/exchange/CredentialExchangeProtocolProvider.kt)
- Nimbus JOSE + JWT: <https://connect2id.com/products/nimbus-jose-jwt>
