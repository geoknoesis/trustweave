# W3C DID test suite (DID Core / DID 1.1–aligned data model)

The official suite lives at **[w3c/did-test-suite](https://github.com/w3c/did-test-suite)**. It exercises DID identifier rules, document properties, JSON production/consumption, resolution, and URL dereferencing against **registered implementation JSON fixtures** (and resolvers where configured).

> **Important:** A default run of `npm run test` **does not call TrustWeave**. It validates the test harness plus all implementations already checked into that repo. To appear on the [implementation report](https://w3c.github.io/did-test-suite/), TrustWeave would need an [implementations/*.json](https://github.com/w3c/did-test-suite/tree/main/packages/did-core-test-server/suites/implementations) entry and a PR to the W3C repo (or a local fork with your fixture).

Many tests remain relevant for **DID 1.1** (syntax, core properties, consumption/production of JSON representations), even though the suite historically targets DID Core 1.0.

## Prerequisites

- Node.js](https://nodejs.org/) (≥ 14; LTS recommended)
- Git](https://git-scm.com/)

## Run locally

```powershell
# Shallow clone (any directory; example uses temp)
$dir = Join-Path $env:TEMP "w3c-did-test-suite"
git clone --depth 1 https://github.com/w3c/did-test-suite.git $dir
cd $dir

npm install
npm run test
```

Optional: generate the HTML/JSON implementation report (slower):

```powershell
npm run test-and-generate-report
```

## Example successful run (upstream suite)

| Package              | Result                                      |
|----------------------|---------------------------------------------|
| `jest-did-matcher`   | 13 suites, **185** tests passed             |
| `did-core-test-server` | 6 suites, **12 124** passed, 65 todo      |

Exit code **0** = all executed tests passed (todo items are skipped placeholders).

## Relating this to TrustWeave

1. **Unit/integration tests in this repo** (`DidDocumentJsonParser`, `DidDocumentJsonProducer`, `DidValidator`, etc.) exercise TrustWeave directly.
2. **W3C suite** = interoperability contract; add a fixture produced by `DidDocumentJsonProducer` / round-tripped through `DidDocumentJsonParser` under `implementations/trustweave.json` in a clone, wire it into `default.js` for `did-consumption` / `did-production` / `did-core-properties`, then run `npm run test` to see TrustWeave-shaped documents against the same matchers.

See also [did-1-1-implementation-report.md](./did-1-1-implementation-report.md).
