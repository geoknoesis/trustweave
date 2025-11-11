# Layout Migration Roadmap

## Phase 0 – Preparation
- Publish an ADR summarizing the new module layout (core, trust, spi, integrations).
- Create Gradle module skeletons (`vericore-trust`, `vericore-spi`) with empty `package-info` placeholders and updated settings.gradle entries.
- Add documentation placeholder pages referencing the upcoming split (`docs/modules/vericore-trust.md`, `docs/modules/vericore-spi.md`).

## Phase 1 – SPI Extraction ✅
1. Move service locator + adapter loader code from `com.geoknoesis.vericore.core.services` into `vericore-spi`.
2. Relocate plugin descriptor classes (`com.geoknoesis.vericore.spi`) into the same module.
3. Update dependent modules (`vericore-did`, `vericore-anchor`, integrations) to depend on `vericore-spi` instead of `vericore-core`.
4. Run Gradle build to ensure dependency graph remains acyclic; adjust package imports accordingly.

## Phase 2 – Trust Runtime Split ✅
1. Migrate `com.geoknoesis.vericore.trust` and supporting DSL runtime pieces from `vericore-core` into `vericore-trust`.
2. Within `vericore-core`, keep only credential-facing DSL entry points; refactor DSL builders to delegate to `vericore-trust` contexts.
3. Update `vericore-all` and examples to include the new module dependency.
4. Expand docs to highlight configurations now available via `vericore-trust`.

## Phase 3 – Package Cleanup
1. Move `com.geoknoesis.vericore.did.delegation` into `vericore-did`.
2. Evaluate whether wallet abstractions warrant their own module (`vericore-wallet`); if not, consolidate package naming (`credential.wallet.*` -> `wallet.*`).
3. Consolidate blockchain integration helpers into a shared package or module and update adapter modules accordingly.

## Phase 4 – Documentation & Communication
- Refresh module READMEs to reflect new responsibilities.
- Update `docs/modules/overview.md` and scenario docs to point to the relocated APIs.
- Provide migration guidance for downstream integrators (new dependencies, package imports).

## Phase 5 – Cleanup
- Deprecate old package imports (if any) using typealiases and remove once downstream projects migrate.
- Remove temporary forwarding APIs after one release cycle.

