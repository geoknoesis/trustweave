# Verifiable Intent conformance profile

This is a tested subset of draft v0.1, not certification. Reference:
[VI constraint definitions, sections 4.5-4.7](https://www.verifiableintent.dev/spec/constraints/),
reviewed 2026-09-06. Existing fail-closed behavior is preserved for unsupported shapes.

| Area | Evidence | Boundary |
| --- | --- | --- |
| Signatures and chain binding | Algorithm pinning, known-answer and issuance round-trip tests | ES256, one L2 mandate pair |
| Amounts and currency | Constraint regression and signed immediate/autonomous schema vectors | Non-negative signed 64-bit integer minor units and uppercase three-letter currency strings are required even without an amount-range constraint; budget minimum is per payment |
| Merchant/payee allowlists | Disclosure hash and ID downgrade regressions | Open mandates reject unavailable disclosures; pinned IDs cannot fall back to names |
| Cart items | 512 exhaustive allocations and signed merchant checkout tests | Narrow pinned checkout JWT profile; stricter allocation semantics for overlapping alternatives |
| Cumulative budget | PostgreSQL concurrency, rollback and uncertain-commit tests | One budget constraint; reserved plus settled spending counts; confirmed non-execution can release funds; shared ledger required |
| Agent recurrence | Date boundaries, malformed values, companion constraints and count races | All declared frequency codes accepted as guidance, not strict scheduling; single-use without agent recurrence |
| Merchant recurrence | Signed checkout setup round trip and metadata mismatch vectors | One bounded subscription setup; all four signed metadata fields required |
| Reconciliation | Concurrent duplicate release, conflicting outcomes and restored replay tests | Privileged host verifies external journal; no automatic refund or release on timeout |
| Recovery | HTTP host failure plus pg_dump/pg_restore into independent PostgreSQL | Quiesced zero-RPO component exercise, not production restore qualification |

Unsupported: multi-pair L2, multiple budgets/recurrence constraints, mixed recurrence modes,
unknown recurrence fields, open-ended merchant setup, unverified merchant metadata, payment
provider evidence authentication, full cross-provider interoperability and automatic billing jobs.
Unknown open-mandate constraint types remain rejected. Pinned Python-issued immediate
positive/adversarial vectors and Kotlin-issued immediate/autonomous payment exports now
exercise both implementation directions. See the [cross-stack reproduction guide](../../../docs/operations/vi-cross-stack.md)
for exact scope and remaining gates. This is not a complete conformance suite.

Tests are compiled with the module's normal `test` task, require Docker for PostgreSQL and
fail when the required database cannot start. No silent skip or weaker coverage floor is used.

Expanded qualification adds Python-issued autonomous and checkout vectors, Kotlin checkout exports, and a 214-transition Python model compared with PostgreSQL. The versioned [matrix](../../../config/vi-conformance-matrix.json) records scope and two intentional merchant-policy differences; executed results are required before counting a gate as passed.
