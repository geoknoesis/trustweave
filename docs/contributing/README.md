# Contributing to VeriCore Docs

VeriCore’s documentation is authored and maintained by **Geoknoesis LLC**, and community contributions are welcome. Use this guide as your checklist when filing issues or opening pull requests—every section explains the intent so that newcomers can match the house style quickly.

## Filing Issues

- **Purpose:** Capture bugs, outdated snippets, or missing context.  
  - Open a GitHub issue tagged `docs`, include the affected page paths, and describe what readers currently experience.  
  - Attach reproduction steps or screenshots when the problem involves runnable code or failing commands.  
- **Small fixes:** For obvious typos or broken links, feel free to submit a PR directly; call out any assumptions you made.

## Writing Style

- **Audience** – experienced Kotlin developers who value clarity and concise guidance.
- **Tone** – conversational but professional. Avoid marketing fluff; emphasize how-tos and rationale.
- **Formatting** – use Markdown with ATX headings. Prefer code fences with language tags (`kotlin`, `bash`, `json`).
- **Terminology** – treat “VeriCore” as the product name; refer to modules in inline code (`vericore-trust`).
- **Snippets** – every code block must be bracketed by prose such as **What this does**, **Result**, or **Design significance**. Keep explanations *outside* the fenced block.
- **Links** – make sure all relative links resolve from within `docs/`. Manual link sweeps are expected until the automated checker (tracked in `layout/migration-roadmap.md`) lands.

## Structure

- **Getting Started** – quick starts, installation, tutorials. Use incremental numbered steps.
- **Core Concepts** – conceptual explanations with light code. Each top-level topic should have `Overview`, `Why it matters`, and `Next steps` sections.
- **API Reference** – stays in sync with code; mention package names and important defaults.
- **Tutorials** – end-to-end flows. Include prerequisites, numbered steps, and a “What’s next” section.

## Submitting Changes

1. **Branching:** Fork and create a feature branch (`docs/my-improvement`) so reviewers can scope the diff easily.
2. **Editing:** Update or add Markdown files. Keep line length under ~120 characters and follow the snippet narration rules above.
3. **Verification:**  
   - `./gradlew build` — ensures Kotlin samples and fixtures still compile.  
   - `./gradlew :vericore-examples:runQuickStartSample` — required whenever you touch quick-start instructions or referenced code.
4. **Preview:** Render Markdown locally (VS Code markdown preview, GitBook CLI, or equivalent) to check formatting.
5. **Pull request:** Describe the change, list affected pages, mention related issues, and attach screenshots for visual updates.

## Code Samples

- Use idiomatic Kotlin (coroutines, `Result`, extension DSLs).
- Prefer concise examples that compile. Include imports when not obvious.
- When referencing build files, show `build.gradle.kts` snippets.
- Tie examples back to runnable projects where possible (e.g., link to `vericore-examples:runQuickStartSample`).
- Annotate each snippet with **What it does / Result / Design significance** so readers understand the intent immediately.

## Diagrams and Media

- Store assets under `docs/assets/`. Optimize PNG/SVG sizes (<500 KB).
- Provide alt text for accessibility.

## Licensing

- Contributions are accepted under the project’s license. Do not include third-party material that has conflicting licenses.

## Documentation Verification Checklist

**Goal:** ensure documentation edits do not break runnable samples or navigation.

- `./gradlew build` — ensures Kotlin samples (including docs/quick-start) compile.
- `./gradlew :vericore-examples:runQuickStartSample` — validates the end-to-end quick-start flow.
- Manual link review for new or renamed pages (link checker automation is planned).

If you have questions, open an issue or reach out on the project discussion board. Thanks again for helping us keep VeriCore’s documentation top-tier.

