# Changelog

All notable API changes are described here. The project does not yet follow strict semantic versioning in this file; treat entries as migration notes.

## [0.6.0] - 2025-03-23

### Added

- ktlint plugin for code formatting (`./gradlew ktlintCheck`, `./gradlew ktlintFormat`)
- Experimental/Stub Plugins section in docs/plugins.md documenting plugins not yet fully implemented
- TrustWeaveExtensionsTest: createDidAndIssue and createDidIssueAndStore tests via TrustWeave API

### Changed

- Version set to 0.6.0 for release
- Documentation links in README updated to point to correct docs/ paths
- SECURITY.md supported versions updated to 0.6.x
- CredentialStorage: KDoc for notRevoked(), revoked(), valid() documenting revocation handling limitations
- revoked() filter now returns false (accurate status requires CredentialRevocationManager)
- CloudHSM KMS: Fixed GitHub URL typo in error message
- DelegationDslTest: Marked @Disabled with clear reason (DelegationService not yet implemented)
- Testkit: Removed CredentialServiceRegistry TODOs and dead code
- Removed orphaned distribution/trustweave-bom (referenced non-existent projects)

### Fixed

- README links (GETTING_STARTED.md, API_GUIDE.md, etc.) now point to existing docs
- Documentation artifact coordinates aligned with build (org.trustweave:anchors-plugins-*, kms-plugins-*, did-plugins-*)

---

## Unreleased

- Presentations: use **`presentationResult`** / **`presentationFromWalletResult`** and **`buildResult()`** only; throwing helpers **`TrustWeave.presentation`**, **`presentationFromWallet`**, **`PresentationBuilder.build()`**, and **`WalletPresentationBuilder.build()`** have been removed.
- Configuration: use **`credentialService`** and **`getCredentialService()`**; **`TrustWeaveConfig.issuer`** and **`TrustWeave.getIssuer()`** have been removed.
- Test sources: legacy **`TrustLayer*`** / **`InMemoryTrustLayer*`** class and file names renamed to **`TrustWeave*`** / **`InMemoryTrustWeave*`** for consistent terminology.
- **`docs/README.md`**: examples now use **`TrustWeave`** / **`trustWeave`** and current result-style APIs (aligned with the root **`README.md`** quick start).
- **Documentation**: contributing test templates consolidated as **`trustweave-test-templates.md`** (links updated from **`trust-layer-test-templates.md`** in **`writing-tests.md`** and **`modules/trustweave-testkit.md`**); **`atlas-parametric-architecture-overview.md`** snippets aligned with **`DidCreationResult`**, **`DidResolutionResult`**, **`IssuanceResult.getOrThrow()`**, and **`BlockchainService.anchor(..., serializer, chainId)`**; **`wallet-api.md`**, **`parametric-insurance-mga-implementation-guide.md`**, and example **`println`** copy avoid the deprecated product name “Trust Layer” where **TrustWeave** is meant.
- **Documentation (sweep)**: Source **`docs/**/*.md`** (and mirrored **`docs/_site/**/*.md`**) updated for **`TrustWeave.build { }`** instead of assigning **`trustWeave { }`** to a facade variable; removed **`getDslContext()`** in favor of **`trustWeave.configuration`**, **`resolveDid`**, and **`trustWeave.revocation { }`**; **`org.trustweave.credential.model.vc.*`** imports; **`core-api.md`** quick reference, DID resolve/update, anchoring **`read`**, and advanced sections aligned with current APIs; **`architecture-overview.md`**, **`dsl-guide.md`**, **`trust-registry.md`**, **`mental-model.md`**, and **`STYLE_GUIDE.md`** terminology refreshed.
- **Documentation (follow-up)**: **`api-patterns.md`** DID update/rotate examples match **`DidDocument`** returns; **`mental-model`** / **`architecture-overview`** diagrams drop **`TrustWeaveContext`**; **`blockchain-anchoring.md`** fixes read/anchor examples (**`BlockchainException`**, not **`Result`+`TrustWeaveError`**); **`readAnchor`** renamed to **`blockchains.read`** across scenarios/tutorials; **`api-reference/README.md`**, **`error-handling.md`** (wallet **`WalletCreationResult`**, **`signedBy(did)`**), **`dids.md`**, and **`smart-contracts.md`** error text aligned with current behavior.
- Removed historical / internal audit markdown from **`docs/`** (phase summaries, documentation-improvement trackers, navigation meta, VC API cleanup logs, protocol code-review notes, **`API_SCORE`**, duplicate production-readiness evaluations under **`features/credential-exchange-protocols/`**, etc.); user-facing guides under **`getting-started`**, **`how-to`**, **`introduction`**, **`api-reference`**, and **`scenarios`** are unchanged.
