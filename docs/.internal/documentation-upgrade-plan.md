---
title: Documentation Context Upgrade Plan
---

# Documentation Context Upgrade Plan

This plan inventories every section in the TrustWeave documentation set and sketches how we will add contextual framing and per-snippet explanations. We will work area by area, updating code blocks with “What it does / Why it matters / Result” captions and introducing narrative glue where it is currently missing.

## Introduction
- `introduction/README.md`, `what-is-TrustWeave.md`, `key-features.md`, `use-cases.md`: keep the high-level story but add brief paragraphs explaining how each example relates to day-to-day SDK usage. No code snippets present.
- `introduction/architecture-overview.md`: already updated with a mermaid flow; ensure each embedded snippet (if any) has captions. Currently none; no action required.

## Getting Started
- `getting-started/README.md`: add overview paragraphs linking the scenarios to business outcomes.
- `installation.md`, `project-setup.md`, `your-first-application.md`: convert bullet procedures into numbered steps with inline “Purpose / Result” notes. Annotate any shell snippets (installation commands) with the expected output or side effects.
- `quick-start.md`: code snippets already partially annotated; add missing context lines for verification/anchoring sections.
- Scenario guides (`earth-observation`, `academic-credentials`, `national-education`, `professional`, `proof-of-location`, `spatial-web-authorization`, `digital-workflow-provenance`, `news-industry`, `data-catalog-dcat`, `iot-device-identity`, `healthcare`, `supply-chain`): for each Kotlin block, introduce a short lead-in (domain goal) and close with the workflow outcome. Ensure legacy map-based configuration is replaced with typed options.

## Core Concepts
- `core-concepts/README.md`: summarise why each concept page exists; no snippets.
- `dids.md`, `verifiable-credentials.md`, `wallets.md`, `blockchain-anchoring.md`, `key-management.md`, `json-canonicalization.md`, `trust-registry.md`: already structured with “What/Why/How” but audit for any anonymous code blocks and add explicit explanations before/after each.

## Core Modules
- `modules/*.md`: for each module page, add an introductory paragraph that explains how the module composes with the rest of TrustWeave. Annotate module-specific Kotlin or Gradle snippets with usage rationale.

## Integration Modules
- `integrations/*.md`: verify each integration example (walt.id, GoDiddy, Algorand, Polygon) uses typed options and add before/after commentary for every snippet describing configuration steps and expected results (e.g., “Registers GoDiddy issuer, returns OkHttp-backed client”).

## API Reference
- `api-reference/README.md` and individual API pages: tables already in place. For every Kotlin code sample, add a short caption enumerating parameters, return values, and success criteria.

## Tutorials
- `tutorials/README.md` and individual tutorials: convert section transitions into scenario narratives. Each code block should have a preceding “Goal” sentence and a following “Outcome” sentence.

## Advanced Topics
- `advanced/README.md`: note which advanced guides exist and their intended audience.
- `key-rotation.md`, `verification-policies.md`: audit existing snippets for explanation; add missing “Why” lines around code. Remaining pages (SPI, adapters, error handling, testing, performance) require a full refresh with context + snippet annotations.

## Best Practices
- `best-practices/*.md`: for each guideline, add concrete examples or links. Ensure code samples (if any) have explanation blocks.

## Contributing, FAQ, Licensing, SaaS
- `contributing/README.md` and subpages: expand sections with reasoning for each step, annotate command snippets with expected output.
- `faq.md`: keep Q/A style but add references back to detailed guides where code is shown.
- `licensing/README.md`: no code, ensure context already sufficient.
- `saas/*.md`: audit for outdated package names and add textual explanation to the example blocks (many currently show configuration JSON or Kotlin snippets without commentary).

## Execution Approach
1. Sweep documents folder by folder, starting with `getting-started/` scenarios where snippet coverage is heaviest.
2. For each file, follow this checklist:
   - Introduce purpose/context paragraphs for each major section.
   - For every code block, add a preceding “What this snippet does” lead-in and a trailing “Result / Next step” explanation.
   - Replace any outdated API usage discovered along the way.
3. Update cross-links as needed to keep navigation consistent.
4. After each batch, run `./gradlew build` to ensure example code still compiles.

Progress will be tracked via the active TODOs. Once the inventory is complete, we will mark `doc-review-roadmap` as done and move to `section-refresh` for the actual content rewrites.

