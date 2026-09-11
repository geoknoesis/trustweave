---
title: Testing Strategies
nav_exclude: true
redirect_from:
  - /advanced/testing-strategies/
nav_order: 90
---

# Testing Strategies

The maintained [testing guide](../../contributing/testing-guidelines.md) contains a
complete fixture example compiled and executed by the SDK. Use that source as the
copyable starting point; this page maps test types to the claims they can support.

| Test type | Verifies | Does not establish |
| --- | --- | --- |
| Pure unit/property tests | Parsing, boundaries, state transitions and adversarial rejection | Provider behavior or deployment configuration |
| In-memory testkit fixtures | Local workflow contracts and isolated registries | Cryptographic interoperability or managed custody |
| Real local component integrations | HTTP wire behavior, OTLP export, database/pool semantics exercised | Production scale, networking or provider availability |
| Independent implementation vectors | Named supported-profile interoperability and documented differences | Untested profiles or durable external enforcement |
| Hosted deployment exercises | Identified release/topology/provider behavior with recorded evidence | Other environments or untested failure modes |

Use `org.trustweave:testkit:0.7.0` as a test dependency in an application. In this SDK
checkout, use `testImplementation(project(":testkit"))`. Choose the actual DID/KMS
implementation when the claim concerns cryptographic or provider behavior; the fixture's
mock DID method is intended for local application contract tests.

Prefer semantic assertions over merely checking that a result is non-null. For credentials,
verify signature/issuer/subject expectations and adversarial rejection. For wallets, verify
storage isolation and encrypted recovery. For authorization, verify replay, budget/count
limits and uncertain outcomes. For hosts, verify correlation, privacy, overload and cleanup.

The [integration guide](../../contributing/testing/integration-testing.md) covers prerequisites
and failure reporting. The [VI conformance profile](../../../credentials/plugins/verifiable-intent/CONFORMANCE.md)
and [cross-stack guide](../../operations/vi-cross-stack.md) identify implemented and unsupported
profiles. The [acceptance checklist](../../contributing/testing/acceptance.md) defines what remains
before a perfect testing/documentation assessment can be justified.
