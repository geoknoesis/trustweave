# Microsoft Entra Verified ID — TrustWeave plugin

`org.trustweave.integrations.entra` integrates TrustWeave's credential exchange API
with the [Microsoft Entra Verified ID Request Service](https://learn.microsoft.com/entra/verified-id/).

## What this plugin does

- Issues OAuth2 `client_credentials` bearer tokens for the Verified ID API (cached, thread-safe).
- Calls `POST /v1.0/verifiableCredentials/createIssuanceRequest` and surfaces the
  resulting deep-link URL / QR code so a wallet can pick up the issuance request.
- Calls `POST /v1.0/verifiableCredentials/createPresentationRequest` for verifier flows.
- Parses Entra Verified ID webhook callback payloads
  (`request_retrieved`, `issuance_successful`, `presentation_verified`, `issuance_error`,
  `presentation_error`).
- Registers an `entra` protocol on the `CredentialExchangeProtocol` SPI for transparent
  integration with the rest of TrustWeave.

The credential itself is delivered out-of-band by the Verified ID service to the
caller's webhook; this plugin therefore implements `offer` (issuance request) and
`requestProof` (presentation request) synchronously, and exposes
`EntraPresentationClient.parseCallbackPayload` for processing the asynchronous result.

## Configuration

| Field | Required | Description |
|---|---|---|
| `tenantId` | yes | Azure AD tenant GUID |
| `clientId` | yes | App registration with Verified ID permission |
| `clientSecret` | yes | Client secret for `client_credentials` |
| `authorityDid` | yes | DID issued by Verified ID for your authority |
| `authorityVerificationKeyId` | no | Override signing key id |
| `apiBaseUrl` | no | Defaults to `https://verifiedid.did.msidentity.com` |
| `tokenEndpointBaseUrl` | no | Defaults to `https://login.microsoftonline.com` (override for sovereign clouds) |
| `scope` | no | Defaults to the documented Verified ID resource id |

## Usage

```kotlin
val integration = EntraIntegration(
    EntraConfig(
        tenantId = System.getenv("ENTRA_TENANT_ID"),
        clientId = System.getenv("ENTRA_CLIENT_ID"),
        clientSecret = System.getenv("ENTRA_CLIENT_SECRET"),
        authorityDid = System.getenv("ENTRA_AUTHORITY_DID"),
    ),
)

// Issuance: produces the deep link + QR code for the wallet
val envelope = integration.exchangeProtocol.offer(
    ExchangeRequest.Offer(
        protocolName = ExchangeProtocolName("entra"),
        issuerDid = Did(integration.config.authorityDid),
        holderDid = Did("did:web:holder.example.com"),
        credentialPreview = CredentialPreview(attributes = emptyList()),
        options = entraIssuanceOptions(
            manifestUrl = "https://verifiedid.did.msidentity.com/.../manifest",
            credentialType = "EmployeeCredential",
            callbackUrl = "https://your-webhook.example.com/entra/callback",
            clientName = "Acme Corp",
            claims = mapOf("given_name" to "Ada"),
        ),
    ),
)

// In your webhook handler:
val callback = integration.presentationClient.parseCallbackPayload(rawJson)
when (callback.requestStatus) {
    "presentation_verified" -> processVerifiedCredentials(callback.verifiedCredentialsData)
    "issuance_successful" -> markIssuanceDelivered(callback.requestId)
    else -> /* request_retrieved, etc. */
}
```

## SPI

`META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`
registers `EntraExchangeProtocolProvider`, which exposes the `"entra"` protocol name.

```kotlin
val provider = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
    .first { it.name == "entra" }
val protocol = provider.create("entra", mapOf("config" to config))
```

## Local development

There is no local equivalent of the Entra Verified ID Request Service. The plugin's
unit tests stand in via [WireMock](https://wiremock.org/) (see
`src/test/kotlin/org/trustweave/integrations/entra/`).

For real-Entra smoke testing:

1. Follow Microsoft's setup guide:
   <https://learn.microsoft.com/entra/verified-id/verifiable-credentials-configure-tenant>
2. Note your tenant id, app registration client id + secret, and the authority DID
   that Verified ID issued.
3. Export them as environment variables and run the plugin's tests (real-Entra
   tests use `@EnabledIfEnvironmentVariable`):

```bash
export ENTRA_TENANT_ID=...
export ENTRA_CLIENT_ID=...
export ENTRA_CLIENT_SECRET=...
export ENTRA_AUTHORITY_DID=did:web:verifiedid.example.com
./gradlew :credentials:plugins:platforms:entra:test
```

## References

- [Verified ID Request Service REST API](https://learn.microsoft.com/entra/verified-id/issuance-request-api)
- [Presentation Request API](https://learn.microsoft.com/entra/verified-id/presentation-request-api)
- [Microsoft identity platform: client_credentials grant](https://learn.microsoft.com/entra/identity-platform/v2-oauth2-client-creds-grant-flow)
