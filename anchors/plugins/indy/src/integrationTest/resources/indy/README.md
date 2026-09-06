# Disposable Indy integration fixture

The pinned BC Gov node image runs four real validator processes with generated
local genesis transactions. A small test-only HTTP adapter sends the SDK's signed
requests to native `indy-vdr` and returns the ledger reply. The ledger browser is
not a VDR submission API. No hosted ledger, production key or account is used.

The genesis trustee seed in the test is public test data. Never reuse it in a
persistent ledger. Only the HTTP adapter port is exposed; validator connections
remain inside the container. Testcontainers removes the container after the suite.

Run `TRUSTWEAVE_INDY_INTEGRATION=required ./gradlew :anchors:plugins:indy:integrationTest`
on a Docker-equipped machine. CI and release evidence require this mode: missing
Docker or failed consensus is a failure, never a successful skipped qualification.
Default local mode skips only when Docker is unavailable and produces no live evidence.

This fixture validates SDK requests against local Indy consensus and the native
VDR library. It does not qualify a hosted deployment or the standalone Rust proxy's
packaging, TLS, IAM or operational configuration. The node image is legacy software
used only in this isolated test fixture.

Sources: [BC Gov VON network](https://github.com/bcgov/von-network/tree/0885f85f70ae3a1bba8512895c48bebafd50c03a)
and [Indy VDR](https://github.com/hyperledger-indy/indy-vdr).

The client currently uses one named attribute per DID. A subsequent write replaces the
readable value. Reading an earlier sequence number fails closed; this integration
test checks that it never returns the replacement under an older reference. Historical
anchor retrieval requires a separately qualified history implementation.
