---
title: Blockchain-Anchored Revocation
nav_exclude: true
---

# Blockchain-Anchored Revocation

TrustWeave separates two concerns:

1. **Status-list revocation** — maintained by `CredentialRevocationManager` against W3C
   StatusList 2021 bitstrings. Fast, off-chain, mutable.
2. **Blockchain anchoring** — performed by `trustWeave.blockchains.anchor(...)` against
   any registered chain plugin. Tamper-evident, append-only, billable.

Anchoring a revocation list combines (1) and (2): you update the status list locally,
then anchor the resulting `StatusListCredential` (or just its digest) to a chain
whenever you need on-chain integrity. There is **no** built-in
`BlockchainRevocationRegistry` or `AnchorStrategy` API today — you compose the two
pieces yourself.

## Components

| Component | Source | Purpose |
|---|---|---|
| `CredentialRevocationManager` | `org.trustweave.credential.revocation.CredentialRevocationManager` | Create/update status lists, revoke/suspend credentials, query status. |
| `RevocationManagers.default()` | `org.trustweave.credential.revocation.RevocationManagers` | In-memory implementation for dev/test. |
| Status-list plugins | `credentials/plugins/status-list/{bitstring,token,database}` | Persistent / token-bitstring / DB-backed status list storage. |
| `trustWeave.blockchains` | `org.trustweave.anchor.services.BlockchainService` | `anchor(data, serializer, chainId)` and `read(ref, serializer)`. |
| Chain plugins | `anchors:plugins:{algorand,ethereum,polygon,…}` | Concrete `BlockchainAnchorClient`s discovered via SPI. |

## Step 1 — Configure a revocation manager

```kotlin
import org.trustweave.credential.revocation.RevocationManagers
import org.trustweave.trust.TrustWeave

val trustWeave = TrustWeave.build {
    // ... DID, KMS, etc.
    credentials {
        revocationManager(RevocationManagers.default())   // in-memory; swap for a persistent impl in prod
    }
}
```

## Step 2 — Create a status list

Use the DSL or call the manager directly. The DSL returns a typed `StatusListId`.

```kotlin
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.trust.dsl.credential.revocation

val statusListId = trustWeave.revocation {
    forIssuer("did:key:university")
    purpose(StatusPurpose.REVOCATION)
    size(131072)        // 16 KB bitstring, holds ~131k credentials
}.createStatusList()
```

## Step 3 — Issue credentials that reference the status list

Issue credentials with a `credentialStatus` entry pointing at `statusListId`. The
issuance DSL (`trustWeave.issue { credentialStatus(...) }`) wires this through; see
[How-To: Issue Credentials](../how-to/issue-credentials.md).

## Step 4 — Revoke / suspend

```kotlin
import org.trustweave.trust.dsl.credential.revoke

trustWeave.revoke {
    credential("urn:uuid:cred-123")
    statusList(statusListId.value)
}
```

For batch operations, drop down to the manager:

```kotlin
import org.trustweave.credential.revocation.RevocationManagers

val manager = RevocationManagers.default()
manager.revokeCredentials(listOf("cred-1", "cred-2", "cred-3"), statusListId)
```

## Step 5 — Anchor the status list to a blockchain

After material updates, anchor the status list payload through `BlockchainService`.
Anchor the full `StatusListCredential` (small — a few KB) when you want consumers to
fetch and verify directly from the chain, or anchor only its digest (a few dozen
bytes) when you want a cheap tamper-evident marker.

```kotlin
import org.trustweave.credential.model.vc.VerifiableCredential

// You are responsible for rendering the status-list bitstring as a VerifiableCredential.
// One way: keep the latest StatusListCredential next to your status-list storage.
val statusListCredential: VerifiableCredential = renderStatusListCredential(statusListId)

val anchorResult = trustWeave.blockchains.anchor(
    data       = statusListCredential,
    serializer = VerifiableCredential.serializer(),
    chainId    = "algorand:testnet"
)

println("Anchored: tx=${anchorResult.ref.txHash} chain=${anchorResult.ref.chainId}")
```

Reading back later:

```kotlin
val fetched = trustWeave.blockchains.read(
    ref        = anchorResult.ref,
    serializer = VerifiableCredential.serializer()
)
```

## Step 6 — Check status

At verification time, the credential's `credentialStatus` entry tells the verifier
which list and which index to consult. `RevocationStatus` carries both `revoked`
and `suspended` flags.

```kotlin
import org.trustweave.credential.revocation.RevocationStatus

val status: RevocationStatus = trustWeave.revocation { }.check(credential)

when {
    status.revoked   -> println("REVOKED${status.reason?.let { " ($it)" } ?: ""}")
    status.suspended -> println("SUSPENDED")
    else             -> println("VALID")
}
```

## Anchoring cadence — pick a policy in application code

There is no built-in `AnchorStrategy`. Choose what fits your traffic:

| Pattern | When to anchor | Trade-off |
|---|---|---|
| **Eager** | After every `revoke()` call | Highest integrity, highest cost (1 tx per revocation). |
| **Periodic** | On a scheduler (e.g. every hour) | Bounded latency, predictable cost. |
| **Threshold** | After N revocations since the last anchor | Cost scales with volume. |
| **Lazy / on-demand** | When a verifier explicitly requests on-chain proof | Cheapest, latency on the verify path. |

Simplest periodic anchoring loop:

```kotlin
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
scope.launch {
    while (isActive) {
        val statusListCredential = renderStatusListCredential(statusListId)
        runCatching {
            trustWeave.blockchains.anchor(
                data       = statusListCredential,
                serializer = VerifiableCredential.serializer(),
                chainId    = "algorand:testnet"
            )
        }.onFailure { logger.warn("Anchor failed", it) }
        delay(60.minutes)
    }
}
```

## Statistics and monitoring

`CredentialRevocationManager` exposes per-list statistics. There is no built-in
"pending anchors" view — track that in your own application state (e.g. a counter
incremented on each `revoke` and reset on each successful `anchor`).

```kotlin
val stats = RevocationManagers.default().getStatusListStatistics(statusListId)
println("Capacity: ${stats?.totalCapacity}, used: ${stats?.usedIndices}, " +
        "revoked: ${stats?.revokedCount}, suspended: ${stats?.suspendedCount}")
```

## Related documentation

- [Blockchain Anchoring](./blockchain-anchoring.md) — general anchoring guide
- [Verifiable Credentials](./verifiable-credentials.md) — credential model and lifecycle
- [Core API — Revocation and Status List Management](../api-reference/core-api.md#revocation-and-status-list-management) — DSL and manager reference
