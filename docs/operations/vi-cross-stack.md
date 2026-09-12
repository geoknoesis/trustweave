# Reproducing VI cross-stack checks

The reference is the official Python implementation at
`https://github.com/agent-intent/verifiable-intent`, pinned to commit
`356c29635f1c44df7de02edb58699ca9f29bece6`. These checks demonstrate specific
interoperable flows; they do not certify all draft features.

## Run

From the repository root, use Python 3.11 with `cryptography==46.0.3` and
`pytest==9.0.2` in an isolated environment. Do not install into a production runtime.
Clone the reference into `.gradle/vi-reference`, check out the exact commit above,
and leave its tracked files unchanged. The harness rejects a different revision
or modified tracked source, including staged changes.

```text
python scripts/vi-cross-stack.py generate --reference .gradle/vi-reference --file credentials/plugins/verifiable-intent/src/test/resources/vi_python_cross_stack.json
```

Generation uses the reference's public demonstration keys and random disclosure
salts. Regeneration changes bytes; review semantic outcomes rather than expecting
identical JSON hashes. The checked-in fixture contains public keys and synthetic
tokens only. Never substitute customer credentials or production private keys.

Run the VI module's normal Gradle `test` and `ktlintCheck` tasks. The full module
suite requires Docker. For the cross-stack checks alone, filter tests to
`*CrossStackConformanceTest`,
`*IssuanceRoundTripTest.issue an immediate chain via KMS and verify it` and
`*IssuanceRoundTripTest.issue an autonomous chain via KMS and verify it`.
Also include `*IssuanceRoundTripTest.merchant authenticated autonomous checkout verifies and enforces signed quantities` to generate the checkout export. These filtered cases do not require a live provider or Docker.

Then verify the Kotlin exports in Python:

```text
python scripts/vi-cross-stack.py verify --reference .gradle/vi-reference --file credentials/plugins/verifiable-intent/build/qualification/vi-kotlin-immediate.json
python scripts/vi-cross-stack.py verify --reference .gradle/vi-reference --file credentials/plugins/verifiable-intent/build/qualification/vi-kotlin-autonomous.json
```

Exports are generated during tests using ephemeral in-memory KMS keys. Always
require a successful fresh test run before consuming them: an existing file alone
does not prove that the current tree passed. The reverse verifier also tests wrong
L2 audience/nonce and, for payment tokens, wrong L3 audience/nonce.

To run the reference's own suite, set `PYTHONPATH` to the absolute path of
`.gradle/vi-reference/src`, then run
`python -m pytest .gradle/vi-reference/tests -q --junitxml=.gradle/vi-reference-results.xml`.
Those tests qualify the pinned reference, not TrustWeave's implementation.

## What full conformance still requires

| Area | Current evidence | Additional gate |
| --- | --- | --- |
| Immediate issuance and verification | Python fixture: positive, wrong audience, wrong nonce, expiry, withheld mandates, invalid signature; Kotlin export verified in Python | Expand selective-disclosure permutations and all malformed JOSE/claim encodings in both directions |
| Autonomous payment | Python-issued L3 positives, boundary amounts, withheld/reordered disclosures and routed/challenge negatives; reverse Kotlin export | Complete supported-constraint and malformed-encoding permutations in both directions |
| Merchant checkout | Python-issued signed merchant/cart adversarial vectors plus reverse Kotlin checkout export; two explicit peer policy differences | Independent merchant implementation with identical documented trust profile and wider adversarial cart/disclosure vectors |
| Stateful constraints | 214 sequential Python-model/PostgreSQL transitions plus dedicated Kotlin concurrency, rollback and reconciliation tests | External executor agreement, independent concurrent traces, uncertain commits and recovery traces |
| Multiple mandate pairs | Unsupported and rejected | Implement and test pair-specific key routing, cross-references and isolation before acceptance |
| Multiple/mixed recurrence constraints | Unsupported combinations rejected | Define and implement intersection/aggregation semantics with vectors proving no budget/count bypass |
| Merchant profiles | One bounded authenticated profile | Explicitly specify and test additional authenticated profiles, including trust provisioning |

The Python verifier is an interoperability peer, not a security oracle. Agreement
on chain structure does not prove durable budget enforcement, authenticated
merchant metadata or payment settlement. Keep intentional stricter rejection
policies documented; never relax fail-closed checks simply to make the two stacks
agree. Full acceptance requires a versioned requirement-to-vector inventory, both
directions, every supported constraint/profile, and no unexplained differences.

## Expanded autonomous and ledger qualification

The versioned [conformance matrix](../../config/vi-conformance-matrix.json) now identifies
6 immediate and 20 autonomous/payment/checkout Python-issued cases, reverse Kotlin exports, and a
214-transition ledger model. Regenerate the autonomous fixture only after reviewing
semantic changes; random disclosure salts mean generated bytes need not match:

```text
python scripts/vi-cross-stack.py generate-autonomous --reference .gradle/vi-reference --file credentials/plugins/verifiable-intent/src/test/resources/vi_python_autonomous.json
python scripts/vi-cross-stack.py verify --reference .gradle/vi-reference --file credentials/plugins/verifiable-intent/src/test/resources/vi_python_autonomous.json
python scripts/vi-cross-stack.py verify --reference .gradle/vi-reference --file credentials/plugins/verifiable-intent/build/qualification/vi-kotlin-checkout.json
python scripts/vi-stateful-model.py --check --file credentials/plugins/verifiable-intent/src/test/resources/vi_stateful_model.json
```

The Python verification harness composes the pinned reference's chain and payment
constraint checks and resolves disclosed payee entries as its own example does.
The Kotlin factory exposes each autonomous case as a separate JUnit invocation.
Wrong challenges, expired tokens, changed routed presentations and out-of-range
payments must fail. Reordered full disclosures and inclusive amount boundaries pass.

Two explicit differences remain: the reference chain checker does not authenticate
TrustWeave's pinned merchant challenge/cart profile. A wrong merchant audience and
an excessive signed cart quantity therefore pass the reference chain/payment checks
but fail the SDK. `referenceExpected` and `difference` record this boundary; an
unexplained difference fails the harness. This is not permission to relax SDK checks.

`CrossLanguageLedgerModelTest` compares each of 214 model transitions with real
PostgreSQL decisions, spending and terminal-state counts. It reconstructs the ledger
object every 17 operations to check persistent state. The Python model is an
independent-language specification model, not an external settlement provider. Existing
concurrent/rollback/uncertain-commit tests remain necessary; sequential model agreement
does not replace them. Merchant recurrence, full external journal authentication and
broader provider interoperability retain their separate qualification requirements.
