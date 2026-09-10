# Intent payment validation follow-up

This follows the [latest remediation assessment](../2026-09-06-production-readiness/follow-up-review/remediation.html),
which supersedes the round-2 report. Scope: the current local TrustWeave SDK tree.

## Fixed defect: TW-PV-01

`ChainVerifier.paymentRequiredFields` previously checked only the presence of
`payment_amount.amount` and a printable currency. Immediate mode returned success
without running `ConstraintChecker`; autonomous mode could also omit an optional
amount-range constraint. Both paths therefore needed unconditional payment schema
validation at their shared chain boundary.

The documented profile uses non-negative integer minor units and uppercase
three-letter currency strings. Regression vectors exercise valid zero, positive
and maximum signed 64-bit values, as well as negative, quoted, fractional,
overflowing, null, boolean, collection and missing amounts, and malformed currencies.
All vectors use real ES256 signatures and the public verification API.

The signed baseline reproduced 26 incorrectly accepted cases out of 36 vectors.
The shared chain validator now enforces both amount and currency syntax before
either mode can return success. All 36 vectors pass after the fix, including
valid boundary values. Currency syntax does not establish ISO currency membership.

The complete intent module passed **87 tests, zero failures/errors/skips**, plus
module lint and API compatibility checks. The previously interrupted PostgreSQL
and independent backup/restore exercises completed. The six existing Python-issued
cases passed in Kotlin; eight reverse checks passed against the pinned reference.
No broader conformance claim is made.

See the [HTML report](index.html), [validation evidence](validation.json),
[baseline failures](before.xml) and [final build log](validation.log).
The six-category score remains **8.7 / 10**. Current full-repository coverage,
hosted CI/release, live custody, complete interoperability and production
PITR/journal/pager qualification remain outside this run.
