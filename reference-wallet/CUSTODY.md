# Dual custody integration contract

Both custody adapters live in `lib/custody`. They are experimental building blocks,
not enabled replacements for the existing browser wallet. Existing identities and
credential backups are unchanged. No hardware production certification is claimed.

## Explicit profiles

`signCustodyPayload(identity, payload, accessToken?)` dispatches on a required
`profile` discriminant. Unknown profiles, missing authorization and provider
failures reject; there is no fallback to the browser Ed25519 key.

| Profile | Implemented proof | Verification and operational boundary |
|---|---|---|
| `passkey` | ES256 WebAuthn assertion over SHA-256 of the exact JSON payload | Exact RP/origin, UP and UV flags, enrolled backup-eligibility policy, key and challenge binding. This is an application proof envelope, not an SD-JWT KB-JWT or ordinary JWT. |
| `managed-kms` | EdDSA or ES256 compact JWS returned by an authenticated HTTPS service | Exact payload, algorithm, key ID and pinned public-key verification; bounded response, timeout and no redirects/retries. The client cannot attest that the service actually uses an HSM. |

## Passkey integration

Call `enrollPasskey(displayName)` from a user action. Enrollment creates a resident
P-256 credential, requires user verification and confirms key possession with an
assertion. Enrollment records whether the credential is backup-eligible. Synced
passkeys are accepted with that explicit identity policy; verification rejects a
changed eligibility flag and backup-state without eligibility. Legacy identities
without this field retain the device-only policy. Sync-provider account recovery
is part of the trust boundary for backup-eligible credentials.
It accepts an exact HTTPS origin/RP hostname, with HTTP localhost for development.
Attestation is `none`: vendor/device provenance is not established. A virtual or
software authenticator can pass these protocol checks.

The integrating application must persist the public identity through an authenticated
enrollment, bind it to the holder account, and obtain a fresh verifier-issued nonce.
Use `verifyPasskeyPayload` with the server's enrolled identity and expected audience,
nonce and expiry in epoch milliseconds. Its mandatory `consume` callback must
atomically enforce one-use challenges and persist the chosen signature-counter
policy. Do not return success without a durable transaction. Zero-counter support
is an application policy decision. Separately verify every issuer credential and
the credential-to-enrolled-holder binding; this primitive verifies possession only.

Production activation additionally requires enrollment/recovery UI and server
persistence, device attestation policy where hardware provenance is required,
issuer/verifier support for the chosen presentation protocol and physical-device
validation. Existing Ed25519 did:key credentials cannot silently become P-256
passkey credentials. Reissuance is required when identity/key binding changes.

## Managed service contract

Provision `endpoint`, `keyId`, `algorithm` and `publicKey` through trusted enrollment.
Never obtain an endpoint or pinned key from an untrusted QR or credential. Supply
a short-lived bearer access token in memory; the adapter does not persist it.

The HTTPS endpoint receives:

```json
{"keyId":"enrolled-key","algorithm":"EdDSA","encodedPayload":"base64url-of-exact-JSON"}
```

It returns `{"jws":"protected.payload.signature"}`. The protected header must
match the enrolled algorithm/key ID; payload bytes must exactly match the request.
ES256 signatures use JWS raw R||S encoding, not ASN.1 DER.

The service must authenticate the token, derive tenant/holder identity from trusted
session state, authorize the specific key and operation, validate audience and
nonce/expiry, enforce one-use authorization, invoke the selected KMS without private
key export and persist audit/recovery state. Client-side checks are not a substitute
for this service authorization. No production service endpoint is implemented or
configured by these client adapters. A non-production KMS resource and credential
profile were requested for the remaining provider integration.

## Validation limits

Tests use real browser WebAuthn with a Chromium virtual authenticator and locally
generated cryptographic keys for managed-service contract fixtures. They are not
physical HSM, physical passkey, hosted IAM or recovery-service validation. Neither
profile is wired into the existing Ed25519 reference-wallet enrollment/presentation
UI yet; do not advertise production custody until that application work and actual
provider qualification are complete.

Protocol reference: [W3C WebAuthn Level 3](https://www.w3.org/TR/webauthn-3/).
