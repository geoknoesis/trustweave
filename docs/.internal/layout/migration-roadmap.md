# Layout Migration Roadmap

## Phase 0 – Preparation
- Publish an ADR summarizing the new module layout (core, trust, spi, integrations).
- Create Gradle module skeletons (`TrustWeave-trust`, `TrustWeave-spi`) with empty `package-info` placeholders and updated settings.gradle entries.
- Add documentation placeholder pages referencing the upcoming split (`docs/modules/TrustWeave-trust.md`, `docs/modules/TrustWeave-spi.md`).

## Phase 1 – SPI Extraction ✅
1. Move service locator + adapter loader code from `com.trustweave.core.services` into `TrustWeave-spi`.
2. Relocate plugin descriptor classes (`com.trustweave.spi`) into the same module.
3. Update dependent modules (`TrustWeave-did`, `TrustWeave-anchor`, integrations) to depend on `TrustWeave-spi` instead of `TrustWeave-core`.
4. Run Gradle build to ensure dependency graph remains acyclic; adjust package imports accordingly.

## Phase 2 – Trust Runtime Split ✅
1. Migrate `com.trustweave.trust` and supporting DSL runtime pieces from `TrustWeave-core` into `TrustWeave-trust`.
2. Within `TrustWeave-core`, keep only credential-facing DSL entry points; refactor DSL builders to delegate to `TrustWeave-trust` contexts.
3. Update `TrustWeave-all` and examples to include the new module dependency.
4. Expand docs to highlight configurations now available via `TrustWeave-trust`.

## Phase 3 – Package Cleanup
1. Move `com.trustweave.did.delegation` into `TrustWeave-did`.
2. Evaluate whether wallet abstractions warrant their own module (`TrustWeave-wallet`); if not, consolidate package naming (`credential.wallet.*` -> `wallet.*`).
3. Consolidate blockchain integration helpers into a shared package or module and update adapter modules accordingly.

## Phase 4 – Documentation & Communication
- Refresh module READMEs to reflect new responsibilities.
- Update `docs/modules/overview.md` and scenario docs to point to the relocated APIs.
- Provide migration guidance for downstream integrators (new dependencies, package imports).

## Phase 5 – Cleanup
- Deprecate old package imports (if any) using typealiases and remove once downstream projects migrate.
- Remove temporary forwarding APIs after one release cycle.

