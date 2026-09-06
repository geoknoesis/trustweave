# Provider deployment and wallet custody profiles

## Round 9 implementation update

Both passkey and managed-KMS profiles are selected. Experimental adapters and
their validation boundaries are documented in
[the dual custody contract](../../reference-wallet/CUSTODY.md). They are not yet
wired into the existing Ed25519 wallet UI; physical-device qualification and a
production managed service remain open. No provider maturity was promoted.

File, database and cloud wallet factories now enforce the typed
`WalletCreationOptions.deploymentPolicy` before opening resources. Set
`WalletDeploymentPolicy.SUPPORTED_ONLY` for production, or `EXPERIMENTAL` for an
explicitly accepted experiment. The compatibility default is `LEGACY`. Builder
configuration also carries this policy. Direct constructors and other domain
providers still require their own deployment gate.

The file factory honors `WalletCreationOptions.encryptionKey`, rejects conflicting
typed/legacy keys and unsafe wallet IDs. Wallet IDs are limited to 1–128 ASCII
letters, digits, underscores and hyphens; callers using other IDs must migrate
their identifiers explicitly. The configured storage directory must be controlled
by the application operator; this is not isolation from a local administrator.

The reference-wallet import and demo-verifier boundaries share a strict profile:
Ed25519 did:key issuer, canonical key reference, unambiguous VC-JWT versus SD-JWT,
consistent issuer/subject/key binding, valid time claims and top-level object
disclosures only. Encrypted claim payloads remain available under the existing
browser profile; the new custody primitives do not implement claim decryption.

## Provider selection

API stability and deployment maturity are different assessments. GA labels in
`module-maturity.md` describe API support, not attestation of a hosted provider or
custody device. The runtime catalog and generated `assessed-capabilities.md`
are the authority for explicitly assessed operations.

Before constructing a client or starting a workflow, call:

```kotlin
ModuleCapabilities.requireDeployment(
    module = "wallet:plugins:database",
    operations = setOf("store", "get", "page-records"),
    formats = setOf("json-vc"),
)
```

This example intentionally fails today: the database provider is experimental.
Production allows only catalog entries marked `supported`; there are currently
none. Unknown modules, unknown maturity values and stubs always fail this gate.
For an explicitly accepted experimental deployment, pass
`allowExperimental = true`; operations and formats still apply. Empty requirement
sets cannot bypass maturity checks. Older `requireOperations` / `requireFormats`
helpers do not enforce maturity.

The internal registry can enforce `requireSupportedProviders = true`. Its default
remains compatible with experimental use. Registration cannot promote catalog
maturity through different plugin metadata. The file/database/cloud factories apply the selected typed policy automatically.
Other domain registries and direct constructors must call the public gate; this
is not automatic global enforcement across the SDK.

To promote a provider, record its tested version, operations, formats,
authentication, timeout/retry behavior, failure/recovery contracts, concurrency
limits and operational ownership. Attach evidence from the actual target.
Emulator/local HTTP tests establish only their tested scope. Round 7 Accountly
evidence covers local Accountly/Keycloak/Kill Bill, not PSP settlement, hosted
staging or cloud KMS.

## Reference-wallet custody

The implemented profile is browser software custody:

- Ed25519 signing and X25519 agreement CryptoKeys are non-extractable.
- Each load checks private-key type, algorithm, usage and non-extractability.
- A random signing challenge verifies DID binding. Ephemeral Diffie-Hellman
  verifies the agreement key against the corresponding public key.
- Imports cannot overwrite existing keys. Interrupted migration retries succeed
  only after validating the previously committed keys.
- Import seed buffers are cleared on every exit, including validation failures.
- Corruption/substitution fails closed and preserves metadata/credentials.
  Only an absent key enables the replacement-identity workflow.

These checks detect storage corruption/substitution when application code is
trusted. They cannot protect against malicious same-origin code invoking WebCrypto,
a compromised browser/OS or replacement of both identity and keys. Non-extractability
does not prove hardware protection. Clearing buffers does not prove removal of
every runtime-managed copy. Validation runs on each load and adds cryptographic
work; no signing latency SLA is claimed.

## Production custody completion criteria

Both profiles are selected; production integration and qualification remain open:

| Target | Required implementation and evidence |
|---|---|
| Device-bound passkey | WebAuthn registration/assertion validation, user verification, RP/origin binding, lifecycle and compatible issuer/verifier proof formats. An unlock prompt around a browser Ed25519 key does not satisfy hardware signing. |
| HSM/KMS service | Holder authorization per operation, supported algorithms, tenant isolation, audit records, rotation/recovery policy and actual device/service failure tests. This changes the custody trust model. |

Test enrollment, signing denial/cancellation, replay/origin attacks, key loss,
revocation/reissuance and restore on the chosen profile. The current did:key
Ed25519/X25519 wallet cannot swap in a P-256 passkey without changing identity/proof
and agreement protocols. Do not promote it to hardware custody based on software
hardening tests alone.
