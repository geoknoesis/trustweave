# Verification security migration

The production-readiness remediation changes acceptance behavior and public signatures.
Recompile consumers and review the ABI diff before release.

## OpenID Federation

Configure `TrustChainResolver(trustedAnchorKeys = mapOf(anchorId to anchorPublicJwkSet))`.
Provision those public keys through an independently trusted channel, never from the incoming
chain. With no configured keys, verification fails closed. The exchange provider accepts the
same map in its `trustedAnchorKeys` option, alongside `trustedAnchorIds`.

This implementation requires a complete chain ending in the anchor entity configuration.
It checks the leaf self-signature, superior endorsements, issuer/subject links, validity times
and the pinned anchor signature. Resolution returns success only after verification.
The default HTTP client applies the federation egress guard; injecting a client transfers
responsibility for egress controls to the integrator.

## Verifiable Intent

Provide verifier-controlled expected audience and nonce for L2 and each presented L3 token:
`expectedL3PaymentAud`, `expectedL3PaymentNonce`, `expectedL3CheckoutAud`, and
`expectedL3CheckoutNonce`. Missing expectations fail by default. Nonce comparison is not a
replay database: the application must issue fresh challenges and atomically consume them.
The explicit offline-audit opt-out is unsuitable for authorizing payments.

Amounts must be non-negative JSON integers and constraints must name their currency.
Budget and recurrence constraints require external state that this verifier cannot establish;
open mandates containing them fail closed. Autonomous checkout remains unsupported because
line-item matching is unimplemented. These restrictions are not claims of complete VI support.

## Wallet status and bounded sessions

File and database factories now accept an optional `WalletStatusResolver` constructor
argument. Unknown status matches neither value of the revoked filter. Unfiltered
listing remains available for recovery and inspection. Database `countCredentials()`
provides a storage count without remote status resolution; full statistics remain
an advisory scan. See [operations guidance](../operations/observability.md).

OID4VP's default session store has configurable capacity and TTL. SIOP uses the same
bounded five-minute store; CHAPI retains no generated messages. Handle session
capacity exceptions at the application boundary. The OIDC4VCI exception hierarchy
adds `CapacityExceeded`; update exhaustive `when` expressions in consumers.

Testkit wallet factories now reject both strict deployment policies. No wallet has
been promoted to supported maturity by this remediation.

## Reference-wallet contracts

`verifyPresentation()` is asynchronous because verified encrypted disclosures use
Web Crypto. Await its result. `resetWallet()` returns `keysCleared`; a false value
means local records were reset but browser key storage still needs manual erasure.
Passkey identities now record backup eligibility at enrollment. Existing identities
without the field retain their device-only verification policy. See the
[custody contract](../../reference-wallet/CUSTODY.md) for the sync-provider boundary.

## Credential anchors and database status lists

Credential anchor verification compares the actual ledger payload with the credential,
removing only the evidence added by anchoring and respecting `includeProof`. Evidence
metadata cannot replace the ledger comparison. Missing anchors and changed credentials
fail verification.

Database status checks reject missing lists, missing credential assignments and indexes
outside the declared list size. Allocation is serialized per list and rejects exhaustion;
repeating an assignment returns the existing index. An existing credential cannot be
silently moved to a different status index.

Indy ATTRIB writes now use the named attribute and the native VDR signing format.
GET_ATTRIB only exposes the current value: reading an older sequence number after another
write for the same DID fails instead of returning the replacement payload.
