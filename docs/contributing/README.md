# Contributing to VeriCore Docs

Thanks for helping improve the documentation! Clear, actionable docs are one of VeriCore’s biggest features. Follow the guidelines below to keep everything consistent and discoverable.

## Filing Issues

- Document bugs or gaps via GitHub issues (`docs` label). Include:
  - A short summary of the missing or confusing content.
  - Page links and reproduction steps if you spotted outdated examples.
- For typos or small fixes, feel free to open a pull request directly.

## Writing Style

- **Audience** – experienced Kotlin developers who value clarity and concise guidance.
- **Tone** – conversational but professional. Avoid marketing fluff; emphasize how-tos and rationale.
- **Formatting** – use Markdown with ATX headings. Prefer code fences with language tags (`kotlin`, `bash`, `json`).
- **Terminology** – treat “VeriCore” as the product name; refer to modules in inline code (`vericore-trust`).
- **Links** – make sure all relative links resolve from within `docs/`. Run the broken-link checker (planned) or manually verify paths.

## Structure

- **Getting Started** – quick starts, installation, tutorials. Use incremental numbered steps.
- **Core Concepts** – conceptual explanations with light code. Each top-level topic should have `Overview`, `Why it matters`, and `Next steps` sections.
- **API Reference** – stays in sync with code; mention package names and important defaults.
- **Tutorials** – end-to-end flows. Include prerequisites, numbered steps, and a “What’s next” section.

## Submitting Changes

1. Fork and create a feature branch (`docs/my-improvement`).
2. Update or add Markdown files. Keep line length under ~120 chars for readability.
3. Run `./gradlew dokkaHtml` (if available) or at minimum `./gradlew build` to ensure samples compile. When you touch quick-start content, also run `./gradlew :vericore-examples:runQuickStartSample` to keep the runnable demo green.
4. Preview Markdown locally (VS Code, GitBook, or static site generator).
5. Submit a PR describing the change. Mention related issues and screenshots when relevant.

## Code Samples

- Use idiomatic Kotlin (coroutines, `Result`, extension DSLs).
- Prefer concise examples that compile. Include imports when not obvious.
- When referencing build files, show `build.gradle.kts` snippets.
- Tie examples back to runnable projects where possible (e.g., link to `vericore-examples:runQuickStartSample`).

## Diagrams and Media

- Store assets under `docs/assets/`. Optimize PNG/SVG sizes (<500 KB).
- Provide alt text for accessibility.

## Licensing

- Contributions are accepted under the project’s license. Do not include third-party material that has conflicting licenses.

## Documentation Verification Checklist

Run these commands before submitting substantial documentation changes:

- `./gradlew build` — ensures Kotlin samples (including docs/quick-start) compile.
- `./gradlew :vericore-examples:runQuickStartSample` — validates the end-to-end quick-start flow.
- Manual link review for new or renamed pages (link checker automation is planned).

If you have questions, open an issue or reach out on the project discussion board. Thanks again for contributing! 

