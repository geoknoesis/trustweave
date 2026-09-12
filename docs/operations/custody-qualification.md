# Live custody and key-loss qualification

Status: **software PKCS#11 boundary qualified in CI; live custody remains not
qualified**. CI provisions an isolated SoftHSM2 token and verifies P-256 key
generation, lookup, signing, deletion, unsupported-algorithm handling and reuse of
an existing key after recreating the KMS client. These named tests are mandatory in
`config/testing-contract.json`; the skip policy does not permit them to be skipped.

SoftHSM2 does not qualify a hosted or physical HSM, provider IAM, production audit
delivery, dual-control recovery or a deployed Accountly wallet flow. No identified
non-production KMS resource and credential profile has been recorded for those
exercises. Accountly billing validation does not qualify custody. The reference
wallet's managed and passkey adapters remain experimental; see [the implemented
contract](../../reference-wallet/CUSTODY.md).

## Identify the test boundary

Before execution, record these non-secret values in the evidence record:

- Provider, account/project/subscription, region and immutable key resource ID.
- Existing credential profile or workload identity; never include credential values.
- Key algorithm, public-key fingerprint and service endpoint under test.
- Disposable tenant, wallet and key; explicit scope for reversible access revocation.
- Application commit, deployed artifact digest, configuration revision and test time.
- Recovery operator identity, independent approver and recovery policy version.

Use an isolated non-production resource. Do not infer permission to disable a
shared key, change an account-wide policy or schedule deletion. A provider alias
alone is insufficient: record the resolved immutable key ID before and after each
exercise. The signing principal and recovery administrator must be distinct.

## Acceptance matrix

| Gate | Exercise | Required evidence |
| --- | --- | --- |
| Provider identity | Resolve the identified key through the configured SDK and retrieve its public key | Exact resource ID, algorithm, public-key fingerprint and provider request ID |
| Real signing | Sign a fresh random challenge through TrustWeave; verify independently with the public key; reject altered message/signature | Challenge digest, verification results and correlated provider audit event; no private material |
| Authorization | Attempt signing as the wrong tenant, wrong holder and an unprivileged principal | All rejected before signing; audit trail proves no unauthorized provider sign operation |
| Protocol | Test audience, expiry, nonce replay, key ID and algorithm substitution through the deployed endpoint | Exact expected outcomes, durable challenge consumption and independently verified JWS |
| Provider outage | Deny access to the disposable key or isolate the test service, then request signing | Bounded failure, no alternate-key/local-key fallback and no success response |
| Access recovery | Restore authorized access using the recovery administrator, restart the service and retry with a fresh challenge | Recovery audit event, unchanged key fingerprint and successful fresh proof; old nonce still rejected |
| Lost-key replacement | Make the old disposable key unavailable; enroll a distinct replacement through the recovery workflow | Independent recovery authorization, new fingerprint, old binding revoked, credential reissuance where binding changes |
| Durable recovery | Restore application state into an isolated instance and replay pre-recovery challenges | Old authorizations rejected; recovery completion and key binding survive restart; no duplicated signing authorization |
| Incident visibility | Trigger the test failure/recovery alerts | Correlated application/provider events, alert delivery and acknowledgement timestamps |

Revoking access and restoring it demonstrates **access recovery**, not recovery of a
destroyed private key. Replacement-key recovery proves continuity through a new
identity binding; it cannot recreate signatures from a lost non-exportable key.
Do not silently relabel old did:key credentials or accept both old and new holder
bindings without an explicit, tested policy.

## Execution and evidence rules

1. Capture the identified resource and approved mutation scope. Confirm the
   application endpoint implements the server obligations in `CUSTODY.md`.
2. Run baseline signing and all rejection cases before mutating access.
3. Record the recovery start and deny signing only for the disposable resource.
   Preserve the original policy/configuration for restoration.
4. Exercise recovery, independently verify the new proof and test all old
   authorizations again. Compare immutable IDs and public-key fingerprints.
5. Restore test access/configuration in a cleanup step even if assertions fail.
   Record cleanup failure as a failed exercise requiring operator attention.
6. Retain sanitized results plus immutable references to restricted audit records.
   Do not publish bearer tokens, wallet backups, private keys or full credentials.

Each gate records `passed`, `failed`, or `not_run`, its command/driver, start/end
times, expected/actual outcome and evidence reference. Missing inputs, unavailable
services and skipped tests are `not_run`, never a pass. Report recovery duration
as an observation for this resource; do not turn it into a production RTO promise.

## Remaining application work

The current reference-wallet adapters do not implement a production managed
signing endpoint, durable enrollment/recovery service or custody selection in the
existing Ed25519 UI. Those components must be implemented and exercised before
end-to-end custody can be qualified. A direct provider SDK test can close only the
provider identity/signing gate. Physical passkey and sync-account recovery require
their own device/provider evidence and are not covered by a cloud KMS exercise.

The Accountly repository contains a protected `Staging production qualification`
workflow and `scripts/qualify-deployment.py`. It exercises issuance and verification
on both sides of a `did:web` key rotation, cross-tenant rejection, mixed authenticated
load, process restart, isolated backup restore, and Alertmanager-sourced on-call
acknowledgement. This is an executable qualification contract. It becomes evidence
only after a successful run records the identified deployment revision, image digest,
KMS resource, database version, region, recovery operations and alert acknowledgement.
