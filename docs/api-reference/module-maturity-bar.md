# The module maturity bar

Every one of the 110 modules carries a maturity in
[`trustweave-capabilities.json`](../../common/src/main/resources/trustweave-capabilities.json).
This page is the rule those maturities are assigned by, so that a reader can disagree with a
particular classification on the evidence rather than on taste.

Before this, 100 modules carried no maturity at all. A consumer reading the catalog could not tell
*"we assessed this and it is experimental"* from *"nobody has looked at it yet"* — and the second
is the one that matters, because it is the one where the absence of a warning means nothing.

## The three ratings

### `stub` — delivers no callable capability

Either every public entry point throws, or the module has no SDK surface at all (a build
aggregator, an application shell). Depending on one for function will fail at runtime, by design:
these fail closed rather than pretending.

Eleven modules: `anchors:plugins:starknet`, `did:plugins:threebox`, `did:plugins:tezos`,
`did:plugins:btcr`, `credentials:plugins:platforms:salesforce`,
`credentials:plugins:platforms:servicenow`, `distribution:all`, `distribution:bom`, and the three
`reference-wallet:android` modules.

`distribution:examples` is deliberately **not** on this list: it carries about 2,500 lines of
runnable example code with its own tests, so it is `experimental` like any other implemented
module.

### `experimental` — implemented and exercised, not qualified

There is a real implementation and tests run against it, but at least one requirement of
`supported` is unmet. `ModuleCapabilities.requireDeployment` refuses these unless the host passes
`allowExperimental`, so a production policy excludes them until someone says otherwise.

Ninety-nine modules — currently everything that is not a stub.

### `supported` — qualified for production

**All four** of the following, and each has to be a thing someone can open and read:

| | Requirement | How it is evidenced |
|---|---|---|
| 1 | **Coverage floor** — ≥70% line and ≥60% branch for the module | The merged Kover report |
| 2 | **Security review** — named in a dated review, findings resolved | A file under `docs/reviews/` |
| 3 | **Interoperability evidence** — a retained artifact from a cross-stack or vector-based test | A CI artifact from a named run |
| 4 | **Documented operational limits** — what it does not do, how it fails, where it stops scaling | A runbook section under `docs/operations/` |

## Nothing is `supported` yet, and that is the honest answer

Thirty-two of the 110 modules meet requirement 1. **None meets all four**, so none is classified `supported`.

The gap is not coverage. It is that requirements 2–4 are evidence about a module that mostly does
not exist yet: one cross-stack interoperability artifact is retained in CI (verifiable-intent), and
no module has a documented operational-limits section of its own.

Classifying a module `supported` without that evidence would be the same error this repository has
been correcting elsewhere — asserting a control that was never established. So the catalog says
`experimental`, and this section says exactly what would change it.

### What the GA core needs

These eight are the intended first `supported` set. Coverage figures are from the merged report.

| Module | Line coverage | Still needs |
|---|---|---|
| `kms:kms-core` | 82.9% ✅ | security review · interop evidence · documented limits |
| `credentials:plugins:verifiable-intent` | 90.0% ✅ | security review · documented limits (interop evidence ✅) |
| `did:plugins:key` | 68.2% | coverage (+1.8) · security review · interop evidence · documented limits |
| `did:plugins:web` | 21.5% | coverage (+48.5) · security review · interop evidence · documented limits |
| `did:did-core` | 58.4% | coverage (+11.6) · security review · interop evidence · documented limits |
| `credentials:credential-api` | 68.0% | coverage (+2.0) · security review · interop evidence · documented limits |
| `wallet:wallet-core` | 30.4% | coverage (+39.6) · security review · interop evidence · documented limits |
| one KMS provider | `kms:plugins:aws` 26.6% | coverage (+43.4) · **a completed custody qualification run** · documented limits |

The KMS provider row is the one that does not compress: a qualified custody profile needs the
live qualification to have actually run, which needs an AWS account with the three keys and the
IAM deny in place. That is the same blocker as A3 in the 13 September review.

## Changing a classification

`scripts/check-capability-coverage.py` enforces two things: every module carries a maturity from
`{stub, experimental, supported}`, and the count of unassessed modules can only fall. It is now
zero, so the ratchet is closed — a new module must be classified in the commit that adds it.

The classification is reproducible rather than remembered: it is derived from per-module coverage
and test evidence by the rule above. To promote a module, add the missing evidence, link it here,
and change the entry — in that order.
